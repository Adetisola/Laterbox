package com.laterbox.app.media.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionDiagnosticsBufferTest {

    @Test
    fun buffer_circularCapacity_retainsLast50Entries() {
        val buffer = InspectionDiagnosticsBuffer(capacity = 50)

        for (i in 1..60) {
            buffer.record(
                InspectionDiagnosticEntry(
                    id = "entry_$i",
                    url = "https://example.com/video_$i",
                    adapterId = "yt-dlp",
                    stage = "stage_1_fast",
                    playerClient = "visionos",
                    runtimeReadyMs = 200L,
                    processSpawnToOutputMs = 300L,
                    ytdlpElapsedMs = 500L,
                    parseMs = 10L,
                    dbWriteMs = 5L,
                    totalMs = 600L,
                    videoFormatCount = 5,
                    audioFormatCount = 2,
                    thumbnailCount = 1,
                    isWarmProcess = true,
                    success = true
                )
            )
        }

        val entries = buffer.getEntries()
        assertEquals(50, entries.size)
        // getEntries() is reversed (newest first)
        assertEquals("entry_60", entries.first().id)
        assertEquals("entry_11", entries.last().id)
    }

    @Test
    fun buffer_sanitizesUrls_redactsSensitiveQueryParams() {
        val sanitized = InspectionDiagnosticEntry.sanitizeUrl(
            "https://instagram.com/reel/abc123xyz/?token=SECRET_TOKEN&sig=SIG_VALUE&key=SECRET_KEY&public=123"
        )
        assertFalse(sanitized.contains("SECRET_TOKEN"))
        assertFalse(sanitized.contains("SIG_VALUE"))
        assertFalse(sanitized.contains("SECRET_KEY"))
        assertTrue(sanitized.contains("public=123"))
        assertTrue(sanitized.contains("token=[REDACTED]"))

        val buffer = InspectionDiagnosticsBuffer(capacity = 10)
        buffer.record(
            InspectionDiagnosticEntry(
                id = "test_entry_1",
                url = "https://youtube.com/watch?v=123&token=SECRET_TOKEN",
                adapterId = "yt-dlp",
                stage = "stage_1_fast",
                totalMs = 500L,
                success = true
            )
        )

        val report = buffer.exportAsText()
        assertTrue(report.contains("yt-dlp"))
        assertTrue(report.contains("stage_1_fast"))
        assertFalse(report.contains("SECRET_TOKEN"))
    }
}
