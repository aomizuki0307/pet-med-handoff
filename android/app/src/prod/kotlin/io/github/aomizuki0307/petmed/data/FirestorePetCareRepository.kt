package io.github.aomizuki0307.petmed.data

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import io.github.aomizuki0307.petmed.domain.model.DoseRecord
import io.github.aomizuki0307.petmed.domain.model.DoseStatus
import io.github.aomizuki0307.petmed.domain.model.Household
import io.github.aomizuki0307.petmed.domain.model.HouseholdState
import io.github.aomizuki0307.petmed.domain.model.Medication
import io.github.aomizuki0307.petmed.domain.model.Member
import io.github.aomizuki0307.petmed.domain.model.Pet
import io.github.aomizuki0307.petmed.domain.model.PlanTier
import io.github.aomizuki0307.petmed.domain.model.RecordSource
import io.github.aomizuki0307.petmed.domain.model.ScheduleSlot
import io.github.aomizuki0307.petmed.domain.model.Species
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Firestore実装。ドキュメント構成は docs/05_data_model.md が正典。
 * オフライン: Firestore組込みの永続キャッシュ+書込キューに委ねる。
 * doseRecords は append-only（rulesで update/delete 拒否）。
 */
class FirestorePetCareRepository(
    context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val scope: CoroutineScope,
) : PetCareRepository {

    override val isBackendConfigured = true

    private val prefs = context.getSharedPreferences("firestore_repo", Context.MODE_PRIVATE)

    private val _householdState = MutableStateFlow<HouseholdState?>(null)
    override val householdState: StateFlow<HouseholdState?> = _householdState

    private val _doseRecords = MutableStateFlow<List<DoseRecord>>(emptyList())
    override val doseRecords: StateFlow<List<DoseRecord>> = _doseRecords

    private var listeners = mutableListOf<ListenerRegistration>()

    private var household: Household? = null
    private var members: List<Member> = emptyList()
    private var pets: List<Pet> = emptyList()
    private var medications: List<Medication> = emptyList()

    init {
        scope.launch {
            ensureSignedIn()
            prefs.getString(KEY_HID, null)?.let { attach(it) }
        }
    }

    private suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user!!.uid
    }

    val currentHouseholdId: String? get() = prefs.getString(KEY_HID, null)

    // ---------- listeners ----------

    private fun attach(hid: String) {
        detach()
        val hhRef = db.collection("households").document(hid)

        listeners += hhRef.addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                household = snap.toHousehold()
                publish()
            }
        }
        listeners += hhRef.collection("members").addSnapshotListener { snap, _ ->
            if (snap != null) {
                members = snap.documents.map { it.toMember() }
                publish()
            }
        }
        listeners += hhRef.collection("pets").addSnapshotListener { snap, _ ->
            if (snap != null) {
                pets = snap.documents.map { it.toPet() }
                publish()
            }
        }
        listeners += hhRef.collection("medications").addSnapshotListener { snap, _ ->
            if (snap != null) {
                medications = snap.documents.mapNotNull { runCatching { it.toMedication() }.getOrNull() }
                publish()
            }
        }
        listeners += hhRef.collection("doseRecords")
            .orderBy("recordedAt", Query.Direction.DESCENDING)
            .limit(1000)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    _doseRecords.value = snap.documents.mapNotNull { doc ->
                        runCatching { doc.toDoseRecord() }.getOrNull()
                    }
                }
            }
    }

    private fun detach() {
        listeners.forEach { it.remove() }
        listeners.clear()
        household = null
        members = emptyList()
        pets = emptyList()
        medications = emptyList()
        _householdState.value = null
        _doseRecords.value = emptyList()
    }

    private fun publish() {
        val hh = household ?: return
        val uid = auth.currentUser?.uid ?: return
        _householdState.value = HouseholdState(
            household = hh,
            members = members,
            pets = pets,
            medications = medications,
            myUid = uid,
            myDisplayName = members.find { it.uid == uid }?.displayName ?: "",
        )
    }

    // ---------- commands ----------

    override suspend fun createHousehold(displayName: String, householdName: String) {
        val uid = ensureSignedIn()
        val hhRef = db.collection("households").document()
        val batch = db.batch()
        batch.set(
            hhRef,
            mapOf(
                "name" to householdName,
                "createdByUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "planTier" to "free",
            ),
        )
        batch.set(
            hhRef.collection("members").document(uid),
            mapOf(
                "displayName" to displayName,
                "role" to "owner",
                "joinedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()
        prefs.edit().putString(KEY_HID, hhRef.id).apply()
        attach(hhRef.id)
    }

    override suspend fun joinHousehold(code: String, displayName: String) {
        val uid = ensureSignedIn()
        val inviteSnap = db.collection("invites").document(code).get().await()
        if (!inviteSnap.exists()) throw PetCareRepository.JoinException("not found")
        val hid = inviteSnap.getString("hid") ?: throw PetCareRepository.JoinException("broken invite")
        val expiresAt = inviteSnap.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
        val revoked = inviteSnap.getBoolean("revoked") ?: false
        if (revoked || expiresAt < System.currentTimeMillis()) {
            throw PetCareRepository.JoinException("expired")
        }
        // rules側でも inviteCode の get() 検証を行う（docs/05）
        db.collection("households").document(hid)
            .collection("members").document(uid)
            .set(
                mapOf(
                    "displayName" to displayName,
                    "role" to "member",
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "inviteCode" to code,
                ),
            ).await()
        prefs.edit().putString(KEY_HID, hid).apply()
        attach(hid)
    }

    private fun hhRef() = db.collection("households")
        .document(checkNotNull(currentHouseholdId) { "no household" })

    override suspend fun addPet(name: String, species: Species, birthYear: Int?) {
        hhRef().collection("pets").document().set(
            mapOf(
                "name" to name,
                "species" to species.name.lowercase(),
                "birthYear" to birthYear,
                "note" to "",
                "archived" to false,
            ),
        ).await()
    }

    override suspend fun updatePet(pet: Pet) {
        hhRef().collection("pets").document(pet.id).set(
            mapOf(
                "name" to pet.name,
                "species" to pet.species.name.lowercase(),
                "birthYear" to pet.birthYear,
                "note" to pet.note,
                "archived" to pet.archived,
            ),
        ).await()
    }

    override suspend fun addMedication(medication: Medication) {
        val ref = hhRef().collection("medications").document()
        ref.set(medication.toMap()).await()
    }

    override suspend fun updateMedication(medication: Medication) {
        hhRef().collection("medications").document(medication.id).set(medication.toMap()).await()
    }

    override suspend fun recordDose(
        petId: String,
        medId: String,
        slotId: String,
        slotDate: LocalDate,
        status: DoseStatus,
        scheduledAt: Instant,
        source: RecordSource,
        cancelsRecordId: String?,
        clientRecordId: String,
    ) {
        // ドキュメントID=clientRecordId で冪等化（同一IDの再createはrulesで拒否される）
        val data = mapOf(
            "petId" to petId,
            "medId" to medId,
            "slotId" to slotId,
            "slotDate" to slotDate.toString(),
            "status" to status.name,
            "cancelsRecordId" to cancelsRecordId,
            "scheduledAt" to Timestamp(scheduledAt.epochSecond, scheduledAt.nano),
            "recordedAt" to FieldValue.serverTimestamp(),
            "recordedByUid" to (auth.currentUser?.uid ?: ""),
            "recordedByName" to (_householdState.value?.myDisplayName ?: ""),
            "source" to source.name.lowercase(),
            "clientRecordId" to clientRecordId,
        )
        runCatching {
            hhRef().collection("doseRecords").document(clientRecordId).set(data).await()
        }.onFailure { e ->
            // 既存ID（冪等な再送）は成功扱い。それ以外は再throw
            if (e.message?.contains("PERMISSION_DENIED") != true) throw e
        }
    }

    override suspend fun createInvite(): String {
        val hid = checkNotNull(currentHouseholdId)
        val code = (100000..999999).random().toString()
        db.collection("invites").document(code).set(
            mapOf(
                "hid" to hid,
                "createdByUid" to (auth.currentUser?.uid ?: ""),
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Instant.now().plusSeconds(72 * 3600).epochSecond, 0),
                "revoked" to false,
            ),
        ).await()
        return code
    }

    override suspend fun updateDisplayName(name: String) {
        val uid = auth.currentUser?.uid ?: return
        hhRef().collection("members").document(uid)
            .update("displayName", name).await()
    }

    override suspend fun markPurchaseIntent(plan: String) {
        hhRef().update("planTier", "paid_intent").await()
    }

    override suspend fun deleteMyAccount() {
        val uid = auth.currentUser?.uid ?: return
        val st = _householdState.value
        val isOwner = st?.members?.any { it.uid == uid && it.isOwner } == true
        val soleMember = (st?.members?.size ?: 0) <= 1
        if (isOwner && soleMember) {
            deleteHousehold()
            return
        }
        hhRef().collection("members").document(uid).delete().await()
        finishDeletion()
    }

    override suspend fun deleteHousehold() {
        val ref = hhRef()
        // クライアント側の再帰削除（30世帯規模。Cloud Functionsは使わない — docs/05）
        for (col in listOf("doseRecords", "medications", "pets", "members")) {
            while (true) {
                val snap = ref.collection(col).limit(200).get().await()
                if (snap.isEmpty) break
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        ref.delete().await()
        finishDeletion()
    }

    private suspend fun finishDeletion() {
        detach()
        prefs.edit().remove(KEY_HID).apply()
        runCatching { auth.currentUser?.delete()?.await() }
        auth.signOut()
    }

    // ---------- mapping ----------

    private fun DocumentSnapshot.toHousehold() = Household(
        id = id,
        name = getString("name") ?: "",
        createdByUid = getString("createdByUid") ?: "",
        createdAt = getTimestamp("createdAt")?.toInstant() ?: Instant.now(),
        planTier = when (getString("planTier")) {
            "paid_intent" -> PlanTier.PAID_INTENT
            "trial" -> PlanTier.TRIAL
            else -> PlanTier.FREE
        },
    )

    private fun DocumentSnapshot.toMember() = Member(
        uid = id,
        displayName = getString("displayName") ?: "",
        isOwner = getString("role") == "owner",
        joinedAt = getTimestamp("joinedAt")?.toInstant() ?: Instant.now(),
    )

    private fun DocumentSnapshot.toPet() = Pet(
        id = id,
        name = getString("name") ?: "",
        species = if (getString("species") == "dog") Species.DOG else Species.CAT,
        birthYear = getLong("birthYear")?.toInt(),
        note = getString("note") ?: "",
        archived = getBoolean("archived") ?: false,
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toMedication(): Medication {
        val slotList = (get("slots") as? List<Map<String, Any?>>).orEmpty().map {
            ScheduleSlot(
                slotId = it["slotId"] as? String ?: "",
                time = LocalTime.parse(it["time"] as? String ?: "08:00"),
                label = it["label"] as? String ?: "",
            )
        }
        return Medication(
            id = id,
            petId = getString("petId") ?: "",
            name = getString("name") ?: "",
            dosageText = getString("dosageText") ?: "",
            slots = slotList,
            daysOfWeek = (get("daysOfWeek") as? List<Long>).orEmpty().map { it.toInt() }.toSet(),
            startDate = LocalDate.parse(getString("startDate") ?: LocalDate.now().toString()),
            endDate = getString("endDate")?.let(LocalDate::parse),
            note = getString("note") ?: "",
            active = getBoolean("active") ?: true,
        )
    }

    private fun Medication.toMap() = mapOf(
        "petId" to petId,
        "name" to name,
        "dosageText" to dosageText,
        "slots" to slots.map {
            mapOf("slotId" to it.slotId, "time" to it.time.toString(), "label" to it.label)
        },
        "daysOfWeek" to daysOfWeek.toList(),
        "startDate" to startDate.toString(),
        "endDate" to endDate?.toString(),
        "note" to note,
        "active" to active,
    )

    private fun DocumentSnapshot.toDoseRecord() = DoseRecord(
        id = id,
        petId = getString("petId") ?: "",
        medId = getString("medId") ?: "",
        slotId = getString("slotId") ?: "",
        slotDate = LocalDate.parse(getString("slotDate")!!),
        status = DoseStatus.valueOf(getString("status") ?: "GIVEN"),
        cancelsRecordId = getString("cancelsRecordId"),
        scheduledAt = getTimestamp("scheduledAt")?.toInstant() ?: Instant.EPOCH,
        // serverTimestamp未確定（ローカルペンディング）の間は現在時刻で表示
        recordedAt = getTimestamp("recordedAt")?.toInstant() ?: Instant.now(),
        recordedByUid = getString("recordedByUid") ?: "",
        recordedByName = getString("recordedByName") ?: "",
        source = if (getString("source") == "notification") RecordSource.NOTIFICATION else RecordSource.APP,
        clientRecordId = getString("clientRecordId") ?: id,
    )

    private fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())

    companion object {
        private const val KEY_HID = "householdId"
    }
}
