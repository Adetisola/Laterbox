package com.laterbox.app.ui.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.data.repository.CollectionRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.domain.model.Collection
import com.laterbox.app.domain.model.MetadataStatus
import com.laterbox.app.domain.model.SavedLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavedItemDetailUiState(
    val link: SavedLink? = null,
    val editedTitle: String = "",
    val editedNote: String = "",
    val selectedCollectionId: Long? = null,
    val collections: List<Collection> = emptyList(),
    val isSaving: Boolean = false
)

class SavedItemDetailViewModel(
    private val linkRepository: LinkRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _currentLink = MutableStateFlow<SavedLink?>(null)
    private val _editedTitle = MutableStateFlow("")
    private val _editedNote = MutableStateFlow("")
    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    private val _isSaving = MutableStateFlow(false)

    val uiState: StateFlow<SavedItemDetailUiState> = combine(
        _currentLink,
        _editedTitle,
        _editedNote,
        _selectedCollectionId,
        _isSaving,
        collectionRepository.collections
    ) { params ->
        SavedItemDetailUiState(
            link = params[0] as SavedLink?,
            editedTitle = params[1] as String,
            editedNote = params[2] as String,
            selectedCollectionId = params[3] as Long?,
            isSaving = params[4] as Boolean,
            collections = (params[5] as List<*>).filterIsInstance<Collection>()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavedItemDetailUiState()
    )

    fun setLink(link: SavedLink) {
        _currentLink.value = link
        _editedTitle.value = link.title
        _editedNote.value = link.personalNote.orEmpty()
        _selectedCollectionId.value = link.collectionId
    }

    fun onTitleChange(newTitle: String) {
        _editedTitle.value = newTitle
    }

    fun onNoteChange(newNote: String) {
        _editedNote.value = newNote
    }

    fun onCollectionChange(collectionId: Long?) {
        _selectedCollectionId.value = collectionId
    }

    fun onSaveEdits(onSuccess: () -> Unit) {
        val link = _currentLink.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val updated = link.copy(
                    title = if (_editedTitle.value.isNotBlank()) _editedTitle.value.trim() else link.domain,
                    personalNote = _editedNote.value.trim().ifEmpty { null },
                    collectionId = _selectedCollectionId.value
                )
                linkRepository.updateLink(updated)
                _currentLink.value = updated
                onSuccess()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onToggleRead() {
        val link = _currentLink.value ?: return
        viewModelScope.launch {
            val updated = link.copy(isRead = !link.isRead)
            linkRepository.updateLink(updated)
            _currentLink.value = updated
        }
    }

    fun onToggleArchive(onComplete: () -> Unit) {
        val link = _currentLink.value ?: return
        viewModelScope.launch {
            linkRepository.setArchived(link.id, !link.isArchived)
            onComplete()
        }
    }

    fun onDelete(onDeleted: () -> Unit) {
        val link = _currentLink.value ?: return
        viewModelScope.launch {
            linkRepository.deleteLink(link.id)
            _currentLink.value = null
            onDeleted()
        }
    }

    fun onRetryMetadata() {
        val link = _currentLink.value ?: return
        viewModelScope.launch {
            val updated = link.copy(metadataStatus = MetadataStatus.PENDING)
            linkRepository.updateLink(updated)
            _currentLink.value = updated
        }
    }
}
