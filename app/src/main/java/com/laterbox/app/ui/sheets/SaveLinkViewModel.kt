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
    val urlError: String? = null,
    val title: String = "",
    val personalNote: String = "",
    val selectedCollectionIds: Set<Long> = emptySet(),
    val collections: List<Collection> = emptyList(),
    val duplicateLink: SavedLink? = null,
    val isSaving: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val reminderAt: Long? = null,
    val maxNoteLength: Int = 500,
    val maxTitleLength: Int = 200
) {
    val selectedCollectionId: Long?
        get() = selectedCollectionIds.firstOrNull()

    val canSave: Boolean
        get() = !isSaving
}

class SaveLinkViewModel(
    private val linkRepository: LinkRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _url = MutableStateFlow("")
    private val _urlError = MutableStateFlow<String?>(null)
    private val _title = MutableStateFlow("")
    private val _personalNote = MutableStateFlow("")
    private val _selectedCollectionIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _duplicateLink = MutableStateFlow<SavedLink?>(null)
    private val _isSaving = MutableStateFlow(false)
    private val _isSavedSuccess = MutableStateFlow(false)
    private val _reminderAt = MutableStateFlow<Long?>(null)

    private var duplicateCheckJob: Job? = null

    val uiState: StateFlow<SaveLinkUiState> = combine(
        _url,
        _urlError,
        _title,
        _personalNote,
        _selectedCollectionIds,
        _duplicateLink,
        _isSaving,
        _isSavedSuccess,
        _reminderAt,
        collectionRepository.collections
    ) { params ->
        SaveLinkUiState(
            url = params[0] as String,
            urlError = params[1] as String?,
            title = params[2] as String,
            personalNote = params[3] as String,
            selectedCollectionIds = params[4] as Set<Long>,
            duplicateLink = params[5] as SavedLink?,
            isSaving = params[6] as Boolean,
            isSavedSuccess = params[7] as Boolean,
            reminderAt = params[8] as Long?,
            collections = (params[9] as List<*>).filterIsInstance<Collection>()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SaveLinkUiState()
    )

    fun onUrlChange(newUrl: String) {
        _url.value = newUrl
        if (_urlError.value != null) {
            _urlError.value = null
        }
        checkDuplicate(newUrl)
    }

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle.take(200)
    }

    fun onPersonalNoteChange(newNote: String) {
        _personalNote.value = newNote.take(500)
    }

    fun onSelectCollection(collectionId: Long?) {
        if (collectionId == null) {
            _selectedCollectionIds.value = emptySet()
        } else {
            onToggleCollection(collectionId)
        }
    }

    fun onToggleCollection(collectionId: Long) {
        val current = _selectedCollectionIds.value.toMutableSet()
        if (current.contains(collectionId)) {
            current.remove(collectionId)
        } else {
            current.add(collectionId)
        }
        _selectedCollectionIds.value = current
    }

    fun onCreateCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val newId = collectionRepository.createCollection(trimmed)
            _selectedCollectionIds.value = _selectedCollectionIds.value + newId
        }
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

    fun onReminderAtChange(reminderAt: Long?) {
        _reminderAt.value = reminderAt
    }

    fun onSave(onSuccess: () -> Unit) {
        val rawUrl = _url.value.trim()
        if (rawUrl.isBlank()) {
            _urlError.value = "Please enter a website address"
            return
        }

        if (!rawUrl.contains(".") && !rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
            _urlError.value = "Please enter a valid website address"
            return
        }

        _urlError.value = null

        val finalUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            "https://$rawUrl"
        } else {
            rawUrl
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val existing = _duplicateLink.value
                val chosenColIds = _selectedCollectionIds.value.toList()
                val chosenReminder = _reminderAt.value
                if (existing != null) {
                    val mergedColIds = (existing.collectionIds + chosenColIds).distinct()
                    linkRepository.updateLink(
                        existing.copy(
                            title = if (_title.value.isNotBlank()) _title.value.trim() else existing.title,
                            personalNote = if (_personalNote.value.isNotBlank()) _personalNote.value.trim() else existing.personalNote,
                            collectionIds = mergedColIds,
                            collectionId = mergedColIds.firstOrNull(),
                            reminderAt = chosenReminder ?: existing.reminderAt,
                            isArchived = false // Restores to inbox if it was archived
                        )
                    )
                    linkRepository.setLinkCollections(existing.id, mergedColIds)
                    if (chosenReminder != null) {
                        linkRepository.setReminder(existing.id, chosenReminder)
                    }
                } else {
                    linkRepository.saveLink(
                        originalUrl = finalUrl,
                        title = _title.value.ifBlank { null },
                        personalNote = _personalNote.value.ifBlank { null },
                        collectionIds = chosenColIds,
                        reminderAt = chosenReminder
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
        _urlError.value = null
        _title.value = ""
        _personalNote.value = ""
        _selectedCollectionIds.value = emptySet()
        _duplicateLink.value = null
        _reminderAt.value = null
        _isSaving.value = false
        _isSavedSuccess.value = false
    }
}

