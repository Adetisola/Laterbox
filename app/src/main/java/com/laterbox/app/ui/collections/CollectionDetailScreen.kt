package com.laterbox.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.components.EmptyStateView
import com.laterbox.app.ui.components.SavedLinkCard
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun CollectionDetailScreen(
    uiState: CollectionDetailUiState,
    onBackClick: () -> Unit,
    onLinkClick: (SavedLink) -> Unit,
    onQuickOpenClick: (SavedLink) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with back navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button (min 48dp touch target)
                Box(
                    modifier = Modifier
                        .size(spacing.minTouchTarget)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Back to collections",
                            onClick = onBackClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "←",
                        style = typography.uiMedium.copy(fontSize = 20.sp),
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(spacing.xs))

                Column {
                    Text(
                        text = uiState.collectionName,
                        style = typography.displayLarge.copy(fontSize = 22.sp),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${uiState.links.size} saved link${if (uiState.links.size != 1) "s" else ""}",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.accentPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                uiState.links.isEmpty() -> {
                    EmptyStateView(
                        title = "This collection is empty",
                        subtitle = "You can add links to this shelf from the Inbox or directly from the share sheet.",
                        actionText = "← Back to Collections",
                        onActionClick = onBackClick,
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = spacing.base,
                            end = spacing.base,
                            top = spacing.sm,
                            bottom = 80.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(
                            items = uiState.links,
                            key = { it.id }
                        ) { link ->
                            SavedLinkCard(
                                link = link,
                                onClick = { onLinkClick(link) },
                                onQuickOpenClick = { onQuickOpenClick(link) }
                            )
                        }
                    }
                }
            }
        }
    }
}
