package com.laterbox.app.media.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.media.domain.model.DownloadTask
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DownloadsTab {
    LIBRARY,
    QUEUE,
    STORAGE
}

data class DownloadsUiState(
    val selectedTab: DownloadsTab = DownloadsTab.LIBRARY,
    val selectedMediaType: MediaType? = null,
    val searchQuery: String = "",
    val assets: List<MediaAsset> = emptyList(),
    val queueTasks: List<DownloadTask> = emptyList(),
    val storageBreakdown: Map<MediaType, Long> = emptyMap(),
    val totalStorageBytes: Long = 0L,
    val selectedAssetIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
)

private data class ViewSelections(
    val selectedTab: DownloadsTab,
    val selectedMediaType: MediaType?,
    val searchQuery: String,
    val selectedAssetIds: Set<String>,
    val isSelectionMode: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(DownloadsTab.LIBRARY)
    private val _selectedMediaType = MutableStateFlow<MediaType?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedAssetIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    private val _selections = combine(
        _selectedTab,
        _selectedMediaType,
        _searchQuery,
        _selectedAssetIds,
        _isSelectionMode
    ) { tab, type, query, ids, mode ->
        ViewSelections(tab, type, query, ids, mode)
    }

    private val _assets = combine(_selectedMediaType, _searchQuery) { type, query ->
        type to query
    }.flatMapLatest { (type, query) ->
        mediaRepository.filterAssets(type, query)
    }

    private val _queueTasks = mediaRepository.getQueueTasks()
    private val _storageBreakdown = mediaRepository.getStorageBreakdown()

    val uiState: StateFlow<DownloadsUiState> = combine(
        _selections,
        _assets,
        _queueTasks,
        _storageBreakdown
    ) { selections, assets, tasks, storage ->
        DownloadsUiState(
            selectedTab = selections.selectedTab,
            selectedMediaType = selections.selectedMediaType,
            searchQuery = selections.searchQuery,
            assets = assets,
            queueTasks = tasks,
            storageBreakdown = storage,
            totalStorageBytes = storage.values.sum(),
            selectedAssetIds = selections.selectedAssetIds,
            isSelectionMode = selections.isSelectionMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DownloadsUiState()
    )

    fun onTabSelected(tab: DownloadsTab) {
        _selectedTab.value = tab
    }

    fun onMediaTypeSelected(type: MediaType?) {
        _selectedMediaType.value = type
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onToggleAssetSelection(assetId: String) {
        val current = _selectedAssetIds.value.toMutableSet()
        if (current.contains(assetId)) {
            current.remove(assetId)
        } else {
            current.add(assetId)
        }
        _selectedAssetIds.value = current
        _isSelectionMode.value = current.isNotEmpty()
    }

    fun onClearSelection() {
        _selectedAssetIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun onDeleteSelectedAssets() {
        val ids = _selectedAssetIds.value.toList()
        viewModelScope.launch {
            for (id in ids) {
                mediaRepository.deleteAsset(id)
            }
            onClearSelection()
        }
    }

    fun onDeleteAsset(assetId: String) {
        viewModelScope.launch {
            mediaRepository.deleteAsset(assetId)
        }
    }

    fun onExportAsset(assetId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = mediaRepository.exportAsset(assetId)
            onResult(result.isSuccess)
        }
    }

    fun onOverrideCellular(taskId: String) {
        viewModelScope.launch {
            mediaRepository.overrideCellularRequirement(taskId)
        }
    }

    fun onPauseTask(taskId: String) {
        viewModelScope.launch {
            mediaRepository.pauseDownload(taskId)
        }
    }

    fun onResumeTask(taskId: String) {
        viewModelScope.launch {
            mediaRepository.resumeDownload(taskId)
        }
    }

    fun onCancelTask(taskId: String) {
        viewModelScope.launch {
            mediaRepository.cancelDownload(taskId)
        }
    }

    fun onDismissTask(taskId: String) {
        viewModelScope.launch {
            mediaRepository.dismissTask(taskId)
        }
    }

    fun onRetryTask(taskId: String) {
        viewModelScope.launch {
            mediaRepository.retryDownload(taskId)
        }
    }
}
