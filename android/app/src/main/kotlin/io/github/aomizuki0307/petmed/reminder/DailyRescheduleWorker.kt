package io.github.aomizuki0307.petmed.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.aomizuki0307.petmed.PetMedApp
import io.github.aomizuki0307.petmed.domain.DoseSlotCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * 24時間ごとのセーフティネット:
 * スロットキャッシュを再構築し、アラームの取りこぼしを防ぐ。
 */
class DailyRescheduleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = PetMedApp.from(applicationContext)
        val state = app.container.repository.householdState.first()
        val slots = if (state == null) {
            emptyList()
        } else {
            DoseSlotCalculator.upcomingSlots(state.pets, state.medications, LocalDateTime.now())
        }
        app.container.scheduleCache.write(slots)
        app.container.alarmScheduler.scheduleNext()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_reschedule"
    }
}
