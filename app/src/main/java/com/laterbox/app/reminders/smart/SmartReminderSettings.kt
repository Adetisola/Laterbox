package com.laterbox.app.reminders.smart

import com.laterbox.app.data.preferences.SmartReminderPersistence

enum class SmartScheduleType {
    MORNING,
    EVENING,
    WEEKEND_SAT,
    WEEKEND_SUN,
    DAILY,
    CUSTOM
}

data class MorningEveningConfig(
    val enabled: Boolean = false,
    val morningHour: Int = 9,
    val morningMinute: Int = 0,
    val eveningHour: Int = 20,
    val eveningMinute: Int = 0
)

data class WeekendConfig(
    val enabled: Boolean = false,
    val hour: Int = 10,
    val minute: Int = 0
)

data class DailyDigestConfig(
    val enabled: Boolean = true,
    val hour: Int = 20,
    val minute: Int = 0,
    val weekdaysOnly: Boolean = false
)

data class CustomScheduleConfig(
    val enabled: Boolean = false,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5), // 1 = Monday .. 7 = Sunday (matching java.time.DayOfWeek)
    val hour: Int = 12,
    val minute: Int = 0
)

data class SmartReminderSettings(
    val enabled: Boolean = true,
    val morningEvening: MorningEveningConfig = MorningEveningConfig(),
    val weekendRoundup: WeekendConfig = WeekendConfig(),
    val dailyDigest: DailyDigestConfig = DailyDigestConfig(),
    val custom: CustomScheduleConfig = CustomScheduleConfig(),
    val persistence: SmartReminderPersistence = SmartReminderPersistence.TWICE
)
