package io.github.aomizuki0307.petmed.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.aomizuki0307.petmed.data.AnalyticsLogger
import io.github.aomizuki0307.petmed.data.ScheduleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 常に「次の1トリガ時刻」だけをAlarmManagerにセットする。
 * アラームはトリガ時刻のみを運び、発火時に DoseAlarmReceiver がキャッシュから
 * 同時刻の全スロットを読んで通知する（同時刻に複数の薬があっても欠落しない）。
 * SCHEDULE_EXACT_ALARM 拒否時は setWindow(±10分) に劣化し、クラッシュさせない。
 * スケジュールの源泉は ScheduleCache のみ（Firestoreに依存しない — docs/05）。
 */
class AlarmScheduler(
    private val context: Context,
    private val scheduleCache: ScheduleCache,
    private val analytics: AnalyticsLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    /** アプリ内(Activity/Worker)から使うfire-and-forget版 */
    fun scheduleNext() {
        scope.launch { scheduleNextNow() }
    }

    /**
     * キャッシュから次のトリガ時刻を読み、アラームを1本だけセットする。
     * BroadcastReceiverからは goAsync + この suspend 版を使う。
     * [afterExclusive] 指定時はその時刻より厳密に後のスロットを選ぶ
     * （発火後の再スケジュール用 — 早発火時に同一スロットを再登録して通知が繰り返すのを防ぐ）。
     */
    suspend fun scheduleNextNow(afterExclusive: LocalDateTime? = null) {
        val next = if (afterExclusive != null) {
            scheduleCache.nextAfter(afterExclusive, strict = true)
        } else {
            scheduleCache.nextAfter(LocalDateTime.now())
        } ?: run {
            alarmManager.cancel(pendingIntent())
            return
        }
        val triggerAt = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(triggerLdt = next.at.toString(), scheduledAtMillis = triggerAt)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // 劣化動作: ±10分の窓
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt - Duration.ofMinutes(10).toMillis(),
                    Duration.ofMinutes(20).toMillis(),
                    pi,
                )
            }
        } catch (e: SecurityException) {
            // 権限が発火直前に剥奪されたケース。inexactで再試行
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt - Duration.ofMinutes(10).toMillis(),
                Duration.ofMinutes(20).toMillis(),
                pi,
            )
            analytics.log("app_error", mapOf("screen" to "alarm_security_exception"))
        }
    }

    private fun pendingIntent(
        triggerLdt: String = "",
        scheduledAtMillis: Long = 0L,
    ): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TRIGGER_LDT, triggerLdt)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAtMillis)
        }
        // 単一アラーム運用のためrequestCodeは固定。FLAG_UPDATE_CURRENTでextras更新
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val REQUEST_CODE = 41001

        /** アラームPendingIntent用: トリガ時刻(LocalDateTime文字列) */
        const val EXTRA_TRIGGER_LDT = "triggerLdt"

        /** 通知アクションIntent用（DoseAlarmReceiverが各スロットに付与） */
        const val EXTRA_PET_NAME = "petName"
        const val EXTRA_MED_NAME = "medName"
        const val EXTRA_SLOT_LABEL = "slotLabel"
        const val EXTRA_PET_ID = "petId"
        const val EXTRA_MED_ID = "medId"
        const val EXTRA_SLOT_ID = "slotId"
        const val EXTRA_SLOT_DATE = "slotDate"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
        const val EXTRA_NOTIF_ID = "notifId"
    }
}
