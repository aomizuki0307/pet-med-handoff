package io.github.aomizuki0307.petmed.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.aomizuki0307.petmed.PetMedApp
import io.github.aomizuki0307.petmed.domain.DoubleDoseDetector
import io.github.aomizuki0307.petmed.domain.model.RecordSource
import java.time.Instant
import java.time.LocalDate

/** 通知アクションからの投薬記録（オフラインでもFirestoreキュー/Fakeに書ける） */
class RecordDoseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = PetMedApp.from(applicationContext)
        val petId = inputData.getString(KEY_PET_ID) ?: return Result.failure()
        val medId = inputData.getString(KEY_MED_ID) ?: return Result.failure()
        val slotId = inputData.getString(KEY_SLOT_ID) ?: return Result.failure()
        val slotDate = inputData.getString(KEY_SLOT_DATE)?.let(LocalDate::parse) ?: return Result.failure()
        val scheduledAt = Instant.ofEpochMilli(inputData.getLong(KEY_SCHEDULED_AT, 0L))

        return try {
            val status = DoubleDoseDetector.statusForGiven(scheduledAt, Instant.now())
            app.container.repository.recordDose(
                petId = petId,
                medId = medId,
                slotId = slotId,
                slotDate = slotDate,
                status = status,
                scheduledAt = scheduledAt,
                source = RecordSource.NOTIFICATION,
                // 冪等キー: 同一スロットへの通知起点の記録は1回だけ
                clientRecordId = "notif-$petId-$medId-$slotId-$slotDate",
            )
            app.container.analytics.log(
                "dose_recorded",
                mapOf("status" to status.name.lowercase(), "source" to "notification"),
            )
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_PET_ID = "petId"
        const val KEY_MED_ID = "medId"
        const val KEY_SLOT_ID = "slotId"
        const val KEY_SLOT_DATE = "slotDate"
        const val KEY_SCHEDULED_AT = "scheduledAt"
    }
}
