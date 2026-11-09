package com.laterbox.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.data.preferences.InboxViewMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.MetadataStatus
import com.laterbox.app.domain.model.SavedLink
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

    private val _activeDateRangeFilter = MutableStateFlow(DateRangeFilter.ALL)
    val activeDateRangeFilter: StateFlow<DateRangeFilter> = _activeDateRangeFilter

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val baseLinksFlow = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            linkRepository.inboxLinks
        } else {
            linkRepository.searchLinks(query)
        }
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<InboxUiState> = combine(
        baseLinksFlow,
        _searchQuery,
        _activeFilterType,
        _activeDateRangeFilter,
        _isRefreshing,
        userPreferencesRepository.userPreferences
    ) { args: Array<Any?> ->
        val rawLinks = args[0] as List<SavedLink>
        val query = args[1] as String
        val filterType = args[2] as ContentType?
        val dateFilter = args[3] as DateRangeFilter
        val refreshing = args[4] as Boolean
        val prefs = args[5] as UserPreferences

        // If searching, filter out archived links since searchLinks searches all
        val unarchived = rawLinks.filter { !it.isArchived }

        val dateFiltered = if (dateFilter != DateRangeFilter.ALL) {
            unarchived.filter { dateFilter.matches(it.createdAt) }
        } else {
            unarchived
        }

        val filtered = if (filterType != null) {
            dateFiltered.filter { it.contentType == filterType }
        } else {
            dateFiltered
        }

        val pendingCount = unarchived.count {
            it.metadataStatus == MetadataStatus.PENDING || it.metadataStatus == MetadataStatus.FETCHING
        }

        InboxUiState(
            links = filtered,
            isLoading = false,
            isRefreshing = refreshing,
            searchQuery = query,
            activeFilterType = filterType,
            activeDateRangeFilter = dateFilter,
            pendingMetadataCount = pendingCount,
            viewMode = prefs.inboxViewMode,
            hasCompletedOnboarding = prefs.hasCompletedOnboarding
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
        _activeDateRangeFilter.value = DateRangeFilter.ALL
    }

    fun onFilterContentType(type: ContentType?) {
        _activeFilterType.value = if (_activeFilterType.value == type) null else type
    }

    fun onFilterDateRange(filter: DateRangeFilter) {
        _activeDateRangeFilter.value = if (_activeDateRangeFilter.value == filter) DateRangeFilter.ALL else filter
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

    fun unarchive(linkId: Long) {
        viewModelScope.launch {
            linkRepository.setArchived(linkId, false)
        }
    }

    fun onDelete(linkId: Long) {
        viewModelScope.launch {
            linkRepository.deleteLink(linkId)
        }
    }

    fun onRestore(link: SavedLink) {
        viewModelScope.launch {
            linkRepository.restoreLink(link)
        }
    }

    fun restoreLink(link: SavedLink) = onRestore(link)

    fun onLinkOpened(linkId: Long) {
        viewModelScope.launch {
            val autoMark = userPreferencesRepository.userPreferences.first().autoMarkAsRead
            if (autoMark) {
                linkRepository.setRead(linkId, true)
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val startTime = System.currentTimeMillis()
            try {
                linkRepository.retryPendingAndFailedFetches()
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 450L) {
                    kotlinx.coroutines.delay(450L - elapsed)
                }
            } catch (_: Exception) {
                // Ensure pull-to-refresh spinner always finishes gracefully
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onToggleViewMode() {
        viewModelScope.launch {
            val currentMode = userPreferencesRepository.userPreferences.first().inboxViewMode
            val nextMode = if (currentMode == InboxViewMode.HERO) InboxViewMode.COMPACT else InboxViewMode.HERO
            userPreferencesRepository.setInboxViewMode(nextMode)
        }
    }

    fun onCompleteOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setHasCompletedOnboarding(true)
        }
    }
}
