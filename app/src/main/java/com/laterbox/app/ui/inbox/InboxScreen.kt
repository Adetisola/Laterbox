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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Job
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.laterbox.app.capture.ClipboardSuggestion
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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.TextButton
import com.laterbox.app.ui.components.LaterboxConfirmDialog
import kotlin.math.roundToInt

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
    onNotificationHistoryClick: () -> Unit = {},
    onToggleViewMode: () -> Unit,
    onRefresh: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    onToggleRead: (SavedLink) -> Unit,
    onArchive: (SavedLink) -> Unit,
    onDelete: (SavedLink) -> Unit,
    onToggleSelectLink: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onArchiveSelected: () -> Unit = {},
    onToggleReadSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onCheckClipboard: (ClipboardManager?) -> Unit = {},
    onSaveClipboardSuggestion: (ClipboardSuggestion) -> Unit = {},
    onDismissClipboardSuggestion: (ClipboardSuggestion) -> Unit = {},
    onMenuClick: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    bottomComposerHeightPx: Int = 0,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = uiState.isSelectionMode) {
        onClearSelection()
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        onCheckClipboard(clipboard)
        onPauseOrDispose {}
    }

    var showDateFilterRow by rememberSaveable { mutableStateOf(false) }
    var collapsedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }

    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    var headerOffsetPx by rememberSaveable { mutableFloatStateOf(0f) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            headerOffsetPx = 0f
        }
    }

    val isScrolled by remember {
        derivedStateOf {
            headerHeightPx > 0f && headerOffsetPx <= -headerHeightPx + 10f
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f && headerHeightPx > 0f && headerOffsetPx > -headerHeightPx) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                    val consumed = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0f && headerHeightPx > 0f && headerOffsetPx < 0f) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                    val consumedY = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }
    }

    val showJumpToTop by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) && listState.firstVisibleItemIndex >= 3
        }
    }

    if (showDeleteConfirmDialog) {
        LaterboxConfirmDialog(
            title = "Delete ${uiState.selectedCount} ${if (uiState.selectedCount == 1) "link" else "links"}?",
            message = "This permanently removes the selected links and any downloaded media from your device.",
            confirmText = "Delete",
            onConfirm = {
                showDeleteConfirmDialog = false
                onDeleteSelected()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
            .nestedScroll(nestedScrollConnection)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.isSelectionMode) {
                // Contextual Selection Toolbar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surfaceElevated,
                    shadowElevation = 3.dp,
                    border = BorderStroke(spacing.hairline, colors.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.base, vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LaterboxIconButton(
                            iconRes = LaterboxIcons.Close,
                            contentDescription = "Cancel selection",
                            onClick = onClearSelection
                        )
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text(
                            text = "${uiState.selectedCount} selected",
                            style = typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onSelectAll,
                            contentPadding = PaddingValues(horizontal = spacing.xs)
                        ) {
                            Text(
                                text = "Select all",
                                style = typography.buttonLabel.copy(color = colors.accentPrimary)
                            )
                        }

                        val selectedLinks = remember(uiState.links, uiState.selectedLinkIds) {
                            uiState.links.filter { it.id in uiState.selectedLinkIds }
                        }
                        val readCount = selectedLinks.count { it.isRead }
                        val allRead = selectedLinks.isNotEmpty() && readCount == selectedLinks.size

                        LaterboxIconButton(
                            iconRes = if (allRead) LaterboxIcons.ViewList else LaterboxIcons.Check,
                            contentDescription = if (allRead) "Mark as unread" else "Mark as read",
                            onClick = onToggleReadSelected
                        )
                        Spacer(modifier = Modifier.width(spacing.xs))
                        LaterboxIconButton(
                            iconRes = LaterboxIcons.Archive,
                            contentDescription = "Archive selected",
                            onClick = onArchiveSelected
                        )
                        Spacer(modifier = Modifier.width(spacing.xs))
                        LaterboxIconButton(
                            iconRes = LaterboxIcons.Delete,
                            contentDescription = "Delete selected",
                            onClick = { showDeleteConfirmDialog = true }
                        )
                    }
                }
            } else {
                // Collapsible Header: scrolls away cleanly as user scrolls down without recursive height jitter
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val currentHeight = (placeable.height + headerOffsetPx.roundToInt()).coerceAtLeast(0)
                            layout(placeable.width, currentHeight) {
                                placeable.place(0, headerOffsetPx.roundToInt())
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { headerHeightPx = it.height.toFloat() }
                    ) {
                        // Header: Menu & Brand / Title on left; Notification Bell & Settings buttons on right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.base, vertical = spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (onMenuClick != null) {
                                    LaterboxIconButton(
                                        iconRes = LaterboxIcons.Menu,
                                        contentDescription = "Open navigation menu",
                                        onClick = onMenuClick
                                    )
                                    Spacer(modifier = Modifier.width(spacing.sm))
                                }
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
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    LaterboxIconButton(
                                        iconRes = LaterboxIcons.Bell,
                                        contentDescription = "Notification History",
                                        onClick = onNotificationHistoryClick
                                    )
                                    if (uiState.unreadNotificationCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 6.dp, end = 6.dp)
                                                .size(7.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(colors.accentPrimary)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(spacing.xs))

                                LaterboxIconButton(
                                    iconRes = LaterboxIcons.Settings,
                                    contentDescription = "Settings",
                                    onClick = onSettingsClick
                                )
                            }
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

                        // Background Metadata Sync Banner (Quiet editorial bar)
                        if (uiState.pendingMetadataCount > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.accentContainer.copy(alpha = 0.5f))
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

                        // Foreground Clipboard Suggestion Banner
                        AnimatedVisibility(
                            visible = uiState.clipboardSuggestion != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            uiState.clipboardSuggestion?.let { suggestion ->
                                ClipboardSuggestionBanner(
                                    suggestion = suggestion,
                                    onSave = { onSaveClipboardSuggestion(suggestion) },
                                    onDismiss = { onDismissClipboardSuggestion(suggestion) }
                                )
                            }
                        }
                    }
                }
            }

            // Sticky Content Type Filter Chips Row (pinned at top below status bar when header scrolls away)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.backgroundPrimary)
                    .padding(horizontal = spacing.base, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
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

                if (isScrolled && !uiState.isSelectionMode) {
                    Spacer(modifier = Modifier.width(spacing.xs))
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Search,
                        contentDescription = "Search",
                        onClick = {
                            scrollJob?.cancel()
                            scrollJob = coroutineScope.launch {
                                headerOffsetPx = 0f
                                listState.animateScrollToItem(0)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Settings,
                        contentDescription = "Settings",
                        onClick = onSettingsClick
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

                        val density = LocalDensity.current
                        val composerDp = with(density) { bottomComposerHeightPx.toDp() }
                        val listBottomClearance = if (bottomComposerHeightPx > 0) composerDp + 12.dp else 24.dp

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spacing.base,
                                end = spacing.base,
                                top = 0.dp,
                                bottom = listBottomClearance
                            )
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
                                    itemsIndexed(
                                        items = group.links,
                                        key = { _, it -> it.id },
                                        contentType = { _, _ -> "link_card" }
                                    ) { index, link ->
                                        val cardShape = if (index == 0) {
                                            RoundedCornerShape(
                                                topStart = 0.dp,
                                                topEnd = 16.dp,
                                                bottomStart = 16.dp,
                                                bottomEnd = 16.dp
                                            )
                                        } else {
                                            LaterboxShapeTokens.Card
                                        }
                                        SavedLinkCard(
                                            link = link,
                                            onClick = {
                                                if (uiState.isSelectionMode) {
                                                    onToggleSelectLink(link.id)
                                                } else {
                                                    onLinkClick(link)
                                                }
                                            },
                                            onQuickOpenClick = { onQuickOpenClick(link) },
                                            viewMode = uiState.viewMode,
                                            shape = cardShape,
                                            modifier = Modifier.padding(bottom = spacing.sm),
                                            onToggleRead = { onToggleRead(link) },
                                            onArchive = { onArchive(link) },
                                            onDelete = { onDelete(link) },
                                            isMediaReady = link.id in uiState.mediaReadyLinkIds,
                                            isSelected = link.id in uiState.selectedLinkIds,
                                            isInSelectionMode = uiState.isSelectionMode,
                                            onLongClick = { onToggleSelectLink(link.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Jump to Top (positioned cleanly above the floating composer, hidden while keyboard is open)
        if (!uiState.isSelectionMode) {
            val density = LocalDensity.current
            val composerDp = with(density) { bottomComposerHeightPx.toDp() }
            val isImeVisible = WindowInsets.ime.getBottom(density) > WindowInsets.navigationBars.getBottom(density)
            val jumpBottomClearance = if (bottomComposerHeightPx > 0) composerDp + 12.dp else 20.dp
            AnimatedVisibility(
                visible = showJumpToTop && !isImeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = jumpBottomClearance)
                    .testTag("inbox_jump_to_top")
            ) {
                Surface(
                    onClick = {
                        scrollJob?.cancel()
                        scrollJob = coroutineScope.launch {
                            if (listState.firstVisibleItemIndex > 2) {
                                listState.scrollToItem(2)
                            }
                            listState.animateScrollToItem(0)
                        }
                    },
                    shape = CircleShape,
                    color = colors.surfaceElevated,
                    contentColor = colors.accentPrimary,
                    shadowElevation = 4.dp,
                    border = BorderStroke(spacing.hairline, colors.borderSubtle),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = LaterboxIcons.ChevronDown),
                            contentDescription = "Jump to top",
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer(rotationZ = 180f),
                            tint = colors.accentPrimary
                        )
                    }
                }
            }
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
    com.laterbox.app.ui.components.LaterboxFilterChip(
        text = text,
        isSelected = isSelected,
        onClick = onClick
    )
}

@Composable
private fun ClipboardSuggestionBanner(
    suggestion: ClipboardSuggestion,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.base, vertical = spacing.xs)
            .clip(LaterboxShapeTokens.Card)
            .background(colors.surfaceElevated)
            .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.Card)
            .padding(horizontal = spacing.md, vertical = spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = LaterboxIcons.Globe),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.accentPrimary
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Save link from clipboard?",
                    style = typography.bodyPrimary.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                    color = colors.textPrimary
                )
                Text(
                    text = suggestion.domain,
                    style = typography.metaText,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(spacing.sm))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(spacing.radiusSmall))
                    .background(colors.accentPrimary)
                    .clickable(role = Role.Button, onClick = onSave)
                    .padding(horizontal = spacing.md, vertical = spacing.xs)
            ) {
                Text(
                    text = "Save",
                    style = typography.buttonLabel.copy(
                        color = colors.backgroundPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }
            Spacer(modifier = Modifier.width(spacing.xs))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(LaterboxShapeTokens.IconButton)
                    .clickable(role = Role.Button, onClickLabel = "Dismiss", onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Close),
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = colors.textSecondary
                )
            }
        }
    }
}
