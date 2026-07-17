package io.github.aomizuki0307.petmed.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.aomizuki0307.petmed.MainActivity
import io.github.aomizuki0307.petmed.PetMedApp
import io.github.aomizuki0307.petmed.R

/** 投薬時刻の通知を表示し、次のアラームを再セットする */
class DoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = PetMedApp.from(context)
        val petName = intent.getStringExtra(AlarmScheduler.EXTRA_PET_NAME) ?: ""
        val medName = intent.getStringExtra(AlarmScheduler.EXTRA_MED_NAME) ?: ""
        val slotLabel = intent.getStringExtra(AlarmScheduler.EXTRA_SLOT_LABEL) ?: ""
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)

        showNotification(context, intent, petName, medName, slotLabel)

        // 発火成功の自己計測（±15分KPI、通知内容は送らない — docs/07）
        val delayMin = if (scheduledAt > 0) {
            ((System.currentTimeMillis() - scheduledAt) / 60000).toInt()
        } else {
            -1
        }
        app.container.analytics.log("alarm_fired", mapOf("delayMinutes" to delayMin))

        app.container.alarmScheduler.scheduleNext()
    }

    private fun showNotification(
        context: Context,
        source: Intent,
        petName: String,
        medName: String,
        slotLabel: String,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // 通知権限なし。権限状態はToday画面のPermissionStatusCardで可視化済み
        }

        val notifId = (source.getStringExtra(AlarmScheduler.EXTRA_SLOT_ID) ?: "").hashCode()

        val contentIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val giveActionIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            Intent(context, DoseActionReceiver::class.java).putExtras(source),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, PetMedApp.CHANNEL_DOSE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_title, petName))
            .setContentText(context.getString(R.string.notif_body, medName, slotLabel))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_action_given), giveActionIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}
