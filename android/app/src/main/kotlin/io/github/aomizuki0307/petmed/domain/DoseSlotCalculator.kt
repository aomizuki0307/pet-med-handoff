package io.github.aomizuki0307.petmed.domain

import io.github.aomizuki0307.petmed.domain.model.DoseSlotInstance
import io.github.aomizuki0307.petmed.domain.model.Medication
import io.github.aomizuki0307.petmed.domain.model.Pet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 投薬スロットの展開・slotKey・次回発火時刻の計算（純Kotlin・副作用なし）。
 * slotDate はスロットが属する日付で固定する（深夜スロットも当日扱い）。
 */
object DoseSlotCalculator {

    fun slotKey(petId: String, medId: String, slotId: String, slotDate: LocalDate): String =
        "${petId}_${medId}_${slotId}_$slotDate"

    /** 指定日の全スロットを展開する（activeな薬・曜日・期間でフィルタ） */
    fun slotsForDate(
        pets: List<Pet>,
        medications: List<Medication>,
        date: LocalDate,
    ): List<DoseSlotInstance> {
        val petById = pets.filter { !it.archived }.associateBy { it.id }
        return medications
            .filter { it.active }
            .filter { it.petId in petById }
            .filter { date.dayOfWeek.value in it.daysOfWeek }
            .filter { !date.isBefore(it.startDate) }
            .filter { it.endDate == null || !date.isAfter(it.endDate) }
            .flatMap { med ->
                med.slots.map { slot ->
                    DoseSlotInstance(
                        petId = med.petId,
                        petName = petById.getValue(med.petId).name,
                        medId = med.id,
                        medName = med.name,
                        slotId = slot.slotId,
                        slotLabel = slot.label,
                        slotDate = date,
                        time = slot.time,
                    )
                }
            }
            .sortedWith(compareBy({ it.time }, { it.petName }, { it.medName }))
    }

    /** 今後 [hours] 時間以内のスロットを時系列で返す（アラームキャッシュ用） */
    fun upcomingSlots(
        pets: List<Pet>,
        medications: List<Medication>,
        now: LocalDateTime,
        hours: Long = 48,
    ): List<Pair<DoseSlotInstance, LocalDateTime>> {
        val end = now.plusHours(hours)
        return (0..(hours / 24 + 1)).flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset)
            slotsForDate(pets, medications, date).map { it to LocalDateTime.of(date, it.time) }
        }
            .filter { (_, at) -> !at.isBefore(now) && !at.isAfter(end) }
            .sortedBy { it.second }
    }

    /** 次に鳴らすべきアラーム時刻（なければnull） */
    fun nextAlarmTime(
        pets: List<Pet>,
        medications: List<Medication>,
        now: LocalDateTime,
    ): LocalDateTime? = upcomingSlots(pets, medications, now, hours = 24 * 7).firstOrNull()?.second

    fun scheduledInstant(slotDate: LocalDate, time: java.time.LocalTime, zone: ZoneId): Instant =
        LocalDateTime.of(slotDate, time).atZone(zone).toInstant()
}
