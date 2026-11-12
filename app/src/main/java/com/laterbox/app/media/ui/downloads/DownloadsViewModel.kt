package com.laterbox.app.media.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.media.data.adapters.InspectionResultJsonAdapter
import com.laterbox.app.media.data.db.dao.MediaInspectionDao
import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.DownloadTask
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DownloadsTab {
    LIBRARY,
    QUEUE,
    STORAGE
}

data class ReadyToDownloadItem(
    val savedLinkId: Long,
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val candidateCount: Int,
    val mediaType: MediaType = MediaType.VIDEO
)

data class DownloadsUiState(
    val selectedTab: DownloadsTab = DownloadsTab.LIBRARY,
    val selectedMediaType: MediaType? = null,
    val searchQuery: String = "",
    val assets: List<MediaAsset> = emptyList(),
    val queueTasks: List<DownloadTask> = emptyList(),
    val storageBreakdown: Map<MediaType, Long> = emptyMap(),
    val totalStorageBytes: Long = 0L,
    val selectedAssetIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val readyToDownloadItems: List<ReadyToDownloadItem> = emptyList(),
    val selectedTaskIds: Set<String> = emptySet(),
    val isQueueSelectionMode: Boolean = false
)

private data class ViewSelections(
    val selectedTab: DownloadsTab,
    val selectedMediaType: MediaType?,
    val searchQuery: String,
    val selectedAssetIds: Set<String>,
    val isSelectionMode: Boolean,
    val selectedTaskIds: Set<String>,
    val isQueueSelectionMode: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val mediaRepository: MediaRepository,
    private val inspectionDao: MediaInspectionDao? = null
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(DownloadsTab.LIBRARY)
    private val _selectedMediaType = MutableStateFlow<MediaType?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedAssetIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isQueueSelectionMode = MutableStateFlow(false)

    private val _selections = combine(
        _selectedTab,
        _selectedMediaType,
        _searchQuery,
        _selectedAssetIds,
        _isSelectionMode,
        _selectedTaskIds,
        _isQueueSelectionMode
    ) { args: Array<Any?> ->
        ViewSelections(
            selectedTab = args[0] as DownloadsTab,
            selectedMediaType = args[1] as MediaType?,
            searchQuery = args[2] as String,
            selectedAssetIds = args[3] as Set<String>,
            isSelectionMode = args[4] as Boolean,
            selectedTaskIds = args[5] as Set<String>,
            isQueueSelectionMode = args[6] as Boolean
        )
    }

    private val _assets = combine(_selectedMediaType, _searchQuery) { type, query ->
        type to query
    }.flatMapLatest { (type, query) ->
        mediaRepository.filterAssets(type, query)
    }

    private val _queueTasks = mediaRepository.getQueueTasks()
    private val _storageBreakdown = mediaRepository.getStorageBreakdown()
    private val _completedInspections = inspectionDao?.getCompletedInspections() ?: flowOf(emptyList())

    val uiState: StateFlow<DownloadsUiState> = combine(
        _selections,
        _assets,
        _queueTasks,
        _storageBreakdown,
        _completedInspections
    ) { selections, assets, tasks, storage, inspections ->
        val downloadedLinkIds = assets.mapNotNull { it.savedLinkId }.toSet()
        val readyItems = inspections.mapNotNull { entity ->
            val result = entity.resultJson?.let { json ->
                try {
                    InspectionResultJsonAdapter.fromJson(json)
                } catch (_: Exception) { null }
            }
            if (result is InspectionResult.Available && result.candidates.isNotEmpty()) {
                val nonThumbCandidates = result.candidates.filter { !it.isThumbnail }
                val thumb = result.thumbnailUrl ?: result.candidates.firstOrNull { it.isThumbnail }?.source?.let { (it as? AcquisitionSource.DirectHttp)?.url }
                ReadyToDownloadItem(
                    savedLinkId = entity.savedLinkId,
                    title = result.title ?: entity.url,
                    url = entity.url,
                    thumbnailUrl = thumb,
                    candidateCount = if (nonThumbCandidates.isNotEmpty()) nonThumbCandidates.size else result.candidates.size,
                    mediaType = nonThumbCandidates.firstOrNull()?.mediaType ?: MediaType.VIDEO
                )
            } else null
        }.filter { !downloadedLinkIds.contains(it.savedLinkId) }

        DownloadsUiState(
            selectedTab = selections.selectedTab,
            selectedMediaType = selections.selectedMediaType,
            searchQuery = selections.searchQuery,
            assets = assets,
            queueTasks = tasks,
            storageBreakdown = storage,
            totalStorageBytes = storage.values.sum(),
            selectedAssetIds = selections.selectedAssetIds,
            isSelectionMode = selections.isSelectionMode,
            readyToDownloadItems = readyItems,
            selectedTaskIds = selections.selectedTaskIds,
            isQueueSelectionMode = selections.isQueueSelectionMode
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

    fun onSelectAllAssets(assets: List<MediaAsset>) {
        _selectedAssetIds.value = assets.map { it.id }.toSet()
        _isSelectionMode.value = true
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

    fun onExportSelectedAssets(onResult: (Int, Int) -> Unit = { _, _ -> }) {
        val ids = _selectedAssetIds.value.toList()
        viewModelScope.launch {
            var successCount = 0
            for (id in ids) {
                val res = mediaRepository.exportAsset(id)
                if (res.isSuccess) successCount++
            }
            onClearSelection()
            onResult(successCount, ids.size)
        }
    }

    fun onToggleTaskSelection(taskId: String) {
        val current = _selectedTaskIds.value.toMutableSet()
        if (current.contains(taskId)) {
            current.remove(taskId)
        } else {
            current.add(taskId)
        }
        _selectedTaskIds.value = current
        _isQueueSelectionMode.value = current.isNotEmpty()
    }

    fun onSelectAllTasks(tasks: List<DownloadTask>) {
        _selectedTaskIds.value = tasks.map { it.id }.toSet()
        _isQueueSelectionMode.value = true
    }

    fun onClearTaskSelection() {
        _selectedTaskIds.value = emptySet()
        _isQueueSelectionMode.value = false
    }

    fun onCancelSelectedTasks() {
        val ids = _selectedTaskIds.value.toList()
        viewModelScope.launch {
            for (id in ids) {
                mediaRepository.cancelDownload(id)
            }
            onClearTaskSelection()
        }
    }

    fun onRetrySelectedTasks() {
        val ids = _selectedTaskIds.value.toList()
        viewModelScope.launch {
            for (id in ids) {
                mediaRepository.retryDownload(id)
            }
            onClearTaskSelection()
        }
    }

    fun onDismissSelectedTasks() {
        val ids = _selectedTaskIds.value.toList()
        viewModelScope.launch {
            for (id in ids) {
                mediaRepository.dismissTask(id)
            }
            onClearTaskSelection()
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
