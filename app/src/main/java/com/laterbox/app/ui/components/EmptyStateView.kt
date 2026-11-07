package com.laterbox.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    supportingHint: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LaterboxTheme.spacing.xl, vertical = LaterboxTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = LaterboxTheme.typography.editorialTitle,
            color = LaterboxTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.s))

        Text(
            text = subtitle,
            style = LaterboxTheme.typography.bodyPrimary,
            color = LaterboxTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xl))

            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LaterboxTheme.colors.surfacePrimary,
                    contentColor = LaterboxTheme.colors.textPrimary
                ),
                modifier = Modifier
                    .sizeIn(minWidth = 140.dp, minHeight = LaterboxTheme.spacing.minTouchTarget)
                    .border(
                        width = LaterboxTheme.spacing.hairline,
                        color = LaterboxTheme.colors.borderSubtle,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Text(
                    text = actionText,
                    style = LaterboxTheme.typography.uiLabel
                )
            }
        }

        if (supportingHint != null) {
            Spacer(modifier = Modifier.height(LaterboxTheme.spacing.m))

            Text(
                text = supportingHint,
                style = LaterboxTheme.typography.metadataMono,
                color = LaterboxTheme.colors.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
