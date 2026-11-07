package com.laterbox.app.ui.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.data.repository.CollectionRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.domain.model.Collection
import com.laterbox.app.domain.model.SavedLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SaveLinkUiState(
    val url: String = "",
    val title: String = "",
    val personalNote: String = "",
    val selectedCollectionId: Long? = null,
    val collections: List<Collection> = emptyList(),
    val duplicateLink: SavedLink? = null,
    val isSaving: Boolean = false,
    val isSavedSuccess: Boolean = false
) {
    val canSave: Boolean
        get() = url.isNotBlank() && !isSaving
}

class SaveLinkViewModel(
    private val linkRepository: LinkRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _url = MutableStateFlow("")
    private val _title = MutableStateFlow("")
    private val _personalNote = MutableStateFlow("")
    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    private val _duplicateLink = MutableStateFlow<SavedLink?>(null)
    private val _isSaving = MutableStateFlow(false)
    private val _isSavedSuccess = MutableStateFlow(false)

    private var duplicateCheckJob: Job? = null

    val uiState: StateFlow<SaveLinkUiState> = combine(
        _url,
        _title,
        _personalNote,
        _selectedCollectionId,
        _duplicateLink,
        _isSaving,
        _isSavedSuccess,
        collectionRepository.collections
    ) { params ->
        SaveLinkUiState(
            url = params[0] as String,
            title = params[1] as String,
            personalNote = params[2] as String,
            selectedCollectionId = params[3] as Long?,
            duplicateLink = params[4] as SavedLink?,
            isSaving = params[5] as Boolean,
            isSavedSuccess = params[6] as Boolean,
            collections = (params[7] as List<*>).filterIsInstance<Collection>()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SaveLinkUiState()
    )

    fun onUrlChange(newUrl: String) {
        _url.value = newUrl
        checkDuplicate(newUrl)
    }

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle
    }

    fun onPersonalNoteChange(newNote: String) {
        _personalNote.value = newNote
    }

    fun onSelectCollection(collectionId: Long?) {
        _selectedCollectionId.value = collectionId
    }

    private fun checkDuplicate(rawUrl: String) {
        duplicateCheckJob?.cancel()
        val trimmed = rawUrl.trim()
        if (trimmed.length < 5) {
            _duplicateLink.value = null
            return
        }

        duplicateCheckJob = viewModelScope.launch {
            delay(250) // Debounce
            val formatted = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
            val existing = linkRepository.findDuplicate(formatted)
            _duplicateLink.value = existing
        }
    }

    fun onSave(onSuccess: () -> Unit) {
        val rawUrl = _url.value.trim()
        if (rawUrl.isBlank()) return

        val finalUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            "https://$rawUrl"
        } else {
            rawUrl
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val existing = _duplicateLink.value
                if (existing != null) {
                    // Update existing duplicate
                    linkRepository.updateLink(
                        existing.copy(
                            title = if (_title.value.isNotBlank()) _title.value.trim() else existing.title,
                            personalNote = if (_personalNote.value.isNotBlank()) _personalNote.value.trim() else existing.personalNote,
                            collectionId = _selectedCollectionId.value ?: existing.collectionId,
                            isArchived = false // Restores to inbox if it was archived
                        )
                    )
                } else {
                    linkRepository.saveLink(
                        originalUrl = finalUrl,
                        title = _title.value.ifBlank { null },
                        personalNote = _personalNote.value.ifBlank { null },
                        collectionId = _selectedCollectionId.value
                    )
                }
                _isSavedSuccess.value = true
                onSuccess()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun reset() {
        _url.value = ""
        _title.value = ""
        _personalNote.value = ""
        _selectedCollectionId.value = null
        _duplicateLink.value = null
        _isSaving.value = false
        _isSavedSuccess.value = false
    }
}
