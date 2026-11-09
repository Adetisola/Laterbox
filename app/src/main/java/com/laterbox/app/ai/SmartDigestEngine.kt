package com.laterbox.app.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.generationConfig
import com.google.firebase.vertexai.vertexAI
import com.laterbox.app.data.preferences.AiEngine
import com.laterbox.app.domain.model.SavedLink
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object SmartDigestEngine {

    private const val TAG = "SmartDigestEngine"
    private const val CLOUD_MODEL_NAME = "gemini-2.5-flash-lite"
    private const val AI_TIMEOUT_MS = 6_000L

    suspend fun generateDigestCopy(
        link: SavedLink,
        userName: String? = null,
        engine: AiEngine = AiEngine.CLOUD
    ): SmartReminderCopy {
        if (engine == AiEngine.ON_DEVICE) {
            // Private on-device mode: strictly local execution with zero network transmission
            return ContextAwareCopyGenerator.generateCopy(link, userName)
        }

        return try {
            val aiResult = withTimeoutOrNull(AI_TIMEOUT_MS) {
                generateCloudAiCopy(link, userName)
            }
            aiResult ?: ContextAwareCopyGenerator.generateCopy(link, userName)
        } catch (t: Throwable) {
            Log.w(TAG, "Cloud AI generation failed or unavailable; falling back to context copy: ${t.message}")
            ContextAwareCopyGenerator.generateCopy(link, userName)
        }
    }

    private suspend fun generateCloudAiCopy(
        link: SavedLink,
        userName: String?
    ): SmartReminderCopy? {
        val generativeModel = Firebase.vertexAI.generativeModel(
            modelName = CLOUD_MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.7f
                maxOutputTokens = 160
            }
        )

        val prompt = buildPrompt(link, userName)
        val response = generativeModel.generateContent(prompt)
        val responseText = response.text?.trim() ?: return null

        return parseAiResponse(responseText)
    }

    private fun buildPrompt(link: SavedLink, userName: String?): String {
        val userGreeting = if (!userName.isNullOrBlank()) "The user's name is $userName." else "No user name provided."
        val noteSection = if (!link.personalNote.isNullOrBlank()) "User personal note: \"${link.personalNote}\"." else "No personal note."
        val descriptionSection = if (!link.description.isNullOrBlank()) "Description: \"${link.description.take(150)}\"." else ""

        return """
            You are a calm, quiet personal assistant for Laterbox, an inbox where users save links to revisit later.
            Generate a thoughtful, natural notification reminding the user about a saved link.
            
            Context:
            - $userGreeting
            - Title: "${link.title}"
            - Domain: "${link.domain}"
            - Site Name: "${link.siteName ?: link.domain}"
            - Content Type: ${link.contentType.displayName}
            - $noteSection
            - $descriptionSection
            
            Strict Guidelines:
            - NEVER use the words 'AI', 'robot', 'assistant', 'machine learning', or 'algorithm'.
            - If user's name is given, address them warmly (e.g., 'Hi Sarah, ...').
            - Be concise, friendly, and non-intrusive.
            - Output MUST be valid JSON only with keys "title" (under 40 chars) and "body" (under 120 chars).
            
            Example:
            {"title":"Revisit your saved reel","body":"Hi Alex, seems like you haven't checked the Instagram video you saved a few days ago."}
        """.trimIndent()
    }

    fun parseAiResponse(rawText: String): SmartReminderCopy? {
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
                isAiGenerated = true
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
                    isAiGenerated = true
                )
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
