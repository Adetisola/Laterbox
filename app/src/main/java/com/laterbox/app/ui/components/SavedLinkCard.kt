package com.laterbox.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.laterbox.app.data.preferences.InboxViewMode
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedLinkCard(
    link: SavedLink,
    onClick: () -> Unit,
    onQuickOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewMode: InboxViewMode = InboxViewMode.HERO,
    onToggleRead: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val imageUrl = link.previewImageUrl?.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }
    var imageLoadFailed by remember(imageUrl) { mutableStateOf(false) }

    val hasRenderableImage = imageUrl != null && !imageLoadFailed
    val isHeroMode = viewMode == InboxViewMode.HERO && hasRenderableImage

    val heroImageRequest = remember(imageUrl, context) {
        imageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .diskCacheKey(it)
                .memoryCacheKey(it)
                .build()
        }
    }

    val compactImageRequest = remember(imageUrl, context) {
        imageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .diskCacheKey(it)
                .memoryCacheKey(it)
                .size(144, 144)
                .build()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = LaterboxShapeTokens.Card,
        colors = CardDefaults.cardColors(
            containerColor = LaterboxTheme.colors.surfacePrimary
        ),
        border = BorderStroke(
            width = LaterboxTheme.spacing.hairline,
            color = LaterboxTheme.colors.borderSubtle
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Text Content Section with standard padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = LaterboxTheme.spacing.m,
                        end = LaterboxTheme.spacing.m,
                        top = LaterboxTheme.spacing.sm,
                        bottom = if (isHeroMode) LaterboxTheme.spacing.xs else LaterboxTheme.spacing.xs
                    )
            ) {
                // Header Row: Unread Dot + Favicon + Domain + Content Type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!link.isRead) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(LaterboxTheme.colors.accentPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    FaviconImage(
                        domain = link.domain,
                        faviconUrl = link.faviconUrl,
                        size = 16.dp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = link.domain,
                        style = LaterboxTheme.typography.metadataMono,
                        color = LaterboxTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    ContentTypeBadge(contentType = link.contentType)
                }

                Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xs))

                if (isHeroMode) {
                    // Hero Mode Text Layout: Full-width Title + Notes above the image
                    Text(
                        text = link.title,
                        style = LaterboxTheme.typography.cardTitle,
                        color = LaterboxTheme.colors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    val supportingText = link.personalNote ?: link.description
                    if (!supportingText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xs))
                        Text(
                            text = supportingText,
                            style = LaterboxTheme.typography.bodyPrimary,
                            color = LaterboxTheme.colors.textSecondary,
                            fontStyle = if (!link.personalNote.isNullOrBlank()) FontStyle.Italic else FontStyle.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (hasRenderableImage && compactImageRequest != null) {
                    // Compact Mode Layout: Two-column asymmetrical (Title on left, 72dp thumbnail on right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = LaterboxTheme.spacing.sm)
                        ) {
                            Text(
                                text = link.title,
                                style = LaterboxTheme.typography.cardTitle,
                                color = LaterboxTheme.colors.textPrimary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!link.personalNote.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xs))
                                Text(
                                    text = link.personalNote,
                                    style = LaterboxTheme.typography.bodyPrimary,
                                    color = LaterboxTheme.colors.textSecondary,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Thumbnail image (72.dp x 72.dp)
                        AsyncImage(
                            model = compactImageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onError = { imageLoadFailed = true },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(LaterboxTheme.spacing.thumbnailRadius))
                                .background(LaterboxTheme.colors.surfaceSecondary)
                                .border(
                                    width = LaterboxTheme.spacing.hairline,
                                    color = LaterboxTheme.colors.borderSubtle,
                                    shape = RoundedCornerShape(LaterboxTheme.spacing.thumbnailRadius)
                                )
                        )
                    }
                } else {
                    // Typographic Text-only Layout (Expands full width)
                    Text(
                        text = link.title,
                        style = LaterboxTheme.typography.cardTitle,
                        color = LaterboxTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val supportingText = link.personalNote ?: link.description
                    if (!supportingText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xs))
                        Text(
                            text = supportingText,
                            style = LaterboxTheme.typography.bodyPrimary,
                            color = LaterboxTheme.colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Hero Mode: Full-width OG Image at the bottom of the card hugging aspect ratio
            if (isHeroMode && heroImageRequest != null) {
                Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xs))
                AsyncImage(
                    model = heroImageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageLoadFailed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 260.dp)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = 0.dp,
                                bottomEnd = 0.dp
                            )
                        )
                        .background(LaterboxTheme.colors.surfaceSecondary)
                        .border(
                            width = LaterboxTheme.spacing.hairline,
                            color = LaterboxTheme.colors.borderSubtle
                        )
                )
            }

            // Card Footer Row: Relative Saved Time + Collection tag on Left; View ↗ and More (⋮) on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = LaterboxTheme.spacing.m,
                        end = LaterboxTheme.spacing.m,
                        top = LaterboxTheme.spacing.xs,
                        bottom = LaterboxTheme.spacing.sm
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = DateGrouper.formatRelativeTime(link.createdAt),
                        style = LaterboxTheme.typography.metadataMono,
                        color = LaterboxTheme.colors.textTertiary,
                        fontSize = 11.sp
                    )

                    if (!link.collectionName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "· #${link.collectionName}",
                            style = LaterboxTheme.typography.metadataMono,
                            color = LaterboxTheme.colors.accentPrimary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Direct "View ↗" text button with 12dp backdrop
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(LaterboxShapeTokens.Button)
                            .background(LaterboxTheme.colors.surfaceSecondary.copy(alpha = 0.8f))
                            .border(
                                BorderStroke(1.dp, LaterboxTheme.colors.borderSubtle.copy(alpha = 0.5f)),
                                LaterboxShapeTokens.Button
                            )
                            .clickable(onClick = onQuickOpenClick)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "View",
                                style = LaterboxTheme.typography.uiMedium,
                                color = LaterboxTheme.colors.accentPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                painter = painterResource(id = LaterboxIcons.ArrowUpRight),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = LaterboxTheme.colors.accentPrimary
                            )
                        }
                    }

                    // More (⋮) button with 12dp backdrop and dropdown menu
                    Box {
                        LaterboxIconButton(
                            iconRes = LaterboxIcons.MoreVertical,
                            contentDescription = "More actions for ${link.title.ifBlank { link.domain }}",
                            onClick = { showMenu = true },
                            containerColor = LaterboxTheme.colors.surfaceSecondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(34.dp)
                        )

                        LinkQuickActionsMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            link = link,
                            onToggleRead = { onToggleRead?.invoke() },
                            onCopyLink = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("URL", link.originalUrl))
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                            },
                            onShareLink = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, link.originalUrl)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share link")
                                context.startActivity(shareIntent)
                            },
                            onArchive = { onArchive?.invoke() },
                            onDelete = { onDelete?.invoke() }
                        )
                    }
                }
            }
        }
    }
}
