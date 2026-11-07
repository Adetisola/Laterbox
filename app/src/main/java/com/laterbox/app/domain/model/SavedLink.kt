package com.laterbox.app.domain.model

data class SavedLink(
    val id: Long = 0,
    val originalUrl: String,
    val normalizedUrl: String,
    val title: String,
    val description: String? = null,
    val siteName: String? = null,
    val domain: String,
    val faviconUrl: String? = null,
    val previewImageUrl: String? = null,
    val contentType: ContentType = ContentType.WEBSITE,
    val personalNote: String? = null,
    val collectionId: Long? = null,
    val collectionName: String? = null,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val metadataStatus: MetadataStatus = MetadataStatus.PENDING,
    val retryCount: Int = 0,
    val lastFetchedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
