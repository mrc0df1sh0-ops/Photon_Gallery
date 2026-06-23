package com.inferno.gallery.utils

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object HashUtils {

    /**
     * Generates an MD5 hash of the file contents.
     * This is a cryptographic hash — two files with the same MD5 are byte-identical.
     * Used for "Exact Copies" detection.
     *
     * Reads only the first 64KB + last 64KB for large files (>1MB) as a fast approximation
     * combined with file size matching for accurate results without reading entire files.
     */
    fun computeFileHash(file: File): String? {
        try {
            if (!file.exists() || !file.canRead()) return null
            val digest = MessageDigest.getInstance("MD5")
            val fileSize = file.length()

            FileInputStream(file).use { fis ->
                if (fileSize <= 1_048_576L) {
                    // Small file: hash entire content
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                } else {
                    // Large file: hash first 64KB + last 64KB + file size
                    // This is extremely unlikely to produce false positives when combined
                    // with size-based pre-grouping
                    val chunkSize = 65536
                    val buffer = ByteArray(chunkSize)

                    // First 64KB
                    var totalRead = 0
                    while (totalRead < chunkSize) {
                        val read = fis.read(buffer, 0, minOf(chunkSize - totalRead, buffer.size))
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        totalRead += read
                    }

                    // Last 64KB
                    val channel = fis.channel
                    val lastStart = maxOf(0L, fileSize - chunkSize)
                    channel.position(lastStart)
                    totalRead = 0
                    while (totalRead < chunkSize) {
                        val read = fis.read(buffer, 0, minOf(chunkSize - totalRead, buffer.size))
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        totalRead += read
                    }

                    // Include file size in hash to further reduce collisions
                    digest.update(fileSize.toString().toByteArray())
                }
            }

            val hashBytes = digest.digest()
            return hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generates a 64-bit Difference Hash (dHash) for the given bitmap.
     *
     * dHash is more discriminating than aHash because it encodes gradient direction
     * (whether the next pixel is brighter or darker) rather than absolute brightness.
     * This makes it far more resistant to false positives from unrelated images.
     *
     * Process: resize to 9x8, convert to grayscale, compare each pixel to its right neighbor.
     * Produces a 64-bit hash (8 rows × 8 comparisons per row).
     */
    fun generatePerceptualHash(bitmap: Bitmap): Long? {
        try {
            // 1. Resize to 9x8 (9 wide so we get 8 horizontal differences per row)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 9, 8, true)

            // 2. Convert to grayscale
            val pixels = IntArray(9 * 8)
            scaledBitmap.getPixels(pixels, 0, 9, 0, 0, 9, 8)

            val grayPixels = IntArray(9 * 8)
            for (i in pixels.indices) {
                val color = pixels[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                grayPixels[i] = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
            }

            // 3. Compute 64-bit dHash: compare each pixel to the one to its right
            var hash = 0L
            var bitIndex = 0
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val leftPixel = grayPixels[row * 9 + col]
                    val rightPixel = grayPixels[row * 9 + col + 1]
                    if (leftPixel > rightPixel) {
                        hash = hash or (1L shl bitIndex)
                    }
                    bitIndex++
                }
            }

            scaledBitmap.recycle()
            return hash
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Computes the Hamming Distance between two 64-bit hashes.
     * A distance of 0 means visually identical images.
     * A distance <= 5 usually indicates visually similar images (rescaled, recompressed).
     * A distance <= 10 may indicate loosely similar images.
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        val xor = hash1 xor hash2
        return java.lang.Long.bitCount(xor)
    }
}
