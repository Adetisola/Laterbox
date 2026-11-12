package com.laterbox.app.media.data.diagnostics

import androidx.annotation.Keep
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

@Keep
data class InspectionDiagnosticEntry(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val url: String,
    val adapterId: String,
    val stage: String = "single", // "stage_0_oembed", "stage_1_fast", "stage_2_complete", "single"
    val playerClient: String? = null,
    val runtimeReadyMs: Long = 0L,
    val processSpawnToOutputMs: Long = 0L,
    val ytdlpElapsedMs: Long = 0L,
    val jsonSizeBytes: Int = 0,
    val parseMs: Long = 0L,
    val dbWriteMs: Long = 0L,
    val totalMs: Long = 0L,
    val videoFormatCount: Int = 0,
    val audioFormatCount: Int = 0,
    val thumbnailCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val isWarmProcess: Boolean = false,
    val linkId: Long? = null,
    val generation: Long? = null,
    val processId: String? = null,
    val rowsFound: Int? = null,
    val shownReason: String? = null,
    val success: Boolean = true,
    val error: String? = null
) {
    fun toFormattedString(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timeStr = sdf.format(Date(timestamp))
        val engineStr = if (isWarmProcess) "Warm" else "Spawn"
        val statusStr = if (success) "SUCCESS" else "FAILED: ${sanitizeErrorMessage(error)}"
        val warningsStr = if (warnings.isNotEmpty()) "\n  Warnings:\n" + warnings.joinToString("\n") { "    - $it" } else ""
        val extraInfoList = listOfNotNull(
            linkId?.let { "LinkId: $it" },
            generation?.let { "Gen: $it" },
            processId?.let { "PID: $it" },
            rowsFound?.let { "RowsFound: $it" },
            shownReason?.let { "Reason: $it" }
        )
        val extraInfoStr = if (extraInfoList.isNotEmpty()) "\n  Context: " + extraInfoList.joinToString(" | ") else ""

        return """
            [$timeStr] $statusStr
            URL: ${sanitizeUrl(url)}
            Adapter: $adapterId | Stage: $stage | Engine: $engineStr | PlayerClient: ${playerClient ?: "default"}$extraInfoStr
            Timing (ms):
              Runtime Ready: ${runtimeReadyMs}ms
              Process Spawn to Output: ${processSpawnToOutputMs}ms
              yt-dlp Elapsed: ${ytdlpElapsedMs}ms
              Parse: ${parseMs}ms
              DB Write: ${dbWriteMs}ms
              Total: ${totalMs}ms
            Payload:
              JSON Size: ${jsonSizeBytes} bytes
              Formats: ${videoFormatCount}v, ${audioFormatCount}a, ${thumbnailCount}t$warningsStr
        """.trimIndent()
    }

    companion object {
        fun sanitizeUrl(url: String): String {
            return try {
                val uri = URI(url)
                val cleanQuery = uri.query?.split("&")?.mapNotNull { param ->
                    val key = param.substringBefore("=").lowercase()
                    if (key in listOf("sig", "token", "signature", "key", "auth", "expire", "expires", "cookie", "session", "pass", "pwd", "password", "ticket", "access_token") ||
                        key.startsWith("x-amz-") || key.startsWith("x-goog-")) {
                        "${param.substringBefore("=")}=[REDACTED]"
                    } else {
                        param
                    }
                }?.joinToString("&")
                URI(uri.scheme, uri.authority, uri.path, cleanQuery, null).toString()
            } catch (_: Throwable) {
                url.substringBefore("?")
            }
        }

        fun sanitizeErrorMessage(error: String?): String {
            if (error.isNullOrBlank()) return ""
            return error
                .replace(Regex("""(https?://[^\s\?]+)\?[^\s]+""")) { matchResult ->
                    sanitizeUrl(matchResult.value)
                }
                .replace(Regex("""(token|sig|signature|key|auth|cookie|session|x-amz-[a-zA-Z0-9_\-]+)=[a-zA-Z0-9_\-\.%]+""", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
                .replace(Regex("""(Bearer|Basic|Token)\s+[a-zA-Z0-9_\-\.]+""", RegexOption.IGNORE_CASE), "$1 [REDACTED]")
                .take(400)
        }

        fun sanitizeWarnings(stderr: String?): List<String> {
            if (stderr.isNullOrBlank()) return emptyList()
            return stderr.lines()
                .filter { it.contains("WARNING", ignoreCase = true) || it.contains("WARN", ignoreCase = true) || it.contains("ERROR", ignoreCase = true) }
                .map { line ->
                    line.replace(Regex("""(https?://[^\s\?]+)\?[^\s]+""")) { matchResult ->
                        sanitizeUrl(matchResult.value)
                    }
                    .replace(Regex("""(token|sig|signature|key|auth|cookie|session|x-amz-[a-zA-Z0-9_\-]+)=[a-zA-Z0-9_\-\.%]+""", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
                    .replace(Regex("""(Bearer|Basic|Token)\s+[a-zA-Z0-9_\-\.]+""", RegexOption.IGNORE_CASE), "$1 [REDACTED]")
                    .take(300)
                }
                .take(10)
        }
    }
}

class InspectionDiagnosticsBuffer(private val capacity: Int = 50) {
    private val buffer = LinkedList<InspectionDiagnosticEntry>()

    @Synchronized
    fun record(entry: InspectionDiagnosticEntry) {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(entry)
    }

    @Synchronized
    fun getEntries(): List<InspectionDiagnosticEntry> = buffer.toList().reversed()

    @Synchronized
    fun clear() = buffer.clear()

    @Synchronized
    fun exportAsText(): String {
        if (buffer.isEmpty()) return "No inspection diagnostics recorded yet."
        return buffer.reversed().joinToString("\n\n" + "-".repeat(40) + "\n\n") { it.toFormattedString() }
    }
}
