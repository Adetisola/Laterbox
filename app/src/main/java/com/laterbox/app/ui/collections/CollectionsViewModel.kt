package com.laterbox.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.data.repository.CollectionRepository
import com.laterbox.app.domain.model.Collection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollectionsViewModel(
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _showCreateDialog = MutableStateFlow(false)
    private val _collectionToRename = MutableStateFlow<Collection?>(null)
    private val _collectionToDelete = MutableStateFlow<Collection?>(null)

    val uiState: StateFlow<CollectionsUiState> = combine(
        collectionRepository.collectionsWithCounts,
        _showCreateDialog,
        _collectionToRename,
        _collectionToDelete
    ) { collections, showCreate, toRename, toDelete ->
        CollectionsUiState(
            collections = collections,
            isLoading = false,
            showCreateDialog = showCreate,
            collectionToRename = toRename,
            collectionToDelete = toDelete
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CollectionsUiState(isLoading = true)
    )

    fun onOpenCreateDialog() {
        _showCreateDialog.value = true
    }

    fun onCloseCreateDialog() {
        _showCreateDialog.value = false
    }

    fun onCreateCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                collectionRepository.createCollection(trimmed)
                _showCreateDialog.value = false
            } catch (e: Exception) {
                // Ignore unique constraint or duplicate
            }
        }
    }

    fun onOpenRenameDialog(collection: Collection) {
        _collectionToRename.value = collection
    }

    fun onCloseRenameDialog() {
        _collectionToRename.value = null
    }

    fun onRenameCollection(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                collectionRepository.updateCollection(id, trimmed)
                _collectionToRename.value = null
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun onOpenDeleteDialog(collection: Collection) {
        _collectionToDelete.value = collection
    }

    fun onCloseDeleteDialog() {
        _collectionToDelete.value = null
    }

    fun onDeleteCollection(id: Long) {
        viewModelScope.launch {
            collectionRepository.deleteCollection(id)
            _collectionToDelete.value = null
        }
    }
}
