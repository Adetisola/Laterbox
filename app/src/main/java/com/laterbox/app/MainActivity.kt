package com.laterbox.app

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

class MainActivity : ComponentActivity() {

    companion object {
        private var hasShownColdSplash = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

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

            val darkTheme = when (preferences.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val fontScale = preferences.fontSizeScale.scaleFactor

            LaterboxTheme(darkTheme = darkTheme, fontScale = fontScale) {
                LaterboxApp()
            }
        }
    }
}
