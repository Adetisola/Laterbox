package com.laterbox.app.data.backup

import com.laterbox.app.domain.model.ContentType

data class BackupCollection(
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class BackupLink(
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
    val collectionName: String? = null,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class LaterboxBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appName: String = "Laterbox",
    val collections: List<BackupCollection> = emptyList(),
    val links: List<BackupLink> = emptyList()
)

data class BackupRestoreResult(
    val restoredLinksCount: Int,
    val skippedDuplicatesCount: Int,
    val restoredCollectionsCount: Int
)
