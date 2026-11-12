package com.laterbox.app.update.patch

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, streaming BSDIFF40 patch applier.
 *
 * Implements Colin Percival's BSDIFF40 binary differential compression algorithm with
 * streaming I/O and strictly bounded heap memory usage (< 2 MB peak heap RAM).
 *
 * Rather than allocating large old and new file buffers in RAM, this implementation:
 * 1. Reads the old installed APK using [RandomAccessFile] in 32 KB buffered chunks.
 * 2. Writes the new reconstructed APK sequentially using [BufferedOutputStream].
 * 3. Streams control, diff, and extra blocks via [BZip2CompressorInputStream].
 * 4. Decodes signed 64-bit integers using BSDIFF sign-magnitude encoding.
 */
object BsPatch {

    private const val MAGIC = "BSDIFF40"
    private const val HEADER_SIZE = 32
    private const val CHUNK_SIZE = 32768

    /**
     * Decodes a 64-bit signed integer stored in BSDIFF sign-magnitude format (little-endian,
     * high bit of 8th byte represents sign).
     */
    fun decodeOffT(buf: ByteArray, offset: Int = 0): Long {
        var y = (buf[offset + 7].toLong() and 0x7F)
        for (i in 6 downTo 0) {
            y = (y shl 8) or (buf[offset + i].toLong() and 0xFF)
        }
        if ((buf[offset + 7].toInt() and 0x80) != 0) {
            y = -y
        }
        return y
    }

