package com.laterbox.app.ai.speech

import com.laterbox.app.ai.AiFailure
import com.laterbox.app.ai.GeminiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Base64

sealed interface TranscribeResult {
    data class Success(val text: String) : TranscribeResult
    data class Failure(val reason: AiFailure) : TranscribeResult
}

interface GeminiSpeechTranscriber {
    suspend fun transcribe(audio: RecordedAudio, customApiKey: String?): TranscribeResult
}

/**
 * Recorded-audio transcription with gemini-3.5-transcribe (Files API + Interactions API) and a
 * Free-Tier-friendly fallback to gemini-flash-lite-latest (:generateContent with inline audio)
 * when the primary route hits 429, empty output, or 4xx/5xx server errors.
 */
class GeminiTranscribeClient(
    private val rootUrl: String = DEFAULT_ROOT_URL,
    private val timeoutMs: Long = 45_000L
) : GeminiSpeechTranscriber {

    override suspend fun transcribe(audio: RecordedAudio, customApiKey: String?): TranscribeResult {
        val apiKey = GeminiClient.resolveApiKey(customApiKey)
            ?: return TranscribeResult.Failure(AiFailure.NoApiKey)
        var remoteName: String? = null
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(timeoutMs) {
                    val primary: TranscribeResult = try {
                        val (name, uri) = upload(audio, apiKey)
                        remoteName = name
                        interact(uri, audio.mimeType, apiKey)
                    } catch (e: HttpFailure) {
                        TranscribeResult.Failure(e.toFailure())
                    }
                    if (primary is TranscribeResult.Failure && shouldTryFlashLiteFallback(primary.reason, audio)) {
                        val secondary = tryFlashLiteInline(audio, apiKey)
                        if (secondary is TranscribeResult.Success) secondary else primary
                    } else {
                        primary
                    }
                }
            }
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) TranscribeResult.Failure(AiFailure.Timeout) else throw ce
        } catch (_: UnknownHostException) {
            TranscribeResult.Failure(AiFailure.Offline)
        } catch (_: ConnectException) {
            TranscribeResult.Failure(AiFailure.Offline)
        } catch (_: SocketTimeoutException) {
            TranscribeResult.Failure(AiFailure.Timeout)
        } catch (e: HttpFailure) {
            TranscribeResult.Failure(e.toFailure())
        } catch (e: Exception) {
            TranscribeResult.Failure(
                AiFailure.ServerError(-1, GeminiClient.redactSecrets(e.javaClass.simpleName, apiKey))
            )
        } finally {
            remoteName?.let { deleteRemote(it, apiKey) }
        }
    }

    private fun shouldTryFlashLiteFallback(reason: AiFailure, audio: RecordedAudio): Boolean {
        if (audio.file.length() !in 1L..MAX_INLINE_BYTES) return false
        return reason is AiFailure.RateLimited ||
            reason is AiFailure.MalformedResponse ||
            reason is AiFailure.EmptyResponse ||
            reason is AiFailure.ServerError
    }

    private class HttpFailure(val code: Int, val body: String) : Exception("HTTP $code") {
        fun toFailure(): AiFailure = when {
            code == 429 -> AiFailure.RateLimited(code)
            code == 401 || code == 403 -> AiFailure.AuthError(code)
            else -> AiFailure.ServerError(code)
        }
    }

    private fun open(url: String, method: String, apiKey: String): HttpURLConnection {
        val parsed = URL(url)
        val isLoopback = parsed.host.equals("127.0.0.1", ignoreCase = true) ||
            parsed.host.equals("localhost", ignoreCase = true)
        val conn = (if (isLoopback) parsed.openConnection(Proxy.NO_PROXY) else parsed.openConnection()) as HttpURLConnection
        return conn.apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("x-goog-api-key", apiKey)
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private suspend fun upload(audio: RecordedAudio, apiKey: String): Pair<String?, String> {
        val length = audio.file.length()
        val start = open("$rootUrl/upload/v1beta/files", "POST", apiKey).apply {
            doOutput = true
            setRequestProperty("X-Goog-Upload-Protocol", "resumable")
            setRequestProperty("X-Goog-Upload-Command", "start")
            setRequestProperty("X-Goog-Upload-Header-Content-Length", length.toString())
            setRequestProperty("X-Goog-Upload-Header-Content-Type", audio.mimeType)
            setRequestProperty("Content-Type", "application/json")
        }
        val uploadUrl: String
        try {
            start.outputStream.use { it.write("""{"file":{"display_name":"laterbox_dictation"}}""".toByteArray()) }
            if (start.responseCode !in 200..299) throw HttpFailure(start.responseCode, readBody(start))
            uploadUrl = start.getHeaderField("X-Goog-Upload-URL") ?: throw IllegalStateException("no upload url")
        } finally {
            start.disconnect()
        }
        val up = open(uploadUrl, "POST", apiKey).apply {
            doOutput = true
            setFixedLengthStreamingMode(length)
            setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
            setRequestProperty("X-Goog-Upload-Offset", "0")
        }
        try {
            audio.file.inputStream().use { input -> up.outputStream.use { input.copyTo(it) } }
            val body = readBody(up)
            if (up.responseCode !in 200..299) throw HttpFailure(up.responseCode, body)
            val file = JSONObject(body).getJSONObject("file")
            val name = file.optString("name").takeIf { it.isNotBlank() }
            val uri = file.getString("uri")
            if (name != null && file.optString("state") == "PROCESSING") {
                waitUntilFileActive(name, apiKey)
            }
            return name to uri
        } finally {
            up.disconnect()
        }
    }

    private suspend fun waitUntilFileActive(name: String, apiKey: String) {
        repeat(6) {
            delay(250)
            val conn = open("$rootUrl/v1beta/$name", "GET", apiKey)
            try {
                if (conn.responseCode in 200..299) {
                    val state = JSONObject(readBody(conn)).optString("state")
                    if (state != "PROCESSING") return
                } else {
                    return
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun interact(fileUri: String, mimeType: String, apiKey: String): TranscribeResult {
        val payload = buildInteractionPayload(fileUri, mimeType)
        val conn = open("$rootUrl/v1beta/interactions", "POST", apiKey).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(payload.toByteArray()) }
            val body = readBody(conn)
            if (conn.responseCode !in 200..299) throw HttpFailure(conn.responseCode, body)
            val text = parseTranscript(body)
            return if (text.isNullOrBlank()) TranscribeResult.Failure(AiFailure.MalformedResponse)
            else TranscribeResult.Success(text.trim())
        } finally {
            conn.disconnect()
        }
    }

    private fun tryFlashLiteInline(audio: RecordedAudio, apiKey: String): TranscribeResult {
        return try {
            val encoded = Base64.getEncoder().encodeToString(audio.file.readBytes())
            val payload = buildInlineGeneratePayload(encoded, audio.mimeType)
            val conn = open("$rootUrl/v1beta/models/${GeminiClient.CLOUD_MODEL_NAME}:generateContent", "POST", apiKey).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                conn.outputStream.use { it.write(payload.toByteArray()) }
                val body = readBody(conn)
                if (conn.responseCode !in 200..299) {
                    return TranscribeResult.Failure(HttpFailure(conn.responseCode, body).toFailure())
                }
                val text = GeminiClient.extractTextFromResponseJson(body)?.trim()
                if (text.isNullOrBlank()) TranscribeResult.Failure(AiFailure.MalformedResponse)
                else TranscribeResult.Success(text)
            } finally {
                conn.disconnect()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            TranscribeResult.Failure(AiFailure.ServerError(-1))
        }
    }

    private fun deleteRemote(name: String, apiKey: String) {
        runCatching {
            val conn = open("$rootUrl/v1beta/$name", "DELETE", apiKey)
            try { conn.responseCode } finally { conn.disconnect() }
        }
    }

    companion object {
        const val MODEL = "gemini-3.5-transcribe"
        const val DEFAULT_ROOT_URL = "https://generativelanguage.googleapis.com"
        const val MAX_INLINE_BYTES = 15L * 1024L * 1024L

        fun buildInteractionPayload(fileUri: String, mimeType: String): String =
            JSONObject().apply {
                put("model", MODEL)
                put("input", JSONArray().put(JSONObject().apply {
                    put("type", "audio")
                    put("uri", fileUri)
                    put("mime_type", mimeType)
                }))
                put("generation_config", JSONObject().put(
                    "transcription_config", JSONObject().put("mode", "smart")
                ))
            }.toString()

        fun buildInlineGeneratePayload(base64Audio: String, mimeType: String): String =
            JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put(
                            "text",
                            "Transcribe this spoken audio accurately with natural punctuation. Return only the transcript text."
                        ))
                        put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", mimeType)
                            put("data", base64Audio)
                        }))
                    })
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                    put("maxOutputTokens", 1024)
                })
            }.toString()

        /** Tolerant parser across the response shapes the Interactions API may return. */
        fun parseTranscript(json: String): String? {
            return try {
                val root = JSONObject(json)
                root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
                val sb = StringBuilder()
                fun collect(arr: JSONArray?) {
                    if (arr == null) return
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        o.optString("text").takeIf { it.isNotBlank() }?.let { sb.append(it) }
                        collect(o.optJSONArray("content"))
                        collect(o.optJSONArray("parts"))
                    }
                }
                collect(root.optJSONArray("outputs"))
                if (sb.isEmpty()) collect(root.optJSONArray("steps"))
                if (sb.isEmpty()) {
                    val cands = root.optJSONArray("candidates")
                    collect(cands?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts"))
                }
                sb.toString().takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }
}
