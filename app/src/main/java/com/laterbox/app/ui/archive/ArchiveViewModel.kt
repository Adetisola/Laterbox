package com.laterbox.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.domain.model.SavedLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArchiveViewModel(
    private val linkRepository: LinkRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _linkToDelete = MutableStateFlow<SavedLink?>(null)

    val uiState: StateFlow<ArchiveUiState> = combine(
        linkRepository.archivedLinks,
        _searchQuery,
        _linkToDelete
    ) { archived, query, toDelete ->
        val filtered = if (query.isBlank()) {
            archived
        } else {
            archived.filter { link ->
                link.title.contains(query, ignoreCase = true) ||
                    link.domain.contains(query, ignoreCase = true) ||
                    (link.description?.contains(query, ignoreCase = true) == true) ||
                    (link.personalNote?.contains(query, ignoreCase = true) == true)
            }
        }
        ArchiveUiState(
            links = filtered,
            isLoading = false,
            searchQuery = query,
            linkToDelete = toDelete
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ArchiveUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onClearSearch() {
        _searchQuery.value = ""
    }

    fun onRestoreLink(linkId: Long) {
        viewModelScope.launch {
            linkRepository.setArchived(linkId, false)
        }
    }

    fun onOpenDeleteDialog(link: SavedLink) {
        _linkToDelete.value = link
    }

    fun onCloseDeleteDialog() {
        _linkToDelete.value = null
    }

    fun onPermanentDelete(linkId: Long) {
        viewModelScope.launch {
            linkRepository.deleteLink(linkId)
            _linkToDelete.value = null
        }
    }
}
