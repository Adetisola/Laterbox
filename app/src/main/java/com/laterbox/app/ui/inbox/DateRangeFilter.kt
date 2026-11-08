package com.laterbox.app.ui.inbox

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class DateRangeFilter(val label: String) {
    ALL("Any time"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    LAST_3_MONTHS("Last 3 months"),
    OLDER("Older");

    fun matches(createdAt: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (this == ALL) return true
        val zoneId = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val linkDate = Instant.ofEpochMilli(createdAt).atZone(zoneId).toLocalDate()
        val daysDiff = ChronoUnit.DAYS.between(linkDate, today)

        return when (this) {
            ALL -> true
            TODAY -> linkDate == today
            YESTERDAY -> linkDate == today.minusDays(1)
            LAST_7_DAYS -> daysDiff in 0..7
            LAST_30_DAYS -> daysDiff in 0..30
            LAST_3_MONTHS -> daysDiff in 0..90
            OLDER -> daysDiff > 90
        }
    }
}
