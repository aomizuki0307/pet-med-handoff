package io.github.aomizuki0307.petmed.domain

import io.github.aomizuki0307.petmed.domain.model.Household
import io.github.aomizuki0307.petmed.domain.model.PlanTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FreeTierPolicyTest {

    private val createdAt = Instant.parse("2026-07-01T00:00:00Z")

    private fun household(tier: PlanTier = PlanTier.FREE) =
        Household(id = "h1", name = "test", createdByUid = "u1", createdAt = createdAt, planTier = tier)

    @Test
    fun `作成から14日間はトライアルで全機能`() {
        val hh = household()
        val day13 = createdAt.plus(Duration.ofDays(13))
        assertTrue(FreeTierPolicy.hasFullAccess(hh, day13))
        assertTrue(FreeTierPolicy.canAddMember(hh, 4, day13))
        assertNull(FreeTierPolicy.historyLimitDays(hh, day13))
    }

    @Test
    fun `14日経過後は無料枠に制限される`() {
        val hh = household()
        val day14 = createdAt.plus(Duration.ofDays(14))
        assertFalse(FreeTierPolicy.hasFullAccess(hh, day14))
        assertFalse(FreeTierPolicy.canAddPet(hh, 1, day14))
        assertTrue(FreeTierPolicy.canAddPet(hh, 0, day14))
        assertFalse(FreeTierPolicy.canAddMember(hh, 1, day14))
        assertTrue(FreeTierPolicy.canAddMedication(hh, 1, day14))
        assertFalse(FreeTierPolicy.canAddMedication(hh, 2, day14))
        assertEquals(7L, FreeTierPolicy.historyLimitDays(hh, day14))
    }

    @Test
    fun `有料意向登録済みは期限後も全機能`() {
        val hh = household(PlanTier.PAID_INTENT)
        val day30 = createdAt.plus(Duration.ofDays(30))
        assertTrue(FreeTierPolicy.hasFullAccess(hh, day30))
        assertTrue(FreeTierPolicy.canAddPet(hh, 4, day30))
        assertFalse(FreeTierPolicy.canAddPet(hh, 5, day30))
        assertTrue(FreeTierPolicy.canAddMember(hh, 4, day30))
        assertFalse(FreeTierPolicy.canAddMember(hh, 5, day30))
    }
}
