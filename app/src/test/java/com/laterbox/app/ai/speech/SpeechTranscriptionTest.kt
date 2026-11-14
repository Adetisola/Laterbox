package com.laterbox.app.ai.speech

import com.laterbox.app.ai.AiFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechTranscriptionTest {

    private lateinit var server: MockWebServer
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempFile = File.createTempFile("laterbox_stt_", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempFile.delete()
    }

    @Test
    fun geminiTranscribeClient_usesGemini35TranscribeFilesAndInteractionsApi() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("X-Goog-Upload-URL", "$baseUrl/upload/v1beta/files?upload_id=u1")
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"file":{"name":"files/audio123","uri":"https://generativelanguage.googleapis.com/v1beta/files/audio123"}}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"outputs":[{"text":"Find my Kotlin videos"}]}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(200)) // DELETE remote file

        val client = GeminiTranscribeClient(rootUrl = baseUrl, timeoutMs = 10_000L)
        val result = client.transcribe(RecordedAudio(tempFile, "audio/mp4", 1_500L), "AIzaTestKey1234567890")

        assertTrue("Expected Success, got $result", result is TranscribeResult.Success)
        assertEquals("Find my Kotlin videos", (result as TranscribeResult.Success).text)

        val r1 = server.takeRequest()
        assertEquals("POST", r1.method)
        assertEquals("/upload/v1beta/files", r1.path)
        assertEquals("AIzaTestKey1234567890", r1.getHeader("x-goog-api-key"))

        val r2 = server.takeRequest()
        assertEquals("/upload/v1beta/files?upload_id=u1", r2.path)

        val r3 = server.takeRequest()
        assertEquals("/v1beta/interactions", r3.path)
        val json = JSONObject(r3.body.readUtf8())
        assertEquals("gemini-3.5-transcribe", json.getString("model"))
        assertEquals("audio", json.getJSONArray("input").getJSONObject(0).getString("type"))
        assertEquals("audio/mp4", json.getJSONArray("input").getJSONObject(0).getString("mime_type"))
        assertEquals("smart", json.getJSONObject("generation_config").getJSONObject("transcription_config").getString("mode"))

        val r4 = server.takeRequest()
        assertEquals("DELETE", r4.method)
        assertEquals("/v1beta/files/audio123", r4.path)
    }

    @Test
    fun coordinator_fallsBackAcrossRecoverableErrors_showsBadge_andDeletesTempFile() = runTest(StandardTestDispatcher()) {
        val failures = listOf(
            AiFailure.RateLimited(429),
            AiFailure.Offline,
            AiFailure.Timeout,
            AiFailure.ServerError(503),
            AiFailure.MalformedResponse
        )

        for (failure in failures) {
            val audioFile = File.createTempFile("stt_case_", ".m4a").apply { writeBytes(byteArrayOf(9, 8, 7)) }
            val recorder = FakeRecorder(RecordedAudio(audioFile, "audio/mp4", 1_200L))
            var fallbackCalls = 0
            var delivered: String? = null

            val coordinator = SpeechTranscriptionCoordinator(
                recorder = recorder,
                gemini = object : GeminiSpeechTranscriber {
                    override suspend fun transcribe(audio: RecordedAudio, customApiKey: String?) =
                        TranscribeResult.Failure(failure)
                },
                fallback = object : AndroidFallbackRecognizer {
                    override suspend fun recognize(): String {
                        fallbackCalls++
                        return "Fallback draft text"
                    }
                },
                scope = this,
                apiKeyProvider = { "AIzaTest" },
                badgeVisibleMs = 2_000L
            )

            assertTrue(coordinator.startRecording())
            assertFalse("Concurrent session must be rejected", coordinator.startRecording())

            coordinator.stopAndTranscribe { delivered = it }
            runCurrent()

            assertEquals("Expected fallback for $failure", 1, fallbackCalls)
            assertEquals("Fallback draft text", delivered)
            assertFalse("Temp audio file must be cleaned up", audioFile.exists())
            assertEquals("Using Android speech recognition", coordinator.state.value.statusLabel)

            // Badge stays visible until notice window elapses
            advanceTimeBy(1_500L)
            assertTrue(coordinator.state.value.fallbackBadgeVisible)
            advanceTimeBy(1_000L)
            assertFalse(coordinator.state.value.fallbackBadgeVisible)
            assertNull(coordinator.state.value.statusLabel)
        }
    }

    @Test
    fun coordinator_neverFallsBackOnCancelOrEmptyAudio() = runTest(StandardTestDispatcher()) {
        val shortFile = File.createTempFile("stt_short_", ".m4a").apply { writeBytes(byteArrayOf(1)) }
        var geminiCalled = false
        var fallbackCalled = false

        val coordinator = SpeechTranscriptionCoordinator(
            recorder = FakeRecorder(RecordedAudio(shortFile, "audio/mp4", 120L)),
            gemini = object : GeminiSpeechTranscriber {
                override suspend fun transcribe(audio: RecordedAudio, customApiKey: String?): TranscribeResult {
                    geminiCalled = true
                    return TranscribeResult.Success("x")
                }
            },
            fallback = object : AndroidFallbackRecognizer {
                override suspend fun recognize(): String? {
                    fallbackCalled = true
                    return "should not happen"
                }
            },
            scope = this,
            apiKeyProvider = { "AIzaTest" }
        )

        coordinator.startRecording()
        coordinator.stopAndTranscribe {}
        advanceUntilIdle()
        assertFalse(geminiCalled)
        assertFalse(fallbackCalled)
        assertFalse(shortFile.exists())

        val cancelFile = File.createTempFile("stt_cancel_", ".m4a").apply { writeBytes(byteArrayOf(1, 2)) }
        val c2 = SpeechTranscriptionCoordinator(
            recorder = FakeRecorder(RecordedAudio(cancelFile, "audio/mp4", 2_000L)),
            gemini = object : GeminiSpeechTranscriber {
                override suspend fun transcribe(audio: RecordedAudio, customApiKey: String?) = TranscribeResult.Success("x")
            },
            fallback = object : AndroidFallbackRecognizer {
                override suspend fun recognize() = "x"
            },
            scope = this,
            apiKeyProvider = { "AIzaTest" }
        )
        c2.startRecording()
        c2.cancel()
        advanceUntilIdle()
        assertFalse(cancelFile.exists())
        assertEquals(SpeechStatus.IDLE, c2.state.value.status)
    }

    private class FakeRecorder(private val audio: RecordedAudio) : AudioRecorder {
        override var isRecording: Boolean = false
        override fun start(): Boolean {
            if (isRecording) return false
            isRecording = true
            return true
        }
        override fun stop(): RecordedAudio? {
            if (!isRecording) return null
            isRecording = false
            return audio
        }
        override fun cancel() {
            isRecording = false
            audio.file.delete()
        }
    }
}
