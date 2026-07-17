package io.github.aomizuki0307.petmed.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.aomizuki0307.petmed.PetMedApp
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.domain.DoseSlotCalculator
import io.github.aomizuki0307.petmed.domain.DoubleDoseDetector
import io.github.aomizuki0307.petmed.domain.model.RecordSource
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            // 二重投薬チェック: 既に他の家族が投与済みなら記録せず知らせる
            // （オフラインで相手の記録が届いていない場合は検知できず、同期後の重複バナーで捕捉する — docs/05）
            val existing = DoubleDoseDetector.existingGiven(
                app.container.repository.doseRecords.first(),
                DoseSlotCalculator.slotKey(petId, medId, slotId, slotDate),
            )
            if (existing != null) {
                app.container.analytics.log(
                    "double_dose_warned",
                    mapOf("proceeded" to false, "source" to "notification"),
                )
                showAlreadyGivenNotification(existing.recordedByName, existing.recordedAt)
                return Result.success()
            }
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

    private fun showAlreadyGivenNotification(byName: String, recordedAt: Instant) {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val time = LocalDateTime.ofInstant(recordedAt, ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("H:mm"))
        val notification = NotificationCompat.Builder(applicationContext, PetMedApp.CHANNEL_DOSE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.dup_warn_title))
            .setContentText(applicationContext.getString(R.string.dup_warn_notif_body, byName, time))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_DUP, notification)
    }

    companion object {
        private const val NOTIF_ID_DUP = 42001
        const val KEY_PET_ID = "petId"
        const val KEY_MED_ID = "medId"
        const val KEY_SLOT_ID = "slotId"
        const val KEY_SLOT_DATE = "slotDate"
        const val KEY_SCHEDULED_AT = "scheduledAt"
    }
}
