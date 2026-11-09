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
        engine: AiEngine = AiEngine.CLOUD
    ): SmartReminderCopy {
        // Cloud AI via Google AI Studio is the sole, primary intelligence engine.
        // Deterministic local generator is used strictly as a safe offline/error fallback.
        return try {
            val aiResult = withTimeoutOrNull(AI_TIMEOUT_MS) {
                generateCloudAiCopy(link, userName, origin)
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
        origin: ReminderOrigin
    ): SmartReminderCopy? {
        val prompt = buildPrompt(link, userName, origin)

        // 1. Attempt official Firebase AI SDK
        try {
            val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = CLOUD_MODEL_NAME,
                generationConfig = generationConfig {
                    temperature = 0.7f
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
            Log.w(TAG, "Firebase AI SDK attempt failed (${e.javaClass.simpleName}: ${e.message}). Attempting direct Google AI endpoint fallback...")
        }

        // 2. Direct Google Generative Language API call with project API key fallback
        try {
            val directResponse = withContext(Dispatchers.IO) {
                generateDirectGeminiContent(prompt)
            }
            if (!directResponse.isNullOrBlank()) {
                val parsed = parseAiResponse(directResponse, origin)
                if (parsed != null) return parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Gemini API fallback failed: ${e.message}")
        }

        return null
    }

    private fun generateDirectGeminiContent(prompt: String): String? {
        val apiKey = try {
            Firebase.app.options.apiKey
        } catch (_: Exception) {
            null
        } ?: return null

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
                put("temperature", 0.7)
                put("maxOutputTokens", 160)
            })
        }.toString()

        val url = java.net.URL(endpoint)
        val conn = url.openConnection() as java.net.HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
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

    private fun buildPrompt(link: SavedLink, userName: String?, origin: ReminderOrigin): String {
        val userGreeting = if (!userName.isNullOrBlank()) "The user's name is $userName." else "No user name provided."
        val noteSection = if (!link.personalNote.isNullOrBlank()) "User personal note: \"${link.personalNote}\"." else "No personal note."
        val descriptionSection = if (!link.description.isNullOrBlank()) "Description: \"${link.description.take(150)}\"." else ""
        val contextIntent = if (origin == ReminderOrigin.MANUAL) {
            "The user explicitly asked to be reminded about this link. Acknowledge their intention naturally and warmly (e.g., 'Hi Timi, here is what you scheduled to revisit...')."
        } else {
            "This is a quiet, proactive background digest reminder for an unread link in their inbox. Nudge gently and naturally."
        }

        return """
            You are a calm, quiet personal assistant for Laterbox, an inbox where users save links to revisit later.
            Generate a thoughtful, natural notification reminding the user about a saved link.
            
            Context:
            - $contextIntent
            - $userGreeting
            - Title: "${link.title}"
            - Domain: "${link.domain}"
            - Site Name: "${link.siteName ?: link.domain}"
            - Content Type: ${link.contentType.displayName}
            - $noteSection
            - $descriptionSection
            
            Strict Guidelines:
            - NEVER use the words 'AI', 'robot', 'assistant', 'machine learning', or 'algorithm'.
            - If user's name is given, address them warmly.
            - Be concise, friendly, and human.
            - Output MUST be valid JSON only with keys "title" (under 40 chars) and "body" (under 120 chars).
            
            Example:
            {"title":"Scheduled link ready","body":"Hi Timi, here is that video from YouTube you asked to revisit."}
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
