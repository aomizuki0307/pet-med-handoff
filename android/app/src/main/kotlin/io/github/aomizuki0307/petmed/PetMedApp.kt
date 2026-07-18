package io.github.aomizuki0307.petmed

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.aomizuki0307.petmed.data.ContainerFactory
import io.github.aomizuki0307.petmed.di.AppContainer
import io.github.aomizuki0307.petmed.domain.DoseSlotCalculator
import io.github.aomizuki0307.petmed.reminder.DailyRescheduleWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class PetMedApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = ContainerFactory.create(this)
        createNotificationChannel()
        observeScheduleChanges()
        enqueueDailyReschedule()
    }

    /**
     * 世帯状態が変わるたびにスロットキャッシュを更新し、次のアラームを再セット。
     * 起動直後の初期null(Firestore復元前)は dropWhile で無視する —
     * これを処理すると復元前に永続キャッシュと既存アラームを消してしまう。
     * 非null後のnull(=削除・退出)は正しくクリアとして処理される。
     */
    private fun observeScheduleChanges() {
        appScope.launch {
            container.repository.householdState
                .dropWhile { it == null }
                .collect { state ->
                    val slots = if (state == null) {
                        emptyList()
                    } else {
                        DoseSlotCalculator.upcomingSlots(
                            pets = state.pets,
                            medications = state.medications,
                            now = LocalDateTime.now(),
                        )
                    }
                    container.scheduleCache.write(slots)
                    container.alarmScheduler.scheduleNext()
                }
        }
    }

    private fun enqueueDailyReschedule() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyRescheduleWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyRescheduleWorker>(24, TimeUnit.HOURS).build(),
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOSE,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.notif_channel_desc) }
        )
    }

    companion object {
        const val CHANNEL_DOSE = "dose_reminders"

        fun from(context: Context): PetMedApp = context.applicationContext as PetMedApp
    }
}
