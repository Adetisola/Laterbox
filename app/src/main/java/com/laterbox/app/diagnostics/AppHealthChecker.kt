package com.laterbox.app.diagnostics

import android.app.AlarmManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationManagerCompat
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState
import com.laterbox.app.media.data.adapters.ytdlp.QuickJsHelper
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class AppHealthStatus {
    HEALTHY,
    ATTENTION_NEEDED,
    CRITICAL
}

enum class IssueSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class AppHealthIssue(
    val id: String,
    val severity: IssueSeverity,
    val title: String,
    val summary: String,
    val suggestedFix: String
)

data class AppHealthReport(
    val checkedAt: Long,
    val ytDlpStateLabel: String,
    val ytDlpVersion: String?,
    val ffmpegStateLabel: String,
    val quickJsAvailable: Boolean,
    val failedInspectionsCount: Int,
    val failedDownloadsCount: Int,
    val availableStorageBytes: Long,
    val isNetworkConnected: Boolean,
    val isNetworkValidated: Boolean,
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val issues: List<AppHealthIssue>,
    val overallStatus: AppHealthStatus
) {
    fun availableStorageMb(): Long = availableStorageBytes / (1024L * 1024L)

    fun toHumanSummary(): String = buildString {
        appendLine("Laterbox App Health: ${overallStatus.name.replace('_', ' ')}")
        appendLine("- yt-dlp engine: $ytDlpStateLabel${ytDlpVersion?.let { " (v$it)" } ?: ""}")
        appendLine("- FFmpeg merger: $ffmpegStateLabel")
        appendLine("- QuickJS runtime: ${if (quickJsAvailable) "Available" else "Missing"}")
        appendLine("- Network: ${when { isNetworkValidated -> "Connected & validated"; isNetworkConnected -> "Connected (unvalidated)"; else -> "Offline" }}")
        appendLine("- Free internal storage: ${availableStorageMb()} MB")
        appendLine("- Notifications permission: ${if (notificationsEnabled) "Enabled" else "Disabled"}")
        appendLine("- Exact alarm scheduling: ${if (exactAlarmsAllowed) "Allowed" else "Restricted"}")
        appendLine("- Failed inspections: $failedInspectionsCount | Failed downloads: $failedDownloadsCount")
        if (issues.isNotEmpty()) {
            appendLine("\nDetected issues (${issues.size}):")
            issues.forEach { issue ->
                appendLine("- [${issue.severity}] ${issue.title}: ${issue.summary} (Fix: ${issue.suggestedFix})")
            }
        } else {
            appendLine("\nAll core subsystems look healthy.")
        }
    }.trim()
}

