package io.github.aomizuki0307.petmed.di

import io.github.aomizuki0307.petmed.data.AnalyticsLogger
import io.github.aomizuki0307.petmed.data.PetCareRepository
import io.github.aomizuki0307.petmed.data.ScheduleCache
import io.github.aomizuki0307.petmed.reminder.AlarmScheduler

/** 手動DIコンテナ。フレーバー別の ContainerFactory が実体を生成する */
interface AppContainer {
    val repository: PetCareRepository
    val analytics: AnalyticsLogger
    val scheduleCache: ScheduleCache
    val alarmScheduler: AlarmScheduler
}
