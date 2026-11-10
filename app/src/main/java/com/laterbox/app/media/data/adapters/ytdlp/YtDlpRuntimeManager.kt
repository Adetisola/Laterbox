package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * High-level initialization state for the yt-dlp core engine (Python runtime + yt-dlp script).
 */
sealed class YtDlpInitState {
    data object NotInitialized : YtDlpInitState()
    data object Initializing : YtDlpInitState()
    data class Initialized(val version: String?) : YtDlpInitState()
    data class Failed(val error: YtDlpInitException) : YtDlpInitState()
}

/**
 * High-level initialization state for the FFmpeg binary and shared libraries.
 */
sealed class FfmpegInitState {
    data object NotInitialized : FfmpegInitState()
    data object Initializing : FfmpegInitState()
    data object Initialized : FfmpegInitState()
    data class Failed(val error: YtDlpInitException) : FfmpegInitState()
}

/**
 * Specific actionable failure categories for runtime initialization.
 */
enum class YtDlpInitFailureCategory(val userDescription: String) {
    UNSUPPORTED_ABI("Your device architecture is not compatible with yt-dlp native libraries."),
    MISSING_NATIVE_LIBRARY("Native engine libraries are missing from the installed APK."),
    NATIVE_LIBRARY_LOAD_FAILURE("Failed to load native system libraries."),
    ARCHIVE_EXTRACTION_FAILURE("Failed to unpack the Python or FFmpeg runtime archives."),
    PYTHON_RUNTIME_INIT_FAILURE("Python runtime initialization failed."),
    YTDLP_SCRIPT_INIT_FAILURE("Failed to unpack yt-dlp binary bundle."),
    FFMPEG_INIT_FAILURE("Failed to initialize FFmpeg media processor."),
    STORAGE_OR_PERMISSION_ERROR("Insufficient internal storage or filesystem permission denied."),
    UNKNOWN("An unexpected initialization error occurred.")
}

/**
 * Preserves the exact root-cause Throwable, categorized failure reason, and diagnostic details.
 */
