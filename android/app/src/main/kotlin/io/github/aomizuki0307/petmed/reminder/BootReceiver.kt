package io.github.aomizuki0307.petmed.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aomizuki0307.petmed.PetMedApp

/** 再起動・時刻/タイムゾーン変更でアラームを復元する */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> PetMedApp.from(context).container.alarmScheduler.scheduleNext()
        }
    }
}
