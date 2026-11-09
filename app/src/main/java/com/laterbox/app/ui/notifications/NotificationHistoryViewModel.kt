package com.laterbox.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.data.repository.NotificationHistoryRepository
import com.laterbox.app.domain.model.NotificationHistoryItem
import com.laterbox.app.ui.components.DateGrouper
import com.laterbox.app.ui.components.NotificationDateGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationHistoryUiState(
    val isLoading: Boolean = false,
    val groupedItems: List<NotificationDateGroup> = emptyList(),
    val totalCount: Int = 0,
    val unreadCount: Int = 0
)

class NotificationHistoryViewModel(
    private val repository: NotificationHistoryRepository
) : ViewModel() {

    val uiState: StateFlow<NotificationHistoryUiState> = combine(
        repository.allHistory,
        repository.unreadCount
    ) { historyList: List<NotificationHistoryItem>, unread: Int ->
        val grouped = DateGrouper.groupNotifications(historyList)
        NotificationHistoryUiState(
            isLoading = false,
            groupedItems = grouped,
            totalCount = historyList.size,
            unreadCount = unread
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationHistoryUiState(isLoading = true)
    )

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            repository.markAsRead(id)
        }
    }

    fun markAsUnread(id: Long) {
        viewModelScope.launch {
            repository.markAsUnread(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
