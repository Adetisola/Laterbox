package com.laterbox.app.media.domain.model

sealed interface AcquisitionSource {
    data class DirectHttp(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val mimeType: String? = null
    ) : AcquisitionSource

    data class YtDlp(
        val url: String,
        val formatSelector: String,
        val expectedExt: String = "mp4"
    ) : AcquisitionSource
}
