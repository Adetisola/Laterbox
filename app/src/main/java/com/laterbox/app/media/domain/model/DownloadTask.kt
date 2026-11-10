package com.laterbox.app.media.domain.model

data class DownloadTask(
    val id: String,
    val savedLinkId: Long,
    val sourceUrl: String,
    val sourcePlatform: String,
    val candidateId: String?,
    val formatSelector: String?,
    val mediaType: MediaType,
    val qualityLabel: String?,
    val title: String?,
    val assetId: String?,
    val status: DownloadState,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val allowCellularOverride: Boolean = false,
    val supportsRange: Boolean = true,
    val retryCount: Int = 0,
    val errorCode: FailureCode? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
