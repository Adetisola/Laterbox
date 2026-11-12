package com.laterbox.app.ui.notifications

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.laterbox.app.domain.model.NotificationHistoryItem
import com.laterbox.app.ui.components.DateGroupHeader
import com.laterbox.app.ui.components.DateGrouper
import com.laterbox.app.ui.components.FaviconImage
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun NotificationHistoryScreen(
    viewModel: NotificationHistoryViewModel,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String, Long?) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing
    val typography = LaterboxTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.base, vertical = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onMenuClick != null) {
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Menu,
                        contentDescription = "Open navigation menu",
                        onClick = onMenuClick
                    )
                } else {
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.ArrowLeft,
                        contentDescription = "Back",
                        onClick = onNavigateBack
                    )
                }
                Spacer(modifier = Modifier.width(spacing.sm))
                Column {
                    Text(
                        text = "Notifications",
                        style = typography.sheetTitle,
                        color = colors.textPrimary
                    )
                    if (uiState.unreadCount > 0) {
                        Text(
                            text = "${uiState.unreadCount} unread",
                            style = typography.caption,
                            color = colors.accentPrimary
                        )
                    }
                }
            }

            if (uiState.unreadCount > 0) {
                TextButton(onClick = { viewModel.markAllAsRead() }) {
                    Text(
                        text = "Mark all read",
                        style = typography.buttonLabel,
                        color = colors.accentPrimary
                    )
                }
            }
        }

        // Content
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accentPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            uiState.groupedItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(LaterboxIcons.Bell),
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(spacing.base))
                        Text(
                            text = "No notifications yet",
                            style = typography.sheetTitle,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(
                            text = "Scheduled reminders and smart digests will appear here after they arrive.",
                            style = typography.bodySecondary,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.xl)
                ) {
                    uiState.groupedItems.forEach { group ->
                        item(key = "header_${group.title}") {
                            DateGroupHeader(
                                title = group.title,
                                count = group.items.size,
                                isCollapsed = false,
                                onToggleCollapse = {},
                                modifier = Modifier.padding(
                                    horizontal = spacing.base,
                                    vertical = spacing.xs
                                )
                            )
                        }

                        items(
                            items = group.items,
                            key = { it.id }
                        ) { item ->
                            NotificationHistoryCard(
                                item = item,
                                onClick = {
                                    viewModel.markAsRead(item.id)
                                    onOpenUrl(item.url, item.linkId)
                                },
                                onToggleRead = {
                                    if (item.isRead) {
                                        viewModel.markAsUnread(item.id)
                                    } else {
                                        viewModel.markAsRead(item.id)
                                    }
                                },
                                modifier = Modifier.padding(
                                    horizontal = spacing.base,
                                    vertical = spacing.xs
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationHistoryCard(
    item: NotificationHistoryItem,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing
    val typography = LaterboxTheme.typography
    val context = LocalContext.current

    val cardBg = if (item.isRead) colors.surfacePrimary else colors.surfaceSecondary
    val borderCol = if (item.isRead) colors.borderSubtle else colors.accentPrimary.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(
                width = if (item.isRead) spacing.hairline else 1.dp,
                color = borderCol,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(spacing.base)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: [Favicon] [Domain & Origin] [Spacer] [Unread Dot] [Time] [Mark Read Button]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FaviconImage(
                    domain = item.domain,
                    faviconUrl = item.faviconUrl,
                    size = 18.dp
                )
                Spacer(modifier = Modifier.width(spacing.xs))
                Text(
                    text = item.domain,
                    style = typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(spacing.xs))
                val originLabel = when (item.origin) {
                    "MANUAL" -> "Reminder"
                    "SMART_DIGEST" -> "Smart Digest"
                    else -> "Digest"
                }
                Text(
                    text = "• $originLabel",
                    style = typography.caption,
                    color = colors.textTertiary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.weight(1f))

                if (!item.isRead) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accentPrimary)
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                }

                Text(
                    text = DateGrouper.formatRelativeTime(item.createdAt),
                    style = typography.caption,
                    color = colors.textTertiary
                )

                Spacer(modifier = Modifier.width(spacing.xs))

                LaterboxIconButton(
                    iconRes = if (item.isRead) LaterboxIcons.Check else LaterboxIcons.Sparkles,
                    contentDescription = if (item.isRead) "Mark unread" else "Mark read",
                    onClick = onToggleRead,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(spacing.xs))

            // Content Row: Title & Body on left; Preview image thumbnail on right (if present)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = if (!item.previewImageUrl.isNullOrBlank()) spacing.sm else 0.dp)
                ) {
                    Text(
                        text = item.title,
                        style = typography.cardTitle.copy(
                            fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.SemiBold
                        ),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.body,
                        style = typography.bodySecondary,
                        color = colors.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!item.previewImageUrl.isNullOrBlank()) {
                    val request = ImageRequest.Builder(context)
                        .data(item.previewImageUrl)
                        .crossfade(true)
                        .build()
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}
