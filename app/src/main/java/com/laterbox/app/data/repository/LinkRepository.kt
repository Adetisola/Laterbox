package com.laterbox.app.data.repository

import com.laterbox.app.data.local.dao.SavedLinkDao
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.data.local.entity.SavedLinkWithCollection
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.MetadataStatus
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.domain.util.UrlNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LinkRepository(private val savedLinkDao: SavedLinkDao) {

    val inboxLinks: Flow<List<SavedLink>> = savedLinkDao.getInboxLinks().map { list ->
        list.map { it.toDomain() }
    }

    val archivedLinks: Flow<List<SavedLink>> = savedLinkDao.getArchivedLinks().map { list ->
        list.map { it.toDomain() }
    }

    fun getLinksByCollection(collectionId: Long): Flow<List<SavedLink>> {
        return savedLinkDao.getLinksByCollection(collectionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun searchLinks(query: String): Flow<List<SavedLink>> {
        return savedLinkDao.searchLinks(query.trim()).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun findDuplicate(rawUrl: String): SavedLink? {
        val normalized = UrlNormalizer.normalize(rawUrl)
        if (normalized.isEmpty()) return null
        return savedLinkDao.findLinkByNormalizedUrl(normalized)?.toDomain()
    }

    fun getLinkById(id: Long): Flow<SavedLink?> {
        return savedLinkDao.getLinkById(id).map { it?.toDomain() }
    }

    suspend fun saveLink(
        originalUrl: String,
        title: String? = null,
        personalNote: String? = null,
        collectionId: Long? = null,
        contentType: ContentType = ContentType.WEBSITE
    ): Long {
        val trimmedUrl = originalUrl.trim()
        val normalized = UrlNormalizer.normalize(trimmedUrl)
        val domain = UrlNormalizer.extractDomain(trimmedUrl)
        val resolvedTitle = if (!title.isNullOrBlank()) title.trim() else domain

        val entity = SavedLinkEntity(
            originalUrl = trimmedUrl,
            normalizedUrl = normalized,
            title = resolvedTitle,
            domain = domain,
            contentType = contentType,
            personalNote = personalNote?.trim()?.ifEmpty { null },
            collectionId = collectionId,
            isRead = false,
            isArchived = false,
            metadataStatus = MetadataStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return savedLinkDao.insertLink(entity)
    }

    suspend fun updateLink(link: SavedLink) {
        val entity = SavedLinkEntity(
            id = link.id,
            originalUrl = link.originalUrl,
            normalizedUrl = link.normalizedUrl,
            title = link.title,
            description = link.description,
            siteName = link.siteName,
            domain = link.domain,
            faviconUrl = link.faviconUrl,
            previewImageUrl = link.previewImageUrl,
            contentType = link.contentType,
            personalNote = link.personalNote,
            collectionId = link.collectionId,
            isRead = link.isRead,
            isArchived = link.isArchived,
            metadataStatus = link.metadataStatus,
            retryCount = link.retryCount,
            lastFetchedAt = link.lastFetchedAt,
            createdAt = link.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        savedLinkDao.updateLink(entity)
    }

    suspend fun deleteLink(id: Long) {
        savedLinkDao.deleteLink(id)
    }

    suspend fun setArchived(id: Long, isArchived: Boolean) {
        savedLinkDao.setArchived(id, isArchived)
    }

    suspend fun setRead(id: Long, isRead: Boolean) {
        savedLinkDao.setRead(id, isRead)
    }

    private fun SavedLinkWithCollection.toDomain(): SavedLink {
        return SavedLink(
            id = link.id,
            originalUrl = link.originalUrl,
            normalizedUrl = link.normalizedUrl,
            title = link.title,
            description = link.description,
            siteName = link.siteName,
            domain = link.domain,
            faviconUrl = link.faviconUrl,
            previewImageUrl = link.previewImageUrl,
            contentType = link.contentType,
            personalNote = link.personalNote,
            collectionId = link.collectionId,
            collectionName = collection?.name,
            isRead = link.isRead,
            isArchived = link.isArchived,
            metadataStatus = link.metadataStatus,
            retryCount = link.retryCount,
            lastFetchedAt = link.lastFetchedAt,
            createdAt = link.createdAt,
            updatedAt = link.updatedAt
        )
    }
}
