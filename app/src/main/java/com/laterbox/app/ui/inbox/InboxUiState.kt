package com.laterbox.app.ui.inbox

import com.laterbox.app.data.preferences.InboxViewMode
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink

data class InboxUiState(
    val links: List<SavedLink> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val activeFilterType: ContentType? = null,
    val pendingMetadataCount: Int = 0,
    val viewMode: InboxViewMode = InboxViewMode.HERO,
    val hasCompletedOnboarding: Boolean = true
) {
    val isEmpty: Boolean
        get() = !isLoading && links.isEmpty() && searchQuery.isBlank() && activeFilterType == null

    val isSearchEmpty: Boolean
        get() = !isLoading && links.isEmpty() && (searchQuery.isNotBlank() || activeFilterType != null)
}
