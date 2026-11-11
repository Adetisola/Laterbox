package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YtDlpOptimizerTest {

    private lateinit var context: Context
    private lateinit var testWorkDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testWorkDir = File(context.cacheDir, "optimizer_test_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testWorkDir.deleteRecursively()
    }

    private fun createSampleZip(zipFile: File, includeEjs: Boolean = true, flagValue: String = "False") {
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // __main__.py
            zos.putNextEntry(ZipEntry("__main__.py"))
            zos.write("print('yt-dlp')".toByteArray())
            zos.closeEntry()

            // yt_dlp/__init__.py
            zos.putNextEntry(ZipEntry("yt_dlp/__init__.py"))
            zos.write("# init".toByteArray())
            zos.closeEntry()

            // yt_dlp/YoutubeDL.py
            zos.putNextEntry(ZipEntry("yt_dlp/YoutubeDL.py"))
            zos.write("class YoutubeDL: pass".toByteArray())
            zos.closeEntry()

            // yt_dlp/extractor/youtube/jsc/_builtin/ejs.py
            if (includeEjs) {
                zos.putNextEntry(ZipEntry(YtDlpOptimizer.EJS_RELATIVE_PATH))
                val content = """
                    class EJS:
                        _ENABLE_PREPROCESSED_PLAYER_CACHE = $flagValue
                """.trimIndent()
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
    }

    @Test
    fun getCacheDir_createsAndReturnsWritableDirectory() {
        val cacheDir = YtDlpOptimizer.getCacheDir(context)
        assertTrue(cacheDir.exists())
        assertTrue(cacheDir.isDirectory)
        assertTrue(cacheDir.canWrite())
    }

    @Test
    fun validateUnpackedDir_returnsFalseForEmptyOrIncompleteDirectory() {
        val emptyDir = File(testWorkDir, "empty").apply { mkdirs() }
        assertFalse(YtDlpOptimizer.validateUnpackedDir(emptyDir))

        val incompleteDir = File(testWorkDir, "incomplete").apply { mkdirs() }
        File(incompleteDir, "__main__.py").writeText("content")
        assertFalse(YtDlpOptimizer.validateUnpackedDir(incompleteDir))
    }

    @Test
    fun validateUnpackedDir_returnsTrueWhenAllEssentialFilesExist() {
        val validDir = File(testWorkDir, "valid").apply { mkdirs() }
        File(validDir, "__main__.py").writeText("print('main')")
        File(validDir, "yt_dlp").mkdirs()
        File(validDir, "yt_dlp/__init__.py").writeText("# init")
        File(validDir, "yt_dlp/YoutubeDL.py").writeText("class YoutubeDL: pass")

        assertTrue(YtDlpOptimizer.validateUnpackedDir(validDir))
    }

    @Test
    fun unzip_extractsAllEntriesCorrectly() {
        val zipFile = File(testWorkDir, "bundle.zip")
        createSampleZip(zipFile)

        val targetDir = File(testWorkDir, "unpacked")
        YtDlpOptimizer.unzip(zipFile, targetDir)

        assertTrue(File(targetDir, "__main__.py").exists())
        assertTrue(File(targetDir, "yt_dlp/__init__.py").exists())
        assertTrue(File(targetDir, "yt_dlp/YoutubeDL.py").exists())
        assertTrue(File(targetDir, YtDlpOptimizer.EJS_RELATIVE_PATH).exists())
    }

    @Test
    fun patchPreprocessedPlayerCache_patchesFlagWhenFound() {
        val dir = File(testWorkDir, "pkg").apply { mkdirs() }
        val ejsFile = File(dir, YtDlpOptimizer.EJS_RELATIVE_PATH).apply {
            parentFile?.mkdirs()
            writeText("prefix\n_ENABLE_PREPROCESSED_PLAYER_CACHE = False\nsuffix")
        }

        val patched = YtDlpOptimizer.patchPreprocessedPlayerCache(dir)
        assertTrue(patched)

        val updated = ejsFile.readText()
        assertTrue(updated.contains("_ENABLE_PREPROCESSED_PLAYER_CACHE = True"))
        assertFalse(updated.contains("_ENABLE_PREPROCESSED_PLAYER_CACHE = False"))

        // Idempotent second run
        val patchedAgain = YtDlpOptimizer.patchPreprocessedPlayerCache(dir)
        assertTrue(patchedAgain)
    }

    @Test
    fun cleanCacheIfVersionChanged_purgesStaleEntriesOnVersionMismatch() {
        val cacheDir = YtDlpOptimizer.getCacheDir(context)
        val dummyFile = File(cacheDir, "old_cached_player.js").apply { writeText("cached js") }
        assertTrue(dummyFile.exists())

        // Record initial version
        YtDlpOptimizer.cleanCacheIfVersionChanged(context, "2025.10.01")

        // New version triggers cache purge
        YtDlpOptimizer.cleanCacheIfVersionChanged(context, "2025.11.12")
        assertFalse(dummyFile.exists())
    }

    @Test
    fun ensureOptimized_extractsArchive_patchesFlag_andSetsPath() = runTest {
        val noBackup = context.noBackupFilesDir ?: File(context.filesDir, "no_backup").apply { mkdirs() }
        val archiveFile = File(noBackup, "youtubedl-android/yt-dlp/yt-dlp")
        createSampleZip(archiveFile)

        val result = YtDlpOptimizer.ensureOptimized(context, forceReextract = true)

        assertTrue(result.isOptimized)
        assertFalse(result.fallbackToArchive)
        assertTrue(result.isPlayerCachePatched)
        assertNotNull(result.activePath)
        assertTrue(result.activePath?.exists() == true)
        assertTrue(result.activePath?.isDirectory == true)

        val active = YtDlpOptimizer.getYtDlpBinaryPath()
        assertEquals(result.activePath?.absolutePath, active?.absolutePath)

        val ejs = File(result.activePath, YtDlpOptimizer.EJS_RELATIVE_PATH)
        assertTrue(ejs.readText().contains("_ENABLE_PREPROCESSED_PLAYER_CACHE = True"))
    }
}
