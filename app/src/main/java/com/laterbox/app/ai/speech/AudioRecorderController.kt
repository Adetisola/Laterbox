package com.laterbox.app.ai.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/** A finished temporary recording. The caller owns the file and must delete it. */
data class RecordedAudio(val file: File, val mimeType: String, val durationMs: Long)

/** Abstraction over microphone capture so the coordinator can be tested without hardware. */
interface AudioRecorder {
    val isRecording: Boolean
    /** Begins capture. Returns false when a session is already active or capture failed. */
    fun start(): Boolean
    /** Stops capture and returns the temp recording, or null on failure. */
    fun stop(): RecordedAudio?
    /** Stops capture and deletes any temp audio. Safe to call repeatedly. */
    fun cancel()
}

class AudioRecorderController(private val context: Context) : AudioRecorder {
    private val lock = Any()
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var focusRequest: AudioFocusRequest? = null

    override val isRecording: Boolean get() = synchronized(lock) { recorder != null }

    override fun start(): Boolean = synchronized(lock) {
        if (recorder != null) return false
        val dir = File(context.cacheDir, "dictation").apply { mkdirs() }
        val out = File(dir, "rec_${UUID.randomUUID()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        requestTransientFocus()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(64_000)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            startedAt = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            runCatching { r.release() }
            abandonTransientFocus()
            out.delete()
            false
        }
    }

    override fun stop(): RecordedAudio? = synchronized(lock) {
        val r = recorder ?: return null
        val f = file
        recorder = null
        file = null
        val duration = System.currentTimeMillis() - startedAt
        val ok = try {
            r.stop(); true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { r.release() }
            abandonTransientFocus()
        }
        if (!ok || f == null) {
            f?.delete()
            return null
        }
        RecordedAudio(f, "audio/mp4", duration)
    }

    override fun cancel() {
        val rec = stop()
        rec?.file?.delete()
    }

    private fun requestTransientFocus() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun abandonTransientFocus() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        }
        focusRequest = null
    }
}
