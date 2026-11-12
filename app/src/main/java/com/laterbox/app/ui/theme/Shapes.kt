package com.laterbox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Cohesive shape tokens across Laterbox:
 * - 6dp: Badges, unread indicators
 * - 8dp: Media thumbnails
 * - 12dp: Buttons, chips, icon backdrops, text fields
 * - 16dp: Link cards, dialogs, collapsed FAB
 * - 20dp: Floating bottom navigation pill
 * - 24dp: Bottom sheets (topStart, topEnd)
 */
object LaterboxShapeTokens {
    val Badge = RoundedCornerShape(6.dp)
    val Thumbnail = RoundedCornerShape(8.dp)
    val Button = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(12.dp)
    val IconButton = RoundedCornerShape(12.dp)
    val InputField = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Fab = RoundedCornerShape(16.dp)
    val PillNav = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Dialog = RoundedCornerShape(16.dp)
}
