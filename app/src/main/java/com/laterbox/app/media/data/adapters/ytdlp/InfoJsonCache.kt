package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import java.io.File
import java.security.MessageDigest

object InfoJsonCache {

    private const val TTL_MS = 5 * 3600 * 1000L // 5 hours

    fun getCacheDir(context: Context): File {
        return File(context.cacheDir, "media_info").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun urlHash(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(url.trim().toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun save(context: Context, url: String, json: String, linkId: Long? = null) {
        try {
            val dir = getCacheDir(context)
            val hash = urlHash(url)
            val fileByHash = File(dir, "$hash.json")
            fileByHash.writeText(json)
            if (linkId != null && linkId > 0) {
                val fileByLinkId = File(dir, "link_${linkId}.json")
                fileByLinkId.writeText(json)
            }
        } catch (_: Throwable) {}
    }

    fun getValidInfoJson(context: Context, url: String, linkId: Long? = null): File? {
        val dir = getCacheDir(context)
        val now = System.currentTimeMillis()

        // 1. Check by linkId if provided
        if (linkId != null && linkId > 0) {
            val fileByLinkId = File(dir, "link_${linkId}.json")
            if (fileByLinkId.exists() && fileByLinkId.length() > 0L && (now - fileByLinkId.lastModified() < TTL_MS)) {
                return fileByLinkId
            }
        }

        // 2. Check by url hash
        val hash = urlHash(url)
        val fileByHash = File(dir, "$hash.json")
        if (fileByHash.exists() && fileByHash.length() > 0L && (now - fileByHash.lastModified() < TTL_MS)) {
            return fileByHash
        }

        return null
    }

    fun invalidate(context: Context, url: String, linkId: Long? = null) {
        try {
            val dir = getCacheDir(context)
            if (linkId != null && linkId > 0) {
                val fileByLinkId = File(dir, "link_${linkId}.json")
                if (fileByLinkId.exists()) fileByLinkId.delete()
            }
            val hash = urlHash(url)
            val fileByHash = File(dir, "$hash.json")
            if (fileByHash.exists()) fileByHash.delete()
        } catch (_: Throwable) {}
    }

    fun clearExpired(context: Context) {
        try {
            val dir = getCacheDir(context)
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { file ->
                if (now - file.lastModified() >= TTL_MS) {
                    file.delete()
                }
            }
        } catch (_: Throwable) {}
    }
}
