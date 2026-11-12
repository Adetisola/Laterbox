package com.laterbox.app.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun buildRequestPayload_includesSystemInstructionAndTurns() {
        val payload = GeminiClient.buildRequestPayload(
            turns = listOf(
                GeminiTurn(GeminiRole.USER, "Hi"),
                GeminiTurn(GeminiRole.MODEL, "Hello!"),
                GeminiTurn(GeminiRole.USER, "Check links")
            ),
            systemInstruction = "Be helpful and concise.",
            options = GeminiGenerationOptions(temperature = 0.5f, maxOutputTokens = 512)
        )

        val root = JSONObject(payload)
        assertEquals(
            "Be helpful and concise.",
            root.getJSONObject("system_instruction").getJSONArray("parts").getJSONObject(0).getString("text")
        )
        val contents = root.getJSONArray("contents")
        assertEquals(3, contents.length())
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals("user", contents.getJSONObject(2).getString("role"))
        assertEquals("Check links", contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals(512, root.getJSONObject("generationConfig").getInt("maxOutputTokens"))
    }

    @Test
    fun streamChat_parsesSseDeltasAndEmitsCompleted() = runBlocking {
        val sseBody = buildString {
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello \"}]}}]}\n\n")
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Laterbox!\"}]}}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody)
        )

        val baseUrl = server.url("/v1beta/models").toString().trimEnd('/')
        val events = GeminiClient.streamChat(
            turns = listOf(GeminiTurn(GeminiRole.USER, "Say hi")),
            systemInstruction = "System guide",
            customApiKey = "AIzaSyTestFakeKey12345678901234567",
            baseUrl = baseUrl
        ).toList()

        val recorded = server.takeRequest()
        assertEquals(
            "/v1beta/models/${GeminiClient.CLOUD_MODEL_NAME}:streamGenerateContent?alt=sse",
            recorded.path
        )
        assertEquals("AIzaSyTestFakeKey12345678901234567", recorded.getHeader("x-goog-api-key"))

        assertEquals(
            listOf(
                GeminiStreamEvent.Delta("Hello "),
                GeminiStreamEvent.Delta("Laterbox!"),
                GeminiStreamEvent.Completed("Hello Laterbox!")
            ),
            events
        )
    }

    @Test
    fun streamChat_flushesCumulativeOrTrailingBufferWithoutTrailingBlankLine() = runBlocking {
        val sseBody = buildString {
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]}}]}\n\n")
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi there\"}]}}]}\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody)
        )

        val baseUrl = server.url("/v1beta/models").toString().trimEnd('/')
        val events = GeminiClient.streamChat(
            turns = listOf(GeminiTurn(GeminiRole.USER, "Test")),
            customApiKey = "AIzaSyTestFakeKey12345678901234567",
            baseUrl = baseUrl
        ).toList()

        assertEquals(
            listOf(
                GeminiStreamEvent.Delta("Hi"),
                GeminiStreamEvent.Delta(" there"),
                GeminiStreamEvent.Completed("Hi there")
            ),
            events
        )
    }

    @Test
    fun streamChat_handlesRateLimitAuthServerAndEmptyErrors() = runBlocking {
        val baseUrl = server.url("/v1beta/models").toString().trimEnd('/')
        val testKey = "AIzaSySecretKey12345678901234567890"

        server.enqueue(MockResponse().setResponseCode(429).setBody("quota exceeded"))
        val rateLimited = GeminiClient.streamChat(
            turns = listOf(GeminiTurn(GeminiRole.USER, "q")),
            customApiKey = testKey,
            baseUrl = baseUrl
        ).toList()
        assertEquals(listOf(GeminiStreamEvent.Error(AiFailure.RateLimited(429))), rateLimited)

        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        val authError = GeminiClient.streamChat(
            turns = listOf(GeminiTurn(GeminiRole.USER, "q")),
            customApiKey = testKey,
            baseUrl = baseUrl
        ).toList()
        assertEquals(listOf(GeminiStreamEvent.Error(AiFailure.AuthError(401))), authError)

        server.enqueue(MockResponse().setResponseCode(500).setBody("error with key $testKey"))
        val serverError = GeminiClient.streamChat(
            turns = listOf(GeminiTurn(GeminiRole.USER, "q")),
            customApiKey = testKey,
            baseUrl = baseUrl
        ).toList()
        val err = (serverError.single() as GeminiStreamEvent.Error).reason as AiFailure.ServerError
        assertEquals(500, err.code)
        assertFalse("Secret API key must be scrubbed from error details", err.details.orEmpty().contains(testKey))

        server.enqueue(MockResponse().setResponseCode(200).setBody("data: [DONE]\n\n"))
        val empty = GeminiClient.streamChat(
            turns = listOf(GeminiTurn(GeminiRole.USER, "q")),
            customApiKey = testKey,
            baseUrl = baseUrl
        ).toList()
        assertEquals(listOf(GeminiStreamEvent.Error(AiFailure.EmptyResponse)), empty)
    }

    @Test
    fun generateText_and_streamChat_mapUnreachablePortToOffline() = runBlocking {
        val freePort = ServerSocket(0).use { it.localPort }
        val deadBaseUrl = "http://127.0.0.1:$freePort/v1beta/models"

        val textResult = GeminiClient.generateText(
            turns = listOf(GeminiTurn(GeminiRole.USER, "hello")),
            customApiKey = "AIzaSyTestFakeKey12345678901234567",
            baseUrl = deadBaseUrl
        )
        assertEquals(GeminiTextResult.Failure(AiFailure.Offline), textResult)

        val streamResult = GeminiClient.streamChat(
            turns = listOf(GeminiTurn(GeminiRole.USER, "hello")),
            customApiKey = "AIzaSyTestFakeKey12345678901234567",
            baseUrl = deadBaseUrl
        ).toList()
        assertEquals(listOf(GeminiStreamEvent.Error(AiFailure.Offline)), streamResult)
    }

    @Test
    fun redactSecrets_scrubsExplicitAndPatternApiKeys() {
        val key = "custom-secret-token-xyz"
        val aiza = "AIzaSyAbCdEfGhIjKlMnOpQrStUvWxYz123456"
        val cleaned = GeminiClient.redactSecrets("Failed calling with $key and $aiza", key)
        assertFalse(cleaned.contains(key))
        assertFalse(cleaned.contains(aiza))
        assertTrue(cleaned.contains("[REDACTED]"))
        assertTrue(cleaned.contains("[REDACTED_KEY]"))
    }
}
