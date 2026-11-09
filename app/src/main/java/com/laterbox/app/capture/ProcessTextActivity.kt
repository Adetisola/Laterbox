package com.laterbox.app.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.ui.AppViewModelProvider
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxPrimaryButton
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.components.LaterboxSheetSurface
import com.laterbox.app.ui.save.SaveFormMode
import com.laterbox.app.ui.save.SaveLinkForm
import com.laterbox.app.ui.sheets.SaveLinkViewModel
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.launch

/**
 * Handles Android's native text selection action: "Save to Laterbox" (ACTION_PROCESS_TEXT).
 */
class ProcessTextActivity : ComponentActivity() {

    private val saveLinkViewModel: SaveLinkViewModel by viewModels { AppViewModelProvider.Factory }
    private var extractionResult by mutableStateOf<UrlExtractionResult>(UrlExtractionResult.NoValidUrl)
    private var selectedCandidateUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure source text in the calling app is never modified
        setResult(Activity.RESULT_CANCELED)
        enableEdgeToEdge()

        val app = application as LaterboxApplication
        val userPrefsRepo = app.container.userPreferencesRepository

        // Extract selected text
        val rawSelectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)

        extractionResult = UrlCaptureExtractor.extract(rawSelectedText)
        if (extractionResult is UrlExtractionResult.SingleUrl) {
            val single = extractionResult as UrlExtractionResult.SingleUrl
            selectedCandidateUrl = single.normalizedUrl
            saveLinkViewModel.prefill(single.normalizedUrl)
        }

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
            val coroutineScope = rememberCoroutineScope()

            LaterboxTheme(darkTheme = darkTheme, fontScale = fontScale) {
                val uiState by saveLinkViewModel.uiState.collectAsState()
                val colors = LaterboxTheme.colors
                val typography = LaterboxTheme.typography
                val spacing = LaterboxTheme.spacing

                BackHandler {
                    finish()
                }

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
                            when {
                                selectedCandidateUrl != null -> {
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
                                                    coroutineScope.launch {
                                                        selectedCandidateUrl?.let { url ->
                                                            userPrefsRepo.recordHandledClipboardHash(
                                                                ClipboardSuggestionManager.computeHash(url)
                                                            )
                                                        }
                                                        Toast.makeText(
                                                            this@ProcessTextActivity,
                                                            "Saved to Laterbox",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        finish()
                                                    }
                                                }
                                            )
                                        },
                                        onCancelClick = {
                                            finish()
                                        }
                                    )
                                }

                                extractionResult is UrlExtractionResult.MultipleUrls -> {
                                    val multiple = extractionResult as UrlExtractionResult.MultipleUrls
                                    MultipleUrlsPicker(
                                        urls = multiple.urls,
                                        onSelect = { chosen ->
                                            selectedCandidateUrl = chosen.normalizedUrl
                                            saveLinkViewModel.prefill(chosen.normalizedUrl)
                                        },
                                        onCancel = { finish() }
                                    )
                                }

                                else -> {
                                    NoUrlFoundContent(onClose = { finish() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultipleUrlsPicker(
    urls: List<UrlExtractionResult.SingleUrl>,
    onSelect: (UrlExtractionResult.SingleUrl) -> Unit,
    onCancel: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Multiple Links Selected",
            style = typography.sheetTitle,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = "Select which link you want to save to Laterbox:",
            style = typography.bodySecondary,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(spacing.md))

        urls.forEach { single ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LaterboxShapeTokens.Card)
                    .background(colors.surfacePrimary)
                    .clickable { onSelect(single) }
                    .padding(spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Globe),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.accentPrimary
                )
                Spacer(modifier = Modifier.width(spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = single.domain,
                        style = typography.bodyPrimary.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                    Text(
                        text = single.rawUrl,
                        style = typography.metaText,
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.xs))
        }

        Spacer(modifier = Modifier.height(spacing.sm))
        LaterboxSecondaryButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NoUrlFoundContent(onClose: () -> Unit) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "No Link in Selection",
            style = typography.sheetTitle,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = "Laterbox currently saves web links (http:// or https://). No valid website address was found in the selected text.",
            style = typography.bodySecondary,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(spacing.lg))
        LaterboxSecondaryButton(
            text = "Close",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
