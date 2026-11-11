package com.laterbox.app.media.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object ThumbnailGenerator {

    private const val TAG = "ThumbnailGenerator"
    private const val THUMBNAILS_DIR = "media_thumbnails"

    suspend fun generateThumbnail(
        context: Context,
        mediaFile: File,
        mediaType: MediaType,
        assetId: String,
        artworkUrl: String? = null,
        httpClient: OkHttpClient = OkHttpClient()
    ): String? = withContext(Dispatchers.IO) {
        val thumbsDir = File(context.filesDir, THUMBNAILS_DIR).apply { if (!exists()) mkdirs() }
        val targetThumbFile = File(thumbsDir, "$assetId.jpg")

        try {
            when (mediaType) {
                MediaType.VIDEO -> {
                    if (!mediaFile.exists() || mediaFile.length() == 0L) return@withContext null
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(mediaFile.absolutePath)
                        // Retrieve frame at 1 second (1,000,000 us) to avoid initial black frames
                        var bitmap: Bitmap? = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (bitmap == null || isBlankBitmap(bitmap)) {
                            bitmap = retriever.frameAtTime
                        }
                        if (bitmap != null) {
                            FileOutputStream(targetThumbFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            bitmap.recycle()
                            return@withContext "$THUMBNAILS_DIR/$assetId.jpg"
                        }
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }

                MediaType.AUDIO -> {
                    // 1. Try embedded picture via MediaMetadataRetriever
                    if (mediaFile.exists() && mediaFile.length() > 0L) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(mediaFile.absolutePath)
                            val pictureBytes = retriever.embeddedPicture
                            if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                                targetThumbFile.writeBytes(pictureBytes)
                                return@withContext "$THUMBNAILS_DIR/$assetId.jpg"
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed reading embedded picture: ${e.message}")
                        } finally {
                            try { retriever.release() } catch (_: Exception) {}
                        }
                    }

                    // 2. Fall back to downloading remote artworkUrl if present
                    if (!artworkUrl.isNullOrBlank() && artworkUrl.startsWith("http")) {
                        try {
                            val request = Request.Builder().url(artworkUrl).build()
                            val response = httpClient.newCall(request).execute()
                            response.use { resp ->
                                if (resp.isSuccessful && resp.body != null) {
                                    FileOutputStream(targetThumbFile).use { out ->
                                        resp.body!!.byteStream().copyTo(out)
                                    }
                                    return@withContext "$THUMBNAILS_DIR/$assetId.jpg"
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed downloading artwork from $artworkUrl: ${e.message}")
                        }
                    }
                }

                MediaType.IMAGE -> {
                    // For images, we can either point to the image itself or make a small thumbnail
                    if (mediaFile.exists() && mediaFile.length() > 0L) {
                        return@withContext "media/${mediaFile.parentFile?.name}/${mediaFile.name}"
                    }
                }

                MediaType.DOCUMENT, MediaType.OTHER -> {
                    // Documents can use default icon or thumbnail
                    return@withContext null
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Thumbnail generation failed for asset $assetId: ${t.message}")
        }

        return@withContext null
    }

    private fun isBlankBitmap(bitmap: Bitmap): Boolean {
        // Quick sample test of 5 pixels to detect entirely pitch black or transparent frames
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return true
        val samples = intArrayOf(
            bitmap.getPixel(w / 2, h / 2),
            bitmap.getPixel(w / 4, h / 4),
            bitmap.getPixel(3 * w / 4, 3 * h / 4),
            bitmap.getPixel(w / 4, 3 * h / 4),
            bitmap.getPixel(3 * w / 4, h / 4)
        )
        return samples.all { pixel -> (pixel and 0x00FFFFFF) == 0 }
    }
}
