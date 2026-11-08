package com.laterbox.app.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.data.preferences.InboxViewMode
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.components.DateGroupHeader
import com.laterbox.app.ui.components.DateGrouper
import com.laterbox.app.ui.components.EmptyStateView
import com.laterbox.app.ui.components.InboxSkeletonList
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.components.SavedLinkCard
import com.laterbox.app.ui.onboarding.OnboardingSheet
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(
    uiState: InboxUiState,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilterContentType: (ContentType?) -> Unit,
    onFilterDateRange: (DateRangeFilter) -> Unit,
    onLinkClick: (SavedLink) -> Unit,
    onQuickOpenClick: (SavedLink) -> Unit,
    onAddLinkClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onRefresh: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    onToggleRead: (SavedLink) -> Unit,
    onArchive: (SavedLink) -> Unit,
    onDelete: (SavedLink) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var showDateFilterRow by rememberSaveable { mutableStateOf(false) }
    var collapsedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }

    val showJumpToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 6 }
    }
    val isFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 80
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Brand / Title on left; Solo Settings button on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.sm),
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
                        text = "For later, whenever you're ready",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }

                LaterboxIconButton(
                    iconRes = LaterboxIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onSettingsClick
                )
            }

            // Search Row: [Search Field (weight 1f)] [Date Filter Button] [View Toggle Button]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Input Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(LaterboxShapeTokens.InputField)
                        .background(colors.surfacePrimary)
                        .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.InputField)
                        .padding(horizontal = spacing.md),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = LaterboxIcons.Search),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary
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
                                    .size(36.dp)
                                    .clip(LaterboxShapeTokens.IconButton)
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "Clear search",
                                        onClick = onClearSearch
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = LaterboxIcons.Close),
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp),
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(spacing.xs))

                // Date Filter Toggle Button
                LaterboxIconButton(
                    iconRes = LaterboxIcons.Filter,
                    contentDescription = "Filter by date",
                    selected = uiState.activeDateRangeFilter != DateRangeFilter.ALL,
                    onClick = { showDateFilterRow = !showDateFilterRow }
                )

                Spacer(modifier = Modifier.width(spacing.xs))

                // View Mode Toggle button (Hero vs List) right beside search
                LaterboxIconButton(
                    iconRes = if (uiState.viewMode == InboxViewMode.HERO) LaterboxIcons.ViewList else LaterboxIcons.ViewCard,
                    contentDescription = if (uiState.viewMode == InboxViewMode.HERO) "Switch to compact view" else "Switch to hero view",
                    onClick = onToggleViewMode
                )
            }

            // Expandable Date Filter Chips Row
            AnimatedVisibility(
                visible = showDateFilterRow || uiState.activeDateRangeFilter != DateRangeFilter.ALL,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = spacing.base, vertical = spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    DateRangeFilter.entries.forEach { filter ->
                        FilterChip(
                            text = filter.label,
                            isSelected = uiState.activeDateRangeFilter == filter,
                            onClick = { onFilterDateRange(filter) }
                        )
                    }
                }
            }

            // Content Type Filter Chips Row
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

            // Background Metadata Sync Banner (Quiet editorial bar)
            if (uiState.pendingMetadataCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accentSecondary.copy(alpha = 0.5f))
                        .padding(horizontal = spacing.base, vertical = spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.accentPrimary
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = "Enriching ${uiState.pendingMetadataCount} ${if (uiState.pendingMetadataCount == 1) "link" else "links"} in the background…",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }
            }

            // Main Content Area with Pull-to-Refresh
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    uiState.isLoading -> {
                        InboxSkeletonList(
                            isHero = uiState.viewMode == InboxViewMode.HERO,
                            modifier = Modifier.fillMaxSize()
                        )
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
                        val filterLabel = when (uiState.activeFilterType) {
                            ContentType.ARTICLE -> "articles"
                            ContentType.VIDEO -> "videos"
                            ContentType.DISCUSSION -> "discussions"
                            ContentType.PRODUCT -> "products"
                            ContentType.WEBSITE -> "websites"
                            null -> null
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmptyStateView(
                                title = if (filterLabel != null && uiState.searchQuery.isBlank()) {
                                    "No ${filterLabel.replaceFirstChar { it.uppercase() }} saved"
                                } else {
                                    "No matching links"
                                },
                                subtitle = if (uiState.searchQuery.isNotBlank() && filterLabel != null) {
                                    "No $filterLabel matching \"${uiState.searchQuery}\""
                                } else if (uiState.searchQuery.isNotBlank()) {
                                    "Nothing saved matching \"${uiState.searchQuery}\""
                                } else if (filterLabel != null) {
                                    "You don't have any links categorized as $filterLabel yet."
                                } else {
                                    "No links found for this filter."
                                },
                                actionText = "Show all links",
                                onActionClick = {
                                    onClearSearch()
                                    onFilterContentType(null)
                                    onFilterDateRange(DateRangeFilter.ALL)
                                }
                            )
                        }
                    }

                    else -> {
                        val groupedLinks = remember(uiState.links) {
                            DateGrouper.groupLinks(uiState.links)
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spacing.base,
                                end = spacing.base,
                                top = spacing.sm,
                                bottom = 148.dp // Leave clear room for floating bottom bar & FAB
                            ),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            groupedLinks.forEach { group ->
                                stickyHeader(key = "header_${group.title}", contentType = "date_header") {
                                    val isCollapsed = collapsedGroups.contains(group.title)
                                    DateGroupHeader(
                                        title = group.title,
                                        count = group.links.size,
                                        isCollapsed = isCollapsed,
                                        onToggleCollapse = {
                                            collapsedGroups = if (isCollapsed) {
                                                collapsedGroups - group.title
                                            } else {
                                                collapsedGroups + group.title
                                            }
                                        }
                                    )
                                }

                                if (!collapsedGroups.contains(group.title)) {
                                    items(
                                        items = group.links,
                                        key = { it.id },
                                        contentType = { "link_card" }
                                    ) { link ->
                                        SavedLinkCard(
                                            link = link,
                                            onClick = { onLinkClick(link) },
                                            onQuickOpenClick = { onQuickOpenClick(link) },
                                            viewMode = uiState.viewMode,
                                            onToggleRead = { onToggleRead(link) },
                                            onArchive = { onArchive(link) },
                                            onDelete = { onDelete(link) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom-Right Action Button (+ Add Link) [UX15, UX17]
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp)
        ) {
            Surface(
                onClick = onAddLinkClick,
                shape = LaterboxShapeTokens.Fab,
                color = colors.accentPrimary,
                contentColor = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .height(56.dp)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = if (isFabExpanded) 18.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = LaterboxIcons.Plus),
                        contentDescription = "Add Link",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                    AnimatedVisibility(
                        visible = isFabExpanded,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add Link",
                                style = typography.uiMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Jump to top floating action button (floats right above FAB when scrolled deep)
        AnimatedVisibility(
            visible = showJumpToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 26.dp, bottom = 162.dp)
        ) {
            LaterboxIconButton(
                iconRes = LaterboxIcons.ChevronDown,
                contentDescription = "Jump to top",
                onClick = {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier.graphicsLayer(rotationZ = 180f)
            )
        }

        // Onboarding Sheet on First Launch
        if (!uiState.hasCompletedOnboarding) {
            OnboardingSheet(
                onDismiss = onCompleteOnboarding
            )
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

    val bg = if (isSelected) colors.accentPrimary else colors.surfacePrimary
    val textColor = if (isSelected) colors.surfaceElevated else colors.textSecondary
    val borderColor = if (isSelected) colors.accentPrimary else colors.borderSubtle

    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(LaterboxShapeTokens.Chip)
            .background(bg)
            .border(spacing.hairline, borderColor, LaterboxShapeTokens.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.uiMedium,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
