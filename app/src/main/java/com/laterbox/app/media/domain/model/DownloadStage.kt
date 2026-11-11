package com.laterbox.app.media.domain.model

/**
 * Fine-grained download stages for transparent progress reporting.
 */
enum class DownloadStage(val label: String, val isIndeterminate: Boolean = false) {
    QUEUED("Waiting in queue", isIndeterminate = true),
    PREPARING("Preparing download...", isIndeterminate = true),
    INITIALIZING_ENGINE("Initializing media engine...", isIndeterminate = true),
    CONNECTING("Connecting to media server...", isIndeterminate = true),
    EXTRACTING_INFO("Extracting stream information...", isIndeterminate = true),
    DOWNLOADING("Downloading media..."),
    MERGING("Merging audio and video streams...", isIndeterminate = true),
    EXTRACTING_AUDIO("Extracting audio track...", isIndeterminate = true),
    CONVERTING("Converting media format...", isIndeterminate = true),
    TAGGING_METADATA("Tagging audio metadata...", isIndeterminate = true),
    GENERATING_THUMBNAIL("Generating thumbnail...", isIndeterminate = true),
    FINALIZING("Verifying file...", isIndeterminate = true),
    SAVING_TO_LIBRARY("Saving to local library...", isIndeterminate = true),
    COMPLETED("Download complete");

    companion object {
        fun fromNameOrNull(name: String?): DownloadStage? {
            if (name.isNullOrBlank()) return null
            return try {
                valueOf(name)
            } catch (_: Exception) {
                null
            }
        }
    }
}
