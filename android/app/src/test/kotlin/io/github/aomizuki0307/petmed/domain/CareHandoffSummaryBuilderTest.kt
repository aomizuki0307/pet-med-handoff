package io.github.aomizuki0307.petmed.domain

import io.github.aomizuki0307.petmed.domain.model.DoseRecord
import io.github.aomizuki0307.petmed.domain.model.DoseStatus
import io.github.aomizuki0307.petmed.domain.model.Household
import io.github.aomizuki0307.petmed.domain.model.HouseholdState
import io.github.aomizuki0307.petmed.domain.model.Medication
import io.github.aomizuki0307.petmed.domain.model.Member
import io.github.aomizuki0307.petmed.domain.model.Pet
import io.github.aomizuki0307.petmed.domain.model.RecordSource
import io.github.aomizuki0307.petmed.domain.model.ScheduleSlot
import io.github.aomizuki0307.petmed.domain.model.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CareHandoffSummaryBuilderTest {

    private val date = LocalDate.of(2026, 7, 18)
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-07-18T12:00:00Z")
    private val pet = Pet(id = "p1", name = "Mugi", species = Species.CAT)
    private val medication = Medication(
        id = "m1",
        petId = pet.id,
        name = "Kidney tablet",
        dosageText = "Follow veterinarian instructions",
        slots = listOf(
            ScheduleSlot("am", LocalTime.of(8, 0), "Morning"),
            ScheduleSlot("pm", LocalTime.of(20, 0), "Evening"),
        ),
        daysOfWeek = (1..7).toSet(),
        startDate = date.minusDays(1),
    )
    private val household = HouseholdState(
        household = Household("h1", "Home", "u1", now.minusSeconds(86_400)),
        members = listOf(Member("u1", "Mom", true, now.minusSeconds(86_400))),
        pets = listOf(pet),
        medications = listOf(medication),
        myUid = "u1",
        myDisplayName = "Mom",
    )

    private fun record(
        id: String,
        slotId: String,
        status: DoseStatus,
        at: Instant,
        cancels: String? = null,
        by: String = "Mom",
    ) = DoseRecord(
        id = id,
        petId = pet.id,
        medId = medication.id,
        slotId = slotId,
        slotDate = date,
        status = status,
        cancelsRecordId = cancels,
        scheduledAt = date.atTime(if (slotId == "am") 8 else 20, 0).atZone(zone).toInstant(),
        recordedAt = at,
        recordedByUid = by,
        recordedByName = by,
        source = RecordSource.APP,
        clientRecordId = "client-$id",
    )

    @Test
    fun `completed morning leaves evening as next handoff`() {
        val summary = CareHandoffSummaryBuilder.build(
            household = household,
            records = listOf(record("r1", "am", DoseStatus.GIVEN, now.minusSeconds(60))),
            now = now,
            zoneId = zone,
        )

        assertEquals(2, summary.totalDoses)
        assertEquals(1, summary.completedDoses)
        assertEquals(0, summary.skippedDoses)
        assertEquals("pm", summary.nextDose?.slot?.slotId)
        assertFalse(summary.nextDose?.overdue ?: true)
    }

    @Test
    fun `unrecorded past dose is marked overdue`() {
        val summary = CareHandoffSummaryBuilder.build(
            household = household,
            records = emptyList(),
            now = now,
            zoneId = zone,
        )

        assertEquals("am", summary.nextDose?.slot?.slotId)
        assertTrue(summary.nextDose?.overdue == true)
    }

    @Test
    fun `duplicate active records create a warning`() {
        val records = listOf(
            record("r1", "am", DoseStatus.GIVEN, now.minusSeconds(120), by = "Mom"),
            record("r2", "am", DoseStatus.GIVEN, now.minusSeconds(60), by = "Dad"),
        )

        val summary = CareHandoffSummaryBuilder.build(household, records, now, zone)

        assertEquals(1, summary.completedDoses)
        assertEquals(1, summary.duplicateWarnings.size)
        assertEquals(2, summary.duplicateWarnings.single().records.size)
    }

    @Test
    fun `cancelled records do not count as progress or recent activity`() {
        val records = listOf(
            record("r1", "am", DoseStatus.GIVEN, now.minusSeconds(120)),
            record("r2", "am", DoseStatus.CANCELLED, now.minusSeconds(60), cancels = "r1"),
        )

        val summary = CareHandoffSummaryBuilder.build(household, records, now, zone)

        assertEquals(0, summary.completedDoses)
        assertEquals(2, summary.remainingDoses.size)
        assertTrue(summary.recentActivity.isEmpty())
    }
}
