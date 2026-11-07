package com.laterbox.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun ContentTypeBadge(
    contentType: ContentType,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                width = LaterboxTheme.spacing.hairline,
                color = LaterboxTheme.colors.borderSubtle,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = contentType.name,
            style = LaterboxTheme.typography.metadataMono,
            color = LaterboxTheme.colors.textSecondary
        )
    }
}
