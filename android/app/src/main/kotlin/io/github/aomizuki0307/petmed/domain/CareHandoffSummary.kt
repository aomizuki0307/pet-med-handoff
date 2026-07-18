package io.github.aomizuki0307.petmed.domain

import io.github.aomizuki0307.petmed.domain.model.DoseRecord
import io.github.aomizuki0307.petmed.domain.model.DoseSlotInstance
import io.github.aomizuki0307.petmed.domain.model.DoseStatus
import io.github.aomizuki0307.petmed.domain.model.HouseholdState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HandoffDose(
    val slot: DoseSlotInstance,
    val overdue: Boolean,
)

data class HandoffDuplicate(
    val slot: DoseSlotInstance,
    val records: List<DoseRecord>,
)

data class HandoffActivity(
    val record: DoseRecord,
    val petName: String,
    val medicationName: String,
)

/** A read-only snapshot caregivers can use when handing responsibility over. */
data class CareHandoffSummary(
    val date: LocalDate,
    val generatedAt: Instant,
    val totalDoses: Int,
    val completedDoses: Int,
    val skippedDoses: Int,
    val remainingDoses: List<HandoffDose>,
    val duplicateWarnings: List<HandoffDuplicate>,
    val recentActivity: List<HandoffActivity>,
) {
    val nextDose: HandoffDose? get() = remainingDoses.firstOrNull()
    val resolvedDoses: Int get() = completedDoses + skippedDoses
}

/**
 * Builds a deterministic handoff snapshot from the same append-only records as
 * the Today screen. It never interprets dosage text or gives medical advice.
 */
object CareHandoffSummaryBuilder {

    fun build(
        household: HouseholdState,
        records: List<DoseRecord>,
        now: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
        recentLimit: Int = 5,
    ): CareHandoffSummary {
        require(recentLimit >= 0) { "recentLimit must be non-negative" }

        val date = now.atZone(zoneId).toLocalDate()
        val slots = DoseSlotCalculator.slotsForDate(
            household.pets,
            household.medications,
            date,
        ).sortedBy { it.time }
        val effective = DoubleDoseDetector.effectiveRecords(records)
        val recordsBySlot = effective.groupBy(DoubleDoseDetector::slotKeyOf)

        var completed = 0
        var skipped = 0
        val remaining = mutableListOf<HandoffDose>()
        slots.forEach { slot ->
            val slotRecords = recordsBySlot[slot.slotKey].orEmpty()
            when {
                slotRecords.any { it.status == DoseStatus.GIVEN || it.status == DoseStatus.GIVEN_LATE } -> completed++
                slotRecords.any { it.status == DoseStatus.SKIPPED } -> skipped++
                else -> {
                    val scheduledAt = DoseSlotCalculator.scheduledInstant(date, slot.time, zoneId)
                    remaining += HandoffDose(slot = slot, overdue = scheduledAt.isBefore(now))
                }
            }
        }

        val slotByKey = slots.associateBy { it.slotKey }
        val duplicates = DoubleDoseDetector.duplicateSlots(records)
            .mapNotNull { (slotKey, duplicateRecords) ->
                slotByKey[slotKey]?.let { slot ->
                    HandoffDuplicate(slot = slot, records = duplicateRecords)
                }
            }
            .sortedBy { it.slot.time }

        val petNames = household.pets.associate { it.id to it.name }
        val medicationNames = household.medications.associate { it.id to it.name }
        val recent = effective
            .sortedByDescending { it.recordedAt }
            .take(recentLimit)
            .map { record ->
                HandoffActivity(
                    record = record,
                    petName = petNames[record.petId] ?: "—",
                    medicationName = medicationNames[record.medId] ?: "—",
                )
            }

        return CareHandoffSummary(
            date = date,
            generatedAt = now,
            totalDoses = slots.size,
            completedDoses = completed,
            skippedDoses = skipped,
            remainingDoses = remaining,
            duplicateWarnings = duplicates,
            recentActivity = recent,
        )
    }
}
