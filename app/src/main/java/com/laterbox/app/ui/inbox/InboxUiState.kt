package com.laterbox.app.ui.inbox

import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink

data class InboxUiState(
    val links: List<SavedLink> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val activeFilterType: ContentType? = null,
    val pendingMetadataCount: Int = 0
) {
    val isEmpty: Boolean
        get() = !isLoading && links.isEmpty() && searchQuery.isBlank() && activeFilterType == null

    val isSearchEmpty: Boolean
        get() = !isLoading && links.isEmpty() && (searchQuery.isNotBlank() || activeFilterType != null)
}
