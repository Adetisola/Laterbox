package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Optimizes yt-dlp execution by:
 * 1. Detecting the actual archive format (conventional ZIP or shebang-prefixed zipapp as in official yt-dlp builds).
 * 2. Unpacking the bundled/updated yt-dlp binary once per version into an app-private versioned directory.
 *    This allows CPython to load modules from the filesystem and cache compiled bytecode (.pyc) in __pycache__,
 *    avoiding AST recompilation on every execution.
 * 3. Patching [_ENABLE_PREPROCESSED_PLAYER_CACHE = True] in [yt_dlp/extractor/youtube/jsc/_builtin/ejs.py]
 *    so YouTube's prepared player script is preserved and reused across inspections when --cache-dir is passed.
 *    Reapplies this patch whenever an existing directory is reused.
 * 4. Maintaining atomic extraction with backup preservation, integrity validation, duplicate extraction locking,
 *    reflection verification that YoutubeDL.ytdlpPath points to the optimized directory, version-aware cache
 *    invalidation, and automatic fallback to the archive if anything fails.
 */
object YtDlpOptimizer {

    private const val TAG = "YtDlpOptimizer"
    const val EJS_RELATIVE_PATH = "yt_dlp/extractor/youtube/jsc/_builtin/ejs.py"
    const val COMPAT_RELATIVE_PATH = "yt_dlp/compat/__init__.py"
    const val PREPROCESSED_CACHE_FLAG = "_ENABLE_PREPROCESSED_PLAYER_CACHE = False"
    const val PREPROCESSED_CACHE_FLAG_PATCHED = "_ENABLE_PREPROCESSED_PLAYER_CACHE = True"
    private const val PREF_KEY_CACHED_VERSION = "ytdlp_optimizer_cached_version"
    private const val PREFS_NAME = "ytdlp_optimizer_prefs"

    private val extractionMutex = Mutex()

    sealed class ArchiveFormat {
        object ConventionalZip : ArchiveFormat()
        data class ShebangZip(val zipOffset: Long) : ArchiveFormat()
        object Invalid : ArchiveFormat()
    }

    data class OptimizationResult(
        val isOptimized: Boolean,
        val activePath: File?,
        val isPlayerCachePatched: Boolean,
        val fallbackToArchive: Boolean,
        val version: String,
        val error: String? = null
    )

