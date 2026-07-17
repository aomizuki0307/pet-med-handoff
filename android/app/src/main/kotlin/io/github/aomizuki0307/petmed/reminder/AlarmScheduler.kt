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
 * 常に「次の1回」だけをAlarmManagerにセットする。
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
     * キャッシュから次のスロットを読み、アラームを1本だけセットする。
     * BroadcastReceiverからは goAsync + この suspend 版を使う
     * （fire-and-forget だと onReceive 返却後にプロセスが殺され再スケジュールが落ちる）
     */
    suspend fun scheduleNextNow() {
        val next = scheduleCache.nextAfter(LocalDateTime.now()) ?: run {
            alarmManager.cancel(pendingIntent())
            return
        }
        val triggerAt = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(
            petName = next.slot.petName,
            medName = next.slot.medName,
            slotLabel = next.slot.slotLabel,
            petId = next.slot.petId,
            medId = next.slot.medId,
            slotId = next.slot.slotId,
            slotDate = next.slot.slotDate.toString(),
            scheduledAtMillis = triggerAt,
        )
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
        petName: String = "",
        medName: String = "",
        slotLabel: String = "",
        petId: String = "",
        medId: String = "",
        slotId: String = "",
        slotDate: String = "",
        scheduledAtMillis: Long = 0L,
    ): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            putExtra(EXTRA_PET_NAME, petName)
            putExtra(EXTRA_MED_NAME, medName)
            putExtra(EXTRA_SLOT_LABEL, slotLabel)
            putExtra(EXTRA_PET_ID, petId)
            putExtra(EXTRA_MED_ID, medId)
            putExtra(EXTRA_SLOT_ID, slotId)
            putExtra(EXTRA_SLOT_DATE, slotDate)
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
        const val EXTRA_PET_NAME = "petName"
        const val EXTRA_MED_NAME = "medName"
        const val EXTRA_SLOT_LABEL = "slotLabel"
        const val EXTRA_PET_ID = "petId"
        const val EXTRA_MED_ID = "medId"
        const val EXTRA_SLOT_ID = "slotId"
        const val EXTRA_SLOT_DATE = "slotDate"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
    }
}
