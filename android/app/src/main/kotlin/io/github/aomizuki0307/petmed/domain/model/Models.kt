package io.github.aomizuki0307.petmed.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class PlanTier { FREE, TRIAL, PAID_INTENT }

enum class Species { DOG, CAT }

enum class DoseStatus { GIVEN, SKIPPED, GIVEN_LATE, CANCELLED }

enum class RecordSource { APP, NOTIFICATION }

data class Household(
    val id: String,
    val name: String,
    val createdByUid: String,
    val createdAt: Instant,
    val planTier: PlanTier = PlanTier.FREE,
)

data class Member(
    val uid: String,
    val displayName: String,
    val isOwner: Boolean,
    val joinedAt: Instant,
)

data class Pet(
    val id: String,
    val name: String,
    val species: Species,
    val birthYear: Int? = null,
    val note: String = "",
    val archived: Boolean = false,
)

data class ScheduleSlot(
    val slotId: String,
    val time: LocalTime,
    val label: String,
)

data class Medication(
    val id: String,
    val petId: String,
    val name: String,
    val dosageText: String,
    val slots: List<ScheduleSlot>,
    /** ISO曜日 1=月 … 7=日 */
    val daysOfWeek: Set<Int>,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val note: String = "",
    val active: Boolean = true,
)

data class DoseRecord(
    val id: String,
    val petId: String,
    val medId: String,
    val slotId: String,
    val slotDate: LocalDate,
    val status: DoseStatus,
    val cancelsRecordId: String? = null,
    val scheduledAt: Instant,
    val recordedAt: Instant,
    val recordedByUid: String,
    val recordedByName: String,
    val source: RecordSource,
    /** 冪等キー（通知アクションの二重発火対策） */
    val clientRecordId: String,
)

data class Invite(
    val code: String,
    val householdId: String,
    val expiresAt: Instant,
    val revoked: Boolean = false,
)

/** 特定日の「投薬すべき1回」を表す展開済みスロット */
data class DoseSlotInstance(
    val petId: String,
    val petName: String,
    val medId: String,
    val medName: String,
    val slotId: String,
    val slotLabel: String,
    val slotDate: LocalDate,
    val time: LocalTime,
) {
    val slotKey: String get() = "${petId}_${medId}_${slotId}_$slotDate"
}

/** 世帯のスナップショット（リポジトリが流すUI向け状態） */
data class HouseholdState(
    val household: Household,
    val members: List<Member>,
    val pets: List<Pet>,
    val medications: List<Medication>,
    val myUid: String,
    val myDisplayName: String,
)
