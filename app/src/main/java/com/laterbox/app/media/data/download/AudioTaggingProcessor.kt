package com.laterbox.app.media.data.download

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AudioTaggingProcessor {

    private const val TAG = "AudioTaggingProcessor"

    var customProcessRunner: ((List<String>, Map<String, String>) -> Boolean)? = null

    fun tagAudioFile(
        appContext: Context,
        audioFile: File,
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        spotifyTrackId: String?,
        client: OkHttpClient = OkHttpClient()
    ): Boolean {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return false
        }

        // Only tag if at least one metadata field is present
        if (title.isNullOrBlank() && artist.isNullOrBlank() && album.isNullOrBlank()) {
            return false
        }

        val nativeLibDirStr = appContext.applicationInfo.nativeLibraryDir
        val nativeLibDir = if (!nativeLibDirStr.isNullOrBlank()) File(nativeLibDirStr) else null
        val ffmpegBin = if (nativeLibDir != null) File(nativeLibDir, "libffmpeg.so") else null
        val customRunner = customProcessRunner

        if ((ffmpegBin == null || !ffmpegBin.exists()) && customRunner == null) {
            Log.d(TAG, "FFmpeg binary not found at ${ffmpegBin?.absolutePath}, skipping tagging.")
            return false
        }

        val usrLib = File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
        val ldLibPath = when {
            usrLib.exists() && nativeLibDir != null -> "${usrLib.absolutePath}:${nativeLibDir.absolutePath}"
            nativeLibDir != null -> nativeLibDir.absolutePath
            usrLib.exists() -> usrLib.absolutePath
            else -> ""
        }

        val tempCoverFile = if (!artworkUrl.isNullOrBlank()) {
            downloadArtworkToTemp(artworkUrl, audioFile.parentFile, client)
        } else null

        val outputFile = File(audioFile.parentFile, "tagged_${audioFile.name}")

        val cmd = mutableListOf<String>()
        cmd.add(ffmpegBin?.absolutePath ?: "ffmpeg")
        cmd.add("-y")
        cmd.add("-i")
        cmd.add(audioFile.absolutePath)

        if (tempCoverFile != null && tempCoverFile.exists() && tempCoverFile.length() > 0L) {
            cmd.add("-i")
            cmd.add(tempCoverFile.absolutePath)
            cmd.add("-map")
            cmd.add("0:a")
            cmd.add("-map")
            cmd.add("1:v")
            cmd.add("-c:a")
            cmd.add("copy")
            cmd.add("-c:v")
            cmd.add("mjpeg")
            cmd.add("-metadata:s:v")
            cmd.add("title=Album cover")
            cmd.add("-metadata:s:v")
            cmd.add("comment=Cover (front)")
        } else {
            cmd.add("-c")
            cmd.add("copy")
        }

        if (!title.isNullOrBlank()) {
            cmd.add("-metadata")
            cmd.add("title=$title")
        }
        if (!artist.isNullOrBlank()) {
            cmd.add("-metadata")
            cmd.add("artist=$artist")
            cmd.add("-metadata")
            cmd.add("album_artist=$artist")
        }
        if (!album.isNullOrBlank()) {
            cmd.add("-metadata")
            cmd.add("album=$album")
        }
        if (!spotifyTrackId.isNullOrBlank()) {
            cmd.add("-metadata")
            cmd.add("comment=Source: Spotify [$spotifyTrackId]")
        }

        cmd.add(outputFile.absolutePath)

        val env = mapOf("LD_LIBRARY_PATH" to ldLibPath)

        return try {
            val success = if (customRunner != null) {
                customRunner.invoke(cmd, env)
            } else {
                val pb = ProcessBuilder(cmd)
                pb.environment().putAll(env)
                val process = pb.start()
                val exited = process.waitFor(20, TimeUnit.SECONDS)
                if (exited && process.exitValue() == 0) {
                    true
                } else {
                    process.destroy()
                    false
                }
            }

            if (success && outputFile.exists() && outputFile.length() > 0L) {
                val renamed = outputFile.renameTo(audioFile)
                if (!renamed) {
                    outputFile.copyTo(audioFile, overwrite = true)
                    outputFile.delete()
                }
                Log.i(TAG, "Successfully tagged audio file with title='$title', artist='$artist'")
                true
            } else {
                Log.w(TAG, "FFmpeg tagging exited without producing valid tagged output")
                outputFile.delete()
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "FFmpeg audio tagging failed gracefully: ${t.message}", t)
            outputFile.delete()
            false
        } finally {
            tempCoverFile?.delete()
        }
    }

    private fun downloadArtworkToTemp(url: String, parentDir: File?, client: OkHttpClient): File? {
        return try {
            val targetDir = parentDir ?: return null
            val coverFile = File(targetDir, "cover_${System.currentTimeMillis()}.jpg")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val body = response.body ?: return null
            body.byteStream().use { input ->
                FileOutputStream(coverFile).use { output ->
                    input.copyTo(output)
                }
            }
            coverFile
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to download artwork for tagging: ${t.message}")
            null
        }
    }
}
