package com.laterbox.app.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.app
import com.laterbox.app.data.preferences.AiEngine
import com.laterbox.app.domain.model.SavedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object SmartDigestEngine {

    private const val TAG = "SmartDigestEngine"
    private const val CLOUD_MODEL_NAME = "gemini-2.5-flash-lite"
    private const val AI_TIMEOUT_MS = 15_000L

    suspend fun generateDigestCopy(
        link: SavedLink,
        userName: String? = null,
        origin: ReminderOrigin = ReminderOrigin.SMART_DIGEST,
        engine: AiEngine = AiEngine.CLOUD,
        customApiKey: String? = null
    ): SmartReminderCopy {
        // Cloud AI via Google AI Studio is the sole, primary intelligence engine.
        // Deterministic local generator is used strictly as a safe offline/error fallback.
        return try {
            val aiResult = withTimeoutOrNull(AI_TIMEOUT_MS) {
                generateCloudAiCopy(link, userName, origin, customApiKey)
            }
            aiResult ?: ContextAwareCopyGenerator.generateCopy(link, userName, origin = origin)
        } catch (t: Throwable) {
            Log.w(TAG, "Cloud AI generation failed or unavailable; falling back to context copy: ${t.message}")
            ContextAwareCopyGenerator.generateCopy(link, userName, origin = origin)
        }
    }

    private suspend fun generateCloudAiCopy(
        link: SavedLink,
        userName: String?,
        origin: ReminderOrigin,
        customApiKey: String?
    ): SmartReminderCopy? {
        val prompt = buildPrompt(link, userName, origin)

        // 1. Direct Google Generative Language API (Primary — uses Google AI Studio endpoint without App Check friction)
        try {
            val directResponse = withContext(Dispatchers.IO) {
                generateDirectGeminiContent(prompt, customApiKey)
            }
            if (!directResponse.isNullOrBlank()) {
                val parsed = parseAiResponse(directResponse, origin)
                if (parsed != null) return parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Gemini API attempt failed (${e.message}). Falling back to Firebase AI SDK...")
        }

        // 2. Firebase AI SDK attempt
        try {
            val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = CLOUD_MODEL_NAME,
                generationConfig = generationConfig {
                    temperature = 0.85f
                    maxOutputTokens = 160
                }
            )
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text?.trim()
            if (!responseText.isNullOrBlank()) {
                val parsed = parseAiResponse(responseText, origin)
                if (parsed != null) return parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI SDK attempt also failed: ${e.message}")
        }

        return null
    }

    fun resolveApiKey(customApiKey: String?): String? {
        if (!customApiKey.isNullOrBlank()) {
            return customApiKey.trim()
        }
        return try {
            Firebase.app.options.apiKey
        } catch (_: Exception) {
            null
        }
    }

    private fun generateDirectGeminiContent(prompt: String, customApiKey: String?): String? {
        val apiKey = resolveApiKey(customApiKey) ?: return null

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$CLOUD_MODEL_NAME:generateContent?key=$apiKey"
        val jsonPayload = JSONObject().apply {
            val partsArray = org.json.JSONArray().apply {
                put(JSONObject().put("text", prompt))
            }
            val contentsArray = org.json.JSONArray().apply {
                put(JSONObject().put("parts", partsArray))
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.85)
                put("maxOutputTokens", 160)
            })
        }.toString()

        val url = java.net.URL(endpoint)
        val conn = url.openConnection() as java.net.HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.doOutput = true

            conn.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val candidates = root.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                parts?.optJSONObject(0)?.optString("text")
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Log.w(TAG, "Direct Gemini call returned error ($responseCode): $errorText")
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun testGeminiApi(customApiKey: String?): Pair<Boolean, String> {
        val apiKey = resolveApiKey(customApiKey) ?: return Pair(false, "No API key configured.")

        return withContext(Dispatchers.IO) {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$CLOUD_MODEL_NAME:generateContent?key=$apiKey"
            val jsonPayload = JSONObject().apply {
                val partsArray = org.json.JSONArray().apply {
                    put(JSONObject().put("text", "Respond with exact JSON: {\"title\":\"Connected\",\"body\":\"Gemini 2.5 Flash Lite is active\"}"))
                }
                val contentsArray = org.json.JSONArray().apply {
                    put(JSONObject().put("parts", partsArray))
                }
                put("contents", contentsArray)
            }.toString()

            try {
                val conn = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.doOutput = true

                conn.outputStream.use { it.write(jsonPayload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code == 200) {
                    Pair(true, "Connected to Gemini 2.5 Flash Lite")
                } else {
                    val errorStreamText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    val message = if (errorStreamText.contains("API_KEY_SERVICE_BLOCKED")) {
                        "Google Generative Language API is blocked on this key. In Google Cloud Console, enable 'Generative Language API' or enter a Gemini key from Google AI Studio."
                    } else if (code == 400 || code == 403) {
                        "Invalid API key or unauthorized ($code)."
                    } else {
                        "Error ($code): ${errorStreamText.take(120)}"
                    }
                    Pair(false, message)
                }
            } catch (e: Exception) {
                Pair(false, "Connection failed: ${e.message}")
            }
        }
    }

    private fun buildPrompt(link: SavedLink, userName: String?, origin: ReminderOrigin): String {
        val userGreeting = if (!userName.isNullOrBlank()) "The user's name is $userName." else "No user name provided."
        val noteSection = if (!link.personalNote.isNullOrBlank()) {
            """
            User's personal note: "${link.personalNote}"
            IMPORTANT: Do NOT quote or repeat this note in quotes! Instead, understand the user's underlying intent (what they plan to do, e.g. install, read, test, buy, watch) and articulate it in your own words.
            """.trimIndent()
        } else {
            "No personal note left by the user."
        }
        val descriptionSection = if (!link.description.isNullOrBlank()) {
            "Description: \"${link.description.take(160)}\"."
        } else ""

        val contextIntent = if (origin == ReminderOrigin.MANUAL) {
            "The user explicitly scheduled a reminder for this link right now. Mention their goal naturally, warmly, and freshly without cliché reminder templates."
        } else {
            "This is an intelligent background digest nudge for an unread link in their inbox. Nudge them thoughtfully about why it's worth checking out."
        }

        return """
            You are Laterbox's personal bookmarking and reading companion.
            Craft a short, natural, human notification for this saved link.
            
            Context:
            - Intent: $contextIntent
            - User: $userGreeting
            - Title: "${link.title}"
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

        // 1. Robust regex extraction (works on both JVM unit tests and Android runtime)
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

        // 2. Fallback to JSONObject parser
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
        } catch (_: Throwable) {
            null
        }
    }
}
