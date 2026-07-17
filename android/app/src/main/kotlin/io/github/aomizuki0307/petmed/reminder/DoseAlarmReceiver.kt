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
import io.github.aomizuki0307.petmed.data.ScheduleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * アラーム発火: トリガ時刻をextraから受け取り、キャッシュ上の同時刻の全スロットへ
 * 通知を出してから、その時刻より後の次アラームを再セットする。
 * （同時刻に複数の薬があっても2件目以降が欠落しない — 各スロットは独立した通知になる）
 */
class DoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = PetMedApp.from(context)
        val triggerLdt = intent.getStringExtra(AlarmScheduler.EXTRA_TRIGGER_LDT)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val scheduledAtMillis = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)

        // 発火成功の自己計測（±15分KPI、通知内容は送らない — docs/07）
        val delayMin = if (scheduledAtMillis > 0) {
            ((System.currentTimeMillis() - scheduledAtMillis) / 60000).toInt()
        } else {
            -1
        }
        app.container.analytics.log("alarm_fired", mapOf("delayMinutes" to delayMin))

        // goAsync: onReceive返却後のプロセスkillで通知表示・再スケジュールが落ちるのを防ぐ
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(8_000) {
                    if (triggerLdt != null) {
                        app.container.scheduleCache.slotsAt(triggerLdt).forEach { cached ->
                            showNotification(context, cached)
                        }
                        app.container.alarmScheduler.scheduleNextNow(afterExclusive = triggerLdt)
                    } else {
                        app.container.alarmScheduler.scheduleNextNow()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, cached: ScheduleCache.CachedSlot) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // 通知権限なし。権限状態はToday画面のPermissionStatusCardで可視化済み
        }

        val slot = cached.slot
        val notifId = slot.slotKey.hashCode()
        val scheduledAtMillis =
            cached.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val contentIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val actionIntent = Intent(context, DoseActionReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_PET_ID, slot.petId)
            putExtra(AlarmScheduler.EXTRA_MED_ID, slot.medId)
            putExtra(AlarmScheduler.EXTRA_SLOT_ID, slot.slotId)
            putExtra(AlarmScheduler.EXTRA_SLOT_DATE, slot.slotDate.toString())
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, scheduledAtMillis)
            putExtra(AlarmScheduler.EXTRA_NOTIF_ID, notifId)
        }
        val giveActionIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, PetMedApp.CHANNEL_DOSE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_title, slot.petName))
            .setContentText(context.getString(R.string.notif_body, slot.medName, slot.slotLabel))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_action_given), giveActionIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}
