package com.laterbox.app.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ui.theme.LaterboxTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomReminderPickerDialog(
    initialTimestamp: Long? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val calendar = remember {
        Calendar.getInstance().apply {
            if (initialTimestamp != null && initialTimestamp > System.currentTimeMillis()) {
                timeInMillis = initialTimestamp
            } else {
                timeInMillis = System.currentTimeMillis()
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val formattedPreview = remember(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute) {
        val cal = Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val df = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
        df.format(Date(cal.timeInMillis))
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay)
            }.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = millis
                            }
                            selectedYear = utcCal.get(Calendar.YEAR)
                            selectedMonth = utcCal.get(Calendar.MONTH)
                            selectedDay = utcCal.get(Calendar.DAY_OF_MONTH)
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Select Date", color = colors.accentPrimary, style = typography.uiMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = colors.textSecondary, style = typography.uiMedium)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = colors.surfaceElevated
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = colors.surfaceElevated,
                    titleContentColor = colors.textPrimary,
                    headlineContentColor = colors.textPrimary,
                    selectedDayContainerColor = colors.accentPrimary,
                    selectedDayContentColor = colors.surfaceElevated,
                    todayDateBorderColor = colors.accentPrimary
                )
            )
        }
    }

    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedHour = timePickerState.hour
                        selectedMinute = timePickerState.minute
                        showTimePickerDialog = false
                    }
                ) {
                    Text("Select Time", color = colors.accentPrimary, style = typography.uiMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel", color = colors.textSecondary, style = typography.uiMedium)
                }
            },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Pick Reminder Time",
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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = LaterboxShapeTokens.Card,
        containerColor = colors.surfaceElevated,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Clock),
                    contentDescription = null,
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Custom Reminder",
                    style = typography.headlineSecondary,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                Text(
                    text = "Choose when you would like Laterbox to remind you to revisit this link.",
                    style = typography.bodySecondary,
                    color = colors.textSecondary
                )

                // Date Presets Section
                Text(
                    text = "Date",
                    style = typography.uiMedium.copy(fontSize = 13.sp),
                    color = colors.textPrimary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    val nowCal = Calendar.getInstance()
                    val isToday = selectedYear == nowCal.get(Calendar.YEAR) &&
                            selectedMonth == nowCal.get(Calendar.MONTH) &&
                            selectedDay == nowCal.get(Calendar.DAY_OF_MONTH)

                    val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    val isTomorrow = selectedYear == tomorrowCal.get(Calendar.YEAR) &&
                            selectedMonth == tomorrowCal.get(Calendar.MONTH) &&
                            selectedDay == tomorrowCal.get(Calendar.DAY_OF_MONTH)

                    val weekendCal = Calendar.getInstance().apply {
                        val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                        val daysUntilSaturday = (Calendar.SATURDAY - dayOfWeek + 7) % 7
                        val addDays = if (daysUntilSaturday == 0 && get(Calendar.HOUR_OF_DAY) >= 10) 7 else daysUntilSaturday
                        add(Calendar.DAY_OF_YEAR, addDays)
                    }
                    val isWeekend = selectedYear == weekendCal.get(Calendar.YEAR) &&
                            selectedMonth == weekendCal.get(Calendar.MONTH) &&
                            selectedDay == weekendCal.get(Calendar.DAY_OF_MONTH)

                    ReminderOptionPill(
                        label = "Today",
                        isSelected = isToday,
                        onClick = {
                            selectedYear = nowCal.get(Calendar.YEAR)
                            selectedMonth = nowCal.get(Calendar.MONTH)
                            selectedDay = nowCal.get(Calendar.DAY_OF_MONTH)
                        }
                    )

                    ReminderOptionPill(
                        label = "Tomorrow",
                        isSelected = isTomorrow,
                        onClick = {
                            selectedYear = tomorrowCal.get(Calendar.YEAR)
                            selectedMonth = tomorrowCal.get(Calendar.MONTH)
                            selectedDay = tomorrowCal.get(Calendar.DAY_OF_MONTH)
                        }
                    )

                    ReminderOptionPill(
                        label = "This Weekend",
                        isSelected = isWeekend,
                        onClick = {
                            selectedYear = weekendCal.get(Calendar.YEAR)
                            selectedMonth = weekendCal.get(Calendar.MONTH)
                            selectedDay = weekendCal.get(Calendar.DAY_OF_MONTH)
                        }
                    )

                    ReminderOptionPill(
                        label = "Calendar…",
                        isSelected = !isToday && !isTomorrow && !isWeekend,
                        onClick = { showDatePickerDialog = true }
                    )
                }

                // Time Presets Section
                Text(
                    text = "Time",
                    style = typography.uiMedium.copy(fontSize = 13.sp),
                    color = colors.textPrimary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    val isMorning = selectedHour == 9 && selectedMinute == 0
                    val isAfternoon = selectedHour == 13 && selectedMinute == 0
                    val isEvening = selectedHour == 20 && selectedMinute == 0
                    val isNight = selectedHour == 22 && selectedMinute == 0

                    ReminderOptionPill(
                        label = "9:00 AM",
                        isSelected = isMorning,
                        onClick = { selectedHour = 9; selectedMinute = 0 }
                    )

                    ReminderOptionPill(
                        label = "1:00 PM",
                        isSelected = isAfternoon,
                        onClick = { selectedHour = 13; selectedMinute = 0 }
                    )

                    ReminderOptionPill(
                        label = "8:00 PM",
                        isSelected = isEvening,
                        onClick = { selectedHour = 20; selectedMinute = 0 }
                    )

                    ReminderOptionPill(
                        label = "10:00 PM",
                        isSelected = isNight,
                        onClick = { selectedHour = 22; selectedMinute = 0 }
                    )

                    ReminderOptionPill(
                        label = "Custom…",
                        isSelected = !isMorning && !isAfternoon && !isEvening && !isNight,
                        onClick = { showTimePickerDialog = true }
                    )
                }

                // Scheduled Summary Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LaterboxShapeTokens.Chip)
                        .background(colors.surfaceSecondary)
                        .border(1.dp, colors.borderSubtle, LaterboxShapeTokens.Chip)
                        .padding(horizontal = spacing.md, vertical = spacing.sm)
                ) {
                    Column {
                        Text(
                            text = "Scheduled for",
                            style = typography.metaText,
                            color = colors.textTertiary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedPreview,
                            style = typography.uiMedium.copy(fontSize = 13.sp),
                            color = colors.accentPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetCal = Calendar.getInstance().apply {
                        set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val finalMillis = if (targetCal.timeInMillis <= System.currentTimeMillis()) {
                        // If selected past time on today, add 1 hour
                        System.currentTimeMillis() + 60 * 60 * 1000L
                    } else {
                        targetCal.timeInMillis
                    }
                    onConfirm(finalMillis)
                },
                shape = LaterboxShapeTokens.Button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.surfaceElevated
                )
            ) {
                Text("Set Reminder", style = typography.uiMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = colors.textSecondary, style = typography.uiMedium)
            }
        }
    )
}

@Composable
private fun ReminderOptionPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Surface(
        onClick = onClick,
        shape = LaterboxShapeTokens.Chip,
        color = if (isSelected) colors.accentContainer else colors.surfaceSecondary,
        border = BorderStroke(
            1.dp,
            if (isSelected) colors.accentPrimary else colors.borderSubtle
        )
    ) {
        Text(
            text = label,
            style = typography.metaText.copy(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isSelected) colors.accentPrimary else colors.textPrimary,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = 8.dp)
        )
    }
}
