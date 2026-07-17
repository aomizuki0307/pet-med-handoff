package io.github.aomizuki0307.petmed.domain

import io.github.aomizuki0307.petmed.domain.model.Household
import io.github.aomizuki0307.petmed.domain.model.PlanTier
import java.time.Duration
import java.time.Instant

/**
 * 無料枠: 1匹 / 1人 / 薬2件 / 履歴7日。
 * 作成から14日間はトライアルとして全機能可（docs/01 価格仮説）。
 */
object FreeTierPolicy {

    val TRIAL_PERIOD: Duration = Duration.ofDays(14)
    const val FREE_MAX_PETS = 1
    const val FREE_MAX_MEMBERS = 1
    const val FREE_MAX_MEDS = 2
    const val FREE_HISTORY_DAYS = 7L
    const val PAID_MAX_PETS = 5
    const val PAID_MAX_MEMBERS = 5

    fun isTrialActive(household: Household, now: Instant): Boolean =
        now.isBefore(household.createdAt.plus(TRIAL_PERIOD))

    /** 全機能が使える状態か（トライアル中 or 有料意向登録済み） */
    fun hasFullAccess(household: Household, now: Instant): Boolean =
        household.planTier == PlanTier.PAID_INTENT || isTrialActive(household, now)

    fun canAddPet(household: Household, currentCount: Int, now: Instant): Boolean =
        currentCount < if (hasFullAccess(household, now)) PAID_MAX_PETS else FREE_MAX_PETS

    fun canAddMember(household: Household, currentCount: Int, now: Instant): Boolean =
        currentCount < if (hasFullAccess(household, now)) PAID_MAX_MEMBERS else FREE_MAX_MEMBERS

    fun canAddMedication(household: Household, currentCount: Int, now: Instant): Boolean =
        hasFullAccess(household, now) || currentCount < FREE_MAX_MEDS

    /** 履歴の表示可能日数（nullなら無制限） */
    fun historyLimitDays(household: Household, now: Instant): Long? =
        if (hasFullAccess(household, now)) null else FREE_HISTORY_DAYS
}