class AppHealthChecker(
    private val appContext: Context,
    private val database: LaterboxDatabase,
    private val runtimeManager: YtDlpRuntimeManager,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    suspend fun runCheck(
        overrideStorageBytes: Long? = null,
        overrideNetworkConnected: Boolean? = null,
        overrideNetworkValidated: Boolean? = null,
        overrideNotificationsEnabled: Boolean? = null,
        overrideExactAlarmsAllowed: Boolean? = null,
        overrideQuickJsAvailable: Boolean? = null
    ): AppHealthReport = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val ytDlpState = runtimeManager.ytDlpState.value
        val ffmpegState = runtimeManager.ffmpegState.value

        val ytDlpVersion = when (ytDlpState) {
            is YtDlpInitState.Initialized -> ytDlpState.version ?: prefs.ytDlpVersion.ifBlank { null }
            else -> prefs.ytDlpVersion.ifBlank { null }
        }
        val quickJsAvailable = overrideQuickJsAvailable ?: (QuickJsHelper.getQuickJsExecutable(appContext) != null)
        val failedInspections = try {
            database.mediaInspectionDao().countFailedOrNeedsAuth()
        } catch (_: Throwable) { 0 }
        val failedDownloads = try {
            database.downloadTaskDao().countFailedTasks()
        } catch (_: Throwable) { 0 }

        val availableBytes = overrideStorageBytes ?: try {
            StatFs(appContext.filesDir.absolutePath).availableBytes
        } catch (_: Throwable) {
            0L
        }

        val (netConnected, netValidated) = if (overrideNetworkConnected != null && overrideNetworkValidated != null) {
            overrideNetworkConnected to overrideNetworkValidated
        } else {
            inspectConnectivity()
        }

        val notificationsEnabled = overrideNotificationsEnabled ?: try {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        } catch (_: Throwable) { true }

        val exactAlarmsAllowed = overrideExactAlarmsAllowed ?: inspectExactAlarmsAllowed()

        val issues = evaluateIssues(
            ytDlpState = ytDlpState,
            ffmpegState = ffmpegState,
            quickJsAvailable = quickJsAvailable,
            failedInspections = failedInspections,
            failedDownloads = failedDownloads,
            availableBytes = availableBytes,
            netConnected = netConnected,
            netValidated = netValidated,
            notificationsEnabled = notificationsEnabled,
            exactAlarmsAllowed = exactAlarmsAllowed
        )

        val overall = when {
            issues.any { it.severity == IssueSeverity.CRITICAL } -> AppHealthStatus.CRITICAL
            issues.any { it.severity == IssueSeverity.WARNING } -> AppHealthStatus.ATTENTION_NEEDED
            else -> AppHealthStatus.HEALTHY
        }

        AppHealthReport(
            checkedAt = System.currentTimeMillis(),
            ytDlpStateLabel = ytDlpState.name,
            ytDlpVersion = ytDlpVersion,
            ffmpegStateLabel = ffmpegState.name,
            quickJsAvailable = quickJsAvailable,
            failedInspectionsCount = failedInspections,
            failedDownloadsCount = failedDownloads,
            availableStorageBytes = availableBytes,
            isNetworkConnected = netConnected,
            isNetworkValidated = netValidated,
            notificationsEnabled = notificationsEnabled,
            exactAlarmsAllowed = exactAlarmsAllowed,
            issues = issues,
            overallStatus = overall
        )
    }

    private fun inspectConnectivity(): Pair<Boolean, Boolean> {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false to false
            val active = cm.activeNetwork ?: return false to false
            val caps = cm.getNetworkCapabilities(active) ?: return false to false
            val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            internet to validated
        } catch (_: Throwable) {
            false to false
        }
    }

    private fun inspectExactAlarmsAllowed(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                am?.canScheduleExactAlarms() ?: true
            } catch (_: Throwable) {
                true
            }
        } else {
            true
        }
    }

    companion object {
        const val LOW_STORAGE_WARNING_BYTES = 200L * 1024L * 1024L
        const val LOW_STORAGE_CRITICAL_BYTES = 50L * 1024L * 1024L

        fun evaluateIssues(
            ytDlpState: YtDlpInitState,
            ffmpegState: FfmpegInitState,
            quickJsAvailable: Boolean,
            failedInspections: Int,
            failedDownloads: Int,
            availableBytes: Long,
            netConnected: Boolean,
            netValidated: Boolean,
            notificationsEnabled: Boolean,
            exactAlarmsAllowed: Boolean
        ): List<AppHealthIssue> {
            val issues = mutableListOf<AppHealthIssue>()

            if (ytDlpState is YtDlpInitState.Failed) {
                issues += AppHealthIssue(
                    id = "ytdlp_failed",
                    severity = IssueSeverity.CRITICAL,
                    title = "Media extractor failed to initialize",
                    summary = ytDlpState.error.category.userDescription,
                    suggestedFix = "Open Settings → Media & Downloads to inspect runtime diagnostics or reinitialize yt-dlp."
                )
            }
            if (ffmpegState is FfmpegInitState.Failed) {
                issues += AppHealthIssue(
                    id = "ffmpeg_failed",
                    severity = IssueSeverity.WARNING,
                    title = "FFmpeg media processor failed",
                    summary = ffmpegState.error.category.userDescription,
                    suggestedFix = "High-resolution video+audio merging may fail until FFmpeg is reinitialized."
                )
            }
            if (!quickJsAvailable && !Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                issues += AppHealthIssue(
                    id = "quickjs_missing",
                    severity = IssueSeverity.WARNING,
                    title = "QuickJS runtime not detected",
                    summary = "YouTube signature solving may be slower or incomplete without libqjs.so.",
                    suggestedFix = "Verify you installed the matching ABI release APK from Settings → About."
                )
            }
            if (availableBytes in 1 until LOW_STORAGE_CRITICAL_BYTES) {
                issues += AppHealthIssue(
                    id = "storage_critical",
                    severity = IssueSeverity.CRITICAL,
                    title = "Critically low internal storage",
                    summary = "Only ${availableBytes / (1024L * 1024L)} MB free. Downloads and media extraction need temporary workspace.",
                    suggestedFix = "Clear space or export and delete finished downloads in Downloads."
                )
            } else if (availableBytes in LOW_STORAGE_CRITICAL_BYTES until LOW_STORAGE_WARNING_BYTES) {
                issues += AppHealthIssue(
                    id = "storage_low",
                    severity = IssueSeverity.WARNING,
                    title = "Low internal storage",
                    summary = "${availableBytes / (1024L * 1024L)} MB free. Large video downloads may pause or fail.",
                    suggestedFix = "Free up space or manage downloaded media in Downloads."
                )
            }
            if (!netConnected) {
                issues += AppHealthIssue(
                    id = "network_offline",
                    severity = IssueSeverity.WARNING,
                    title = "Device appears offline",
                    summary = "Remote AI chat, link metadata enrichment, and media extraction require an internet connection.",
                    suggestedFix = "Reconnect to Wi-Fi or cellular data."
                )
            } else if (!netValidated) {
                issues += AppHealthIssue(
                    id = "network_unvalidated",
                    severity = IssueSeverity.INFO,
                    title = "Network connectivity unvalidated",
                    summary = "Connected to a network that may have captive portal or restricted internet.",
                    suggestedFix = "Check your connection if background inspections defer."
                )
            }
            if (!notificationsEnabled) {
                issues += AppHealthIssue(
                    id = "notifications_disabled",
                    severity = IssueSeverity.WARNING,
                    title = "Notifications are turned off",
                    summary = "Reminders, recovery alerts, and app health warnings will not appear in the status bar.",
                    suggestedFix = "Allow notifications for Laterbox in Android App Settings."
                )
            }
            if (!exactAlarmsAllowed) {
                issues += AppHealthIssue(
                    id = "exact_alarms_restricted",
                    severity = IssueSeverity.INFO,
                    title = "Exact reminder alarms restricted",
                    summary = "Scheduled link reminders and Smart Digests may fire slightly late.",
                    suggestedFix = "Enable Alarms & reminders permission for Laterbox if punctual reminders matter."
                )
            }
            if (failedInspections > 0) {
                issues += AppHealthIssue(
                    id = "failed_inspections",
                    severity = if (failedInspections >= 3) IssueSeverity.WARNING else IssueSeverity.INFO,
                    title = "$failedInspections link ${if (failedInspections == 1) "inspection" else "inspections"} failed",
                    summary = "Some saved links could not discover downloadable media formats automatically.",
                    suggestedFix = "Open the affected link's detail sheet and tap Retry inspection."
                )
            }
            if (failedDownloads > 0) {
                issues += AppHealthIssue(
                    id = "failed_downloads",
                    severity = IssueSeverity.WARNING,
                    title = "$failedDownloads media ${if (failedDownloads == 1) "download" else "downloads"} failed",
                    summary = "Check Downloads Queue to see error details and retry.",
                    suggestedFix = "Open Downloads to inspect errors or retry transfers."
                )
            }
            return issues
        }
    }
}
