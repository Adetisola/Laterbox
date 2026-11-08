package com.laterbox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.laterbox.app.data.preferences.InboxViewMode
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun SavedLinkCard(
    link: SavedLink,
    onClick: () -> Unit,
    onQuickOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewMode: InboxViewMode = InboxViewMode.HERO
) {
    val hasImage = !link.previewImageUrl.isNullOrBlank()
    val isHeroMode = viewMode == InboxViewMode.HERO && hasImage

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(LaterboxTheme.spacing.cardRadius),
        colors = CardDefaults.cardColors(
            containerColor = LaterboxTheme.colors.surfacePrimary
        ),
        border = androidx.compose.foundation.BorderStroke(
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
                        bottom = if (isHeroMode) LaterboxTheme.spacing.xs else LaterboxTheme.spacing.sm
                    )
            ) {
                // Header Row: Unread Dot + Favicon + Domain + Content Type + Quick Open
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

                    Spacer(modifier = Modifier.weight(1f))

                    // Quick Open External Launch Button (min 48dp touch target)
                    IconButton(
                        onClick = onQuickOpenClick,
                        modifier = Modifier.size(LaterboxTheme.spacing.minTouchTarget)
                    ) {
                        Text(
                            text = "↗",
                            fontSize = 18.sp,
                            color = LaterboxTheme.colors.textSecondary
                        )
                    }
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
                } else if (hasImage) {
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
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(link.previewImageUrl)
                                .crossfade(true)
                                .size(240, 240)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(LaterboxTheme.spacing.thumbnailRadius))
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

                // Bottom Context Row: Collection tag if assigned
                if (!link.collectionName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(LaterboxTheme.spacing.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "# ${link.collectionName}",
                            style = LaterboxTheme.typography.metadataMono,
                            color = LaterboxTheme.colors.textTertiary
                        )
                    }
                }
            }

            // Hero Mode: Full-width OG Image at the bottom of the card hugging aspect ratio
            if (isHeroMode && link.previewImageUrl != null) {
                Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xs))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(link.previewImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 260.dp)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = LaterboxTheme.spacing.cardRadius,
                                bottomEnd = LaterboxTheme.spacing.cardRadius
                            )
                        )
                        .border(
                            width = LaterboxTheme.spacing.hairline,
                            color = LaterboxTheme.colors.borderSubtle,
                            shape = RoundedCornerShape(
                                bottomStart = LaterboxTheme.spacing.cardRadius,
                                bottomEnd = LaterboxTheme.spacing.cardRadius
                            )
                        )
                )
            }
        }
    }
}
