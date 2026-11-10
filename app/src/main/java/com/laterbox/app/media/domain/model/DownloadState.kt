package com.laterbox.app.media.domain.model

enum class DownloadState {
    QUEUED,
    WAITING_FOR_WIFI,
    INSPECTING,
    READY,
    DOWNLOADING,
    PAUSED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    val isActive: Boolean
        get() = this == INSPECTING || this == READY || this == DOWNLOADING || this == PROCESSING
}
