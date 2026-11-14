package com.laterbox.app.ai.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Fallback dictation via the platform recognizer. Returns null when nothing was recognized. */
interface AndroidFallbackRecognizer {
    suspend fun recognize(): String?
}

class AndroidSpeechRecognizerAdapter(private val context: Context) : AndroidFallbackRecognizer {
    override suspend fun recognize(): String? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        return suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            var recognizer: SpeechRecognizer? = null
            fun finish(value: String?) {
                main.post {
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    if (cont.isActive) cont.resume(value)
                }
            }
            main.post {
                val r = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = r
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                    override fun onError(error: Int) = finish(null)
                    override fun onResults(results: Bundle?) {
                        finish(
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()?.takeIf { it.isNotBlank() }
                        )
                    }
                })
                r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                })
            }
            cont.invokeOnCancellation { main.post { runCatching { recognizer?.cancel(); recognizer?.destroy() } } }
        }
    }
}
