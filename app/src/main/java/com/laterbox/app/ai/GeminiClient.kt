package com.laterbox.app.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.laterbox.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

enum class GeminiRole(val apiValue: String) {
    USER("user"),
    MODEL("model")
}

data class GeminiTurn(
    val role: GeminiRole,
    val text: String
)

data class GeminiGenerationOptions(
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 1024
)

sealed interface GeminiTextResult {
    data class Success(val text: String) : GeminiTextResult
    data class Failure(val reason: AiFailure) : GeminiTextResult
}

sealed interface GeminiStreamEvent {
    data class Delta(val chunk: String) : GeminiStreamEvent
    data class Completed(val fullText: String) : GeminiStreamEvent
    data class Error(val reason: AiFailure) : GeminiStreamEvent
}

object GeminiClient {

    private const val TAG = "GeminiClient"
    const val CLOUD_MODEL_NAME = "gemini-flash-lite-latest"
    const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    fun resolveApiKey(customApiKey: String?): String? {
        if (!customApiKey.isNullOrBlank()) {
            return customApiKey.trim()
        }
        val embedded = BuildConfig.EMBEDDED_GEMINI_API_KEY
        if (embedded.isNotBlank()) {
            return embedded.trim()
        }
        return null
    }

    fun redactSecrets(text: String?, apiKey: String? = null): String {
        if (text.isNullOrBlank()) return ""
        var cleaned = text
        if (!apiKey.isNullOrBlank()) {
            cleaned = cleaned.replace(apiKey, "[REDACTED]")
        }
        cleaned = cleaned.replace(Regex("AIza[0-9A-Za-z_-]{20,}"), "[REDACTED_KEY]")
        return cleaned
    }

    fun buildRequestPayload(
        turns: List<GeminiTurn>,
        systemInstruction: String? = null,
        options: GeminiGenerationOptions = GeminiGenerationOptions()
    ): String {
        return JSONObject().apply {
            if (!systemInstruction.isNullOrBlank()) {
                put(
                    "system_instruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
            }
            val contentsArray = JSONArray()
            for (turn in turns) {
                val partObj = JSONObject().put("text", turn.text)
                val turnObj = JSONObject().apply {
                    put("role", turn.role.apiValue)
                    put("parts", JSONArray().put(partObj))
                }
                contentsArray.put(turnObj)
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", options.temperature.toDouble())
                put("maxOutputTokens", options.maxOutputTokens)
            })
        }.toString()
    }

