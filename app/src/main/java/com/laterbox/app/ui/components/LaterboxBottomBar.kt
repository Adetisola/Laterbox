package com.laterbox.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ui.theme.LaterboxTheme

enum class BottomBarDestination(
    val label: String,
    @DrawableRes val iconRes: Int
) {
    INBOX("Inbox", LaterboxIcons.Inbox),
    COLLECTIONS("Collections", LaterboxIcons.Folder),
    ARCHIVE("Archive", LaterboxIcons.Archive)
}

/**
 * Modern floating bottom navigation bar for Laterbox featuring official Hugeicons,
 * slightly rounded pill shape (20dp), subtle shadow, tactile ripple feedback,
 * and clear destination labels.
 */
@Composable
fun LaterboxBottomBar(
    currentDestination: BottomBarDestination,
    onDestinationSelected: (BottomBarDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = LaterboxShapeTokens.PillNav,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.35f)
                )
                .border(
                    BorderStroke(1.dp, LaterboxTheme.colors.borderSubtle.copy(alpha = 0.6f)),
                    LaterboxShapeTokens.PillNav
                ),
            shape = LaterboxShapeTokens.PillNav,
            color = LaterboxTheme.colors.surfacePrimary.copy(alpha = 0.96f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarDestination.entries.forEach { destination ->
                    val isSelected = destination == currentDestination
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) LaterboxTheme.colors.accentPrimary else LaterboxTheme.colors.textSecondary,
                        animationSpec = tween(durationMillis = 200),
                        label = "nav_icon_tint"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) LaterboxTheme.colors.textPrimary else LaterboxTheme.colors.textSecondary,
                        animationSpec = tween(durationMillis = 200),
                        label = "nav_label_color"
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(LaterboxShapeTokens.Button)
                            .selectable(
                                selected = isSelected,
                                interactionSource = interactionSource,
                                indication = ripple(),
                                role = Role.Tab,
                                onClick = { onDestinationSelected(destination) }
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = destination.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = iconTint
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = destination.label,
                            style = LaterboxTheme.typography.uiLabel,
                            color = labelColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(width = 12.dp, height = 3.dp)
                                    .clip(CircleShape)
                                    .background(LaterboxTheme.colors.accentPrimary)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(width = 12.dp, height = 3.dp))
                        }
                    }
                }
            }
        }
    }
}
