package com.laterbox.app.sharing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.domain.util.SharedTextParser
import com.laterbox.app.ui.AppViewModelProvider
import com.laterbox.app.ui.components.LaterboxSheetSurface
import com.laterbox.app.ui.save.SaveFormMode
import com.laterbox.app.ui.save.SaveLinkForm
import com.laterbox.app.ui.sheets.SaveLinkViewModel
import com.laterbox.app.ui.theme.LaterboxTheme

class ShareReceiverActivity : ComponentActivity() {

    private val saveLinkViewModel: SaveLinkViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val parsed = parseIncomingIntent(intent)
        if (parsed == null) {
            finish()
            return
        }

        saveLinkViewModel.prefill(parsed.url, parsed.title, parsed.note)

        val app = application as LaterboxApplication
        val userPrefsRepo = app.container.userPreferencesRepository

        setContent {
            val preferences by userPrefsRepo.userPreferences.collectAsState(
                initial = UserPreferences()
            )

            val darkTheme = when (preferences.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val fontScale = preferences.fontSizeScale.scaleFactor

            LaterboxTheme(darkTheme = darkTheme, fontScale = fontScale) {
                val uiState by saveLinkViewModel.uiState.collectAsState()
                val colors = LaterboxTheme.colors
                val spacing = LaterboxTheme.spacing

                BackHandler {
                    finish()
                }

                // Scrim backdrop
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finish() }
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Floating Bottom Sheet Surface
                    LaterboxSheetSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* consume click to prevent backdrop dismiss */ }
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.base)
                                .verticalScroll(rememberScrollState())
                        ) {
                            SaveLinkForm(
                                uiState = uiState,
                                mode = SaveFormMode.COMPACT,
                                onUrlChange = saveLinkViewModel::onUrlChange,
                                onTitleChange = saveLinkViewModel::onTitleChange,
                                onPersonalNoteChange = saveLinkViewModel::onPersonalNoteChange,
                                onToggleCollection = saveLinkViewModel::onToggleCollection,
                                onCreateCollection = saveLinkViewModel::onCreateCollection,
                                onReminderAtChange = saveLinkViewModel::onReminderAtChange,
                                onSaveClick = {
                                    saveLinkViewModel.onSave(
                                        onSuccess = {
                                            finish()
                                        }
                                    )
                                },
                                onCancelClick = {
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val parsed = parseIncomingIntent(intent) ?: return
        saveLinkViewModel.prefill(parsed.url, parsed.title, parsed.note)
    }

    private fun parseIncomingIntent(intent: Intent?): ParsedContent? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("text/")) return null

        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        val parsed = SharedTextParser.parse(extraText, extraSubject) ?: return null
        return ParsedContent(parsed.url, parsed.title, parsed.note)
    }

    private data class ParsedContent(val url: String, val title: String?, val note: String?)
}
