package com.laterbox.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.laterbox.app.ui.theme.LaterboxTheme

/**
 * Animated shimmering skeleton placeholders to maintain page structure and orientation
 * during initial loads and data retrieval, avoiding layout shifting and isolated spinners [UX03, L02].
 */
@Composable
fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    return alpha
}

@Composable
fun SavedLinkCardSkeleton(
    modifier: Modifier = Modifier,
    isHero: Boolean = true
) {
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing
    val alpha = rememberSkeletonAlpha()
    val placeholderColor = colors.surfaceSecondary.copy(alpha = alpha)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = colors.surfacePrimary),
        border = androidx.compose.foundation.BorderStroke(spacing.hairline, colors.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = spacing.m,
                        end = spacing.m,
                        top = spacing.sm,
                        bottom = if (isHero) spacing.xs else spacing.sm
                    )
            ) {
                // Header row: favicon slot + domain line + badge slot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(placeholderColor)
                    )
                    Spacer(modifier = Modifier.width(spacing.s))
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(spacing.radiusSmall))
                            .background(placeholderColor)
                    )
                    Spacer(modifier = Modifier.width(spacing.s))
                    Box(
                        modifier = Modifier
                            .width(55.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(spacing.radiusSmall))
                            .background(placeholderColor)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.sm))

                // Title skeleton lines
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(placeholderColor)
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(placeholderColor)
                )

                Spacer(modifier = Modifier.height(spacing.xs))

                // Subtitle / note skeleton line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(placeholderColor)
                )
            }

            // Hero image skeleton box
            if (isHero) {
                Spacer(modifier = Modifier.height(spacing.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = spacing.cardRadius,
                                bottomEnd = spacing.cardRadius
                            )
                        )
                        .background(placeholderColor)
                        .border(
                            width = spacing.hairline,
                            color = colors.borderSubtle,
                            shape = RoundedCornerShape(
                                bottomStart = spacing.cardRadius,
                                bottomEnd = spacing.cardRadius
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun InboxSkeletonList(
    modifier: Modifier = Modifier,
    isHero: Boolean = true
) {
    val spacing = LaterboxTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.base,
            end = spacing.base,
            top = spacing.sm,
            bottom = 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        userScrollEnabled = false
    ) {
        item { SavedLinkCardSkeleton(isHero = isHero) }
        item { SavedLinkCardSkeleton(isHero = isHero) }
        item { SavedLinkCardSkeleton(isHero = isHero) }
    }
}

@Composable
fun CollectionCardSkeleton(
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing
    val alpha = rememberSkeletonAlpha()
    val placeholderColor = colors.surfaceSecondary.copy(alpha = alpha)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(spacing.radiusMedium))
            .background(colors.surfacePrimary)
            .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
            .padding(spacing.md)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(spacing.radiusSmall))
                    .background(placeholderColor)
            )

            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(placeholderColor)
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Box(
                    modifier = Modifier
                        .width(45.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(placeholderColor)
                )
            }
        }
    }
}

@Composable
fun CollectionsSkeletonGrid(
    modifier: Modifier = Modifier
) {
    val spacing = LaterboxTheme.spacing
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = PaddingValues(
            start = spacing.base,
            end = spacing.base,
            top = spacing.sm,
            bottom = 80.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = modifier.fillMaxWidth(),
        userScrollEnabled = false
    ) {
        items(4) {
            CollectionCardSkeleton()
        }
    }
}
