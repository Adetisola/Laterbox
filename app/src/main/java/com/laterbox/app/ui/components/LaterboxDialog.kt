package com.laterbox.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import com.laterbox.app.ui.theme.LaterboxTheme

/**
 * Standardised confirmation and action dialog for Laterbox.
 * Follows 16dp rounded corners, 8dp elevation, editorial typography,
 * and standard action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaterboxConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "Cancel",
    neutralText: String? = null,
    onNeutral: (() -> Unit)? = null,
    isDestructive: Boolean = false,
    content: (@Composable () -> Unit)? = null
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        modifier = modifier
    ) {
        Surface(
            shape = LaterboxTheme.shapes.Dialog,
            color = colors.surfaceElevated,
            tonalElevation = LaterboxTheme.elevations.dialog,
            shadowElevation = LaterboxTheme.elevations.dialog
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.xl)
            ) {
                Text(
                    text = title,
                    style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(spacing.sm))

                Text(
                    text = message,
                    style = typography.bodyPrimary,
                    color = colors.textSecondary
                )

                if (content != null) {
                    Spacer(modifier = Modifier.height(spacing.md))
                    content()
                }

                Spacer(modifier = Modifier.height(spacing.xl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LaterboxTextButton(
                        text = dismissText,
                        onClick = onDismiss,
                        color = colors.textSecondary
                    )

                    if (neutralText != null && onNeutral != null) {
                        Spacer(modifier = Modifier.padding(horizontal = spacing.xxs))
                        LaterboxTextButton(
                            text = neutralText,
                            onClick = onNeutral,
                            color = colors.accentPrimary
                        )
                    }

                    Spacer(modifier = Modifier.padding(horizontal = spacing.xs))

                    LaterboxPrimaryButton(
                        text = confirmText,
                        onClick = onConfirm,
                        isDestructive = isDestructive
                    )
                }
            }
        }
    }
}
