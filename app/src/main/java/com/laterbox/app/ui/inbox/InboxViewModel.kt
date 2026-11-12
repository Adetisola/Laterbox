package com.laterbox.app.ui.inbox

import android.content.ClipboardManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.capture.ClipboardSuggestion
import com.laterbox.app.capture.ClipboardSuggestionManager
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

import com.laterbox.app.data.repository.NotificationHistoryRepository
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val linkRepository: LinkRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationHistoryRepository: NotificationHistoryRepository? = null,
    private val inspectionDao: com.laterbox.app.media.data.db.dao.MediaInspectionDao? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _activeFilterType = MutableStateFlow<ContentType?>(null)
    val activeFilterType: StateFlow<ContentType?> = _activeFilterType

    private val _activeDateRangeFilter = MutableStateFlow(DateRangeFilter.ALL)
    val activeDateRangeFilter: StateFlow<DateRangeFilter> = _activeDateRangeFilter

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _clipboardSuggestion = MutableStateFlow<ClipboardSuggestion?>(null)
    val clipboardSuggestion: StateFlow<ClipboardSuggestion?> = _clipboardSuggestion

    private val baseLinksFlow = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            linkRepository.inboxLinks
        } else {
            linkRepository.searchLinks(query)
        }
    }

    private val unreadNotificationsFlow = notificationHistoryRepository?.unreadCount ?: flowOf(0)
    private val mediaReadyFlow = inspectionDao?.getCompletedInspectionLinkIds() ?: flowOf<List<Long>>(emptyList())

    private val _selectedLinkIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedLinkIds: StateFlow<Set<Long>> = _selectedLinkIds

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<InboxUiState> = combine(
        baseLinksFlow,
        _searchQuery,
        _activeFilterType,
        _activeDateRangeFilter,
        _isRefreshing,
        _clipboardSuggestion,
        userPreferencesRepository.userPreferences,
        unreadNotificationsFlow,
        mediaReadyFlow,
        _selectedLinkIds
    ) { args: Array<Any?> ->
        val rawLinks = args[0] as List<SavedLink>
        val query = args[1] as String
        val filterType = args[2] as ContentType?
        val dateFilter = args[3] as DateRangeFilter
        val refreshing = args[4] as Boolean
        val suggestion = args[5] as ClipboardSuggestion?
        val prefs = args[6] as UserPreferences
        val unreadNotifs = args[7] as Int
        val mediaReadyIds = (args[8] as? List<Long>)?.toSet() ?: emptySet()
        val selectedIds = args[9] as Set<Long>

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
            hasCompletedOnboarding = prefs.hasCompletedOnboarding,
            clipboardSuggestion = suggestion,
            unreadNotificationCount = unreadNotifs,
            mediaReadyLinkIds = mediaReadyIds,
            selectedLinkIds = selectedIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InboxUiState(isLoading = true)
    )

    fun checkClipboard(clipboardManager: ClipboardManager?) {
        viewModelScope.launch {
            val clipMeta = ClipboardSuggestionManager.extractClipMetadata(clipboardManager) ?: run {
                _clipboardSuggestion.value = null
                return@launch
            }

            val prefs = userPreferencesRepository.userPreferences.first()
            if (!prefs.clipboardSuggestionEnabled) {
                _clipboardSuggestion.value = null
                return@launch
            }

            val candidate = ClipboardSuggestionManager.evaluateCandidate(
                rawText = clipMeta.text,
                isSensitive = clipMeta.isSensitive,
                clipTimestampMs = clipMeta.timestampMs,
                clipboardEnabled = prefs.clipboardSuggestionEnabled,
                recentHistoryEntries = prefs.recentClipboardHistory,
                isAlreadySaved = { false }
            )

            if (candidate != null) {
                val existing = linkRepository.findDuplicate(candidate.url)
                if (existing != null) {
                    userPreferencesRepository.recordHandledClipboardHash(candidate.hash)
                    _clipboardSuggestion.value = null
                } else {
                    _clipboardSuggestion.value = candidate
                }
            } else {
                _clipboardSuggestion.value = null
            }
        }
    }

    fun dismissClipboardSuggestion(suggestion: ClipboardSuggestion): kotlinx.coroutines.Job {
        _clipboardSuggestion.value = null
        return viewModelScope.launch {
            userPreferencesRepository.recordHandledClipboardHash(suggestion.hash)
        }
    }

    fun saveClipboardSuggestion(suggestion: ClipboardSuggestion, onSuccess: () -> Unit = {}): kotlinx.coroutines.Job {
        _clipboardSuggestion.value = null
        return viewModelScope.launch {
            userPreferencesRepository.recordHandledClipboardHash(suggestion.hash)
            linkRepository.saveLink(originalUrl = suggestion.url)
            onSuccess()
        }
    }

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

    fun onToggleRead(linkId: Long, currentIsRead: Boolean): kotlinx.coroutines.Job =
        viewModelScope.launch {
            linkRepository.setRead(linkId, !currentIsRead)
        }

    fun onArchive(linkId: Long): kotlinx.coroutines.Job =
        viewModelScope.launch {
            linkRepository.setArchived(linkId, true)
        }

    fun unarchive(linkId: Long): kotlinx.coroutines.Job =
        viewModelScope.launch {
            linkRepository.setArchived(linkId, false)
        }

    fun onDelete(linkId: Long): kotlinx.coroutines.Job =
        viewModelScope.launch {
            linkRepository.deleteLink(linkId)
        }

    fun onToggleSelectLink(linkId: Long) {
        val current = _selectedLinkIds.value
        _selectedLinkIds.value = if (current.contains(linkId)) {
            current - linkId
        } else {
            current + linkId
        }
    }

    fun onSelectAll(links: List<SavedLink>) {
        _selectedLinkIds.value = links.map { it.id }.toSet()
    }

    fun onClearSelection() {
        _selectedLinkIds.value = emptySet()
    }

    fun onArchiveSelected(): kotlinx.coroutines.Job = viewModelScope.launch {
        val ids = _selectedLinkIds.value
        if (ids.isNotEmpty()) {
            linkRepository.setArchivedBatch(ids, true)
            _selectedLinkIds.value = emptySet()
        }
    }

    fun onToggleReadSelected(links: List<SavedLink>): kotlinx.coroutines.Job = viewModelScope.launch {
        val ids = _selectedLinkIds.value
        if (ids.isNotEmpty()) {
            val selectedLinks = links.filter { ids.contains(it.id) }
            val allRead = selectedLinks.isNotEmpty() && selectedLinks.all { it.isRead }
            linkRepository.setReadBatch(ids, !allRead)
            _selectedLinkIds.value = emptySet()
        }
    }

    fun onDeleteSelected(): kotlinx.coroutines.Job = viewModelScope.launch {
        val ids = _selectedLinkIds.value
        if (ids.isNotEmpty()) {
            linkRepository.deleteLinks(ids)
            _selectedLinkIds.value = emptySet()
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
