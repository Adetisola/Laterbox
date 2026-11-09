package com.laterbox.app.data.repository

import com.laterbox.app.data.local.dao.NotificationHistoryDao
import com.laterbox.app.data.local.entity.NotificationHistoryEntity
import com.laterbox.app.domain.model.NotificationHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationHistoryRepository(
    private val dao: NotificationHistoryDao
) {

    val allHistory: Flow<List<NotificationHistoryItem>> = dao.getAllHistory().map { list ->
        list.map { it.toDomain() }
    }

    val unreadCount: Flow<Int> = dao.getUnreadCount()

    suspend fun recordNotification(
        url: String,
        title: String,
        body: String,
        origin: String,
        domain: String = "",
        linkId: Long? = null,
        siteName: String? = null,
        previewImageUrl: String? = null,
        faviconUrl: String? = null,
        isRead: Boolean = false,
        createdAt: Long = System.currentTimeMillis()
    ): Long {
        val entity = NotificationHistoryEntity(
            linkId = linkId,
            url = url,
            title = title,
            body = body,
            origin = origin,
            domain = domain,
            siteName = siteName,
            previewImageUrl = previewImageUrl,
            faviconUrl = faviconUrl,
            isRead = isRead,
            createdAt = createdAt
        )
        return dao.insert(entity)
    }

    suspend fun getById(id: Long): NotificationHistoryItem? {
        return dao.getById(id)?.toDomain()
    }

    suspend fun markAsRead(id: Long) {
        dao.markAsRead(id)
    }

    suspend fun markAsUnread(id: Long) {
        dao.markAsUnread(id)
    }

    suspend fun markAllAsRead() {
        dao.markAllAsRead()
    }

    suspend fun markReadByLinkId(linkId: Long) {
        dao.markReadByLinkId(linkId)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    private fun NotificationHistoryEntity.toDomain(): NotificationHistoryItem {
        return NotificationHistoryItem(
            id = id,
            linkId = linkId,
            url = url,
            title = title,
            body = body,
            origin = origin,
            domain = domain,
            siteName = siteName,
            previewImageUrl = previewImageUrl,
            faviconUrl = faviconUrl,
            isRead = isRead,
            createdAt = createdAt
        )
    }
}
