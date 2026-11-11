package com.laterbox.app.media.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.DownloadTask
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.MediaType

@Entity(
    tableName = "download_task",
    foreignKeys = [
        ForeignKey(
            entity = SavedLinkEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedLinkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("savedLinkId"),
        Index("status")
    ]
)
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val savedLinkId: Long,
    val sourceUrl: String,
    val sourcePlatform: String,
    val candidateId: String?,
    val formatSelector: String?,
    val mediaType: String,
    val qualityLabel: String?,
    val title: String?,
    val assetId: String? = null,
    val status: String,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val allowCellularOverride: Boolean = false,
    val supportsRange: Boolean = true,
    val retryCount: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val spotifyTrackId: String? = null,
    val versionTag: String? = null,
    val stage: String? = null,
    val stageDetail: String? = null
) {
    fun toDomain(): DownloadTask = DownloadTask(
        id = id,
        savedLinkId = savedLinkId,
        sourceUrl = sourceUrl,
        sourcePlatform = sourcePlatform,
        candidateId = candidateId,
        formatSelector = formatSelector,
        mediaType = try { MediaType.valueOf(mediaType) } catch (_: Exception) { MediaType.OTHER },
        qualityLabel = qualityLabel,
        title = title,
        assetId = assetId,
        status = try { DownloadState.valueOf(status) } catch (_: Exception) { DownloadState.FAILED },
        progress = progress,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        allowCellularOverride = allowCellularOverride,
        supportsRange = supportsRange,
        retryCount = retryCount,
        errorCode = errorCode?.let { try { FailureCode.valueOf(it) } catch (_: Exception) { FailureCode.UNKNOWN } },
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        spotifyTrackId = spotifyTrackId,
        versionTag = versionTag,
        stage = com.laterbox.app.media.domain.model.DownloadStage.fromNameOrNull(stage),
        stageDetail = stageDetail
    )

    companion object {
        fun fromDomain(task: DownloadTask): DownloadTaskEntity = DownloadTaskEntity(
            id = task.id,
            savedLinkId = task.savedLinkId,
            sourceUrl = task.sourceUrl,
            sourcePlatform = task.sourcePlatform,
            candidateId = task.candidateId,
            formatSelector = task.formatSelector,
            mediaType = task.mediaType.name,
            qualityLabel = task.qualityLabel,
            title = task.title,
            assetId = task.assetId,
            status = task.status.name,
            progress = task.progress,
            downloadedBytes = task.downloadedBytes,
            totalBytes = task.totalBytes,
            allowCellularOverride = task.allowCellularOverride,
            supportsRange = task.supportsRange,
            retryCount = task.retryCount,
            errorCode = task.errorCode?.name,
            errorMessage = task.errorMessage,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            artist = task.artist,
            album = task.album,
            artworkUrl = task.artworkUrl,
            spotifyTrackId = task.spotifyTrackId,
            versionTag = task.versionTag,
            stage = task.stage?.name,
            stageDetail = task.stageDetail
        )
    }
}
