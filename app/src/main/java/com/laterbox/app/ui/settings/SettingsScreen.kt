package com.laterbox.app.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.laterbox.app.capture.accessibility.AccessibilityServiceStateHelper
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import com.laterbox.app.R
import com.laterbox.app.data.preferences.FontSizeScale
import com.laterbox.app.data.preferences.SmartReminderPersistence
import com.laterbox.app.data.preferences.SmartReminderSchedule
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.quicksave.QuickSaveTileService
import com.laterbox.app.ui.components.LaterboxBanner
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxPrimaryButton
import com.laterbox.app.ui.components.LaterboxSecondaryButton
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
    onFontSizeScaleChange: (FontSizeScale) -> Unit,
    onSmartRemindersEnabledChange: (Boolean) -> Unit = {},
    // Independent schedule controls
    onMorningEveningEnabledChange: (Boolean) -> Unit = {},
    onMorningTimeChange: (Int, Int) -> Unit = { _, _ -> },
    onEveningTimeChange: (Int, Int) -> Unit = { _, _ -> },
    onWeekendRoundupEnabledChange: (Boolean) -> Unit = {},
    onWeekendRoundupTimeChange: (Int, Int) -> Unit = { _, _ -> },
    onDailyDigestEnabledChange: (Boolean) -> Unit = {},
    onDailyDigestTimeChange: (Int, Int) -> Unit = { _, _ -> },
    onDailyDigestWeekdaysOnlyChange: (Boolean) -> Unit = {},
    onCustomScheduleEnabledChange: (Boolean) -> Unit = {},
    onCustomScheduleDaysChange: (Set<Int>) -> Unit = {},
    onCustomScheduleTimeChange: (Int, Int) -> Unit = { _, _ -> },
    onSmartReminderPersistenceChange: (SmartReminderPersistence) -> Unit = {},
    // Legacy callbacks for compatibility
    onSmartReminderScheduleChange: (SmartReminderSchedule) -> Unit = {},
    onSmartReminderTimeChange: (Int, Int) -> Unit = { _, _ -> },
    aiConnectionStatus: Pair<Boolean, String>? = null,
    onGeminiApiKeyChange: (String) -> Unit = {},
    onTestAiConnection: () -> Unit = {},
    onSendTestSmartDigest: suspend () -> Boolean = { false },
    onExportBackup: suspend (OutputStream) -> Result<Int>,
    onImportBackup: suspend (InputStream) -> Result<com.laterbox.app.data.backup.BackupRestoreResult>,
    onAccessibilityCaptureEnabledChange: (Boolean) -> Unit = {},
    onClipboardSuggestionEnabledChange: (Boolean) -> Unit = {},
    onToggleExcludedPackage: (String) -> Unit = {},
    onMediaDownloadNetworkPolicyChange: (com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy) -> Unit = {},
    onAutoInspectMediaOnSaveChange: (Boolean) -> Unit = {},
    onFastEngineEnabledChange: (Boolean) -> Unit = {},
    onSetCustomExportDirectory: (String, String) -> Unit = { _, _ -> },
    onClearCustomExportDirectory: () -> Unit = {},
    isUpdatingYtDlp: Boolean = false,
    onUpdateYtDlpClick: () -> Unit = {},
    ytDlpState: com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState = com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState.NotInitialized,
    ffmpegState: com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState = com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState.NotInitialized,
    onGetDiagnosticReport: () -> com.laterbox.app.media.data.adapters.ytdlp.YtDlpDiagnosticReport? = { null },
    onGetDiagnosticReportText: () -> String = { "" },
    onRetryInitializeEngine: () -> Unit = {},
    isCheckingUpdate: Boolean = false,
    availableUpdateInfo: com.laterbox.app.update.AppUpdateInfo? = null,
    onCheckForUpdates: () -> Unit = {},
    onDismissUpdateDialog: () -> Unit = {},
    onPostponeUpdate: (String) -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    var showLicensesDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var diagnosticReport by remember { mutableStateOf<com.laterbox.app.media.data.adapters.ytdlp.YtDlpDiagnosticReport?>(null) }
    var diagnosticReportText by remember { mutableStateOf<String?>(null) }
    var showAccessibilityOnboarding by remember { mutableStateOf(false) }
    var showExcludedAppsDialog by remember { mutableStateOf(false) }
    val accessibilitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Notification & Exact Alarm Permission states
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var canScheduleExact by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager)?.canScheduleExactAlarms() ?: true
            } else {
                true
            }
        )
    }

    LifecycleResumeEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager)?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
        onPauseOrDispose {}
    }

    // Time picker dialog states
    var timePickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }

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
                            val stats = result.getOrNull()
                            Toast.makeText(
                                context,
                                "Restored ${stats?.restoredLinksCount ?: 0} links, ${stats?.restoredCollectionsCount ?: 0} collections",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "Restore failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                val doc = DocumentFile.fromTreeUri(context, uri)
                val folderName = doc?.name ?: "Selected Folder"
                onSetCustomExportDirectory(uri.toString(), folderName)
                Toast.makeText(context, "Export directory set to $folderName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not set directory: ${e.message}", Toast.LENGTH_SHORT).show()
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LaterboxIconButton(
                    iconRes = LaterboxIcons.ArrowLeft,
                    contentDescription = "Back",
                    onClick = onBackClick
                )

                Spacer(modifier = Modifier.width(spacing.sm))

                Text(
                    text = "Settings",
                    style = typography.wordmark,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(spacing.xs))

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
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Text(
                        text = "Theme",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        ThemeOptionChip(
                            label = "System Default",
                            isSelected = preferences.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                        )
                        ThemeOptionChip(
                            label = "Light (Ivory)",
                            isSelected = preferences.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                        )
                        ThemeOptionChip(
                            label = "Dark (Charcoal)",
                            isSelected = preferences.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spacing.hairline)
                            .background(colors.borderSubtle)
                    )

                    Text(
                        text = "Font Size",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        FontSizeScale.values().forEach { scale ->
                            ThemeOptionChip(
                                label = scale.label,
                                isSelected = preferences.fontSizeScale == scale,
                                onClick = { onFontSizeScaleChange(scale) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: Profile & Personalization
            SectionHeader(title = "Profile")

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
                    Text(
                        text = "Your Name",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Used locally to personalize your reminder greetings.",
                        style = typography.bodySecondary.copy(fontSize = 12.sp),
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xxs))

                    var userNameInput by remember(preferences.userName) { mutableStateOf(preferences.userName) }

                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = {
                            userNameInput = it
                            onUserNameChange(it)
                        },
                        placeholder = {
                            Text(
                                text = "e.g. Alex",
                                style = typography.bodySecondary,
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

            // Section: Smart Reminders (Calendar Scheduling & Google AI Studio)
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
                    // Master Toggle
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
                                text = "Quiet, context-aware nudges powered by Google AI Studio",
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
                        // Notification Permission Check Banner
                        if (!notificationsEnabled) {
                            LaterboxBanner(
                                text = "Notifications are disabled. Laterbox cannot deliver your scheduled reminders.",
                                isWarning = true,
                                actionText = "Settings",
                                onActionClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }

                        // Exact Alarm Permission Check Banner (Android 12+)
                        if (!canScheduleExact) {
                            LaterboxBanner(
                                text = "Exact alarm permission is disabled. Reminders will use approximate delivery windows unless exact alarms are allowed.",
                                isWarning = true,
                                actionText = "Allow",
                                onActionClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(fallback)
                                        }
                                    }
                                }
                            )
                        }

                        Divider(colors.borderSubtle)

                        Text(
                            text = "Reminder Schedules",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Enable the calendar schedules you prefer. AlarmManager coordinates each time slot with exact timing.",
                            style = typography.metaText,
                            color = colors.textSecondary
                        )

                        // 1. Daily Digest Schedule
                        ScheduleRow(
                            title = "Daily Digest",
                            description = "Daily reading digest around ${formatTime(preferences.dailyDigestHour, preferences.dailyDigestMinute)}" +
                                if (preferences.dailyDigestWeekdaysOnly) " (Weekdays only)" else "",
                            enabled = preferences.dailyDigestEnabled,
                            onEnabledChange = onDailyDigestEnabledChange
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                val isMorning = preferences.dailyDigestHour == 9 && preferences.dailyDigestMinute == 0
                                val isAfternoon = preferences.dailyDigestHour == 13 && preferences.dailyDigestMinute == 0
                                val isEvening = preferences.dailyDigestHour == 20 && preferences.dailyDigestMinute == 0
                                val isCustom = !isMorning && !isAfternoon && !isEvening

                                ThemeOptionChip(
                                    label = "Morning (9:00 AM)",
                                    isSelected = isMorning,
                                    onClick = { onDailyDigestTimeChange(9, 0) }
                                )
                                ThemeOptionChip(
                                    label = "Afternoon (1:00 PM)",
                                    isSelected = isAfternoon,
                                    onClick = { onDailyDigestTimeChange(13, 0) }
                                )
                                ThemeOptionChip(
                                    label = "Evening (8:00 PM)",
                                    isSelected = isEvening,
                                    onClick = { onDailyDigestTimeChange(20, 0) }
                                )
                                ThemeOptionChip(
                                    label = if (isCustom) formatTime(preferences.dailyDigestHour, preferences.dailyDigestMinute) else "Custom…",
                                    isSelected = isCustom,
                                    onClick = {
                                        timePickerTarget = TimePickerTarget.DailyDigest(
                                            preferences.dailyDigestHour,
                                            preferences.dailyDigestMinute
                                        )
                                    }
                                )
                                ThemeOptionChip(
                                    label = if (preferences.dailyDigestWeekdaysOnly) "Weekdays (Mon–Fri)" else "Every Day",
                                    isSelected = preferences.dailyDigestWeekdaysOnly,
                                    onClick = { onDailyDigestWeekdaysOnlyChange(!preferences.dailyDigestWeekdaysOnly) }
                                )
                            }
                        }

                        Divider(colors.borderSubtle)

                        // 2. Morning & Evening Schedule
                        ScheduleRow(
                            title = "Morning & Evening",
                            description = "Two distinct daily times: ${formatTime(preferences.morningHour, preferences.morningMinute)} and ${formatTime(preferences.eveningHour, preferences.eveningMinute)}",
                            enabled = preferences.morningEveningEnabled,
                            onEnabledChange = onMorningEveningEnabledChange
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                ThemeOptionChip(
                                    label = "Morning: " + formatTime(preferences.morningHour, preferences.morningMinute),
                                    isSelected = true,
                                    onClick = {
                                        timePickerTarget = TimePickerTarget.Morning(
                                            preferences.morningHour,
                                            preferences.morningMinute
                                        )
                                    }
                                )
                                ThemeOptionChip(
                                    label = "Evening: " + formatTime(preferences.eveningHour, preferences.eveningMinute),
                                    isSelected = true,
                                    onClick = {
                                        timePickerTarget = TimePickerTarget.Evening(
                                            preferences.eveningHour,
                                            preferences.eveningMinute
                                        )
                                    }
                                )
                            }
                        }

                        Divider(colors.borderSubtle)

                        // 3. Weekend Roundup Schedule
                        ScheduleRow(
                            title = "Weekend Roundup",
                            description = "Saturday and Sunday morning around ${formatTime(preferences.weekendRoundupHour, preferences.weekendRoundupMinute)}",
                            enabled = preferences.weekendRoundupEnabled,
                            onEnabledChange = onWeekendRoundupEnabledChange
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                ThemeOptionChip(
                                    label = "Delivery: " + formatTime(preferences.weekendRoundupHour, preferences.weekendRoundupMinute),
                                    isSelected = true,
                                    onClick = {
                                        timePickerTarget = TimePickerTarget.Weekend(
                                            preferences.weekendRoundupHour,
                                            preferences.weekendRoundupMinute
                                        )
                                    }
                                )
                            }
                        }

                        Divider(colors.borderSubtle)

                        // 4. Custom Schedule
                        ScheduleRow(
                            title = "Custom Schedule",
                            description = "Nudges only on specific days at ${formatTime(preferences.customScheduleHour, preferences.customScheduleMinute)}",
                            enabled = preferences.customScheduleEnabled,
                            onEnabledChange = onCustomScheduleEnabledChange
                        ) {
                            val daysLabels = listOf(
                                1 to "Mon", 2 to "Tue", 3 to "Wed",
                                4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
                            ) {
                                daysLabels.forEach { (dayVal, dayLabel) ->
                                    val isSelected = preferences.customScheduleDays.contains(dayVal)
                                    ThemeOptionChip(
                                        label = dayLabel,
                                        isSelected = isSelected,
                                        onClick = {
                                            val updated = if (isSelected) {
                                                preferences.customScheduleDays - dayVal
                                            } else {
                                                preferences.customScheduleDays + dayVal
                                            }
                                            onCustomScheduleDaysChange(updated)
                                        }
                                    )
                                }
                                ThemeOptionChip(
                                    label = formatTime(preferences.customScheduleHour, preferences.customScheduleMinute),
                                    isSelected = true,
                                    onClick = {
                                        timePickerTarget = TimePickerTarget.Custom(
                                            preferences.customScheduleHour,
                                            preferences.customScheduleMinute
                                        )
                                    }
                                )
                            }
                        }

                        Divider(colors.borderSubtle)

                        // Persistence
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

                        Divider(colors.borderSubtle)

                        // Google AI Studio Configuration
                        Text(
                            text = "Google AI Studio",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )

                        Text(
                            text = "Powered by Gemini via Google AI Studio for calm, context-aware digests and smart reminders.",
                            style = typography.metaText,
                            color = colors.textSecondary
                        )

                        // Test AI Connection Button
                        LaterboxSecondaryButton(
                            text = "Test AI Connection",
                            leadingIcon = LaterboxIcons.Sparkles,
                            onClick = onTestAiConnection,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Status banner if tested
                        aiConnectionStatus?.let { (isSuccess, message) ->
                            val isTesting = message == "Testing connection..."
                            LaterboxBanner(
                                text = if (isSuccess) "✓ $message" else if (isTesting) "⏳ $message" else "⚠ $message",
                                isSuccess = isSuccess,
                                isWarning = !isSuccess && !isTesting
                            )
                        }

                        // Send Test Smart Reminder
                        LaterboxSecondaryButton(
                            text = "Send Test Smart Reminder",
                            leadingIcon = LaterboxIcons.Sparkles,
                            onClick = {
                                coroutineScope.launch {
                                    val sent = onSendTestSmartDigest()
                                    if (sent) {
                                        Toast.makeText(context, "Test reminder sent! Check notification shade.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Could not send test reminder. Check notification permission and AI connection.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: Quick Capture
            SectionHeader(title = "Quick Capture")

            val isAccessibilityEnabledInSystem = remember(preferences.accessibilityCaptureEnabled) {
                AccessibilityServiceStateHelper.isAccessibilityServiceEnabled(context)
            }

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
                    // 1. Accessibility Link Selection Shortcut
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link Selection Shortcut",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Offers a small floating shortcut when you highlight a web link in supported apps.",
                                style = typography.metaText,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = preferences.accessibilityCaptureEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !isAccessibilityEnabledInSystem) {
                                    showAccessibilityOnboarding = true
                                } else {
                                    onAccessibilityCaptureEnabledChange(enabled)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    if (preferences.accessibilityCaptureEnabled) {
                        if (!isAccessibilityEnabledInSystem) {
                            LaterboxBanner(
                                text = "Android Accessibility permission is required for floating selection capture.",
                                isWarning = true
                            )
                            LaterboxSecondaryButton(
                                text = "Grant Permission in Settings",
                                onClick = {
                                    AccessibilityServiceStateHelper.openAccessibilitySettings(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "✓ Active in Android Accessibility",
                                style = typography.metaText,
                                color = colors.accentPrimary
                            )
                        }

                        LaterboxSecondaryButton(
                            text = "Excluded Apps (${preferences.excludedCapturePackages.size})",
                            onClick = { showExcludedAppsDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Divider(colors.borderSubtle)

                    // 2. Foreground Clipboard Suggestion
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clipboard Suggestion Banner",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Suggests saving when you open Laterbox if a webpage link is on your clipboard.",
                                style = typography.metaText,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = preferences.clipboardSuggestionEnabled,
                            onCheckedChange = onClipboardSuggestionEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    Divider(colors.borderSubtle)

                    // 3. Native Text Selection Menu Info
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(
                            text = "Native Text Selection Menu",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Highlight text in any application and tap 'Save to Laterbox' in the text toolbar. Always available without extra permissions.",
                            style = typography.metaText,
                            color = colors.textSecondary
                        )
                    }

                    Divider(colors.borderSubtle)

                    // 4. Quick Settings Tile
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = "Quick Settings Tile",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Save any copied link instantly from the Android Quick Settings drawer without switching apps.",
                            style = typography.metaText,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(spacing.xxs))
                        LaterboxPrimaryButton(
                            text = "Add Tile to Quick Settings",
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                                    val componentName = ComponentName(context, QuickSaveTileService::class.java)
                                    statusBarManager?.requestAddTileService(
                                        componentName,
                                        context.getString(R.string.save_to_laterbox),
                                        AndroidIcon.createWithResource(context, R.drawable.ic_tile_save),
                                        { it.run() },
                                        { result ->
                                            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                                                Toast.makeText(context, "Tile added to Quick Settings!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Swipe down Quick Settings, tap the pencil edit icon, and drag 'Save to Laterbox' into your tiles.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
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

                    Divider(colors.borderSubtle)

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

            // Section: Media Library & Downloads
            SectionHeader(title = "Media Library & Downloads")

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
                    // Download network policy
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = "Download Network Policy",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Choose which network connection to use for media downloads",
                            style = typography.bodySecondary.copy(fontSize = 12.sp),
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(spacing.xxs))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            ThemeOptionChip(
                                label = "Wi-Fi Only",
                                isSelected = preferences.mediaDownloadNetworkPolicy == com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy.WIFI_ONLY,
                                onClick = { onMediaDownloadNetworkPolicyChange(com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy.WIFI_ONLY) }
                            )
                            ThemeOptionChip(
                                label = "Wi-Fi for >20 MB (Default)",
                                isSelected = preferences.mediaDownloadNetworkPolicy == com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy.WIFI_OVER_20MB,
                                onClick = { onMediaDownloadNetworkPolicyChange(com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy.WIFI_OVER_20MB) }
                            )
                            ThemeOptionChip(
                                label = "Any Network",
                                isSelected = preferences.mediaDownloadNetworkPolicy == com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy.ANY_NETWORK,
                                onClick = { onMediaDownloadNetworkPolicyChange(com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy.ANY_NETWORK) }
                            )
                        }
                    }

                    Divider(colors.borderSubtle)

                    // Auto-inspect links on save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-inspect links on save",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Automatically detect downloadable media when links are saved",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = preferences.autoInspectMediaOnSave,
                            onCheckedChange = onAutoInspectMediaOnSaveChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    Divider(colors.borderSubtle)

                    // Fast engine (warm background extractor process)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fast engine",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Keep extractor warm in background for faster format discovery",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = preferences.fastEngineEnabled,
                            onCheckedChange = onFastEngineEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    Divider(colors.borderSubtle)

                    // yt-dlp extractor engine status and diagnostics
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Media Extractor (yt-dlp)",
                                    style = typography.uiMedium,
                                    color = colors.textPrimary
                                )
                                val statusText = when (ytDlpState) {
                                    is com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState.Initialized -> {
                                        val v = ytDlpState.version?.ifBlank { preferences.ytDlpVersion } ?: preferences.ytDlpVersion
                                        if (v.isNotBlank()) "Ready (v$v)" else "Ready (bundled engine)"
                                    }
                                    is com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState.Initializing -> "Initializing engine…"
                                    is com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState.Failed -> "Initialization failed"
                                    is com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState.NotInitialized -> "Not initialized"
                                }
                                Text(
                                    text = statusText,
                                    style = typography.bodySecondary.copy(fontSize = 12.sp),
                                    color = if (ytDlpState is com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState.Failed) colors.statusDanger else colors.textSecondary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                OutlinedButton(
                                    onClick = {
                                        diagnosticReport = onGetDiagnosticReport()
                                        diagnosticReportText = onGetDiagnosticReportText()
                                        showDiagnosticsDialog = true
                                    },
                                    shape = LaterboxShapeTokens.Button
                                ) {
                                    Text(
                                        text = "Diagnostics",
                                        style = typography.uiMedium.copy(fontSize = 13.sp),
                                        color = colors.accentPrimary
                                    )
                                }

                                OutlinedButton(
                                    onClick = onUpdateYtDlpClick,
                                    enabled = !isUpdatingYtDlp,
                                    shape = LaterboxShapeTokens.Button
                                ) {
                                    if (isUpdatingYtDlp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = colors.accentPrimary
                                        )
                                    } else {
                                        Text(
                                            text = "Check Update",
                                            style = typography.uiMedium.copy(fontSize = 13.sp),
                                            color = colors.accentPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // FFmpeg status sub-row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Media Stream Muxer (FFmpeg)",
                                style = typography.metaText,
                                color = colors.textSecondary
                            )
                            val ffmpegStatusText = when (ffmpegState) {
                                is com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState.Initialized -> "Ready (Audio/Video muxing enabled)"
                                is com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState.Initializing -> "Initializing…"
                                is com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState.Failed -> "Unavailable (single streams only)"
                                is com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState.NotInitialized -> "Pending"
                            }
                            Text(
                                text = ffmpegStatusText,
                                style = typography.metaText,
                                color = if (ffmpegState is com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState.Failed) colors.statusDanger else colors.textSecondary
                            )
                        }

                        if (ytDlpState is com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState.Failed) {
                            val failure = (ytDlpState as com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState.Failed).error
                            LaterboxBanner(
                                text = failure.message,
                                isWarning = true,
                                actionText = "Retry",
                                onActionClick = onRetryInitializeEngine
                            )
                        }

                        Divider(colors.borderSubtle)

                        // Export Location (SAF DocumentTree)
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(
                                text = "Export Storage Location",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Choose where downloaded media files are copied when exported. By default, files are exported to the system Downloads/Laterbox folder.",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(spacing.xxs))

                            val currentExportFolder = preferences.customExportDirectoryName ?: "Default (System MediaStore)"
                            Text(
                                text = "Current: $currentExportFolder",
                                style = typography.metaText.copy(fontWeight = FontWeight.Medium),
                                color = if (preferences.customExportDirectoryUri != null) colors.accentPrimary else colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(spacing.xxs))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                OutlinedButton(
                                    onClick = { directoryPickerLauncher.launch(null) },
                                    shape = LaterboxShapeTokens.Button
                                ) {
                                    Icon(
                                        painter = painterResource(LaterboxIcons.Folder),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = colors.accentPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (preferences.customExportDirectoryUri != null) "Change Folder" else "Choose Folder",
                                        style = typography.uiMedium.copy(fontSize = 13.sp),
                                        color = colors.accentPrimary
                                    )
                                }

                                if (preferences.customExportDirectoryUri != null) {
                                    TextButton(
                                        onClick = onClearCustomExportDirectory
                                    ) {
                                        Text(
                                            text = "Reset to Default",
                                            style = typography.uiMedium.copy(fontSize = 13.sp),
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: Data & Storage (Local-First)
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
                        LaterboxSecondaryButton(
                            text = "Export JSON",
                            onClick = {
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportLauncher.launch("laterbox_backup_$dateStr.json")
                            },
                            modifier = Modifier.weight(1f)
                        )

                        LaterboxSecondaryButton(
                            text = "Import JSON",
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/*"))
                            },
                            modifier = Modifier.weight(1f)
                        )
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
                        text = "Laterbox v${com.laterbox.app.BuildConfig.VERSION_NAME}",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "A calm, local-first reading inbox. Your links and notes remain entirely on your device. No user accounts, no telemetry, and no third-party tracking.",
                        style = typography.bodySecondary,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xs))

                    LaterboxSecondaryButton(
                        text = if (isCheckingUpdate) "Checking for updates..." else "Check for updates",
                        enabled = !isCheckingUpdate,
                        onClick = onCheckForUpdates,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LaterboxSecondaryButton(
                        text = "Open Source Licenses & Typography",
                        onClick = { showLicensesDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // App Update Dialog
        if (availableUpdateInfo != null) {
            com.laterbox.app.update.AppUpdateDialog(
                updateInfo = availableUpdateInfo,
                onDismiss = onDismissUpdateDialog,
                onPostpone = onPostponeUpdate
            )
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

        // Engine Diagnostics Dialog
        if (showDiagnosticsDialog) {
            AlertDialog(
                onDismissRequest = { showDiagnosticsDialog = false },
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = "Media Engine Diagnostics",
                        style = typography.titleSmall,
                        color = colors.textPrimary
                    )
                },
                text = {
                    val reportText = diagnosticReportText ?: diagnosticReport?.toFormattedString() ?: "No diagnostics captured."
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Text(
                            text = reportText,
                            style = typography.codeText.copy(fontSize = 11.sp),
                            color = colors.textPrimary
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        TextButton(
                            onClick = {
                                val reportText = diagnosticReportText ?: diagnosticReport?.toFormattedString() ?: ""
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(reportText))
                                android.widget.Toast.makeText(context, "Diagnostics copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "Copy Report",
                                style = typography.uiMedium,
                                color = colors.accentPrimary
                            )
                        }
                        TextButton(onClick = { showDiagnosticsDialog = false }) {
                            Text(
                                text = "Close",
                                style = typography.uiMedium,
                                color = colors.accentPrimary
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            onRetryInitializeEngine()
                            diagnosticReport = onGetDiagnosticReport()
                        }
                    ) {
                        Text(
                            text = "Retry Init",
                            style = typography.uiMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            )
        }

        // Generic Time Picker Dialog for any schedule target
        timePickerTarget?.let { target ->
            val timePickerState = rememberTimePickerState(
                initialHour = target.hour,
                initialMinute = target.minute,
                is24Hour = false
            )
            AlertDialog(
                onDismissRequest = { timePickerTarget = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (target) {
                                is TimePickerTarget.DailyDigest -> onDailyDigestTimeChange(timePickerState.hour, timePickerState.minute)
                                is TimePickerTarget.Morning -> onMorningTimeChange(timePickerState.hour, timePickerState.minute)
                                is TimePickerTarget.Evening -> onEveningTimeChange(timePickerState.hour, timePickerState.minute)
                                is TimePickerTarget.Weekend -> onWeekendRoundupTimeChange(timePickerState.hour, timePickerState.minute)
                                is TimePickerTarget.Custom -> onCustomScheduleTimeChange(timePickerState.hour, timePickerState.minute)
                            }
                            timePickerTarget = null
                        }
                    ) {
                        Text("Set Time", color = colors.accentPrimary, style = typography.uiMedium)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { timePickerTarget = null }) {
                        Text("Cancel", color = colors.textSecondary, style = typography.uiMedium)
                    }
                },
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = target.title,
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

        if (showAccessibilityOnboarding) {
            AccessibilityOnboardingSheet(
                sheetState = accessibilitySheetState,
                onDismissRequest = { showAccessibilityOnboarding = false },
                onEnableClicked = {
                    onAccessibilityCaptureEnabledChange(true)
                }
            )
        }

        if (showExcludedAppsDialog) {
            ExcludedAppsDialog(
                excludedPackages = preferences.excludedCapturePackages,
                onTogglePackage = onToggleExcludedPackage,
                onDismissRequest = { showExcludedAppsDialog = false }
            )
        }
    }
}

private sealed class TimePickerTarget(val title: String, val hour: Int, val minute: Int) {
    class DailyDigest(h: Int, m: Int) : TimePickerTarget("Daily Digest Time", h, m)
    class Morning(h: Int, m: Int) : TimePickerTarget("Morning Reminder Time", h, m)
    class Evening(h: Int, m: Int) : TimePickerTarget("Evening Reminder Time", h, m)
    class Weekend(h: Int, m: Int) : TimePickerTarget("Weekend Roundup Time", h, m)
    class Custom(h: Int, m: Int) : TimePickerTarget("Custom Schedule Time", h, m)
}

@Composable
private fun ScheduleRow(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.uiMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = description,
                    style = typography.metaText,
                    color = colors.textSecondary
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.surfaceElevated,
                    checkedTrackColor = colors.accentPrimary
                )
            )
        }

        if (enabled) {
            Spacer(modifier = Modifier.height(spacing.xxs))
            content()
        }
    }
}

@Composable
private fun Divider(color: androidx.compose.ui.graphics.Color) {
    val spacing = LaterboxTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(spacing.hairline)
            .background(color)
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (hour >= 12) "PM" else "AM"
    return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)
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
            .height(spacing.chipHeight)
            .clip(RoundedCornerShape(spacing.radiusSmall))
            .background(bg)
            .border(spacing.hairline, borderColor, RoundedCornerShape(spacing.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.metaText,
            color = textColor,
            maxLines = 1
        )
    }
}
