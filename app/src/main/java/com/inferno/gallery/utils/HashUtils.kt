package com.inferno.gallery.utils

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object HashUtils {

    /**
     * Generates a SHA-256 hash of the full file contents.
     * SHA-256 has hardware acceleration on ARMv8 CPUs and is faster than MD5 on modern Android.
     * Full-file hashing eliminates false positives from partial-read strategies.
     */
    fun computeFileHash(file: File): String? {
        return try {
            if (!file.exists() || !file.canRead()) return null
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536) // 64KB chunks
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Returns true if two file sizes are close enough (within 10%) to warrant deeper comparison.
     * Used as a fast pre-filter before expensive perceptual hash comparisons.
     */
    fun similarFileSize(sizeA: Long, sizeB: Long): Boolean {
        if (sizeA <= 0 || sizeB <= 0) return true // unknown size: don't filter
        val larger = maxOf(sizeA, sizeB).toDouble()
        val diff = Math.abs(sizeA - sizeB).toDouble()
        return diff / larger <= 0.10
    }

    /**
     * Generates a 64-bit Difference Hash (dHash) for the given bitmap.
     *
     * dHash encodes gradient direction (whether the next pixel is brighter or darker)
     * rather than absolute brightness. This makes it robust against rescaling,
     * compression artefacts, and minor crops while still catching visually identical images.
     *
     * Process: resize to 9×8, convert to grayscale, compare each pixel to its right neighbor.
     * Produces a 64-bit hash (8 rows × 8 comparisons per row).
     */
    fun generatePerceptualHash(bitmap: Bitmap): Long? {
        return try {
            // 1. Resize to 9×8
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
            hash
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Computes the Hamming Distance between two 64-bit perceptual hashes.
     * Distance 0   = pixel-identical after rescale
     * Distance ≤ 4 = High confidence similar (burst shots, minor edits)
     * Distance ≤ 10 = Medium confidence similar (recompressed, cropped, filtered)
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        val xor = hash1 xor hash2
        return java.lang.Long.bitCount(xor)
    }

    /**
     * Returns a human-readable confidence label for a given Hamming distance.
     * Used to label near-identical groups in the UI.
     */
    fun pHashConfidence(distance: Int): String = when {
        distance <= 4  -> "High"
        distance <= 10 -> "Medium"
        else           -> "Low"
    }
}
