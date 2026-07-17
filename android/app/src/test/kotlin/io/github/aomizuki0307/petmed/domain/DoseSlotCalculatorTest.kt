package io.github.aomizuki0307.petmed.domain

import io.github.aomizuki0307.petmed.domain.model.Medication
import io.github.aomizuki0307.petmed.domain.model.Pet
import io.github.aomizuki0307.petmed.domain.model.ScheduleSlot
import io.github.aomizuki0307.petmed.domain.model.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DoseSlotCalculatorTest {

    private val cat = Pet(id = "p1", name = "ちゃちゃ", species = Species.CAT)

    private fun med(
        id: String = "m1",
        slots: List<ScheduleSlot> = listOf(ScheduleSlot("s-am", LocalTime.of(8, 0), "朝")),
        days: Set<Int> = (1..7).toSet(),
        start: LocalDate = LocalDate.of(2026, 7, 1),
        end: LocalDate? = null,
        active: Boolean = true,
    ) = Medication(
        id = id, petId = cat.id, name = "腎臓の薬", dosageText = "1錠",
        slots = slots, daysOfWeek = days, startDate = start, endDate = end, active = active,
    )

    // 2026-07-17 は金曜(dayOfWeek=5)
    private val friday = LocalDate.of(2026, 7, 17)

    @Test
    fun `毎日2スロットの薬は当日2件展開される`() {
        val m = med(
            slots = listOf(
                ScheduleSlot("s-am", LocalTime.of(8, 0), "朝"),
                ScheduleSlot("s-pm", LocalTime.of(20, 0), "夜"),
            )
        )
        val slots = DoseSlotCalculator.slotsForDate(listOf(cat), listOf(m), friday)
        assertEquals(2, slots.size)
        assertEquals(LocalTime.of(8, 0), slots[0].time)
        assertEquals(LocalTime.of(20, 0), slots[1].time)
    }

    @Test
    fun `対象外の曜日は展開されない`() {
        val m = med(days = setOf(1, 3)) // 月・水のみ
        assertTrue(DoseSlotCalculator.slotsForDate(listOf(cat), listOf(m), friday).isEmpty())
    }

    @Test
    fun `開始前・終了後・inactive・archivedは展開されない`() {
        val notStarted = med(id = "m-a", start = friday.plusDays(1))
        val ended = med(id = "m-b", end = friday.minusDays(1))
        val inactive = med(id = "m-c", active = false)
        assertTrue(
            DoseSlotCalculator.slotsForDate(listOf(cat), listOf(notStarted, ended, inactive), friday).isEmpty()
        )
        val archivedPet = cat.copy(archived = true)
        assertTrue(
            DoseSlotCalculator.slotsForDate(listOf(archivedPet), listOf(med()), friday).isEmpty()
        )
    }

    @Test
    fun `終了日当日は展開される`() {
        val m = med(end = friday)
        assertEquals(1, DoseSlotCalculator.slotsForDate(listOf(cat), listOf(m), friday).size)
    }

    @Test
    fun `slotKeyは決定的で日付を含む`() {
        val key = DoseSlotCalculator.slotKey("p1", "m1", "s-am", friday)
        assertEquals("p1_m1_s-am_2026-07-17", key)
        val slot = DoseSlotCalculator.slotsForDate(listOf(cat), listOf(med()), friday).first()
        assertEquals(key, slot.slotKey)
    }

    @Test
    fun `深夜スロットのslotDateはスロットの属する日付で固定される`() {
        val m = med(slots = listOf(ScheduleSlot("s-mid", LocalTime.of(0, 30), "深夜")))
        val slot = DoseSlotCalculator.slotsForDate(listOf(cat), listOf(m), friday).first()
        assertEquals(friday, slot.slotDate)
        assertEquals("p1_m1_s-mid_2026-07-17", slot.slotKey)
    }

    @Test
    fun `upcomingSlotsは現在以降48時間以内を時系列で返す`() {
        val m = med(
            slots = listOf(
                ScheduleSlot("s-am", LocalTime.of(8, 0), "朝"),
                ScheduleSlot("s-pm", LocalTime.of(20, 0), "夜"),
            )
        )
        val now = LocalDateTime.of(friday, LocalTime.of(9, 0)) // 朝は過ぎている
        val upcoming = DoseSlotCalculator.upcomingSlots(listOf(cat), listOf(m), now)
        // 金夜 + 土朝 + 土夜 + (日朝は48h境界=7/19 9:00より前の8:00なので含む)
        assertEquals(4, upcoming.size)
        assertEquals(LocalDateTime.of(friday, LocalTime.of(20, 0)), upcoming[0].second)
        assertTrue(upcoming.zipWithNext().all { (a, b) -> !a.second.isAfter(b.second) })
    }

    @Test
    fun `nextAlarmTimeは次の1件を返しスロットがなければnull`() {
        val m = med()
        val now = LocalDateTime.of(friday, LocalTime.of(9, 0))
        assertEquals(
            LocalDateTime.of(friday.plusDays(1), LocalTime.of(8, 0)),
            DoseSlotCalculator.nextAlarmTime(listOf(cat), listOf(m), now),
        )
        assertEquals(
            null,
            DoseSlotCalculator.nextAlarmTime(listOf(cat), emptyList(), now),
        )
    }
}
