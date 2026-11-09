package com.laterbox.app.quicksave

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.domain.util.UrlValidator
import com.laterbox.app.ui.AppViewModelProvider
import com.laterbox.app.ui.components.LaterboxPrimaryButton
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.components.LaterboxSheetSurface
import com.laterbox.app.ui.save.SaveFormMode
import com.laterbox.app.ui.save.SaveLinkForm
import com.laterbox.app.ui.sheets.SaveLinkViewModel
import com.laterbox.app.ui.theme.LaterboxTheme

class QuickSaveActivity : ComponentActivity() {

    private val saveLinkViewModel: SaveLinkViewModel by viewModels { AppViewModelProvider.Factory }
    private var hasCheckedClipboard by mutableStateOf(false)
    private var detectedUrl by mutableStateOf<String?>(null)
    private var showManualFallback by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                val typography = LaterboxTheme.typography
                val spacing = LaterboxTheme.spacing

                BackHandler {
                    finish()
                }

                // Backdrop Scrim
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
                    LaterboxSheetSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* prevent backdrop dismiss */ }
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.base)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (detectedUrl != null || showManualFallback) {
                                SaveLinkForm(
                                    uiState = uiState,
                                    mode = if (showManualFallback) SaveFormMode.FULL else SaveFormMode.COMPACT,
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
                            } else if (hasCheckedClipboard) {
                                // Empty clipboard prompt
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "No Link on Clipboard",
                                        style = typography.sheetTitle,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(spacing.xs))
                                    Text(
                                        text = "Copy a webpage link in your browser or another app, then tap the Quick Settings tile to save it immediately.",
                                        style = typography.bodySecondary,
                                        color = colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(spacing.md))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        LaterboxSecondaryButton(
                                            text = "Close",
                                            onClick = { finish() },
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(spacing.sm))
                                        LaterboxPrimaryButton(
                                            text = "Enter Manually",
                                            onClick = { showManualFallback = true },
                                            modifier = Modifier.weight(1.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasCheckedClipboard) {
            checkClipboard()
        }
    }

    private fun checkClipboard() {
        hasCheckedClipboard = true
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            val clipText = item?.text?.toString() ?: item?.uri?.toString()
            val extracted = UrlValidator.extractUrl(clipText)
            if (extracted != null) {
                detectedUrl = extracted
                saveLinkViewModel.prefill(extracted)
            }
        } catch (_: Exception) {
            // Ignore clipboard errors
        }
    }
}
