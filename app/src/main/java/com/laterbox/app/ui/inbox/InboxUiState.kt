package com.laterbox.app.ui.inbox

import com.laterbox.app.capture.ClipboardSuggestion
import com.laterbox.app.data.preferences.InboxViewMode
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink

data class InboxUiState(
    val links: List<SavedLink> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val activeFilterType: ContentType? = null,
    val activeDateRangeFilter: DateRangeFilter = DateRangeFilter.ALL,
    val pendingMetadataCount: Int = 0,
    val viewMode: InboxViewMode = InboxViewMode.HERO,
    val hasCompletedOnboarding: Boolean = true,
    val clipboardSuggestion: ClipboardSuggestion? = null,
    val unreadNotificationCount: Int = 0,
    val mediaReadyLinkIds: Set<Long> = emptySet()
) {
    val isEmpty: Boolean
        get() = !isLoading && links.isEmpty() && searchQuery.isBlank() && activeFilterType == null && activeDateRangeFilter == DateRangeFilter.ALL

    val isSearchEmpty: Boolean
        get() = !isLoading && links.isEmpty() && (searchQuery.isNotBlank() || activeFilterType != null || activeDateRangeFilter != DateRangeFilter.ALL)
}
