package com.laterbox.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.ui.navigation.LaterboxApp
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val openLinkIdState = MutableStateFlow<Long?>(null)
    private val openDestinationState = MutableStateFlow<Pair<String, String?>?>(null)

    companion object {
        const val EXTRA_OPEN_LINK_ID = "com.laterbox.app.extra.OPEN_LINK_ID"
        const val EXTRA_OPEN_DESTINATION = "com.laterbox.app.extra.OPEN_DESTINATION"
        const val EXTRA_CHAT_INITIAL_PROMPT = "com.laterbox.app.extra.CHAT_INITIAL_PROMPT"
        private var hasShownColdSplash = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        if (savedInstanceState == null && !hasShownColdSplash) {
            val splashStartTime = System.currentTimeMillis()
            splashScreen.setKeepOnScreenCondition {
                val elapsed = System.currentTimeMillis() - splashStartTime
                val keep = elapsed < 900L
                if (!keep) {
                    hasShownColdSplash = true
                }
                keep
            }
        }

        enableEdgeToEdge()
        val container = (application as LaterboxApplication).container

        setContent {
            val preferences by container.userPreferencesRepository.userPreferences.collectAsState(
                initial = UserPreferences()
            )
            val openLinkId by openLinkIdState.collectAsState()
            val openDestination by openDestinationState.collectAsState()

            val darkTheme = when (preferences.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val fontScale = preferences.fontSizeScale.scaleFactor

            LaterboxTheme(darkTheme = darkTheme, fontScale = fontScale) {
                LaterboxApp(
                    initialOpenLinkId = openLinkId,
                    onClearOpenLinkId = { openLinkIdState.value = null },
                    initialOpenDestination = openDestination,
                    onClearOpenDestination = { openDestinationState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val linkId = intent?.getLongExtra(EXTRA_OPEN_LINK_ID, -1L) ?: -1L
        if (linkId > 0) {
            openLinkIdState.value = linkId
            intent?.removeExtra(EXTRA_OPEN_LINK_ID)
        }
        val dest = intent?.getStringExtra(EXTRA_OPEN_DESTINATION)
        if (!dest.isNullOrBlank()) {
            val prompt = intent.getStringExtra(EXTRA_CHAT_INITIAL_PROMPT)
            openDestinationState.value = dest to prompt
            intent.removeExtra(EXTRA_OPEN_DESTINATION)
            intent.removeExtra(EXTRA_CHAT_INITIAL_PROMPT)
        }
    }
}
