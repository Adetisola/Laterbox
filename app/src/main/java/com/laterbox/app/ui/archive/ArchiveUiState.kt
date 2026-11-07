package com.laterbox.app.ui.archive

import com.laterbox.app.domain.model.SavedLink

data class ArchiveUiState(
    val links: List<SavedLink> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val linkToDelete: SavedLink? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && links.isEmpty() && searchQuery.isBlank()

    val isSearchEmpty: Boolean
        get() = !isLoading && links.isEmpty() && searchQuery.isNotBlank()
}