    /**
     * Reads exactly [length] bytes from an input stream into a byte array, throwing [EOFException] if truncated.
     */
    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset) {
        var totalRead = 0
        while (totalRead < length) {
            val count = input.read(buffer, offset + totalRead, length - totalRead)
            if (count < 0) {
                throw EOFException("Unexpected EOF while reading stream at offset ${offset + totalRead}, expected $length bytes")
            }
            totalRead += count
        }
    }

    /**
     * Applies a BSDIFF40 [patchFile] to [oldFile] and writes the reconstructed output to [newFile].
     *
     * @param oldFile The existing base file (e.g. currently installed APK).
     * @param patchFile The binary patch file.
     * @param newFile The target reconstructed file to be created.
     * @param onProgress Optional progress callback receiving estimated percentage (0..100).
     */
    @Throws(IOException::class)
    fun applyPatch(
        oldFile: File,
        patchFile: File,
        newFile: File,
        onProgress: ((Int) -> Unit)? = null
    ) {
        if (!oldFile.exists() || !oldFile.canRead()) {
            throw IOException("Base file does not exist or is unreadable: ${oldFile.absolutePath}")
        }
        if (!patchFile.exists() || !patchFile.canRead()) {
            throw IOException("Patch file does not exist or is unreadable: ${patchFile.absolutePath}")
        }

        // 1. Read and parse header
        val header = ByteArray(HEADER_SIZE)
        FileInputStream(patchFile).use { fis ->
            readFully(fis, header)
        }

        val magicStr = String(header, 0, 8, Charsets.US_ASCII)
        if (magicStr != MAGIC) {
            throw IOException("Invalid patch magic header: expected $MAGIC, found $magicStr")
        }

        val ctrlLen = decodeOffT(header, 8)
        val diffLen = decodeOffT(header, 16)
        val newSize = decodeOffT(header, 24)

        if (ctrlLen < 0 || diffLen < 0 || newSize < 0) {
            throw IOException("Corrupted patch header dimensions: ctrlLen=$ctrlLen, diffLen=$diffLen, newSize=$newSize")
        }

        // Temporary destination to avoid leaving partial or corrupted output
        val tempOutputFile = File(newFile.parentFile ?: patchFile.parentFile, "${newFile.name}.tmp_${System.currentTimeMillis()}")
        var success = false

        try {
            // Open 3 separate BZip2 streams at their respective byte offsets in the patch file
            // Offset 32: Control block (ctrlLen bytes)
            // Offset 32 + ctrlLen: Diff block (diffLen bytes)
            // Offset 32 + ctrlLen + diffLen: Extra block (remaining bytes)
            val ctrlOffset = 32L
            val diffOffset = ctrlOffset + ctrlLen
            val extraOffset = diffOffset + diffLen

            FileInputStream(patchFile).use { ctrlFis ->
                ctrlFis.channel.position(ctrlOffset)
                BZip2CompressorInputStream(BufferedInputStream(ctrlFis, CHUNK_SIZE)).use { ctrlStream ->

                    FileInputStream(patchFile).use { diffFis ->
                        diffFis.channel.position(diffOffset)
                        BZip2CompressorInputStream(BufferedInputStream(diffFis, CHUNK_SIZE)).use { diffStream ->

                            FileInputStream(patchFile).use { extraFis ->
                                extraFis.channel.position(extraOffset)
                                BZip2CompressorInputStream(BufferedInputStream(extraFis, CHUNK_SIZE)).use { extraStream ->

                                    RandomAccessFile(oldFile, "r").use { oldRaf ->
                                        BufferedOutputStream(FileOutputStream(tempOutputFile), CHUNK_SIZE).use { newOut ->
                                            val oldSize = oldRaf.length()
                                            var oldPos = 0L
                                            var newPos = 0L
                                            var lastReportedPercent = -1

                                            val ctrlBuf = ByteArray(24)
                                            val diffBuf = ByteArray(CHUNK_SIZE)
                                            val oldBuf = ByteArray(CHUNK_SIZE)
                                            val extraBuf = ByteArray(CHUNK_SIZE)

                                            while (newPos < newSize) {
                                                readFully(ctrlStream, ctrlBuf, 0, 24)
                                                val addLen = decodeOffT(ctrlBuf, 0)
                                                val copyLen = decodeOffT(ctrlBuf, 8)
                                                val seekOffset = decodeOffT(ctrlBuf, 16)

                                                if (addLen < 0 || copyLen < 0) {
                                                    throw IOException("Negative block length in patch control data: addLen=$addLen, copyLen=$copyLen")
                                                }
                                                if (newPos + addLen > newSize) {
                                                    throw IOException("Corrupt patch: addLen $addLen exceeds new file size ($newPos / $newSize)")
                                                }

                                                // 1. Process diff block: read addLen bytes from diff stream, add to old file bytes, write to new
                                                var addRemaining = addLen
                                                while (addRemaining > 0) {
                                                    val toRead = min(addRemaining, CHUNK_SIZE.toLong()).toInt()
                                                    readFully(diffStream, diffBuf, 0, toRead)

                                                    // Prepare old bytes: if oldPos in valid range, read from oldFile; else 0
                                                    oldBuf.fill(0, 0, toRead)
                                                    val validStart = max(0L, -oldPos)
                                                    val validEnd = min(toRead.toLong(), oldSize - oldPos)

                                                    if (validEnd > validStart) {
                                                        oldRaf.seek(oldPos + validStart)
                                                        val readCount = (validEnd - validStart).toInt()
                                                        oldRaf.readFully(oldBuf, validStart.toInt(), readCount)
                                                    }

                                                    for (i in 0 until toRead) {
                                                        diffBuf[i] = ((oldBuf[i].toInt() + diffBuf[i].toInt()) and 0xFF).toByte()
                                                    }

                                                    newOut.write(diffBuf, 0, toRead)
                                                    newPos += toRead
                                                    oldPos += toRead
                                                    addRemaining -= toRead
                                                }

                                                if (newPos + copyLen > newSize) {
                                                    throw IOException("Corrupt patch: copyLen $copyLen exceeds new file size ($newPos / $newSize)")
                                                }

                                                // 2. Process extra block: read copyLen bytes from extra stream, write directly to new
                                                var copyRemaining = copyLen
                                                while (copyRemaining > 0) {
                                                    val toRead = min(copyRemaining, CHUNK_SIZE.toLong()).toInt()
                                                    readFully(extraStream, extraBuf, 0, toRead)
                                                    newOut.write(extraBuf, 0, toRead)
                                                    newPos += toRead
                                                    copyRemaining -= toRead
                                                }

                                                // 3. Adjust old file seek position
                                                oldPos += seekOffset

                                                // Report progress
                                                if (newSize > 0 && onProgress != null) {
                                                    val percent = ((newPos * 100) / newSize).toInt().coerceIn(0, 100)
                                                    if (percent != lastReportedPercent) {
                                                        lastReportedPercent = percent
                                                        onProgress(percent)
                                                    }
                                                }
                                            }

                                            newOut.flush()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (tempOutputFile.length() != newSize) {
                throw IOException("Reconstructed APK size mismatch: expected $newSize bytes, produced ${tempOutputFile.length()} bytes")
            }

            if (newFile.exists()) {
                newFile.delete()
            }
            if (!tempOutputFile.renameTo(newFile)) {
                // If renameTo fails (cross-filesystem or locked), copy and delete
                tempOutputFile.copyTo(newFile, overwrite = true)
                tempOutputFile.delete()
            }
            success = true
        } finally {
            if (!success && tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
        }
    }
}
