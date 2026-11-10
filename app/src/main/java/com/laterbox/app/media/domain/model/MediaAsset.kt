package com.laterbox.app.media.domain.model

enum class AssetFileStatus {
    AVAILABLE,
    MISSING
}

data class MediaAsset(
    val id: String,
    val savedLinkId: Long?,
    val sourcePlatform: String,
    val originalSourceUrl: String,
    val title: String?,
    val mimeType: String,
    val mediaType: MediaType,
    val localPath: String,
    val exportedUri: String?,
    val fileStatus: AssetFileStatus = AssetFileStatus.AVAILABLE,
    val fileSize: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val qualityLabel: String?,
    val checksum: String,
    val createdAt: Long
)
