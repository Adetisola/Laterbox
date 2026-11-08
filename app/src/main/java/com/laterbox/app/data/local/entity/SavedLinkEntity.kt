package com.laterbox.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.MetadataStatus

@Entity(
    tableName = "saved_links",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["normalized_url"], unique = true),
        Index(value = ["collection_id", "created_at"]),
        Index(value = ["is_archived", "created_at"]),
        Index(value = ["is_archived", "updated_at"]),
        Index(value = ["metadata_status"])
    ]
)
data class SavedLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "original_url")
    val originalUrl: String,
    @ColumnInfo(name = "normalized_url")
    val normalizedUrl: String,
    val title: String,
    val description: String? = null,
    @ColumnInfo(name = "site_name")
    val siteName: String? = null,
    val domain: String,
    @ColumnInfo(name = "favicon_url")
    val faviconUrl: String? = null,
    @ColumnInfo(name = "preview_image_url")
    val previewImageUrl: String? = null,
    @ColumnInfo(name = "content_type")
    val contentType: ContentType = ContentType.WEBSITE,
    @ColumnInfo(name = "personal_note")
    val personalNote: String? = null,
    @ColumnInfo(name = "collection_id")
    val collectionId: Long? = null,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "metadata_status")
    val metadataStatus: MetadataStatus = MetadataStatus.PENDING,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_fetched_at")
    val lastFetchedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
