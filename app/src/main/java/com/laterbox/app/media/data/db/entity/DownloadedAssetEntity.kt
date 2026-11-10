package com.laterbox.app.media.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.media.domain.model.AssetFileStatus
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType

@Entity(
    tableName = "downloaded_asset",
    foreignKeys = [
        ForeignKey(
            entity = SavedLinkEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedLinkId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("savedLinkId"),
        Index("checksum"),
        Index("localPath")
    ]
)
data class DownloadedAssetEntity(
    @PrimaryKey val id: String,
    val savedLinkId: Long?,
    val sourcePlatform: String,
    val originalSourceUrl: String,
    val title: String?,
    val mimeType: String,
    val mediaType: String,
    val localPath: String,
    val exportedUri: String? = null,
    val fileStatus: String = "AVAILABLE",
    val fileSize: Long,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val qualityLabel: String? = null,
    val checksum: String,
    val createdAt: Long
) {
    fun toDomain(): MediaAsset = MediaAsset(
        id = id,
        savedLinkId = savedLinkId,
        sourcePlatform = sourcePlatform,
        originalSourceUrl = originalSourceUrl,
        title = title,
        mimeType = mimeType,
        mediaType = try { MediaType.valueOf(mediaType) } catch (_: Exception) { MediaType.OTHER },
        localPath = localPath,
        exportedUri = exportedUri,
        fileStatus = try { AssetFileStatus.valueOf(fileStatus) } catch (_: Exception) { AssetFileStatus.AVAILABLE },
        fileSize = fileSize,
        durationMs = durationMs,
        width = width,
        height = height,
        qualityLabel = qualityLabel,
        checksum = checksum,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(asset: MediaAsset): DownloadedAssetEntity = DownloadedAssetEntity(
            id = asset.id,
            savedLinkId = asset.savedLinkId,
            sourcePlatform = asset.sourcePlatform,
            originalSourceUrl = asset.originalSourceUrl,
            title = asset.title,
            mimeType = asset.mimeType,
            mediaType = asset.mediaType.name,
            localPath = asset.localPath,
            exportedUri = asset.exportedUri,
            fileStatus = asset.fileStatus.name,
            fileSize = asset.fileSize,
            durationMs = asset.durationMs,
            width = asset.width,
            height = asset.height,
            qualityLabel = asset.qualityLabel,
            checksum = asset.checksum,
            createdAt = asset.createdAt
        )
    }
}
