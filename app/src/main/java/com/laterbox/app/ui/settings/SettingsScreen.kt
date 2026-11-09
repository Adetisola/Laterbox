package com.laterbox.app.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.data.preferences.SmartReminderPersistence
import com.laterbox.app.data.preferences.SmartReminderSchedule
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUserNameChange: (String) -> Unit,
    onAutoMarkAsReadChange: (Boolean) -> Unit,
    onAllowSystemBackupChange: (Boolean) -> Unit,
    onFontSizeScaleChange: (com.laterbox.app.data.preferences.FontSizeScale) -> Unit,
    onSmartRemindersEnabledChange: (Boolean) -> Unit = {},
    onSmartReminderScheduleChange: (SmartReminderSchedule) -> Unit = {},
    onSmartReminderTimeChange: (Int, Int) -> Unit = { _, _ -> },
    onSmartReminderPersistenceChange: (SmartReminderPersistence) -> Unit = {},
    onAiEngineChange: (com.laterbox.app.data.preferences.AiEngine) -> Unit = {},
    aiConnectionStatus: Pair<Boolean, String>? = null,
    onGeminiApiKeyChange: (String) -> Unit = {},
    onTestAiConnection: () -> Unit = {},
    onSendTestSmartDigest: suspend () -> Boolean = { false },
    onExportBackup: suspend (OutputStream) -> Result<Int>,
    onImportBackup: suspend (InputStream) -> Result<com.laterbox.app.data.backup.BackupRestoreResult>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showCustomTimePicker by remember { mutableStateOf(false) }

    // Storage Access Framework Launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        val result = onExportBackup(outputStream)
                        if (result.isSuccess) {
                            Toast.makeText(context, "Exported ${result.getOrNull()} links successfully", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val result = onImportBackup(inputStream)
                        if (result.isSuccess) {
                            val r = result.getOrThrow()
                            Toast.makeText(
                                context,
                                "Restored ${r.restoredLinksCount} links, ${r.restoredCollectionsCount} collections (${r.skippedDuplicatesCount} duplicates skipped)",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header with back navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button with tactile backdrop
                LaterboxIconButton(
                    iconRes = LaterboxIcons.ArrowLeft,
                    contentDescription = "Back to inbox",
                    onClick = onBackClick
                )

                Spacer(modifier = Modifier.width(spacing.sm))

                Column {
                    Text(
                        text = "Settings",
                        style = typography.displayLarge.copy(fontSize = 22.sp),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Preferences & privacy",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Section: Appearance
            SectionHeader(title = "Appearance")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "Theme",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        ThemeOptionChip(
                            label = "System",
                            isSelected = preferences.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "Ivory (Light)",
                            isSelected = preferences.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "Charcoal (Dark)",
                            isSelected = preferences.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.sm))

                    Text(
                        text = "Font Size",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        ThemeOptionChip(
                            label = "Small",
                            isSelected = preferences.fontSizeScale == com.laterbox.app.data.preferences.FontSizeScale.SMALL,
                            onClick = { onFontSizeScaleChange(com.laterbox.app.data.preferences.FontSizeScale.SMALL) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "System",
                            isSelected = preferences.fontSizeScale == com.laterbox.app.data.preferences.FontSizeScale.SYSTEM,
                            onClick = { onFontSizeScaleChange(com.laterbox.app.data.preferences.FontSizeScale.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "Large",
                            isSelected = preferences.fontSizeScale == com.laterbox.app.data.preferences.FontSizeScale.LARGE,
                            onClick = { onFontSizeScaleChange(com.laterbox.app.data.preferences.FontSizeScale.LARGE) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Live Typography Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(spacing.radiusSmall))
                            .background(colors.surfaceSecondary)
                            .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                            .padding(spacing.sm)
                    ) {
                        Column {
                            Text(
                                text = "Preview (${preferences.fontSizeScale.label})",
                                style = typography.metaText,
                                color = colors.accentPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Find something interesting. Save it. Get back to it later.",
                                style = typography.bodyPrimary,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: Profile & Personalisation
            SectionHeader(title = "Profile & Personalisation")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        Icon(
                            painter = painterResource(id = LaterboxIcons.User),
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "What should we call you?",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )
                    }

                    Text(
                        text = "Optional. Used for smart notifications and quiet background reminders.",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xxs))

                    var nameInput by remember(preferences.userName) { mutableStateOf(preferences.userName) }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { newValue ->
                            nameInput = newValue.take(50)
                            onUserNameChange(newValue)
                        },
                        placeholder = {
                            Text(
                                text = "Your name or nickname",
                                style = typography.bodyPrimary,
                                color = colors.textTertiary
                            )
                        },
                        singleLine = true,
                        textStyle = typography.bodyPrimary.copy(color = colors.textPrimary),
                        shape = RoundedCornerShape(spacing.radiusSmall),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentPrimary,
                            unfocusedBorderColor = colors.borderSubtle,
                            focusedContainerColor = colors.surfaceSecondary,
                            unfocusedContainerColor = colors.surfaceSecondary,
                            cursorColor = colors.accentPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: Smart Reminders
            SectionHeader(title = "Smart Reminders")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    // Smart Reminders Master Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                Icon(
                                    painter = painterResource(id = LaterboxIcons.Sparkles),
                                    contentDescription = null,
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Smart Reminders",
                                    style = typography.uiMedium,
                                    color = colors.textPrimary
                                )
                            }
                            Text(
                                text = "Quiet, context-aware nudges powered by Gemini 2.5 Flash Lite",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = preferences.smartRemindersEnabled,
                            onCheckedChange = onSmartRemindersEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    if (preferences.smartRemindersEnabled) {
                        Spacer(modifier = Modifier.height(spacing.xxs))

                        // Schedule / Frequency
                        Text(
                            text = "Schedule",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            SmartReminderSchedule.values().forEach { sched ->
                                ThemeOptionChip(
                                    label = sched.label,
                                    isSelected = preferences.smartReminderSchedule == sched,
                                    onClick = { onSmartReminderScheduleChange(sched) }
                                )
                            }
                        }

                        Text(
                            text = preferences.smartReminderSchedule.description,
                            style = typography.metaText,
                            color = colors.textSecondary
                        )

                        // If Daily Digest, show Delivery Time Presets + Custom option
                        if (preferences.smartReminderSchedule == SmartReminderSchedule.DAILY_DIGEST) {
                            Spacer(modifier = Modifier.height(spacing.xxs))

                            Text(
                                text = "Delivery Time",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                val isMorning = preferences.smartReminderHour == 9 && preferences.smartReminderMinute == 0
                                val isAfternoon = preferences.smartReminderHour == 13 && preferences.smartReminderMinute == 0
                                val isEvening = preferences.smartReminderHour == 20 && preferences.smartReminderMinute == 0
                                val isCustom = !isMorning && !isAfternoon && !isEvening

                                ThemeOptionChip(
                                    label = "Morning (9:00 AM)",
                                    isSelected = isMorning,
                                    onClick = { onSmartReminderTimeChange(9, 0) }
                                )
                                ThemeOptionChip(
                                    label = "Afternoon (1:00 PM)",
                                    isSelected = isAfternoon,
                                    onClick = { onSmartReminderTimeChange(13, 0) }
                                )
                                ThemeOptionChip(
                                    label = "Evening (8:00 PM)",
                                    isSelected = isEvening,
                                    onClick = { onSmartReminderTimeChange(20, 0) }
                                )
                                ThemeOptionChip(
                                    label = "Custom…",
                                    isSelected = isCustom,
                                    onClick = { showCustomTimePicker = true }
                                )
                            }

                            val displayHour = when {
                                preferences.smartReminderHour == 0 -> 12
                                preferences.smartReminderHour > 12 -> preferences.smartReminderHour - 12
                                else -> preferences.smartReminderHour
                            }
                            val amPm = if (preferences.smartReminderHour >= 12) "PM" else "AM"
                            val formattedTime = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, preferences.smartReminderMinute, amPm)
                            Text(
                                text = "Delivers daily around $formattedTime",
                                style = typography.metaText,
                                color = colors.accentPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.xxs))

                        // Reminder Persistence / Max Nudges
                        Text(
                            text = "Max Nudges per Link",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            SmartReminderPersistence.values().forEach { persistence ->
                                ThemeOptionChip(
                                    label = persistence.label,
                                    isSelected = preferences.smartReminderPersistence == persistence,
                                    onClick = { onSmartReminderPersistenceChange(persistence) }
                                )
                            }
                        }

                        Text(
                            text = "Number of times Laterbox will gently remind you before leaving a link calm in your inbox.",
                            style = typography.metaText,
                            color = colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(spacing.xs))

                        // Google AI Studio API Key & Connection Test
                        Text(
                            text = "Google AI Studio API Key",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )

                        Text(
                            text = "Laterbox connects to Gemini 2.5 Flash Lite. By default, it uses the embedded app key. If your app key hits Google Cloud service restrictions or quota limits, paste your free Gemini API key from aistudio.google.com here.",
                            style = typography.metaText,
                            color = colors.textSecondary
                        )

                        var apiKeyInput by remember(preferences.geminiApiKey) { mutableStateOf(preferences.geminiApiKey) }

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it.trim()
                                onGeminiApiKeyChange(it.trim())
                            },
                            placeholder = {
                                Text(
                                    text = "AIzaSy... (leave blank for default)",
                                    style = typography.bodySecondary.copy(fontSize = 12.sp),
                                    color = colors.textTertiary
                                )
                            },
                            textStyle = typography.codeText.copy(fontSize = 12.sp, color = colors.textPrimary),
                            singleLine = true,
                            shape = RoundedCornerShape(spacing.radiusSmall),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentPrimary,
                                unfocusedBorderColor = colors.borderSubtle,
                                focusedContainerColor = colors.surfaceSecondary,
                                unfocusedContainerColor = colors.surfaceSecondary,
                                cursorColor = colors.accentPrimary
                            ),
                            trailingIcon = {
                                if (apiKeyInput.isNotEmpty()) {
                                    IconButton(onClick = {
                                        apiKeyInput = ""
                                        onGeminiApiKeyChange("")
                                    }) {
                                        Icon(
                                            painter = painterResource(id = LaterboxIcons.Close),
                                            contentDescription = "Clear",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Test AI Connection Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(spacing.minTouchTarget)
                                .clip(RoundedCornerShape(spacing.radiusSmall))
                                .background(colors.surfaceSecondary)
                                .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                                .clickable { onTestAiConnection() }
                                .padding(horizontal = spacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                Icon(
                                    painter = painterResource(id = LaterboxIcons.Sparkles),
                                    contentDescription = null,
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Test AI Connection",
                                    style = typography.uiMedium.copy(fontSize = 13.sp),
                                    color = colors.accentPrimary
                                )
                            }
                        }

                        // Status banner if tested
                        aiConnectionStatus?.let { (isSuccess, message) ->
                            val statusBorder = if (isSuccess) colors.accentPrimary else colors.statusDanger
                            val statusColor = if (isSuccess) colors.textPrimary else colors.statusDanger

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(spacing.radiusSmall))
                                    .background(colors.surfaceSecondary)
                                    .border(spacing.hairline, statusBorder, RoundedCornerShape(spacing.radiusSmall))
                                    .padding(spacing.sm)
                            ) {
                                Text(
                                    text = if (isSuccess) "✓ $message" else "⚠ $message",
                                    style = typography.metaText.copy(fontSize = 12.sp),
                                    color = statusColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.xxs))

                        // Send test notification button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(spacing.minTouchTarget)
                                .clip(RoundedCornerShape(spacing.radiusSmall))
                                .background(colors.surfaceSecondary)
                                .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                                .clickable {
                                    coroutineScope.launch {
                                        val sent = onSendTestSmartDigest()
                                        if (sent) {
                                            Toast.makeText(context, "Test reminder sent! Check notification shade.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Could not send notification. Ensure notification permissions are enabled.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .padding(horizontal = spacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                Icon(
                                    painter = painterResource(id = LaterboxIcons.Sparkles),
                                    contentDescription = null,
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Send Test Smart Reminder",
                                    style = typography.uiMedium.copy(fontSize = 13.sp),
                                    color = colors.accentPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: Reading & Behavior
            SectionHeader(title = "Reading & Behavior")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    // Auto-mark as read
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-mark as read",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Mark links as read automatically when opened",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = preferences.autoMarkAsRead,
                            onCheckedChange = onAutoMarkAsReadChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spacing.hairline)
                            .background(colors.borderSubtle)
                    )

                    // System backup toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Android OS Auto-Backup",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Allow encrypted system cloud backup for local database",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = preferences.allowSystemBackup,
                            onCheckedChange = onAllowSystemBackupChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: Data & Backup (Local-First)
            SectionHeader(title = "Data & Storage (Local-First)")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "Backup & Restore",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Export your entire library as an open JSON file or restore from a previous backup. No proprietary lock-in.",
                        style = typography.bodySecondary.copy(fontSize = 12.sp),
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        // Export button (min 48dp touch target)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(spacing.minTouchTarget)
                                .clip(RoundedCornerShape(spacing.radiusSmall))
                                .background(colors.surfaceSecondary)
                                .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                                .clickable {
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportLauncher.launch("laterbox_backup_$dateStr.json")
                                }
                                .padding(horizontal = spacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Export JSON",
                                style = typography.uiMedium.copy(fontSize = 13.sp),
                                color = colors.accentPrimary
                            )
                        }

                        // Import button (min 48dp touch target)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(spacing.minTouchTarget)
                                .clip(RoundedCornerShape(spacing.radiusSmall))
                                .background(colors.surfaceSecondary)
                                .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                                .clickable {
                                    importLauncher.launch(arrayOf("application/json", "text/*"))
                                }
                                .padding(horizontal = spacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Import JSON",
                                style = typography.uiMedium.copy(fontSize = 13.sp),
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: About & Privacy
            SectionHeader(title = "About & Privacy")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "Laterbox v1.0.5",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "A calm, local-first reading inbox. Your links and notes remain entirely on your device. No user accounts, no telemetry, and no third-party tracking.",
                        style = typography.bodySecondary,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xs))

                    // Open source licenses button (min 48dp touch target)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spacing.minTouchTarget)
                            .clip(RoundedCornerShape(spacing.radiusSmall))
                            .background(colors.surfaceSecondary)
                            .clickable { showLicensesDialog = true }
                            .padding(horizontal = spacing.md),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Open Source Licenses & Typography",
                            style = typography.uiMedium.copy(fontSize = 13.sp),
                            color = colors.accentPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Licenses Dialog
        if (showLicensesDialog) {
            AlertDialog(
                onDismissRequest = { showLicensesDialog = false },
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = "Open Source Licenses",
                        style = typography.titleSmall,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Text(
                            text = "Typography:\n• Newsreader — SIL Open Font License 1.1 (Production Type)\n• Inter — SIL Open Font License 1.1 (Rasmus Andersson)\n• JetBrains Mono — SIL Open Font License 1.1 (JetBrains)",
                            style = typography.bodySecondary,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(
                            text = "Libraries:\n• AndroidX Jetpack & Jetpack Compose (Apache 2.0)\n• Room SQLite Database (Apache 2.0)\n• Coil Image Loading (Apache 2.0)\n• Jsoup HTML Parser (MIT License)\n• Kotlin & Coroutines (Apache 2.0)",
                            style = typography.bodySecondary,
                            color = colors.textSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLicensesDialog = false }) {
                        Text(
                            text = "Close",
                            style = typography.uiMedium,
                            color = colors.accentPrimary
                        )
                    }
                }
            )
        }

        if (showCustomTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = preferences.smartReminderHour,
                initialMinute = preferences.smartReminderMinute,
                is24Hour = false
            )
            AlertDialog(
                onDismissRequest = { showCustomTimePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onSmartReminderTimeChange(timePickerState.hour, timePickerState.minute)
                            showCustomTimePicker = false
                        }
                    ) {
                        Text("Set Time", color = colors.accentPrimary, style = typography.uiMedium)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomTimePicker = false }) {
                        Text("Cancel", color = colors.textSecondary, style = typography.uiMedium)
                    }
                },
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = "Daily Reminder Time",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimePicker(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = colors.surfaceSecondary,
                                selectorColor = colors.accentPrimary,
                                containerColor = colors.surfaceElevated,
                                timeSelectorSelectedContainerColor = colors.accentContainer,
                                timeSelectorUnselectedContainerColor = colors.surfaceSecondary,
                                timeSelectorSelectedContentColor = colors.accentPrimary,
                                timeSelectorUnselectedContentColor = colors.textPrimary
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val typography = LaterboxTheme.typography
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing

    Text(
        text = title,
        style = typography.metaText,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = spacing.base, vertical = spacing.xs)
    )
}

@Composable
private fun ThemeOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val bg = if (isSelected) colors.surfaceSecondary else colors.backgroundPrimary
    val borderColor = if (isSelected) colors.accentPrimary else colors.borderSubtle
    val textColor = if (isSelected) colors.accentPrimary else colors.textSecondary

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(spacing.radiusSmall))
            .background(bg)
            .border(spacing.hairline, borderColor, RoundedCornerShape(spacing.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.metaText.copy(fontSize = 11.sp),
            color = textColor,
            maxLines = 1
        )
    }
}
