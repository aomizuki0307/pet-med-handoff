package io.github.aomizuki0307.petmed.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.aomizuki0307.petmed.PetMedApp
import io.github.aomizuki0307.petmed.domain.DoseSlotCalculator
import io.github.aomizuki0307.petmed.domain.DoubleDoseDetector
import io.github.aomizuki0307.petmed.domain.FreeTierPolicy
import io.github.aomizuki0307.petmed.domain.model.DoseRecord
import io.github.aomizuki0307.petmed.domain.model.DoseSlotInstance
import io.github.aomizuki0307.petmed.domain.model.DoseStatus
import io.github.aomizuki0307.petmed.domain.model.HouseholdState
import io.github.aomizuki0307.petmed.domain.model.Medication
import io.github.aomizuki0307.petmed.domain.model.Pet
import io.github.aomizuki0307.petmed.domain.model.RecordSource
import io.github.aomizuki0307.petmed.domain.model.Species
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 今日の1スロット分の表示状態 */
data class TodaySlotUi(
    val slot: DoseSlotInstance,
    val given: List<DoseRecord>,
    val skipped: List<DoseRecord>,
) {
    val isDone: Boolean get() = given.isNotEmpty()
    val isDuplicate: Boolean get() = given.size >= 2
    val isSkipped: Boolean get() = given.isEmpty() && skipped.isNotEmpty()
}

data class AppUiState(
    val loading: Boolean = true,
    val household: HouseholdState? = null,
    val todaySlots: List<TodaySlotUi> = emptyList(),
    val records: List<DoseRecord> = emptyList(),
    val hasFullAccess: Boolean = true,
    val historyLimitDays: Long? = null,
    val isBackendConfigured: Boolean = true,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PetMedApp).container
    private val repo = container.repository
    val analytics = container.analytics
    val alarmScheduler = container.alarmScheduler

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val uiState: StateFlow<AppUiState> =
        combine(repo.householdState, repo.doseRecords) { state, records ->
            val now = Instant.now()
            val today = LocalDate.now()
            val slots = state?.let {
                DoseSlotCalculator.slotsForDate(it.pets, it.medications, today)
            } ?: emptyList()
            val effective = DoubleDoseDetector.effectiveRecords(records)
            val todaySlots = slots.map { slot ->
                val slotRecords = effective.filter { r ->
                    DoubleDoseDetector.slotKeyOf(r) == slot.slotKey
                }
                TodaySlotUi(
                    slot = slot,
                    given = slotRecords.filter { it.status == DoseStatus.GIVEN || it.status == DoseStatus.GIVEN_LATE },
                    skipped = slotRecords.filter { it.status == DoseStatus.SKIPPED },
                )
            }
            AppUiState(
                loading = false,
                household = state,
                todaySlots = todaySlots,
                records = records,
                hasFullAccess = state?.let { FreeTierPolicy.hasFullAccess(it.household, now) } ?: true,
                historyLimitDays = state?.let { FreeTierPolicy.historyLimitDays(it.household, now) },
                isBackendConfigured = repo.isBackendConfigured,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    // ---- onboarding ----

    fun createHousehold(displayName: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.createHousehold(displayName, householdName = "$displayName の家")
            analytics.log("onboarding_done", mapOf("mode" to "create"))
            onDone()
        }
    }

    fun joinHousehold(code: String, displayName: String, onDone: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.joinHousehold(code.trim(), displayName)
                analytics.log("onboarding_done", mapOf("mode" to "join"))
                analytics.log("invite_accepted", mapOf("memberCount" to (uiState.value.household?.members?.size ?: 0)))
                onDone()
            } catch (e: Exception) {
                onError()
            }
        }
    }

    // ---- pets & meds ----

    fun addPet(name: String, species: Species, birthYear: Int?, onDone: (petId: String) -> Unit) {
        viewModelScope.launch {
            val petId = repo.addPet(name, species, birthYear)
            val count = (uiState.value.household?.pets?.size ?: 0) + 1
            analytics.log("pet_registered", mapOf("species" to species.name.lowercase(), "petCount" to count))
            onDone(petId)
        }
    }

    fun updatePet(pet: Pet, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.updatePet(pet)
            onDone()
        }
    }

    fun saveMedication(med: Medication, isNew: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            if (isNew) repo.addMedication(med) else repo.updateMedication(med)
            if (isNew) {
                analytics.log(
                    "med_registered",
                    mapOf(
                        "slotCount" to med.slots.size,
                        "daysPerWeek" to med.daysOfWeek.size,
                        "medCount" to (uiState.value.household?.medications?.size ?: 0) + 1,
                    ),
                )
            }
            onDone()
        }
    }

    // ---- dose recording ----

    /** 記録前チェック: 既に投与済みならその記録を返す（警告ダイアログ用） */
    fun existingGivenFor(slot: DoseSlotInstance): DoseRecord? =
        DoubleDoseDetector.existingGiven(uiState.value.records, slot.slotKey)

    fun recordDose(slot: DoseSlotInstance, given: Boolean, proceededAfterWarning: Boolean = false) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val scheduledAt = DoseSlotCalculator.scheduledInstant(slot.slotDate, slot.time, zone)
            val status = if (given) {
                DoubleDoseDetector.statusForGiven(scheduledAt, Instant.now())
            } else {
                DoseStatus.SKIPPED
            }
            repo.recordDose(
                petId = slot.petId,
                medId = slot.medId,
                slotId = slot.slotId,
                slotDate = slot.slotDate,
                status = status,
                scheduledAt = scheduledAt,
                source = RecordSource.APP,
            )
            val isSecond = uiState.value.household?.let { hh ->
                uiState.value.records.any { it.recordedByUid != hh.myUid }
            } ?: false
            analytics.log(
                "dose_recorded",
                mapOf(
                    "status" to status.name.lowercase(),
                    "source" to "app",
                    "isSecondCaregiver" to isSecond,
                ),
            )
            if (proceededAfterWarning) {
                analytics.log("double_dose_warned", mapOf("proceeded" to true))
            }
            _message.value = "recorded"
        }
    }

    fun cancelRecord(record: DoseRecord) {
        viewModelScope.launch {
            repo.recordDose(
                petId = record.petId,
                medId = record.medId,
                slotId = record.slotId,
                slotDate = record.slotDate,
                status = DoseStatus.CANCELLED,
                scheduledAt = record.scheduledAt,
                source = RecordSource.APP,
                cancelsRecordId = record.id,
            )
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---- invite ----

    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode

    fun createInvite() {
        viewModelScope.launch {
            _inviteCode.value = repo.createInvite()
            analytics.log("invite_created")
        }
    }

    // ---- paywall / settings ----

    fun logPaywallViewed(trigger: String) {
        analytics.log("paywall_viewed", mapOf("trigger" to trigger))
    }

    fun purchaseIntent(plan: String) {
        viewModelScope.launch {
            repo.markPurchaseIntent(plan)
            analytics.log("purchase_intent", mapOf("plan" to plan))
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch { repo.updateDisplayName(name) }
    }

    fun deleteMyAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            analytics.log("data_deleted", mapOf("scope" to "member"))
            repo.deleteMyAccount()
            onDone()
        }
    }

    fun deleteHousehold(onDone: () -> Unit) {
        viewModelScope.launch {
            analytics.log("data_deleted", mapOf("scope" to "household"))
            repo.deleteHousehold()
            onDone()
        }
    }
}
