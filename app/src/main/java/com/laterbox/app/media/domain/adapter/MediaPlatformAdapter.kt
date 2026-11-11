package com.laterbox.app.media.domain.adapter

import com.laterbox.app.media.domain.model.InspectionResult

interface MediaPlatformAdapter {
    val id: String
    val inspectionTimeoutMs: Long get() = 30_000L
    fun canHandle(url: String): Boolean
    suspend fun inspect(url: String): InspectionResult
    suspend fun inspect(url: String, processId: String?): InspectionResult = inspect(url)
}
