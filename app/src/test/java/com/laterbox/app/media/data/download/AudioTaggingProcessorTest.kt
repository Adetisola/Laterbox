package com.laterbox.app.media.data.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AudioTaggingProcessorTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var audioFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = File(context.cacheDir, "test_audio_${System.currentTimeMillis()}").apply { mkdirs() }
        audioFile = File(tempDir, "song.m4a").apply {
            writeBytes(ByteArray(1024) { 0x41 })
        }
    }

    @After
    fun tearDown() {
        AudioTaggingProcessor.customProcessRunner = null
        tempDir.deleteRecursively()
    }

    @Test
    fun testTagAudioFileGracefulFallbackWhenFFmpegMissing() {
        // In Robolectric environment without custom runner, libffmpeg.so is not present on host JVM
        val result = AudioTaggingProcessor.tagAudioFile(
            appContext = context,
            audioFile = audioFile,
            title = "Flowers",
            artist = "Miley Cyrus",
            album = "Endless Summer Vacation",
            artworkUrl = null,
            spotifyTrackId = "0yLdNVWF3Srea0uzk55zFn"
        )

        // Must return false gracefully without deleting or corrupting input audio
        assertFalse(result)
        assertTrue(audioFile.exists())
        assertEquals(1024L, audioFile.length())
    }

    @Test
    fun testTagAudioFileWithCustomRunnerGeneratesCorrectCommand() {
        var executedCommand: List<String>? = null
        var executedEnv: Map<String, String>? = null

        AudioTaggingProcessor.customProcessRunner = { cmd, env ->
            executedCommand = cmd
            executedEnv = env
            // Simulate FFmpeg writing tagged output file
            val outputFilePath = cmd.last()
            File(outputFilePath).writeBytes(ByteArray(2048) { 0x42 })
            true
        }

        val result = AudioTaggingProcessor.tagAudioFile(
            appContext = context,
            audioFile = audioFile,
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            artworkUrl = null,
            spotifyTrackId = "0VjIjW4GlUZAMYd2vXMi3b"
        )

        assertTrue(result)
        assertTrue(audioFile.exists())
        assertEquals(2048L, audioFile.length())

        val cmd = executedCommand
        assertTrue(cmd != null)
        assertTrue(cmd!!.contains("title=Blinding Lights"))
        assertTrue(cmd.contains("artist=The Weeknd"))
        assertTrue(cmd.contains("album=After Hours"))
        assertTrue(cmd.contains("comment=Source: Spotify [0VjIjW4GlUZAMYd2vXMi3b]"))
        assertTrue(executedEnv!!.containsKey("LD_LIBRARY_PATH"))
    }
}
