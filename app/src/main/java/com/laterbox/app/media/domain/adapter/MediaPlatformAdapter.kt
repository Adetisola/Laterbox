package com.laterbox.app.media.domain.adapter

import com.laterbox.app.media.domain.model.InspectionResult

interface MediaPlatformAdapter {
    val id: String
    fun canHandle(url: String): Boolean
    suspend fun inspect(url: String): InspectionResult
}
