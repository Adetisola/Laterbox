package com.laterbox.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard elevation tokens across Laterbox surfaces:
 * - flat: 0dp (flat canvas, cards using hairline border stroke)
 * - card: 1dp (subtle card lift where border is complemented by shadow)
 * - sheet: 6dp (modal bottom sheets)
 * - floating: 6dp (floating action buttons and floating navigation pill)
 * - dialog: 8dp (system and alert dialogs)
 */
object LaterboxElevationTokens {
    val flat: Dp = 0.dp
    val card: Dp = 1.dp
    val sheet: Dp = 6.dp
    val floating: Dp = 6.dp
    val dialog: Dp = 8.dp
}
