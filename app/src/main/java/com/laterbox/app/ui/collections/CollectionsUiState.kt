package com.laterbox.app.ui.collections

import com.laterbox.app.domain.model.Collection
import com.laterbox.app.domain.model.CollectionWithCount

data class CollectionsUiState(
    val collections: List<CollectionWithCount> = emptyList(),
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false,
    val collectionToRename: Collection? = null,
    val collectionToDelete: Collection? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && collections.isEmpty()
}