class YtDlpInitException(
    val category: YtDlpInitFailureCategory,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * File descriptor and size for diagnostic auditing.
 */
data class FileAuditEntry(val name: String, val sizeBytes: Long, val isDirectory: Boolean)

/**
 * Comprehensive diagnostic report for hardware, ABI, process, native libraries, and initialization state.
 */
data class YtDlpDiagnosticReport(
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val supportedAbis: List<String>,
    val supported32BitAbis: List<String>,
    val supported64BitAbis: List<String>,
    val processArch: String,
    val is64BitProcess: Boolean,
    val nativeLibraryDir: String,
    val nativeLibrariesPresent: List<FileAuditEntry>,
    val runtimeDirectoryExists: Boolean,
    val runtimeExtractedFiles: List<FileAuditEntry>,
    val ytDlpStateSummary: String,
    val ytDlpVersion: String?,
    val ffmpegStateSummary: String,
    val lastExceptionCategory: YtDlpInitFailureCategory?,
    val lastExceptionMessage: String?,
    val lastExceptionStackTrace: String?
) {
    fun toFormattedString(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Laterbox Media Engine Diagnostics ===")
        sb.appendLine("Device: $manufacturer $model (Android $androidRelease, API $sdkInt)")
        sb.appendLine("Process: Arch=$processArch, 64-bit=$is64BitProcess")
        sb.appendLine("Supported ABIs: ${supportedAbis.joinToString(", ")}")
        sb.appendLine("32-bit ABIs: ${supported32BitAbis.joinToString(", ").ifEmpty { "none" }}")
        sb.appendLine("64-bit ABIs: ${supported64BitAbis.joinToString(", ").ifEmpty { "none" }}")
        sb.appendLine("Native Lib Dir: $nativeLibraryDir")
        sb.appendLine("Native Libraries (${nativeLibrariesPresent.size}):")
        if (nativeLibrariesPresent.isEmpty()) {
            sb.appendLine("  (none found)")
        } else {
            nativeLibrariesPresent.forEach { lib ->
                val sizeStr = "${lib.sizeBytes / 1024} KB"
                sb.appendLine("  - ${lib.name} ($sizeStr)")
            }
        }
        sb.appendLine("Runtime Directory Exists: $runtimeDirectoryExists")
        if (runtimeExtractedFiles.isNotEmpty()) {
            sb.appendLine("Runtime Extracted Files (${runtimeExtractedFiles.size}):")
            runtimeExtractedFiles.take(15).forEach { f ->
                sb.appendLine("  - ${f.name} (${f.sizeBytes / 1024} KB)")
            }
            if (runtimeExtractedFiles.size > 15) {
                sb.appendLine("  ... and ${runtimeExtractedFiles.size - 15} more files")
            }
        }
        sb.appendLine("yt-dlp Engine State: $ytDlpStateSummary (Version: ${ytDlpVersion ?: "unknown"})")
        sb.appendLine("FFmpeg Engine State: $ffmpegStateSummary")
        if (lastExceptionCategory != null) {
            sb.appendLine("\n--- Last Initialization Error ---")
            sb.appendLine("Category: $lastExceptionCategory")
            sb.appendLine("Message: $lastExceptionMessage")
            if (!lastExceptionStackTrace.isNullOrBlank()) {
                sb.appendLine("Stack Trace:")
                sb.appendLine(lastExceptionStackTrace)
            }
        }
        sb.appendLine("==========================================")
        return sb.toString()
    }
}

/**
 * Centralized, thread-safe, coroutine-aware runtime manager for yt-dlp and FFmpeg.
 *
 * Guarantees:
 * 1. Single-point initialization with atomic state transitions.
 * 2. Independent yt-dlp (Python + zipapp) and FFmpeg extraction.
 * 3. NonCancellable archive extraction to avoid corrupt partial files or dangling symlinks.
 * 4. Symlink-aware self-healing cleanup if previous extraction was corrupted.
 * 5. Complete preservation of root-cause Exceptions and Throwable stack traces.
 */
class YtDlpRuntimeManager(
    private val appContext: Context
) {
    private val tag = "YtDlpRuntimeManager"

    private val ytDlpMutex = Mutex()
    private val ffmpegMutex = Mutex()

    private val _ytDlpState = MutableStateFlow<YtDlpInitState>(YtDlpInitState.NotInitialized)
    val ytDlpState: StateFlow<YtDlpInitState> = _ytDlpState.asStateFlow()

    private val _ffmpegState = MutableStateFlow<FfmpegInitState>(FfmpegInitState.NotInitialized)
    val ffmpegState: StateFlow<FfmpegInitState> = _ffmpegState.asStateFlow()

    @Volatile
    private var lastRecordedException: YtDlpInitException? = null

    fun isYtDlpReady(): Boolean = _ytDlpState.value is YtDlpInitState.Initialized

    fun isFfmpegReady(): Boolean = _ffmpegState.value is FfmpegInitState.Initialized

    /**
     * Initializes the core yt-dlp engine (Python runtime + yt-dlp zipapp bundle).
     * This is sufficient for link metadata inspection and yt-dlp updater checks.
     */
    suspend fun ensureYtDlpInitialized(): Result<String?> = withContext(Dispatchers.IO) {
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            val err = YtDlpInitException(
                YtDlpInitFailureCategory.UNKNOWN,
                "Native yt-dlp initialization bypassed in Robolectric test environment."
            )
            return@withContext Result.failure(err)
        }

        val currentState = _ytDlpState.value
        if (currentState is YtDlpInitState.Initialized) {
            return@withContext Result.success(currentState.version)
        }

        ytDlpMutex.withLock {
            val stateUnderLock = _ytDlpState.value
            if (stateUnderLock is YtDlpInitState.Initialized) {
                return@withContext Result.success(stateUnderLock.version)
            }

            _ytDlpState.value = YtDlpInitState.Initializing

            try {
                // 1. Device compatibility pre-flight check
                verifyDeviceAbiSupport()

                // 2. Native library pre-flight check
                verifyNativeLibrariesPresent(listOf("libpython.zip.so", "libpython.so", "libqjs.so"))

                // 3. Perform initialization protected against coroutine cancellation
                withContext(NonCancellable) {
                    try {
                        YoutubeDL.getInstance().init(appContext)
                    } catch (t: Throwable) {
                        Log.w(tag, "Initial YoutubeDL.init() failed: ${t.message}. Attempting self-healing cleanup...", t)
                        // Attempt self-healing purge of potentially corrupt packages/python directory
                        purgeCorruptPythonRuntimeDirectory()
                        // Retry initialization once
                        YoutubeDL.getInstance().init(appContext)
                    }
                }

                val version = try {
                    YoutubeDL.getInstance().version(appContext)
                } catch (_: Throwable) {
                    null
                }

                _ytDlpState.value = YtDlpInitState.Initialized(version)
                Log.i(tag, "yt-dlp successfully initialized (version: $version)")
                Result.success(version)
            } catch (t: Throwable) {
                val categorizedException = categorizeException(t, isFfmpeg = false)
                lastRecordedException = categorizedException
                _ytDlpState.value = YtDlpInitState.Failed(categorizedException)
                Log.e(tag, "yt-dlp initialization failed [${categorizedException.category}]: ${categorizedException.message}", t)
                Result.failure(categorizedException)
            }
        }
    }

    /**
     * Initializes the FFmpeg binary and shared libraries.
     * Required for merging separate video and audio streams into MP4 during downloads.
     */
    suspend fun ensureFfmpegInitialized(): Result<Unit> = withContext(Dispatchers.IO) {
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            val err = YtDlpInitException(
                YtDlpInitFailureCategory.UNKNOWN,
                "FFmpeg initialization bypassed in Robolectric test environment."
            )
            return@withContext Result.failure(err)
        }

        val currentState = _ffmpegState.value
        if (currentState is FfmpegInitState.Initialized) {
            return@withContext Result.success(Unit)
        }

        ffmpegMutex.withLock {
            val stateUnderLock = _ffmpegState.value
            if (stateUnderLock is FfmpegInitState.Initialized) {
                return@withContext Result.success(Unit)
            }

            _ffmpegState.value = FfmpegInitState.Initializing

            try {
                // Ensure yt-dlp is initialized first
                val ytDlpRes = ensureYtDlpInitialized()
                if (ytDlpRes.isFailure) {
                    throw ytDlpRes.exceptionOrNull() ?: Exception("yt-dlp dependency failed")
                }

                verifyNativeLibrariesPresent(listOf("libffmpeg.zip.so", "libffmpeg.so"))

                withContext(NonCancellable) {
                    try {
                        FFmpeg.getInstance().init(appContext)
                    } catch (t: Throwable) {
                        Log.w(tag, "Initial FFmpeg.init() failed: ${t.message}. Attempting self-healing cleanup...", t)
                        purgeCorruptFfmpegRuntimeDirectory()
                        FFmpeg.getInstance().init(appContext)
                    }
                }

                _ffmpegState.value = FfmpegInitState.Initialized
                Log.i(tag, "FFmpeg successfully initialized")
                Result.success(Unit)
            } catch (t: Throwable) {
                val categorizedException = categorizeException(t, isFfmpeg = true)
                lastRecordedException = categorizedException
                _ffmpegState.value = FfmpegInitState.Failed(categorizedException)
                Log.w(tag, "FFmpeg initialization failed [${categorizedException.category}]: ${categorizedException.message}", t)
                Result.failure(categorizedException)
            }
        }
    }

    /**
     * Pre-flight ABI support check.
     */
    private fun verifyDeviceAbiSupport() {
        val supported = Build.SUPPORTED_ABIS ?: emptyArray()
        val hasArm = supported.any { it.contains("arm", ignoreCase = true) }
        if (!hasArm) {
            throw YtDlpInitException(
                YtDlpInitFailureCategory.UNSUPPORTED_ABI,
                "Device ABIs (${supported.joinToString()}) do not support ARM architecture."
            )
        }
    }

    /**
     * Pre-flight verification that required native libraries exist in nativeLibraryDir.
     */
    private fun verifyNativeLibrariesPresent(requiredNames: List<String>) {
        val libDir = File(appContext.applicationInfo.nativeLibraryDir)
        if (!libDir.exists() || !libDir.isDirectory) {
            throw YtDlpInitException(
                YtDlpInitFailureCategory.MISSING_NATIVE_LIBRARY,
                "Application native library directory does not exist: ${libDir.absolutePath}"
            )
        }

        val available = libDir.list()?.toSet() ?: emptySet()
        val missing = requiredNames.filter { it !in available }
        if (missing.isNotEmpty()) {
            throw YtDlpInitException(
                YtDlpInitFailureCategory.MISSING_NATIVE_LIBRARY,
                "Required native libraries are missing from ${libDir.absolutePath}: ${missing.joinToString()}"
            )
        }
    }

    /**
     * Symlink-safe recursive deletion of a directory.
     * Uses File.delete() on directory listings so dangling symlinks (where File.exists() is false)
     * are still removed cleanly.
     */
    private fun safeDeleteRecursively(target: File) {
        try {
            if (!target.exists() && !isSymlink(target)) {
                return
            }
            if (target.isDirectory && !isSymlink(target)) {
                val children = target.list() ?: emptyArray()
                for (childName in children) {
                    safeDeleteRecursively(File(target, childName))
                }
            }
            target.delete()
        } catch (e: Exception) {
            Log.w(tag, "Error deleting ${target.absolutePath}: ${e.message}")
        }
    }

    private fun isSymlink(file: File): Boolean {
        return try {
            val stat = Os.lstat(file.absolutePath)
            (stat.st_mode and android.system.OsConstants.S_IFMT) == android.system.OsConstants.S_IFLNK
        } catch (_: Throwable) {
            // Fallback for JVM/Robolectric
            file.canonicalPath != file.absolutePath
        }
    }

    private fun purgeCorruptPythonRuntimeDirectory() {
        val noBackup = File(appContext.noBackupFilesDir, "youtubedl-android")
        val pythonPackages = File(noBackup, "packages/python")
        if (pythonPackages.exists()) {
            Log.i(tag, "Purging corrupt python runtime directory: ${pythonPackages.absolutePath}")
            safeDeleteRecursively(pythonPackages)
        }
    }

    private fun purgeCorruptFfmpegRuntimeDirectory() {
        val noBackup = File(appContext.noBackupFilesDir, "youtubedl-android")
        val ffmpegPackages = File(noBackup, "packages/ffmpeg")
        if (ffmpegPackages.exists()) {
            Log.i(tag, "Purging corrupt ffmpeg runtime directory: ${ffmpegPackages.absolutePath}")
            safeDeleteRecursively(ffmpegPackages)
        }
    }

    /**
     * Maps arbitrary Throwable into a categorized YtDlpInitException while preserving cause and trace.
     */
    fun categorizeException(t: Throwable, isFfmpeg: Boolean): YtDlpInitException {
        if (t is YtDlpInitException) return t

        val msg = t.message ?: t.localizedMessage ?: t.javaClass.simpleName
        val causeChain = generateSequence(t) { it.cause }.toList()
        val causeNames = causeChain.joinToString { it.javaClass.simpleName }

        val category = when {
            // R8 or ZIP extraction failures (ExceptionInInitializerError, NoClassDefFoundError, ZipException)
            causeChain.any { it is java.lang.ExceptionInInitializerError || it is java.lang.NoClassDefFoundError } ->
                YtDlpInitFailureCategory.ARCHIVE_EXTRACTION_FAILURE
            causeChain.any { it is java.util.zip.ZipException || it.javaClass.name.contains("zip", ignoreCase = true) } ->
                YtDlpInitFailureCategory.ARCHIVE_EXTRACTION_FAILURE
            causeChain.any { it is UnsatisfiedLinkError } ->
                YtDlpInitFailureCategory.NATIVE_LIBRARY_LOAD_FAILURE
            msg.contains("space", ignoreCase = true) || msg.contains("ENOSPC", ignoreCase = true) || msg.contains("storage", ignoreCase = true) ->
                YtDlpInitFailureCategory.STORAGE_OR_PERMISSION_ERROR
            msg.contains("permission", ignoreCase = true) || msg.contains("EACCES", ignoreCase = true) ->
                YtDlpInitFailureCategory.STORAGE_OR_PERMISSION_ERROR
            isFfmpeg ->
                YtDlpInitFailureCategory.FFMPEG_INIT_FAILURE
            msg.contains("python", ignoreCase = true) ->
                YtDlpInitFailureCategory.PYTHON_RUNTIME_INIT_FAILURE
            else ->
                YtDlpInitFailureCategory.UNKNOWN
        }

        val detailedMessage = buildString {
            append(category.userDescription)
            append(" [")
            append(t.javaClass.simpleName)
            if (causeChain.size > 1) {
                append(" -> ")
                append(causeChain.last().javaClass.simpleName)
            }
            append("]: ")
            append(msg)
        }

        return YtDlpInitException(category, detailedMessage, t)
    }

    /**
     * Generates a complete diagnostic report.
     */
    fun captureDiagnosticReport(): YtDlpDiagnosticReport {
        val nativeLibPath = appContext.applicationInfo?.nativeLibraryDir ?: ""
        val libDir = if (nativeLibPath.isNotBlank()) File(nativeLibPath) else null
        val nativeLibs = if (libDir != null && libDir.exists() && libDir.isDirectory) {
            libDir.listFiles()?.map { FileAuditEntry(it.name, it.length(), it.isDirectory) } ?: emptyList()
        } else {
            emptyList()
        }

        val noBackupDir = appContext.noBackupFilesDir
        val noBackup = if (noBackupDir != null) File(noBackupDir, "youtubedl-android") else null
        val runtimeFiles = mutableListOf<FileAuditEntry>()
        if (noBackup != null && noBackup.exists()) {
            noBackup.walkTopDown().take(50).forEach { file ->
                if (file != noBackup) {
                    val relPath = file.relativeTo(noBackup).path
                    runtimeFiles.add(FileAuditEntry(relPath, file.length(), file.isDirectory))
                }
            }
        }

        val ytDlpVersion = try {
            YoutubeDL.getInstance().version(appContext)
        } catch (_: Throwable) {
            null
        }

        val stackTrace = lastRecordedException?.let { ex ->
            val sw = StringWriter()
            ex.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        val processArch = try {
            Os.uname().machine
        } catch (_: Throwable) {
            System.getProperty("os.arch") ?: "unknown"
        }

        val is64Bit = try {
            Process.is64Bit()
        } catch (_: Throwable) {
            false
        }

        return YtDlpDiagnosticReport(
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            supported32BitAbis = Build.SUPPORTED_32_BIT_ABIS?.toList() ?: emptyList(),
            supported64BitAbis = Build.SUPPORTED_64_BIT_ABIS?.toList() ?: emptyList(),
            processArch = processArch,
            is64BitProcess = is64Bit,
            nativeLibraryDir = libDir?.absolutePath ?: "unknown",
            nativeLibrariesPresent = nativeLibs,
            runtimeDirectoryExists = noBackup?.exists() == true,
            runtimeExtractedFiles = runtimeFiles,
            ytDlpStateSummary = _ytDlpState.value.javaClass.simpleName,
            ytDlpVersion = ytDlpVersion,
            ffmpegStateSummary = _ffmpegState.value.javaClass.simpleName,
            lastExceptionCategory = lastRecordedException?.category,
            lastExceptionMessage = lastRecordedException?.message,
            lastExceptionStackTrace = stackTrace
        )
    }
}
