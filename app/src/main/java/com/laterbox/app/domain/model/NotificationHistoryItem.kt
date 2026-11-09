package com.laterbox.app.domain.model

data class NotificationHistoryItem(
    val id: Long,
    val linkId: Long?,
    val url: String,
    val title: String,
    val body: String,
    val origin: String,
    val domain: String,
    val siteName: String?,
    val previewImageUrl: String?,
    val faviconUrl: String?,
    val isRead: Boolean,
    val createdAt: Long
)
