package io.github.aomizuki0307.petmed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.aomizuki0307.petmed.ui.AppViewModel
import io.github.aomizuki0307.petmed.ui.navigation.AppNavHost
import io.github.aomizuki0307.petmed.ui.theme.PetMedTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PetMedApp
        if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            app.container.analytics.log("notification_opened")
        }
        if (isFirstOpen()) {
            app.container.analytics.log("first_open")
        }
        logRetentionPing(app)

        setContent {
            PetMedTheme {
                val viewModel: AppViewModel = viewModel()
                AppNavHost(viewModel)
            }
        }
    }

    private fun isFirstOpen(): Boolean {
        val prefs = getSharedPreferences("app_meta", MODE_PRIVATE)
        val first = !prefs.contains(KEY_FIRST_OPEN_AT)
        if (first) {
            prefs.edit().putLong(KEY_FIRST_OPEN_AT, System.currentTimeMillis()).apply()
        }
        return first
    }

    /** 1/7/14日継続の自己申告ping（同一日は1回のみ） */
    private fun logRetentionPing(app: PetMedApp) {
        val prefs = getSharedPreferences("app_meta", MODE_PRIVATE)
        val firstOpenAt = prefs.getLong(KEY_FIRST_OPEN_AT, System.currentTimeMillis())
        val days = ((System.currentTimeMillis() - firstOpenAt) / 86_400_000L).toInt()
        val milestone = listOf(1, 7, 14).lastOrNull { days >= it } ?: return
        val key = "retention_$milestone"
        if (!prefs.getBoolean(key, false)) {
            prefs.edit().putBoolean(key, true).apply()
            app.container.analytics.log("retention_ping", mapOf("daysSinceFirstOpen" to milestone))
        }
    }

    companion object {
        const val EXTRA_FROM_NOTIFICATION = "fromNotification"
        private const val KEY_FIRST_OPEN_AT = "firstOpenAt"
    }
}
