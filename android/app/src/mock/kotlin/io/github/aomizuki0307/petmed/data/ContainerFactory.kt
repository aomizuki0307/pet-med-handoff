package io.github.aomizuki0307.petmed.data

import android.app.Application
import io.github.aomizuki0307.petmed.di.AppContainer
import io.github.aomizuki0307.petmed.reminder.AlarmScheduler

/** mockフレーバー: 認証情報なしで全フローを動かす（デモデータ入り） */
object ContainerFactory {

    fun create(app: Application): AppContainer {
        val analytics = NoopAnalyticsLogger()
        val cache = ScheduleCache(app)
        return object : AppContainer {
            override val repository: PetCareRepository =
                FakeInMemoryPetCareRepository(seedDemoData = true)
            override val analytics: AnalyticsLogger = analytics
            override val scheduleCache: ScheduleCache = cache
            override val alarmScheduler: AlarmScheduler = AlarmScheduler(app, cache, analytics)
        }
    }
}
