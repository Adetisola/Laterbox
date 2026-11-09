package com.laterbox.app.reminders.smart

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

object ScheduleCalculator {

    fun nextTrigger(
        type: SmartScheduleType,
        settings: SmartReminderSettings,
        now: ZonedDateTime
    ): ZonedDateTime? {
        if (!settings.enabled) return null

        return when (type) {
            SmartScheduleType.MORNING -> {
                if (!settings.morningEvening.enabled) return null
                val targetTime = LocalTime.of(
                    settings.morningEvening.morningHour,
                    settings.morningEvening.morningMinute
                )
                val todayTarget = now.with(targetTime)
                if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
            }

            SmartScheduleType.EVENING -> {
                if (!settings.morningEvening.enabled) return null
                val targetTime = LocalTime.of(
                    settings.morningEvening.eveningHour,
                    settings.morningEvening.eveningMinute
                )
                val todayTarget = now.with(targetTime)
                if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
            }

            SmartScheduleType.WEEKEND_SAT -> {
                if (!settings.weekendRoundup.enabled) return null
                val targetTime = LocalTime.of(
                    settings.weekendRoundup.hour,
                    settings.weekendRoundup.minute
                )
                findNextMatchingDay(
                    now = now,
                    targetTime = targetTime,
                    matchesDay = { it == DayOfWeek.SATURDAY }
                )
            }

            SmartScheduleType.WEEKEND_SUN -> {
                if (!settings.weekendRoundup.enabled) return null
                val targetTime = LocalTime.of(
                    settings.weekendRoundup.hour,
                    settings.weekendRoundup.minute
                )
                findNextMatchingDay(
                    now = now,
                    targetTime = targetTime,
                    matchesDay = { it == DayOfWeek.SUNDAY }
                )
            }

            SmartScheduleType.DAILY -> {
                if (!settings.dailyDigest.enabled) return null
                val targetTime = LocalTime.of(
                    settings.dailyDigest.hour,
                    settings.dailyDigest.minute
                )
                if (settings.dailyDigest.weekdaysOnly) {
                    findNextMatchingDay(
                        now = now,
                        targetTime = targetTime,
                        matchesDay = { it.value in 1..5 } // Monday to Friday
                    )
                } else {
                    val todayTarget = now.with(targetTime)
                    if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
                }
            }

            SmartScheduleType.CUSTOM -> {
                if (!settings.custom.enabled || settings.custom.daysOfWeek.isEmpty()) return null
                val targetTime = LocalTime.of(
                    settings.custom.hour,
                    settings.custom.minute
                )
                findNextMatchingDay(
                    now = now,
                    targetTime = targetTime,
                    matchesDay = { it.value in settings.custom.daysOfWeek }
                )
            }
        }
    }

    private fun findNextMatchingDay(
        now: ZonedDateTime,
        targetTime: LocalTime,
        matchesDay: (DayOfWeek) -> Boolean
    ): ZonedDateTime {
        val todayTarget = now.with(targetTime)
        if (matchesDay(todayTarget.dayOfWeek) && todayTarget.isAfter(now)) {
            return todayTarget
        }

        var candidate = todayTarget.plusDays(1)
        for (i in 1..7) {
            if (matchesDay(candidate.dayOfWeek)) {
                return candidate
            }
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    fun slotKey(type: SmartScheduleType, triggerTime: ZonedDateTime): String {
        return "${type.name}@${triggerTime.toLocalDate()}"
    }
}
