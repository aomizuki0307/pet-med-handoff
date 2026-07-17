package io.github.aomizuki0307.petmed.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aomizuki0307.petmed.PetMedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 再起動・時刻/タイムゾーン変更でアラームを復元する。
 * goAsync + suspend版で、onReceive返却後のプロセスkillによる取りこぼしを防ぐ。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val pending = goAsync()
                val scheduler = PetMedApp.from(context).container.alarmScheduler
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        withTimeoutOrNull(8_000) { scheduler.scheduleNextNow() }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