    /**
     * Locates the ZIP header magic bytes (PK\x03\x04 = 0x50, 0x4B, 0x03, 0x04) in [file].
     * Returns the byte offset within the file, or -1L if not found within the initial 4096 bytes.
     */
    fun findZipOffset(file: File): Long {
        if (!file.exists() || file.length() < 4L) return -1L
        try {
            BufferedInputStream(FileInputStream(file)).use { bis ->
                val buffer = ByteArray(4096)
                val read = bis.read(buffer)
                if (read < 4) return -1L
                for (i in 0..read - 4) {
                    if (buffer[i] == 0x50.toByte() &&
                        buffer[i + 1] == 0x4B.toByte() &&
                        buffer[i + 2] == 0x03.toByte() &&
                        buffer[i + 3] == 0x04.toByte()
                    ) {
                        return i.toLong()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding zip offset in ${file.name}: ${e.message}")
        }
        return -1L
    }

    /**
     * Detects whether [file] is a conventional ZIP archive, a shebang-prefixed zipapp, or invalid.
     */
    fun detectArchiveFormat(file: File): ArchiveFormat {
        val offset = findZipOffset(file)
        return when {
            offset == 0L -> ArchiveFormat.ConventionalZip
            offset > 0L -> ArchiveFormat.ShebangZip(offset)
            else -> ArchiveFormat.Invalid
        }
    }

    /**
     * Returns the persistent disk cache directory used by yt-dlp.
     * Ensures directory exists and is writable.
     */
    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "ytdlp_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Atomically clears old cached challenge/player data when yt-dlp version changes.
     */
    fun cleanCacheIfVersionChanged(context: Context, currentVersion: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastVersion = prefs.getString(PREF_KEY_CACHED_VERSION, null)
            if (lastVersion != null && lastVersion != currentVersion) {
                Log.i(TAG, "yt-dlp version changed from $lastVersion to $currentVersion. Clearing old player/EJS cache...")
                val cacheDir = getCacheDir(context)
                cacheDir.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
            }
            prefs.edit().putString(PREF_KEY_CACHED_VERSION, currentVersion).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean old yt-dlp cache: ${e.message}")
        }
    }

    /**
     * Validates that an unpacked yt-dlp directory contains all critical entry points and is non-empty.
     */
    fun validateUnpackedDir(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val mainPy = File(dir, "__main__.py")
        val initPy = File(dir, "yt_dlp/__init__.py")
        val ytdlpPy = File(dir, "yt_dlp/YoutubeDL.py")
        return mainPy.exists() && mainPy.length() > 0 &&
                initPy.exists() && initPy.length() > 0 &&
                ytdlpPy.exists() && ytdlpPy.length() > 0
    }

    /**
     * Unzips an archive into [destDir] handling both conventional ZIP archives
     * and shebang-prefixed zipapps (skipping to PK\x03\x04 header).
     * Includes canonical path verification for ZIP-slip prevention.
     */
    fun unzip(zipFile: File, destDir: File) {
        val offset = findZipOffset(zipFile)
        if (offset < 0L) {
            throw IllegalArgumentException("File ${zipFile.name} is not a valid ZIP archive or zipapp (PK\\x03\\x04 not found)")
        }

        BufferedInputStream(FileInputStream(zipFile)).use { bis ->
            if (offset > 0L) {
                var skipped = 0L
                while (skipped < offset) {
                    val s = bis.skip(offset - skipped)
                    if (s <= 0L) break
                    skipped += s
                }
                if (skipped != offset) {
                    throw IOException("Failed to skip $offset bytes to ZIP header in ${zipFile.name}")
                }
            }

            ZipInputStream(bis).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val destCanonicalPath = destDir.canonicalPath
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    val canonical = newFile.canonicalPath
                    if (!canonical.startsWith(destCanonicalPath + File.separator) && canonical != destCanonicalPath) {
                        throw SecurityException("Zip slip detected in entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(newFile)).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    /**
     * Applies the EJS preprocessed player cache patch only if the exact target flag is present.
     */
    fun patchPreprocessedPlayerCache(unpackedDir: File): Boolean {
        val ejsFile = File(unpackedDir, EJS_RELATIVE_PATH)
        if (!ejsFile.exists() || !ejsFile.isFile) {
            Log.w(TAG, "EJS file not found at ${ejsFile.absolutePath}, skipping player cache patch.")
            return false
        }
        return try {
            val originalContent = ejsFile.readText()
            if (originalContent.contains(PREPROCESSED_CACHE_FLAG)) {
                val patchedContent = originalContent.replace(PREPROCESSED_CACHE_FLAG, PREPROCESSED_CACHE_FLAG_PATCHED)
                ejsFile.writeText(patchedContent)
                Log.i(TAG, "Successfully patched _ENABLE_PREPROCESSED_PLAYER_CACHE = True in ${ejsFile.name}")
                true
            } else if (originalContent.contains(PREPROCESSED_CACHE_FLAG_PATCHED)) {
                Log.i(TAG, "_ENABLE_PREPROCESSED_PLAYER_CACHE is already set to True")
                true
            } else {
                Log.w(TAG, "Target flag $PREPROCESSED_CACHE_FLAG not found in ${ejsFile.name}. Skipping patch.")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to patch EJS preprocessed player cache: ${e.message}", e)
            false
        }
    }

    /**
     * Parses the minimum supported Python version requirement from the yt_dlp package.
     * Checks [yt_dlp/compat/__init__.py] for sys.version_info < (X, Y).
     * Defaults to (3, 11) for modern yt-dlp releases if not explicitly parsed.
     */
    fun getMinimumPythonRequirement(ytdlpDirOrArchive: File): Pair<Int, Int> {
        return try {
            val text = if (ytdlpDirOrArchive.isDirectory) {
                File(ytdlpDirOrArchive, COMPAT_RELATIVE_PATH).takeIf { it.exists() }?.readText()
            } else {
                val offset = findZipOffset(ytdlpDirOrArchive)
                if (offset >= 0L) {
                    BufferedInputStream(FileInputStream(ytdlpDirOrArchive)).use { bis ->
                        if (offset > 0L) bis.skip(offset)
                        ZipInputStream(bis).use { zis ->
                            var entry = zis.nextEntry
                            var foundText: String? = null
                            while (entry != null) {
                                if (entry.name == COMPAT_RELATIVE_PATH) {
                                    foundText = zis.bufferedReader().readText()
                                    break
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                            foundText
                        }
                    }
                } else null
            }

            if (!text.isNullOrBlank()) {
                val match = Regex("""sys\.version_info\s*<\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)""").find(text)
                if (match != null) {
                    val major = match.groupValues[1].toInt()
                    val minor = match.groupValues[2].toInt()
                    return Pair(major, minor)
                }
            }
            Pair(3, 11)
        } catch (_: Throwable) {
            Pair(3, 11)
        }
    }

    /**
     * Sets the private static [ytdlpPath] field in [YoutubeDL] via reflection.
     */
    fun setYtDlpBinaryPath(target: File): Boolean {
        return try {
            val field = YoutubeDL::class.java.getDeclaredField("ytdlpPath")
            field.isAccessible = true
            field.set(null, target)
            Log.i(TAG, "Updated YoutubeDL.ytdlpPath to: ${target.absolutePath}")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Could not set YoutubeDL.ytdlpPath via reflection: ${e.message}")
            false
        }
    }

    /**
     * Reads the current [ytdlpPath] from [YoutubeDL] via reflection.
     */
    fun getYtDlpBinaryPath(): File? {
        return try {
            val field = YoutubeDL::class.java.getDeclaredField("ytdlpPath")
            field.isAccessible = true
            field.get(null) as? File
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Ensures the installed yt-dlp archive (conventional ZIP or shebang zipapp) is unpacked
     * into a versioned directory, integrity-verified, patched with preprocessed player caching,
     * safely activated, and confirmed active in YoutubeDL.
     *
     * Protected by [extractionMutex] to guarantee no duplicate simultaneous extractions.
     * Preserves previous valid directory until replacement has been successfully activated.
     * Reapplies EJS cache patch when reusing an existing directory.
     * Falls back safely to original archive if anything fails.
     */
    suspend fun ensureOptimized(
        context: Context,
        forceReextract: Boolean = false
    ): OptimizationResult = withContext(Dispatchers.IO) {
        extractionMutex.withLock {
            val noBackup = context.noBackupFilesDir ?: return@withContext OptimizationResult(
                isOptimized = false,
                activePath = null,
                isPlayerCachePatched = false,
                fallbackToArchive = true,
                version = "unknown",
                error = "noBackupFilesDir is null"
            )

            val archiveFile = File(noBackup, "youtubedl-android/yt-dlp/yt-dlp")
            val version = try {
                YoutubeDL.getInstance().version(context)?.ifBlank { null } ?: "bundled"
            } catch (_: Throwable) {
                "bundled"
            }

            val unpackedRoot = File(noBackup, "youtubedl-android/unpacked")
            val targetVersionDir = File(unpackedRoot, "ytdlp-$version")

            cleanCacheIfVersionChanged(context, version)

            // 1. If already extracted, valid, and not forced: re-apply EJS patch, activate, verify path, and return
            if (!forceReextract && validateUnpackedDir(targetVersionDir)) {
                val isPatched = patchPreprocessedPlayerCache(targetVersionDir)
                val isActivated = setYtDlpBinaryPath(targetVersionDir)
                val activeVerified = getYtDlpBinaryPath()?.canonicalPath == targetVersionDir.canonicalPath

                if (isActivated && activeVerified && isPatched) {
                    return@withContext OptimizationResult(
                        isOptimized = true,
                        activePath = targetVersionDir,
                        isPlayerCachePatched = true,
                        fallbackToArchive = false,
                        version = version
                    )
                }
            }

            // 2. Verify archive exists before attempting extraction
            if (!archiveFile.exists() || archiveFile.length() == 0L) {
                Log.w(TAG, "Archive file does not exist at ${archiveFile.absolutePath}")
                return@withContext OptimizationResult(
                    isOptimized = false,
                    activePath = archiveFile,
                    isPlayerCachePatched = false,
                    fallbackToArchive = true,
                    version = version,
                    error = "Archive file not found"
                )
            }

            // 3. Extract to a temp directory, validate, patch, and safely swap preserving backup
            unpackedRoot.mkdirs()
            val tempDir = File(unpackedRoot, "ytdlp-$version.tmp.${System.currentTimeMillis()}")
            tempDir.mkdirs()
            val backupDir = File(unpackedRoot, "ytdlp-$version.bak.${System.currentTimeMillis()}")

            try {
                unzip(archiveFile, tempDir)

                if (!validateUnpackedDir(tempDir)) {
                    throw IllegalStateException("Integrity validation failed on extracted yt-dlp directory")
                }

                val patched = patchPreprocessedPlayerCache(tempDir)
                if (!patched) {
                    throw IllegalStateException("Required EJS preprocessed player cache patching failed")
                }

                // Preserve previous valid directory until replacement has been successfully activated
                if (targetVersionDir.exists()) {
                    if (!targetVersionDir.renameTo(backupDir)) {
                        targetVersionDir.copyRecursively(backupDir, overwrite = true)
                        targetVersionDir.deleteRecursively()
                    }
                }

                val renamed = tempDir.renameTo(targetVersionDir)
                if (!renamed) {
                    tempDir.copyRecursively(targetVersionDir, overwrite = true)
                    tempDir.deleteRecursively()
                }

                // Activate via reflection and verify that the patched directory is actually the path used
                val setPathSuccess = setYtDlpBinaryPath(targetVersionDir)
                val verifiedPath = getYtDlpBinaryPath()
                val isVerified = setPathSuccess && verifiedPath?.canonicalPath == targetVersionDir.canonicalPath

                if (!isVerified) {
                    throw IllegalStateException("Failed to verify that YoutubeDL.ytdlpPath points to optimized directory")
                }

                // Activation succeeded: clean up backup directory safely
                if (backupDir.exists()) {
                    backupDir.deleteRecursively()
                }

                // Purge older versions in unpackedRoot
                unpackedRoot.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name.startsWith("ytdlp-") && file.name != targetVersionDir.name) {
                        file.deleteRecursively()
                    }
                }

                Log.i(TAG, "yt-dlp extraction and optimization complete: $version at ${targetVersionDir.absolutePath}")
                OptimizationResult(
                    isOptimized = true,
                    activePath = targetVersionDir,
                    isPlayerCachePatched = true,
                    fallbackToArchive = false,
                    version = version
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Optimization failed: ${t.message}. Performing safe rollback.", t)
                tempDir.deleteRecursively()

                // Restore from backup if replacement failed
                if (backupDir.exists()) {
                    if (!targetVersionDir.exists()) {
                        backupDir.renameTo(targetVersionDir)
                    } else {
                        backupDir.deleteRecursively()
                    }
                }

                val fallbackTarget = if (validateUnpackedDir(targetVersionDir)) targetVersionDir else archiveFile
                setYtDlpBinaryPath(fallbackTarget)

                OptimizationResult(
                    isOptimized = fallbackTarget == targetVersionDir && validateUnpackedDir(targetVersionDir),
                    activePath = fallbackTarget,
                    isPlayerCachePatched = false,
                    fallbackToArchive = fallbackTarget == archiveFile,
                    version = version,
                    error = t.message
                )
            }
        }
    }
}
