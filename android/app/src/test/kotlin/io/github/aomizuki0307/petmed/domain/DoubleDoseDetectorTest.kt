package io.github.aomizuki0307.petmed.domain

import io.github.aomizuki0307.petmed.domain.model.DoseRecord
import io.github.aomizuki0307.petmed.domain.model.DoseStatus
import io.github.aomizuki0307.petmed.domain.model.RecordSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DoubleDoseDetectorTest {

    private val slotDate = LocalDate.of(2026, 7, 17)
    private val scheduled = Instant.parse("2026-07-16T23:00:00Z") // JST 7/17 08:00

    private fun record(
        id: String,
        status: DoseStatus,
        cancels: String? = null,
        recordedAt: Instant = scheduled.plusSeconds(120),
        by: String = "mom",
    ) = DoseRecord(
        id = id, petId = "p1", medId = "m1", slotId = "s-am", slotDate = slotDate,
        status = status, cancelsRecordId = cancels,
        scheduledAt = scheduled, recordedAt = recordedAt,
        recordedByUid = by, recordedByName = by, source = RecordSource.APP,
        clientRecordId = "c-$id",
    )

    private val slotKey = DoseSlotCalculator.slotKey("p1", "m1", "s-am", slotDate)

    @Test
    fun `投与済みがあれば既存記録として検出される`() {
        val records = listOf(record("r1", DoseStatus.GIVEN))
        assertNotNull(DoubleDoseDetector.existingGiven(records, slotKey))
    }

    @Test
    fun `CANCELLEDで相殺された記録は検出されない`() {
        val records = listOf(
            record("r1", DoseStatus.GIVEN),
            record("r2", DoseStatus.CANCELLED, cancels = "r1"),
        )
        assertNull(DoubleDoseDetector.existingGiven(records, slotKey))
    }

    @Test
    fun `SKIPPEDは投与済みとして扱われない`() {
        val records = listOf(record("r1", DoseStatus.SKIPPED))
        assertNull(DoubleDoseDetector.existingGiven(records, slotKey))
    }

    @Test
    fun `別スロットの投与は検出されない`() {
        val other = DoseSlotCalculator.slotKey("p1", "m1", "s-pm", slotDate)
        val records = listOf(record("r1", DoseStatus.GIVEN))
        assertNull(DoubleDoseDetector.existingGiven(records, other))
    }

    @Test
    fun `同一スロットに有効な投与2件で重複と判定される`() {
        val records = listOf(
            record("r1", DoseStatus.GIVEN, by = "mom"),
            record("r2", DoseStatus.GIVEN, by = "dad", recordedAt = scheduled.plusSeconds(300)),
        )
        val dups = DoubleDoseDetector.duplicateSlots(records)
        assertEquals(1, dups.size)
        assertEquals(2, dups.getValue(slotKey).size)
    }

    @Test
    fun `片方が取り消されれば重複ではない`() {
        val records = listOf(
            record("r1", DoseStatus.GIVEN),
            record("r2", DoseStatus.GIVEN, recordedAt = scheduled.plusSeconds(300)),
            record("r3", DoseStatus.CANCELLED, cancels = "r2"),
        )
        assertTrue(DoubleDoseDetector.duplicateSlots(records).isEmpty())
    }

    @Test
    fun `遅延判定は60分が境界`() {
        assertEquals(
            DoseStatus.GIVEN,
            DoubleDoseDetector.statusForGiven(scheduled, scheduled.plusSeconds(60 * 60)),
        )
        assertEquals(
            DoseStatus.GIVEN_LATE,
            DoubleDoseDetector.statusForGiven(scheduled, scheduled.plusSeconds(60 * 60 + 1)),
        )
    }
}
