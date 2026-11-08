package com.laterbox.app.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.data.preferences.InboxViewMode
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.components.EmptyStateView
import com.laterbox.app.ui.components.SavedLinkCard
import com.laterbox.app.ui.onboarding.OnboardingSheet
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    uiState: InboxUiState,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilterContentType: (ContentType?) -> Unit,
    onLinkClick: (SavedLink) -> Unit,
    onQuickOpenClick: (SavedLink) -> Unit,
    onAddLinkClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onRefresh: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val showJumpToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 6 }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Title & Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Laterbox",
                        style = typography.displayLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Your calm reading inbox",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // View Mode Toggle button (Hero vs Compact)
                    Box(
                        modifier = Modifier
                            .size(spacing.minTouchTarget)
                            .clip(RoundedCornerShape(spacing.radiusSmall))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = if (uiState.viewMode == InboxViewMode.HERO) "Switch to compact view" else "Switch to hero view",
                                onClick = onToggleViewMode
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.viewMode == InboxViewMode.HERO) "▤" else "☰",
                            style = typography.uiMedium.copy(fontSize = 18.sp),
                            color = colors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(spacing.xs))

                    // Settings icon button (min 48dp touch target)
                    Box(
                        modifier = Modifier
                            .size(spacing.minTouchTarget)
                            .clip(RoundedCornerShape(spacing.radiusSmall))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Settings",
                                onClick = onSettingsClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚙",
                            style = typography.uiMedium.copy(fontSize = 18.sp),
                            color = colors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(spacing.xs))

                    // + Add button
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(spacing.radiusSmall))
                            .background(colors.surfaceSecondary)
                            .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Add link manually",
                                onClick = onAddLinkClick
                            )
                            .padding(horizontal = spacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Add link",
                            style = typography.uiMedium.copy(fontSize = 13.sp),
                            color = colors.accentPrimary
                        )
                    }
                }
            }

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.xs)
                    .height(44.dp)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(horizontal = spacing.md),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⌕",
                        style = typography.uiMedium.copy(fontSize = 16.sp),
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))

                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.searchQuery.isEmpty()) {
                            Text(
                                text = "Search links, titles, notes...",
                                style = typography.bodySecondary,
                                color = colors.textSecondary.copy(alpha = 0.7f)
                            )
                        }
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = typography.bodyPrimary.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accentPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onClearSearch() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                style = typography.metaText,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing.base, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                FilterChip(
                    text = "All",
                    isSelected = uiState.activeFilterType == null,
                    onClick = { onFilterContentType(null) }
                )
                FilterChip(
                    text = "Articles",
                    isSelected = uiState.activeFilterType == ContentType.ARTICLE,
                    onClick = { onFilterContentType(ContentType.ARTICLE) }
                )
                FilterChip(
                    text = "Videos",
                    isSelected = uiState.activeFilterType == ContentType.VIDEO,
                    onClick = { onFilterContentType(ContentType.VIDEO) }
                )
                FilterChip(
                    text = "Discussions",
                    isSelected = uiState.activeFilterType == ContentType.DISCUSSION,
                    onClick = { onFilterContentType(ContentType.DISCUSSION) }
                )
                FilterChip(
                    text = "Products",
                    isSelected = uiState.activeFilterType == ContentType.PRODUCT,
                    onClick = { onFilterContentType(ContentType.PRODUCT) }
                )
                FilterChip(
                    text = "Websites",
                    isSelected = uiState.activeFilterType == ContentType.WEBSITE,
                    onClick = { onFilterContentType(ContentType.WEBSITE) }
                )
            }

            // Background Metadata Sync Progress Banner
            AnimatedVisibility(
                visible = uiState.pendingMetadataCount > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.base, vertical = spacing.xs)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(colors.accentContainer.copy(alpha = 0.35f))
                        .border(spacing.hairline, colors.accentPrimary.copy(alpha = 0.25f), RoundedCornerShape(spacing.radiusSmall))
                        .padding(horizontal = spacing.sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.accentPrimary)
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = "Updating metadata for ${uiState.pendingMetadataCount} item(s) in background...",
                        style = typography.metaText,
                        color = colors.accentPrimary
                    )
                }
            }

            // Main Content Area with Pull-To-Refresh
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = colors.accentPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    uiState.isEmpty -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmptyStateView(
                                title = "Nothing saved. Yet.",
                                subtitle = "When something catches your attention online, send it here with Android's share sheet.",
                                actionText = "+ Add link manually",
                                onActionClick = onAddLinkClick
                            )
                        }
                    }

                    uiState.isSearchEmpty -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmptyStateView(
                                title = "No matching links",
                                subtitle = if (uiState.searchQuery.isNotBlank()) {
                                    "Nothing saved matching \"${uiState.searchQuery}\""
                                } else {
                                    "No links found for this filter."
                                },
                                actionText = "Clear filters",
                                onActionClick = {
                                    onClearSearch()
                                    onFilterContentType(null)
                                }
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spacing.base,
                                end = spacing.base,
                                top = spacing.sm,
                                bottom = 80.dp // Leave room for bottom bar & FAB
                            ),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            items(
                                items = uiState.links,
                                key = { it.id }
                            ) { link ->
                                SavedLinkCard(
                                    link = link,
                                    onClick = { onLinkClick(link) },
                                    onQuickOpenClick = { onQuickOpenClick(link) },
                                    viewMode = uiState.viewMode
                                )
                            }
                        }
                    }
                }
            }
        }

        // Jump to top floating action button
        AnimatedVisibility(
            visible = showJumpToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = spacing.base, bottom = 80.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(spacing.hairline, colors.borderSubtle, CircleShape)
                    .clickable {
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↑",
                    style = typography.uiMedium.copy(fontSize = 18.sp),
                    color = colors.textPrimary
                )
            }
        }

        // Onboarding Sheet on First Launch
        if (!uiState.hasCompletedOnboarding) {
            OnboardingSheet(onDismiss = onCompleteOnboarding)
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val bg = if (isSelected) colors.surfaceSecondary else colors.backgroundPrimary
    val borderColor = if (isSelected) colors.accentPrimary else colors.borderSubtle
    val textColor = if (isSelected) colors.accentPrimary else colors.textSecondary

    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(spacing.radiusSmall))
            .background(bg)
            .border(spacing.hairline, borderColor, RoundedCornerShape(spacing.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.metaText.copy(fontSize = 12.sp),
            color = textColor
        )
    }
}
