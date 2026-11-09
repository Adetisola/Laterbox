package com.laterbox.app.ai

import android.util.Log
import com.google.firebase.Firebase
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
    private const val CLOUD_MODEL_NAME = "gemini-2.5-flash-lite"
    private const val AI_TIMEOUT_MS = 15_000L
    private const val GENERATE_CONTENT_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$CLOUD_MODEL_NAME:generateContent"

    suspend fun generateDigestCopy(
        link: SavedLink,
        userName: String? = null,
        origin: ReminderOrigin = ReminderOrigin.SMART_DIGEST,
        customApiKey: String? = null
    ): AiCopyResult {
        val apiKey = resolveApiKey(customApiKey)
            ?: return AiCopyResult.Failure(AiFailure.NoApiKey)

        return try {
            withTimeout(AI_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    executeGeminiRequest(link, userName, origin, apiKey)
                }
            }
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) {
                AiCopyResult.Failure(AiFailure.Timeout)
            } else {
                throw ce
            }
        } catch (e: UnknownHostException) {
            AiCopyResult.Failure(AiFailure.Offline)
        } catch (e: ConnectException) {
            AiCopyResult.Failure(AiFailure.Offline)
        } catch (e: SocketTimeoutException) {
            AiCopyResult.Failure(AiFailure.Timeout)
        } catch (e: IOException) {
            AiCopyResult.Failure(AiFailure.ServerError(code = -1, details = e.message))
        } catch (e: Exception) {
            AiCopyResult.Failure(AiFailure.ServerError(code = -1, details = e.message))
        }
    }

    private fun executeGeminiRequest(
        link: SavedLink,
        userName: String?,
        origin: ReminderOrigin,
        apiKey: String
    ): AiCopyResult {
        val prompt = buildPrompt(link, userName, origin)
        val payload = JSONObject().apply {
            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", prompt))
            }
            val contentsArray = JSONArray().apply {
                put(JSONObject().put("parts", partsArray))
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.85)
                put("maxOutputTokens", 160)
            })
        }.toString()

        val conn = (URL(GENERATE_CONTENT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
        }

        return try {
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            when (responseCode) {
                200 -> {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val rawContent = extractContentFromResponse(responseText)
                    if (rawContent.isNullOrBlank()) {
                        AiCopyResult.Failure(AiFailure.EmptyResponse)
                    } else {
                        val parsed = parseAiResponse(rawContent, origin)
                        if (parsed != null) {
                            AiCopyResult.Success(parsed)
                        } else {
                            AiCopyResult.Failure(AiFailure.MalformedResponse)
                        }
                    }
                }
                429 -> {
                    Log.w(TAG, "Gemini API rate limited ($responseCode)")
                    AiCopyResult.Failure(AiFailure.RateLimited(responseCode))
                }
                400, 401, 403 -> {
                    Log.w(TAG, "Gemini API authentication failed ($responseCode)")
                    AiCopyResult.Failure(AiFailure.AuthError(responseCode))
                }
                else -> {
                    val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.w(TAG, "Gemini API call failed ($responseCode)")
                    val detail = errorBody?.take(160)
                    AiCopyResult.Failure(AiFailure.ServerError(code = responseCode, details = detail))
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun extractContentFromResponse(responseJson: String): String? {
        return try {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            parts?.optJSONObject(0)?.optString("text")
        } catch (_: JSONException) {
            null
        }
    }

    fun resolveApiKey(customApiKey: String?): String? {
        if (!customApiKey.isNullOrBlank()) {
            return customApiKey.trim()
        }
        return try {
            Firebase.app.options.apiKey?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun testGeminiApi(customApiKey: String?): Pair<Boolean, String> {
        val apiKey = resolveApiKey(customApiKey)
            ?: return Pair(false, "No API key configured.")

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
                    Pair(true, "Connected to Gemini 2.5 Flash Lite")
                } else {
                    val errorStreamText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    val message = when {
                        errorStreamText.contains("API_KEY_SERVICE_BLOCKED") ->
                            "Google Generative Language API is blocked on this key. Please enable 'Generative Language API' in Google Cloud Console or use an AI Studio key."
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
                Pair(false, "Connection failed: ${e.message ?: "Unknown error"}")
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
