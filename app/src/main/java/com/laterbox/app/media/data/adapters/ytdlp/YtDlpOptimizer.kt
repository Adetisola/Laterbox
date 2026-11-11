package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Optimizes yt-dlp execution by:
 * 1. Unpacking the bundled/updated yt-dlp zip once per version into an app-private versioned directory.
 *    This allows CPython to load modules from the filesystem and cache compiled bytecode (.pyc) in __pycache__,
 *    avoiding AST recompilation on every execution.
 * 2. Patching [_ENABLE_PREPROCESSED_PLAYER_CACHE = True] in [yt_dlp/extractor/youtube/jsc/_builtin/ejs.py]
 *    so YouTube's prepared player script is preserved and reused across inspections when --cache-dir is passed.
 * 3. Maintaining atomic extraction, integrity validation, duplicate extraction locking, version-aware cache
 *    invalidation, and automatic fallback to the archive zip if anything fails.
 */
object YtDlpOptimizer {

    private const val TAG = "YtDlpOptimizer"
    const val EJS_RELATIVE_PATH = "yt_dlp/extractor/youtube/jsc/_builtin/ejs.py"
    const val PREPROCESSED_CACHE_FLAG = "_ENABLE_PREPROCESSED_PLAYER_CACHE = False"
    const val PREPROCESSED_CACHE_FLAG_PATCHED = "_ENABLE_PREPROCESSED_PLAYER_CACHE = True"
    private const val PREF_KEY_CACHED_VERSION = "ytdlp_optimizer_cached_version"
    private const val PREFS_NAME = "ytdlp_optimizer_prefs"

    private val extractionMutex = Mutex()

    data class OptimizationResult(
        val isOptimized: Boolean,
        val activePath: File?,
        val isPlayerCachePatched: Boolean,
        val fallbackToArchive: Boolean,
        val version: String,
        val error: String? = null
    )

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
     * Validates that an unpacked yt-dlp directory contains all critical files and is not corrupt.
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
     * Unzips an archive into a target destination with zip-slip prevention.
     */
    fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile).buffered()).use { zis ->
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
                    FileOutputStream(newFile).buffered().use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
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
     * Ensures the installed yt-dlp archive is unpacked into a versioned directory,
     * integrity-verified, patched with the preprocessed player cache, and pointed to by YoutubeDL.
     *
     * Protected by [extractionMutex] to guarantee no duplicate simultaneous extractions.
     * Falls back safely to the original archive zip on any failure.
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

            // 1. If already extracted, valid, and not forced, activate and return
            if (!forceReextract && validateUnpackedDir(targetVersionDir)) {
                val isPatched = File(targetVersionDir, EJS_RELATIVE_PATH).takeIf { it.exists() }
                    ?.readText()?.contains(PREPROCESSED_CACHE_FLAG_PATCHED) == true
                setYtDlpBinaryPath(targetVersionDir)
                return@withContext OptimizationResult(
                    isOptimized = true,
                    activePath = targetVersionDir,
                    isPlayerCachePatched = isPatched,
                    fallbackToArchive = false,
                    version = version
                )
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

            // 3. Extract to a temp directory, validate, patch, and atomically swap
            unpackedRoot.mkdirs()
            val tempDir = File(unpackedRoot, "ytdlp-$version.tmp.${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                unzip(archiveFile, tempDir)

                if (!validateUnpackedDir(tempDir)) {
                    throw IllegalStateException("Integrity validation failed on extracted yt-dlp directory")
                }

                val patched = patchPreprocessedPlayerCache(tempDir)

                // Remove existing version directory before swap
                if (targetVersionDir.exists()) {
                    targetVersionDir.deleteRecursively()
                }

                val renamed = tempDir.renameTo(targetVersionDir)
                if (!renamed) {
                    tempDir.copyRecursively(targetVersionDir, overwrite = true)
                    tempDir.deleteRecursively()
                }

                // Purge older versions in unpackedRoot
                unpackedRoot.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name.startsWith("ytdlp-") && file.name != targetVersionDir.name) {
                        file.deleteRecursively()
                    }
                }

                // Point YoutubeDL.ytdlpPath to the optimized unpacked directory
                setYtDlpBinaryPath(targetVersionDir)

                Log.i(TAG, "yt-dlp extraction and optimization complete: $version at ${targetVersionDir.absolutePath}")
                OptimizationResult(
                    isOptimized = true,
                    activePath = targetVersionDir,
                    isPlayerCachePatched = patched,
                    fallbackToArchive = false,
                    version = version
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Optimization failed: ${t.message}. Falling back to archive zip.", t)
                tempDir.deleteRecursively()
                setYtDlpBinaryPath(archiveFile)
                OptimizationResult(
                    isOptimized = false,
                    activePath = archiveFile,
                    isPlayerCachePatched = false,
                    fallbackToArchive = true,
                    version = version,
                    error = t.message
                )
            }
        }
    }
}
