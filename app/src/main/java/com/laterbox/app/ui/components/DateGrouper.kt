package com.laterbox.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.theme.LaterboxTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DateGroup(
    val title: String,
    val links: List<SavedLink>
)

object DateGrouper {
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    fun groupLinks(
        links: List<SavedLink>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DateGroup> {
        if (links.isEmpty()) return emptyList()

        val zoneId = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val yesterday = today.minusDays(1)
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val startOfMonth = today.withDayOfMonth(1)

        val groups = linkedMapOf<String, MutableList<SavedLink>>()

        for (link in links) {
            val linkDate = Instant.ofEpochMilli(link.createdAt).atZone(zoneId).toLocalDate()
            val groupKey = when {
                linkDate == today -> "Today"
                linkDate == yesterday -> "Yesterday"
                !linkDate.isBefore(startOfWeek) -> "Earlier this week"
                !linkDate.isBefore(startOfMonth) -> "Earlier this month"
                else -> linkDate.format(monthYearFormatter)
            }
            groups.getOrPut(groupKey) { mutableListOf() }.add(link)
        }

        return groups.map { (title, groupLinks) ->
            DateGroup(title = title, links = groupLinks)
        }
    }

    fun formatRelativeTime(
        createdAt: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val diffMillis = (nowMillis - createdAt).coerceAtLeast(0)
        val minutes = diffMillis / (60 * 1000)
        val hours = diffMillis / (60 * 60 * 1000)
        val days = diffMillis / (24 * 60 * 60 * 1000)

        return when {
            minutes < 1 -> "Saved just now"
            minutes < 60 -> "Saved ${minutes}m ago"
            hours < 24 -> "Saved ${hours}h ago"
            days == 1L -> "Saved yesterday"
            days < 7 -> "Saved ${days}d ago"
            else -> {
                val date = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                "Saved ${date.format(shortDateFormatter)}"
            }
        }
    }
}

/**
 * Sticky collapsible header for chronological date groups in link lists.
 */
@Composable
fun DateGroupHeader(
    title: String,
    count: Int,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(LaterboxShapeTokens.Chip)
            .clickable(onClick = onToggleCollapse)
            .padding(vertical = 4.dp),
        color = LaterboxTheme.colors.backgroundPrimary.copy(alpha = 0.96f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$title · $count",
                style = LaterboxTheme.typography.uiMedium,
                color = LaterboxTheme.colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(
                    id = if (isCollapsed) LaterboxIcons.ChevronRight else LaterboxIcons.ChevronDown
                ),
                contentDescription = if (isCollapsed) "Expand $title" else "Collapse $title",
                modifier = Modifier.size(16.dp),
                tint = LaterboxTheme.colors.textTertiary
            )
        }
    }
}
