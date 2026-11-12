package com.laterbox.app.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.app
import com.laterbox.app.domain.model.SavedLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

object SmartDigestEngine {

    private const val TAG = "SmartDigestEngine"
    private const val CLOUD_MODEL_NAME = "gemini-flash-lite-latest"
    private const val AI_TIMEOUT_MS = 15_000L
    private const val GENERATE_CONTENT_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$CLOUD_MODEL_NAME:generateContent"

    suspend fun generateDigestCopy(
        link: SavedLink,
        userName: String? = null,
        origin: ReminderOrigin = ReminderOrigin.SMART_DIGEST,
        customApiKey: String? = null
    ): AiCopyResult {
        val prompt = buildPrompt(link, userName, origin)
        return when (val textResult = GeminiClient.generateText(
            turns = listOf(GeminiTurn(GeminiRole.USER, prompt)),
            customApiKey = customApiKey,
            options = GeminiGenerationOptions(temperature = 0.85f, maxOutputTokens = 160),
            timeoutMs = AI_TIMEOUT_MS
        )) {
            is GeminiTextResult.Success -> {
                val parsed = parseAiResponse(textResult.text, origin)
                if (parsed != null) {
                    AiCopyResult.Success(parsed)
                } else {
                    AiCopyResult.Failure(AiFailure.MalformedResponse)
                }
            }
            is GeminiTextResult.Failure -> AiCopyResult.Failure(textResult.reason)
        }
    }

    fun resolveApiKey(customApiKey: String?): String? = GeminiClient.resolveApiKey(customApiKey)

    suspend fun testGeminiApi(customApiKey: String?): Pair<Boolean, String> {
        val apiKey = resolveApiKey(customApiKey)
        if (!apiKey.isNullOrBlank()) {
            return testDirectGeminiApi(apiKey)
        }

        // If no direct key is configured, test Firebase AI Logic SDK
        try {
            return withTimeout(AI_TIMEOUT_MS) {
                val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = CLOUD_MODEL_NAME
                )
                val response = generativeModel.generateContent(
                    "Respond with exact JSON: {\"title\":\"Connected\",\"body\":\"Google AI Studio is active\"}"
                )
                val text = response.text?.trim() ?: ""
                if (text.isNotBlank()) {
                    Pair(true, "Connected to Google AI Studio (Firebase AI Logic)")
                } else {
                    Pair(false, "Received empty response from Google AI Studio")
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI test failed (${e.javaClass.simpleName})")
        }

        return Pair(
            false,
            "No API key entered. Paste your Google AI Studio API key (AIzaSy...) from aistudio.google.com above."
        )
    }

    private suspend fun testDirectGeminiApi(apiKey: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(
                        JSONObject().put(
                            "text",
                            "Respond with exact JSON: {\"title\":\"Connected\",\"body\":\"Gemini 2.5 Flash Lite is active\"}"
                        )
                    )
                }
                val contentsArray = JSONArray().apply {
                    put(JSONObject().put("parts", partsArray))
                }
                put("contents", contentsArray)
            }.toString()

            val conn = (URL(GENERATE_CONTENT_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
            }

            try {
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code == 200) {
                    Pair(true, "Connected to Gemini (Google AI Studio)")
                } else {
                    val errorStreamText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    val message = when {
                        errorStreamText.contains("API_KEY_SERVICE_BLOCKED") ->
                            "Google Generative Language API is blocked on this key. In Google Cloud Console, select 'Don't restrict key' or add 'Generative Language API'."
                        code == 429 ->
                            "Rate limit reached (429). Please wait a moment."
                        code == 400 || code == 401 || code == 403 ->
                            "Invalid API key or unauthorized ($code)."
                        else ->
                            "Server error ($code)."
                    }
                    Pair(false, message)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: UnknownHostException) {
                Pair(false, "Network connection unavailable")
            } catch (e: ConnectException) {
                Pair(false, "Network connection unavailable")
            } catch (e: SocketTimeoutException) {
                Pair(false, "Connection timed out")
            } catch (e: Exception) {
                Pair(false, "Connection failed: ${GeminiClient.redactSecrets(e.message, apiKey).ifBlank { "Unknown error" }}")
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun buildPrompt(link: SavedLink, userName: String?, origin: ReminderOrigin): String {
        val userGreeting = if (!userName.isNullOrBlank()) {
            "The user's name is $userName."
        } else {
            "No user name provided."
        }

        val noteSection = if (!link.personalNote.isNullOrBlank()) {
            """
            User's personal note: "${link.personalNote.take(300)}"
            IMPORTANT: Do NOT quote or repeat this note verbatim in quotes. Instead, articulate their intended action or purpose naturally.
            """.trimIndent()
        } else {
            "No personal note left by the user."
        }

        val descriptionSection = if (!link.description.isNullOrBlank()) {
            "Description: \"${link.description.take(160)}\"."
        } else {
            ""
        }

        val contextIntent = if (origin == ReminderOrigin.MANUAL) {
            "The user explicitly scheduled a reminder for this link right now. Articulate their goal naturally, warmly, and freshly without cliché reminder templates."
        } else {
            "This is an intelligent background digest nudge for an unread link in their inbox. Nudge them thoughtfully about why it is worth revisiting."
        }

        return """
            You are Laterbox's personal bookmarking and reading companion.
            Craft a short, natural, human notification for this saved link.
            
            Context:
            - Intent: $contextIntent
            - User: $userGreeting
            - Title: "${link.title.take(120)}"
            - Domain: "${link.domain}"
            - Site Name: "${link.siteName ?: link.domain}"
            - Content Type: ${link.contentType.displayName}
            - $noteSection
            - $descriptionSection
            
            Strict Guidelines:
            - NEVER wrap the personal note in quotation marks. Synthesize their intent naturally into your message.
            - Speak like a witty, intelligent personal assistant who understands the context.
            - Never use generic robot phrases like 'You asked to be reminded:'.
            - Title MUST be punchy and under 35 characters.
            - Body MUST be under 110 characters so it fits on Android notification drawers.
            - NEVER use the words 'AI', 'robot', 'assistant', 'machine learning', or 'algorithm'.
            - Vary your tone and delivery every single time.
            - Output MUST be valid JSON only with keys "title" and "body".
            
            Example:
            {"title":"Ready for Clipsift?","body":"Hey Timi, here is that image compressor you wanted to test out and install."}
        """.trimIndent()
    }

    fun parseAiResponse(rawText: String, origin: ReminderOrigin = ReminderOrigin.SMART_DIGEST): SmartReminderCopy? {
        val clean = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // 1. Fast regex extraction
        val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(clean)?.groupValues?.get(1)?.trim()
        val bodyMatch = Regex("\"body\"\\s*:\\s*\"([^\"]+)\"").find(clean)?.groupValues?.get(1)?.trim()

        if (!titleMatch.isNullOrBlank() && !bodyMatch.isNullOrBlank()) {
            return SmartReminderCopy(
                title = titleMatch.take(50),
                body = bodyMatch.take(150),
                isAiGenerated = true,
                origin = origin
            )
        }

        // 2. Strict JSONObject parser
        return try {
            val jsonObject = JSONObject(clean)
            val title = jsonObject.optString("title").trim()
            val body = jsonObject.optString("body").trim()

            if (title.isNotEmpty() && body.isNotEmpty()) {
                SmartReminderCopy(
                    title = title.take(50),
                    body = body.take(150),
                    isAiGenerated = true,
                    origin = origin
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
