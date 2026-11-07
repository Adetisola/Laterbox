package com.laterbox.app.data.metadata

import com.laterbox.app.domain.model.ContentType

data class ExtractedMetadata(
    val title: String?,
    val description: String?,
    val siteName: String?,
    val faviconUrl: String?,
    val previewImageUrl: String?,
    val contentType: ContentType
)
