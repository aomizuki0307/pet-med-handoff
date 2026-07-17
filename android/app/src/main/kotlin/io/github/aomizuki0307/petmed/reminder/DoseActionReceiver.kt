package io.github.aomizuki0307.petmed.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * 通知の「投薬済み」アクション。
 * 記録は必ずWorkManager経由でリポジトリを呼ぶ（プロセス死・オフラインでも確実に書き込む）。
 */
class DoseActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = (intent.getStringExtra(AlarmScheduler.EXTRA_SLOT_ID) ?: "").hashCode()
        NotificationManagerCompat.from(context).cancel(notifId)

        val work = OneTimeWorkRequestBuilder<RecordDoseWorker>()
            .setInputData(
                Data.Builder()
                    .putString(RecordDoseWorker.KEY_PET_ID, intent.getStringExtra(AlarmScheduler.EXTRA_PET_ID))
                    .putString(RecordDoseWorker.KEY_MED_ID, intent.getStringExtra(AlarmScheduler.EXTRA_MED_ID))
                    .putString(RecordDoseWorker.KEY_SLOT_ID, intent.getStringExtra(AlarmScheduler.EXTRA_SLOT_ID))
                    .putString(RecordDoseWorker.KEY_SLOT_DATE, intent.getStringExtra(AlarmScheduler.EXTRA_SLOT_DATE))
                    .putLong(RecordDoseWorker.KEY_SCHEDULED_AT, intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L))
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }
}
