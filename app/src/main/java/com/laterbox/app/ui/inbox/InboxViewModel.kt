package com.laterbox.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.MetadataStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val linkRepository: LinkRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _activeFilterType = MutableStateFlow<ContentType?>(null)
    val activeFilterType: StateFlow<ContentType?> = _activeFilterType

    private val baseLinksFlow = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            linkRepository.inboxLinks
        } else {
            linkRepository.searchLinks(query)
        }
    }

    val uiState: StateFlow<InboxUiState> = combine(
        baseLinksFlow,
        _searchQuery,
        _activeFilterType
    ) { rawLinks, query, filterType ->
        // If searching, filter out archived links since searchLinks searches all
        val unarchived = rawLinks.filter { !it.isArchived }
        val filtered = if (filterType != null) {
            unarchived.filter { it.contentType == filterType }
        } else {
            unarchived
        }
        val pendingCount = unarchived.count {
            it.metadataStatus == MetadataStatus.PENDING || it.metadataStatus == MetadataStatus.FETCHING
        }
        InboxUiState(
            links = filtered,
            isLoading = false,
            searchQuery = query,
            activeFilterType = filterType,
            pendingMetadataCount = pendingCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InboxUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onClearSearch() {
        _searchQuery.value = ""
    }

    fun onFilterContentType(type: ContentType?) {
        _activeFilterType.value = if (_activeFilterType.value == type) null else type
    }

    fun onToggleRead(linkId: Long, currentRead: Boolean) {
        viewModelScope.launch {
            linkRepository.setRead(linkId, !currentRead)
        }
    }

    fun onArchive(linkId: Long) {
        viewModelScope.launch {
            linkRepository.setArchived(linkId, true)
        }
    }

    fun onDelete(linkId: Long) {
        viewModelScope.launch {
            linkRepository.deleteLink(linkId)
        }
    }

    fun onLinkOpened(linkId: Long) {
        viewModelScope.launch {
            val autoMark = userPreferencesRepository.userPreferences.first().autoMarkAsRead
            if (autoMark) {
                linkRepository.setRead(linkId, true)
            }
        }
    }
}
