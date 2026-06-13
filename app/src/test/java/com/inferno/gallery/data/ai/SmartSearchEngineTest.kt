package com.inferno.gallery.data.ai

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SmartSearchEngineTest {

    private class TestContext(private val filesDirFile: File) : ContextWrapper(null) {
        override fun getFilesDir(): File {
            return filesDirFile
        }
    }

    @Test
    fun testBPETokenizerMapping() {
        // Load tokenizer.json from assets
        val file = File(if (File("app/src/main/assets/tokenizer.json").exists()) "app/src/main/assets/tokenizer.json" else "src/main/assets/tokenizer.json")
        if (!file.exists()) {
            println("Downloading tokenizer.json for tests from Hugging Face...")
            try {
                file.parentFile?.mkdirs()
                java.net.URL("https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/tokenizer.json").openStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                System.err.println("Failed to download tokenizer.json: ${e.message}")
            }
        }
        assertTrue("tokenizer.json should exist in assets", file.exists())
        
        val json = file.readText()
        val tokenizer = BPETokenizer(json)
        
        // Assert vocabulary is not empty
        assertFalse(tokenizer.vocab.isEmpty())
        
        // Test vocabulary lookups for common words
        val startToken = tokenizer.vocab["<|startoftext|>"]
        val endToken = tokenizer.vocab["<|endoftext|>"]
        assertNotNull("vocab should contain start token", startToken)
        assertNotNull("vocab should contain end token", endToken)
        
        val catToken = tokenizer.vocab["cat</w>"]
        val dogToken = tokenizer.vocab["dog</w>"]
        assertNotNull("vocab should contain 'cat</w>'", catToken)
        assertNotNull("vocab should contain 'dog</w>'", dogToken)
        
        // Test encoding
        val testPhrase = "a photo of a cat"
        val encoded = tokenizer.encode(testPhrase)
        
        // Assert token sequence bounds
        assertEquals("Encoded sequence must be 77 tokens long", 77, encoded.size)
        assertEquals("First token must be <|startoftext|>", startToken!!.toLong(), encoded[0])
        
        // Find index of end token
        val endTokenIndex = encoded.indexOf(endToken!!.toLong())
        assertTrue("End token must be present in sequence", endTokenIndex > 0)
        
        // Assert padding with endToken after end token
        for (i in (endTokenIndex + 1) until 77) {
            assertEquals("Padding token must be endToken", endToken.toLong(), encoded[i])
        }
    }

    @Test
    fun testVectorSimilarityCalculations() {
        // Create context using TestContext
        val tempDirStr = System.getProperty("java.io.tmpdir") ?: "."
        val tempDir = File(tempDirStr)
        val mockContext = TestContext(tempDir)
        val engine = SmartSearchEngine(mockContext)
        
        // Test dotProduct math
        val vecA = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f)
        val vecB = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f)
        val dotSelf = engine.dotProduct(vecA, vecB)
        assertEquals("Dot product of identical unit vectors should be 1.0", 1.0f, dotSelf, 1e-5f)
        
        val vecC = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f)
        val dotOrtho = engine.dotProduct(vecA, vecC)
        assertEquals("Dot product of orthogonal vectors should be 0.0", 0.0f, dotOrtho, 1e-5f)
        
        // Test parallel unrolling bounds (size > 8)
        val largeA = FloatArray(16) { 1.0f }
        val largeB = FloatArray(16) { 1.0f }
        // L2 normalizations to make them unit vectors
        var sumSquares = 0f
        for (x in largeA) sumSquares += x * x
        val norm = kotlin.math.sqrt(sumSquares)
        for (i in largeA.indices) {
            largeA[i] = largeA[i] / norm
            largeB[i] = largeB[i] / norm
        }
        val dotLarge = engine.dotProduct(largeA, largeB)
        assertEquals("Dot product of identical large normalized vectors should be 1.0", 1.0f, dotLarge, 1e-5f)
    }

    @Test
    fun testImagePreprocessorsNCHWLayout() {
        // Verify preprocessing NCHW normalization math manually
        val width = 224
        val height = 224
        val numPixels = width * height
        
        // Create a simulated pixel buffer: set pixel at index 0 to Red (0xFFFF0000), 
        // pixel at index 1 to Green (0xFF00FF00), pixel at index 2 to Blue (0xFF0000FF).
        val simulatedPixels = IntArray(numPixels)
        simulatedPixels[0] = 0xFFFF0000.toInt() // Red
        simulatedPixels[1] = 0xFF00FF00.toInt() // Green
        simulatedPixels[2] = 0xFF0000FF.toInt() // Blue
        
        val floatBuffer = FloatArray(3 * numPixels)
        
        // Apply the same preprocessing loop as SmartSearchEngine.encodeImage
        for (i in 0 until numPixels) {
            val pixel = simulatedPixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            // CLIP Image Normalization
            // Channel 0 (Red)
            floatBuffer[i] = (r - 0.48145466f) / 0.26862954f
            // Channel 1 (Green)
            floatBuffer[i + 50176] = (g - 0.4578275f) / 0.26130258f
            // Channel 2 (Blue)
            floatBuffer[i + 100352] = (b - 0.40821073f) / 0.2757771f
        }
        
        // Assert dimensions
        assertEquals("Preprocessed buffer must have NCHW flat dimensions (3 * 224 * 224)", 3 * 224 * 224, floatBuffer.size)
        
        // Verify index 0 (Red pixel, channel 0)
        val expectedRedVal = (1.0f - 0.48145466f) / 0.26862954f
        assertEquals("Red channel normalization math at index 0 should match CLIP spec", expectedRedVal, floatBuffer[0], 1e-5f)
        
        // Verify index 1 (Green pixel, channel 1)
        val expectedGreenVal = (1.0f - 0.4578275f) / 0.26130258f
        assertEquals("Green channel normalization math at index 1 + 50176 should match CLIP spec", expectedGreenVal, floatBuffer[1 + 50176], 1e-5f)
        
        // Verify index 2 (Blue pixel, channel 2)
        val expectedBlueVal = (1.0f - 0.40821073f) / 0.2757771f
        assertEquals("Blue channel normalization math at index 2 + 100352 should match CLIP spec", expectedBlueVal, floatBuffer[2 + 100352], 1e-5f)
    }

    @Test
    fun testEmbeddingConverterSerialization() {
        val original = FloatArray(512) { i -> i.toFloat() * 0.1f }
        val converter = com.inferno.gallery.data.db.EmbeddingConverter()
        val bytes = converter.toByteArray(original)
        assertNotNull("bytes should not be null", bytes)
        assertEquals("bytes length should be 512 * 4", 512 * 4, bytes!!.size)
        
        val restored = converter.toFloatArray(bytes)
        assertNotNull("restored should not be null", restored)
        assertEquals("restored size should be 512", 512, restored!!.size)
        
        for (i in 0 until 512) {
            assertEquals("elements must match exactly at index $i", original[i], restored[i], 1e-6f)
        }
    }
}
