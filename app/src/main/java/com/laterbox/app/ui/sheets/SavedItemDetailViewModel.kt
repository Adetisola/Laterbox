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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavedItemDetailUiState(
    val link: SavedLink? = null,
    val editedTitle: String = "",
    val editedNote: String = "",
    val selectedCollectionIds: Set<Long> = emptySet(),
    val collections: List<Collection> = emptyList(),
    val isSaving: Boolean = false
) {
    val selectedCollectionId: Long?
        get() = selectedCollectionIds.firstOrNull()
}

class SavedItemDetailViewModel(
    private val linkRepository: LinkRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _currentLink = MutableStateFlow<SavedLink?>(null)
    private val _editedTitle = MutableStateFlow("")
    private val _editedNote = MutableStateFlow("")
    private val _selectedCollectionIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSaving = MutableStateFlow(false)

    val uiState: StateFlow<SavedItemDetailUiState> = combine(
        _currentLink,
        _editedTitle,
        _editedNote,
        _selectedCollectionIds,
        _isSaving,
        collectionRepository.collections
    ) { params ->
        SavedItemDetailUiState(
            link = params[0] as SavedLink?,
            editedTitle = params[1] as String,
            editedNote = params[2] as String,
            selectedCollectionIds = params[3] as Set<Long>,
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
        _selectedCollectionIds.value = link.collectionIds.toSet()
    }

    fun onTitleChange(newTitle: String) {
        _editedTitle.value = newTitle
    }

    fun onNoteChange(newNote: String) {
        _editedNote.value = newNote
    }

    fun onToggleCollection(collectionId: Long) {
        val link = _currentLink.value ?: return
        val currentIds = _selectedCollectionIds.value.toMutableSet()
        val willHave = !currentIds.contains(collectionId)
        if (willHave) {
            currentIds.add(collectionId)
        } else {
            currentIds.remove(collectionId)
        }
        _selectedCollectionIds.value = currentIds
        viewModelScope.launch {
            linkRepository.toggleLinkCollection(link.id, collectionId)
            val updatedNames = if (willHave) {
                val col = collectionRepository.getCollectionById(collectionId).first()
                if (col != null) link.collectionNames + col.name else link.collectionNames
            } else {
                val col = collectionRepository.getCollectionById(collectionId).first()
                if (col != null) link.collectionNames.filter { it != col.name } else link.collectionNames
            }
            _currentLink.value = link.copy(
                collectionIds = currentIds.toList(),
                collectionNames = updatedNames
            )
        }
    }

    fun onCreateAndAssignCollection(name: String) {
        val link = _currentLink.value ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val newId = collectionRepository.createCollection(trimmed)
            linkRepository.toggleLinkCollection(link.id, newId)
            val newSet = _selectedCollectionIds.value + newId
            _selectedCollectionIds.value = newSet
            _currentLink.value = link.copy(
                collectionIds = newSet.toList(),
                collectionNames = link.collectionNames + trimmed
            )
        }
    }

    fun onSaveEdits(onSuccess: () -> Unit) {
        val link = _currentLink.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val updated = link.copy(
                    title = if (_editedTitle.value.isNotBlank()) _editedTitle.value.trim() else link.domain,
                    personalNote = _editedNote.value.trim().ifEmpty { null },
                    collectionIds = _selectedCollectionIds.value.toList()
                )
                linkRepository.updateLink(updated)
                linkRepository.setLinkCollections(link.id, _selectedCollectionIds.value.toList())
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

    fun deleteWithUndo(onDeleted: (SavedLink) -> Unit) {
        val link = _currentLink.value ?: return
        viewModelScope.launch {
            linkRepository.deleteLink(link.id)
            _currentLink.value = null
            onDeleted(link)
        }
    }

    fun onRetryMetadata() {
        val link = _currentLink.value ?: return
        viewModelScope.launch {
            _currentLink.value = link.copy(metadataStatus = MetadataStatus.FETCHING)
            linkRepository.retrySingleLink(link.id)
            val refreshed = linkRepository.getLinkById(link.id)
            refreshed.collect { updated ->
                if (updated != null) {
                    _currentLink.value = updated
                    if (_editedTitle.value == link.title || _editedTitle.value == link.domain) {
                        _editedTitle.value = updated.title
                    }
                }
            }
        }
    }

    fun onSetReminder(reminderAt: Long?) {
        val link = _currentLink.value ?: return
        viewModelScope.launch {
            linkRepository.setReminder(link.id, reminderAt)
            _currentLink.value = link.copy(reminderAt = reminderAt, nudgeDismissed = false)
        }
    }
}
