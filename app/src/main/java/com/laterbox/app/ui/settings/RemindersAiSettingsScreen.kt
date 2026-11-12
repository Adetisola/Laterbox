package com.laterbox.app.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.laterbox.app.data.preferences.SmartReminderPersistence
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.ui.components.LaterboxBanner
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersAiSettingsScreen(
    preferences: UserPreferences,
    onSmartRemindersEnabledChange: (Boolean) -> Unit,
    onMorningEveningEnabledChange: (Boolean) -> Unit,
    onMorningTimeChange: (Int, Int) -> Unit,
    onEveningTimeChange: (Int, Int) -> Unit,
    onWeekendRoundupEnabledChange: (Boolean) -> Unit,
    onWeekendRoundupTimeChange: (Int, Int) -> Unit,
    onDailyDigestEnabledChange: (Boolean) -> Unit,
    onDailyDigestTimeChange: (Int, Int) -> Unit,
    onDailyDigestWeekdaysOnlyChange: (Boolean) -> Unit,
    onCustomScheduleEnabledChange: (Boolean) -> Unit,
    onCustomScheduleDaysChange: (Set<Int>) -> Unit,
    onCustomScheduleTimeChange: (Int, Int) -> Unit,
    onSmartReminderPersistenceChange: (SmartReminderPersistence) -> Unit,
    aiConnectionStatus: Pair<Boolean, String>?,
    onGeminiApiKeyChange: (String) -> Unit,
    onTestAiConnection: () -> Unit,
    onSendTestSmartDigest: suspend () -> Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAssistantEnabledChange: (Boolean) -> Unit = {},
    onResetAiDisclosureConsent: () -> Unit = {},
    onSmartNotificationsExtractionFailureChange: (Boolean) -> Unit = {},
    onSmartNotificationsDownloadFailureChange: (Boolean) -> Unit = {},
    onSmartNotificationsAppHealthChange: (Boolean) -> Unit = {},
    onSmartNotificationsSummariesChange: (Boolean) -> Unit = {},
    onSmartNotificationsTipsChange: (Boolean) -> Unit = {},
    onQuietHoursEnabledChange: (Boolean) -> Unit = {},
    onQuietHoursWindowChange: (Int, Int) -> Unit = { _, _ -> }
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var timePickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }

    val notificationsEnabled = remember(preferences.smartRemindersEnabled) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val canScheduleExact = remember(preferences.smartRemindersEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
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
            SettingsSubpageHeader(
                title = "Reminders & AI",
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            SettingsSectionHeader(title = "Smart Reminders")
            SettingsCard {
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

                        SettingsDivider()

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

                        SettingsDivider()

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

                        SettingsDivider()

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

                        SettingsDivider()

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

                        SettingsDivider()

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

                        SettingsDivider()

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

                        var apiKeyInput by remember(preferences.geminiApiKey) { mutableStateOf(preferences.geminiApiKey) }

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                onGeminiApiKeyChange(it)
                            },
                            placeholder = {
                                Text(
                                    text = "Enter Google AI Studio API key (optional)",
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

            Spacer(modifier = Modifier.height(spacing.md))

            SettingsSectionHeader(title = "Hybrid AI Assistant")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Laterbox AI Assistant",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Show the floating Message bar in Inbox and enable Gemini chat",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = preferences.assistantEnabled,
                            onCheckedChange = onAssistantEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remote AI Privacy Consent",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = if (preferences.assistantAiDisclosureAccepted) {
                                    "Accepted — prompts & bounded link context are sent to Google AI Studio"
                                } else {
                                    "Pending — consent prompt will appear before your first remote message"
                                },
                                style = typography.metaText,
                                color = colors.textSecondary
                            )
                        }
                        if (preferences.assistantAiDisclosureAccepted) {
                            TextButton(onClick = onResetAiDisclosureConsent) {
                                Text(
                                    text = "Reset",
                                    style = typography.buttonLabel,
                                    color = colors.accentPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.md))

            SettingsSectionHeader(title = "Contextual Alerts & Quiet Hours")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    ScheduleRow(
                        title = "Extraction Failure Alerts",
                        description = "Notify when background media inspection exhausts retries",
                        enabled = preferences.smartNotificationsExtractionFailure,
                        onEnabledChange = onSmartNotificationsExtractionFailureChange,
                        content = {}
                    )
                    SettingsDivider()
                    ScheduleRow(
                        title = "Download Failure Alerts",
                        description = "Notify when a queued media download fails permanently",
                        enabled = preferences.smartNotificationsDownloadFailure,
                        onEnabledChange = onSmartNotificationsDownloadFailureChange,
                        content = {}
                    )
                    SettingsDivider()
                    ScheduleRow(
                        title = "App Health Warnings",
                        description = "Alert on extractor, FFmpeg, permission, or low-storage issues",
                        enabled = preferences.smartNotificationsAppHealth,
                        onEnabledChange = onSmartNotificationsAppHealthChange,
                        content = {}
                    )
                    SettingsDivider()
                    ScheduleRow(
                        title = "Contextual Reading Summaries",
                        description = "Gentle summary alerts for older unread items & downloads",
                        enabled = preferences.smartNotificationsSummaries,
                        onEnabledChange = onSmartNotificationsSummariesChange,
                        content = {}
                    )
                    SettingsDivider()
                    ScheduleRow(
                        title = "Non-Repeating Tips & Tricks",
                        description = "Occasional discovery tips for Laterbox shortcuts & media features",
                        enabled = preferences.smartNotificationsTips,
                        onEnabledChange = onSmartNotificationsTipsChange,
                        content = {}
                    )
                    SettingsDivider()
                    ScheduleRow(
                        title = "Quiet Hours",
                        description = "Silence contextual summaries, tips, and health alerts between ${formatTime(preferences.quietHoursStartHour, 0)} and ${formatTime(preferences.quietHoursEndHour, 0)}",
                        enabled = preferences.quietHoursEnabled,
                        onEnabledChange = onQuietHoursEnabledChange
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            ThemeOptionChip(
                                label = "Night (10 PM – 8 AM)",
                                isSelected = preferences.quietHoursStartHour == 22 && preferences.quietHoursEndHour == 8,
                                onClick = { onQuietHoursWindowChange(22, 8) }
                            )
                            ThemeOptionChip(
                                label = "Late (11 PM – 7 AM)",
                                isSelected = preferences.quietHoursStartHour == 23 && preferences.quietHoursEndHour == 7,
                                onClick = { onQuietHoursWindowChange(23, 7) }
                            )
                            ThemeOptionChip(
                                label = "Workday (9 AM – 5 PM)",
                                isSelected = preferences.quietHoursStartHour == 9 && preferences.quietHoursEndHour == 17,
                                onClick = { onQuietHoursWindowChange(9, 17) }
                            )
                            ThemeOptionChip(
                                label = "Early Evening (8 PM – 7 AM)",
                                isSelected = preferences.quietHoursStartHour == 20 && preferences.quietHoursEndHour == 7,
                                onClick = { onQuietHoursWindowChange(20, 7) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Generic Time Picker Dialog
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
    }
}
