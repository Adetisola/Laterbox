package com.laterbox.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_history",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["is_read"]),
        Index(value = ["link_id"])
    ]
)
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "link_id")
    val linkId: Long? = null,
    val url: String,
    val title: String,
    val body: String,
    val origin: String,
    val domain: String,
    @ColumnInfo(name = "site_name")
    val siteName: String? = null,
    @ColumnInfo(name = "preview_image_url")
    val previewImageUrl: String? = null,
    @ColumnInfo(name = "favicon_url")
    val faviconUrl: String? = null,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
