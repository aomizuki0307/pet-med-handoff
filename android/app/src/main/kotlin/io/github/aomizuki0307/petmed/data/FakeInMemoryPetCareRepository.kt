package io.github.aomizuki0307.petmed.data

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * mockフレーバー用のインメモリ実装。認証情報なしで全フローを手動確認できる。
 * main source set に置くのは、prodフレーバーが Firebase 未構成時のフォールバックとして、
 * また単体テストがそのまま使うため。
 */
class FakeInMemoryPetCareRepository(
    private val clock: Clock = Clock.systemDefaultZone(),
    seedDemoData: Boolean = false,
) : PetCareRepository {

    override val isBackendConfigured: Boolean = false

    override val hasPersistedHousehold: Boolean = false // インメモリのため永続世帯なし

    private val myUid = "local-user"
    private var myName = ""

    private val _householdState = MutableStateFlow<HouseholdState?>(null)
    override val householdState: StateFlow<HouseholdState?> = _householdState

    private val _doseRecords = MutableStateFlow<List<DoseRecord>>(emptyList())
    override val doseRecords: StateFlow<List<DoseRecord>> = _doseRecords

    init {
        if (seedDemoData) seedDemo()
    }

    override suspend fun createHousehold(displayName: String, householdName: String) {
        myName = displayName
        val hh = Household(
            id = UUID.randomUUID().toString(),
            name = householdName,
            createdByUid = myUid,
            createdAt = clock.instant(),
        )
        _householdState.value = HouseholdState(
            household = hh,
            members = listOf(Member(myUid, displayName, isOwner = true, joinedAt = clock.instant())),
            pets = emptyList(),
            medications = emptyList(),
            myUid = myUid,
            myDisplayName = displayName,
        )
    }

    override suspend fun joinHousehold(code: String, displayName: String) {
        // モックでは "000000" 以外を有効な招待として扱い、デモ世帯に参加する
        if (code == "000000") throw PetCareRepository.JoinException("invalid code")
        myName = displayName
        seedDemo()
        val st = _householdState.value ?: return
        _householdState.value = st.copy(
            members = st.members + Member(myUid, displayName, isOwner = false, joinedAt = clock.instant()),
            myUid = myUid,
            myDisplayName = displayName,
        )
    }

    override suspend fun addPet(name: String, species: Species, birthYear: Int?): String {
        val st = requireState()
        val pet = Pet(id = UUID.randomUUID().toString(), name = name, species = species, birthYear = birthYear)
        _householdState.value = st.copy(pets = st.pets + pet)
        return pet.id
    }

    override suspend fun updatePet(pet: Pet) {
        val st = requireState()
        _householdState.value = st.copy(pets = st.pets.map { if (it.id == pet.id) pet else it })
    }

    override suspend fun addMedication(medication: Medication) {
        val st = requireState()
        val med = if (medication.id.isBlank()) medication.copy(id = UUID.randomUUID().toString()) else medication
        _householdState.value = st.copy(medications = st.medications + med)
    }

    override suspend fun updateMedication(medication: Medication) {
        val st = requireState()
        _householdState.value =
            st.copy(medications = st.medications.map { if (it.id == medication.id) medication else it })
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
        // 冪等: 同一clientRecordIdは無視（通知アクション二重発火対策）
        if (_doseRecords.value.any { it.clientRecordId == clientRecordId }) return
        val record = DoseRecord(
            id = UUID.randomUUID().toString(),
            petId = petId,
            medId = medId,
            slotId = slotId,
            slotDate = slotDate,
            status = status,
            cancelsRecordId = cancelsRecordId,
            scheduledAt = scheduledAt,
            recordedAt = clock.instant(),
            recordedByUid = myUid,
            recordedByName = myName.ifBlank { "わたし" },
            source = source,
            clientRecordId = clientRecordId,
        )
        _doseRecords.value = _doseRecords.value + record
    }

    override suspend fun createInvite(): String = (100000..999999).random().toString()

    override suspend fun updateDisplayName(name: String) {
        myName = name
        val st = requireState()
        _householdState.value = st.copy(
            members = st.members.map { if (it.uid == myUid) it.copy(displayName = name) else it },
            myDisplayName = name,
        )
    }

    override suspend fun markPurchaseIntent(plan: String) {
        val st = requireState()
        _householdState.value =
            st.copy(household = st.household.copy(planTier = PlanTier.PAID_INTENT))
    }

    override suspend fun deleteMyAccount() {
        _householdState.value = null
        _doseRecords.value = emptyList()
    }

    override suspend fun deleteHousehold() {
        _householdState.value = null
        _doseRecords.value = emptyList()
    }

    private fun requireState(): HouseholdState =
        checkNotNull(_householdState.value) { "household not initialized" }

    /** デモデータ: 高齢猫1匹 + 薬2件 + 別メンバーの投与記録（二重警告の確認用） */
    private fun seedDemo() {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val hh = Household(
            id = "demo-household",
            name = "デモの家",
            createdByUid = "demo-mom",
            createdAt = now.minusSeconds(3600 * 24 * 3),
        )
        val cat = Pet(id = "demo-cat", name = "ちゃちゃ", species = Species.CAT, birthYear = 2010)
        val medKidney = Medication(
            id = "demo-med-kidney",
            petId = cat.id,
            name = "腎臓の薬",
            dosageText = "1回1錠 朝夕",
            slots = listOf(
                ScheduleSlot("slot-am", LocalTime.of(8, 0), "朝"),
                ScheduleSlot("slot-pm", LocalTime.of(20, 0), "夜"),
            ),
            daysOfWeek = (1..7).toSet(),
            startDate = today.minusDays(30),
        )
        val medHeart = Medication(
            id = "demo-med-heart",
            petId = cat.id,
            name = "心臓の薬",
            dosageText = "1回半錠 朝のみ",
            slots = listOf(ScheduleSlot("slot-am", LocalTime.of(8, 0), "朝")),
            daysOfWeek = (1..7).toSet(),
            startDate = today.minusDays(30),
        )
        val mom = Member("demo-mom", "お母さん", isOwner = true, joinedAt = now.minusSeconds(3600 * 24 * 3))
        _householdState.value = HouseholdState(
            household = hh,
            members = listOf(mom),
            pets = listOf(cat),
            medications = listOf(medKidney, medHeart),
            myUid = myUid,
            myDisplayName = myName,
        )
        // 「お母さんが朝8:02に腎臓の薬を投与済み」→ 二重警告の確認ができる
        val scheduled = java.time.LocalDateTime.of(today, LocalTime.of(8, 0))
            .atZone(clock.zone).toInstant()
        _doseRecords.value = listOf(
            DoseRecord(
                id = UUID.randomUUID().toString(),
                petId = cat.id,
                medId = medKidney.id,
                slotId = "slot-am",
                slotDate = today,
                status = DoseStatus.GIVEN,
                cancelsRecordId = null,
                scheduledAt = scheduled,
                recordedAt = scheduled.plusSeconds(120),
                recordedByUid = mom.uid,
                recordedByName = mom.displayName,
                source = RecordSource.APP,
                clientRecordId = UUID.randomUUID().toString(),
            )
        )
    }
}

/** Logcat出力のみの分析ロガー（mock用） */
class NoopAnalyticsLogger : AnalyticsLogger {
    override fun log(name: String, params: Map<String, Any?>) {
        android.util.Log.d("Analytics", "$name $params")
    }
}