    fun extractTextFromResponseJson(responseJson: String): String? {
        return try {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates") ?: return null
            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val partText = parts.optJSONObject(i)?.optString("text")
                if (!partText.isNullOrEmpty()) {
                    sb.append(partText)
                }
            }
            sb.toString().takeIf { it.isNotEmpty() }
        } catch (_: JSONException) {
            null
        }
    }

    suspend fun generateText(
        turns: List<GeminiTurn>,
        systemInstruction: String? = null,
        customApiKey: String? = null,
        options: GeminiGenerationOptions = GeminiGenerationOptions(),
        timeoutMs: Long = 20_000L,
        baseUrl: String = DEFAULT_BASE_URL
    ): GeminiTextResult {
        val apiKey = resolveApiKey(customApiKey)
        if (!apiKey.isNullOrBlank()) {
            return try {
                withContext(Dispatchers.IO) {
                    withTimeout(timeoutMs) {
                        executeRestGenerateContent(
                            turns = turns,
                            systemInstruction = systemInstruction,
                            apiKey = apiKey,
                            options = options,
                            baseUrl = baseUrl
                        )
                    }
                }
            } catch (ce: CancellationException) {
                if (ce is TimeoutCancellationException) {
                    GeminiTextResult.Failure(AiFailure.Timeout)
                } else {
                    throw ce
                }
            } catch (e: UnknownHostException) {
                GeminiTextResult.Failure(AiFailure.Offline)
            } catch (e: ConnectException) {
                GeminiTextResult.Failure(AiFailure.Offline)
            } catch (e: SocketTimeoutException) {
                GeminiTextResult.Failure(AiFailure.Timeout)
            } catch (e: Exception) {
                GeminiTextResult.Failure(
                    AiFailure.ServerError(
                        code = -1,
                        details = redactSecrets(e.message, apiKey).take(160)
                    )
                )
            }
        }

        // Fallback: Firebase AI Logic SDK when no explicit API key is present
        try {
            val result = withTimeout(timeoutMs) {
                val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = CLOUD_MODEL_NAME,
                    generationConfig = generationConfig {
                        temperature = options.temperature
                        maxOutputTokens = options.maxOutputTokens
                    },
                    systemInstruction = systemInstruction?.let { content { text(it) } }
                )
                val history = turns.dropLast(1).map { turn ->
                    content(role = turn.role.apiValue) { text(turn.text) }
                }
                val lastMessage = turns.lastOrNull()?.text ?: ""
                val response = if (history.isEmpty()) {
                    model.generateContent(lastMessage)
                } else {
                    model.startChat(history).sendMessage(lastMessage)
                }
                val responseText = response.text?.trim()
                if (responseText.isNullOrBlank()) {
                    GeminiTextResult.Failure(AiFailure.EmptyResponse)
                } else {
                    GeminiTextResult.Success(responseText)
                }
            }
            return result
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) {
                return GeminiTextResult.Failure(AiFailure.Timeout)
            } else {
                throw ce
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI SDK request failed (${e.javaClass.simpleName})")
        }

        return GeminiTextResult.Failure(AiFailure.NoApiKey)
    }

    fun streamChat(
        turns: List<GeminiTurn>,
        systemInstruction: String? = null,
        customApiKey: String? = null,
        options: GeminiGenerationOptions = GeminiGenerationOptions(temperature = 0.65f, maxOutputTokens = 1400),
        baseUrl: String = DEFAULT_BASE_URL
    ): Flow<GeminiStreamEvent> = flow {
        val apiKey = resolveApiKey(customApiKey)
        if (!apiKey.isNullOrBlank()) {
            val sseUrl = "$baseUrl/$CLOUD_MODEL_NAME:streamGenerateContent?alt=sse"
            val payload = buildRequestPayload(turns, systemInstruction, options)
            var conn: HttpURLConnection? = null
            val accumulated = StringBuilder()

            try {
                conn = openUrlConnection(sseUrl).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("x-goog-api-key", apiKey)
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    doOutput = true
                }

                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    val dataBuffer = StringBuilder()

                    fun flushDataBuffer() {
                        val jsonStr = dataBuffer.toString().trim()
                        dataBuffer.setLength(0)
                        if (jsonStr.isBlank() || jsonStr == "[DONE]") return
                        val chunkText = extractTextFromResponseJson(jsonStr)
                        if (!chunkText.isNullOrEmpty()) {
                            // Some proxies send cumulative text, whereas Google AI Studio SSE sends deltas.
                            // Emit strictly new suffix if chunkText looks cumulative, otherwise emit delta directly.
                            val delta = if (accumulated.isNotEmpty() && chunkText.startsWith(accumulated.toString())) {
                                chunkText.substring(accumulated.length)
                            } else {
                                chunkText
                            }
                            if (delta.isNotEmpty()) {
                                accumulated.append(delta)
                            }
                        }
                    }

                    reader.use { br ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val line = br.readLine() ?: break
                            if (line.startsWith("data:")) {
                                dataBuffer.append(line.removePrefix("data:").trim()).append('\n')
                            } else if (line.isBlank() && dataBuffer.isNotEmpty()) {
                                val prevLen = accumulated.length
                                flushDataBuffer()
                                if (accumulated.length > prevLen) {
                                    emit(GeminiStreamEvent.Delta(accumulated.substring(prevLen)))
                                }
                            } else if (line.trimStart().startsWith("{")) {
                                // Non-SSE raw JSON fallback if server ignored alt=sse
                                dataBuffer.append(line).append('\n')
                            }
                        }
                    }
                    if (dataBuffer.isNotEmpty()) {
                        val prevLen = accumulated.length
                        flushDataBuffer()
                        if (accumulated.length > prevLen) {
                            emit(GeminiStreamEvent.Delta(accumulated.substring(prevLen)))
                        }
                    }

                    val full = accumulated.toString().trim()
                    if (full.isEmpty()) {
                        emit(GeminiStreamEvent.Error(AiFailure.EmptyResponse))
                    } else {
                        emit(GeminiStreamEvent.Completed(full))
                    }
                    return@flow
                } else {
                    val errorBody = try {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }
                    } catch (_: Throwable) {
                        null
                    }
                    emit(GeminiStreamEvent.Error(classifyHttpFailure(code, errorBody, apiKey)))
                    return@flow
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: UnknownHostException) {
                emit(GeminiStreamEvent.Error(AiFailure.Offline))
                return@flow
            } catch (e: ConnectException) {
                emit(GeminiStreamEvent.Error(AiFailure.Offline))
                return@flow
            } catch (e: SocketTimeoutException) {
                emit(GeminiStreamEvent.Error(AiFailure.Timeout))
                return@flow
            } catch (e: Exception) {
                emit(
                    GeminiStreamEvent.Error(
                        AiFailure.ServerError(-1, redactSecrets(e.message, apiKey).take(160))
                    )
                )
                return@flow
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Throwable) {}
            }
        }

        // Fallback to Firebase AI Logic SDK streaming when no direct key is set
        try {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = CLOUD_MODEL_NAME,
                generationConfig = generationConfig {
                    temperature = options.temperature
                    maxOutputTokens = options.maxOutputTokens
                },
                systemInstruction = systemInstruction?.let { content { text(it) } }
            )
            val history: List<Content> = turns.dropLast(1).map { turn ->
                content(role = turn.role.apiValue) { text(turn.text) }
            }
            val lastMessage = turns.lastOrNull()?.text ?: ""
            val flowStream = if (history.isEmpty()) {
                model.generateContentStream(lastMessage)
            } else {
                model.startChat(history).sendMessageStream(lastMessage)
            }
            val full = StringBuilder()
            flowStream.collect { chunk ->
                currentCoroutineContext().ensureActive()
                val text = chunk.text
                if (!text.isNullOrEmpty()) {
                    full.append(text)
                    emit(GeminiStreamEvent.Delta(text))
                }
            }
            if (full.isEmpty()) {
                emit(GeminiStreamEvent.Error(AiFailure.EmptyResponse))
            } else {
                emit(GeminiStreamEvent.Completed(full.toString().trim()))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI stream failed (${e.javaClass.simpleName})")
            emit(GeminiStreamEvent.Error(AiFailure.NoApiKey))
        }
    }.flowOn(Dispatchers.IO)

    private fun executeRestGenerateContent(
        turns: List<GeminiTurn>,
        systemInstruction: String?,
        apiKey: String,
        options: GeminiGenerationOptions,
        baseUrl: String
    ): GeminiTextResult {
        val url = "$baseUrl/$CLOUD_MODEL_NAME:generateContent"
        val payload = buildRequestPayload(turns, systemInstruction, options)

        val conn = openUrlConnection(url).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
        }

        return try {
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val rawContent = extractTextFromResponseJson(responseText)
                if (rawContent.isNullOrBlank()) {
                    GeminiTextResult.Failure(AiFailure.EmptyResponse)
                } else {
                    GeminiTextResult.Success(rawContent)
                }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                GeminiTextResult.Failure(classifyHttpFailure(responseCode, errorBody, apiKey))
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openUrlConnection(urlString: String): HttpURLConnection {
        val parsed = URL(urlString)
        val isLoopback = parsed.host.equals("127.0.0.1", ignoreCase = true) ||
            parsed.host.equals("localhost", ignoreCase = true)
        return (if (isLoopback) parsed.openConnection(Proxy.NO_PROXY) else parsed.openConnection()) as HttpURLConnection
    }

    fun classifyHttpFailure(responseCode: Int, errorBody: String?, apiKey: String? = null): AiFailure {
        val cleanBody = redactSecrets(errorBody, apiKey)
        return when (responseCode) {
            429 -> {
                Log.w(TAG, "Gemini API rate limited ($responseCode)")
                AiFailure.RateLimited(responseCode)
            }
            400, 401, 403 -> {
                Log.w(TAG, "Gemini API authentication failed ($responseCode)")
                AiFailure.AuthError(responseCode)
            }
            else -> {
                Log.w(TAG, "Gemini API call failed ($responseCode)")
                AiFailure.ServerError(
                    code = responseCode,
                    details = cleanBody.take(160).takeIf { it.isNotBlank() }
                )
            }
        }
    }
}
