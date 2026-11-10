package com.laterbox.app.media.data.storage

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

object ChecksumUtil {

    fun calculateSha256(file: File): String {
        return FileInputStream(file).use { calculateSha256(it) }
    }

    fun calculateSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
