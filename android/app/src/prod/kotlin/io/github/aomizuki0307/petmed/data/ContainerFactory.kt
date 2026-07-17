package io.github.aomizuki0307.petmed.data

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.github.aomizuki0307.petmed.di.AppContainer
import io.github.aomizuki0307.petmed.reminder.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.security.MessageDigest

/**
 * prodフレーバー: Firebase構成済みなら Firestore 実装、
 * google-services.json 未配置なら Fake にフォールバック（値は捏造しない — 起動可能性を優先）。
 */
object ContainerFactory {

    fun create(app: Application): AppContainer {
        val firebaseApp = FirebaseApp.initializeApp(app)
        val cache = ScheduleCache(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        if (firebaseApp == null) {
            val analytics = NoopAnalyticsLogger()
            return object : AppContainer {
                override val repository: PetCareRepository = FakeInMemoryPetCareRepository()
                override val analytics: AnalyticsLogger = analytics
                override val scheduleCache: ScheduleCache = cache
                override val alarmScheduler: AlarmScheduler = AlarmScheduler(app, cache, analytics)
            }
        }

        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = FirestorePetCareRepository(app, db, auth, scope)
        val analytics = FirestoreAnalyticsLogger(db) { repository.currentHouseholdId }
        return object : AppContainer {
            override val repository: PetCareRepository = repository
            override val analytics: AnalyticsLogger = analytics
            override val scheduleCache: ScheduleCache = cache
            override val alarmScheduler: AlarmScheduler = AlarmScheduler(app, cache, analytics)
        }
    }
}

/**
 * Firestore `events` コレクションへの計測イベント書き込み。
 * PII・医療入力値の送信禁止（docs/07）。uid生値は送らず世帯IDの短縮ハッシュのみ。
 */
class FirestoreAnalyticsLogger(
    private val db: FirebaseFirestore,
    private val hidProvider: () -> String?,
) : AnalyticsLogger {

    override fun log(name: String, params: Map<String, Any?>) {
        runCatching {
            db.collection("events").add(
                mapOf(
                    "name" to name,
                    "params" to params,
                    "ts" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "hidHash" to (hidProvider()?.let(::sha256Head) ?: ""),
                    "appVersion" to io.github.aomizuki0307.petmed.BuildConfig.VERSION_NAME,
                ),
            )
        }
    }

    private fun sha256Head(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(8)
}
