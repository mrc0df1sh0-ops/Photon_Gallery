package com.inferno.gallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.util.Collections

class ONNXImageEncoder(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    fun initialize() {
        if (ortEnv != null) return
        ortEnv = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            // Limit threads to prevent background stutter
            setIntraOpNumThreads(4)
        }
        val assetManager = context.assets
        val modelBytes = assetManager.open("mobileclip_s2_image_int8.onnx").readBytes()
        ortSession = ortEnv?.createSession(modelBytes, options)
    }

    suspend fun encodeImage(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        if (ortEnv == null || ortSession == null) {
            initialize()
        }
        
        val targetSize = 256
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        
        // MobileCLIP normalization (assuming standard CLIP normalization for Xenova model)
        val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
        
        // Shape: [1, 3, 256, 256]
        val floatBuffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize)
        val pixels = IntArray(targetSize * targetSize)
        scaledBitmap.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

        for (i in 0 until targetSize * targetSize) {
            val pixel = pixels[i]
            val r = ((pixel shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
            val g = ((pixel shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
            val b = ((pixel and 0xFF) / 255.0f - mean[2]) / std[2]

            // Planar format: RRR... GGG... BBB...
            floatBuffer.put(i, r)
            floatBuffer.put(i + targetSize * targetSize, g)
            floatBuffer.put(i + 2 * targetSize * targetSize, b)
        }

        val inputName = ortSession!!.inputNames.iterator().next()
        val tensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong()))
        
        ortSession!!.run(Collections.singletonMap(inputName, tensor)).use { result ->
            val outputTensor = result[0] as OnnxTensor
            val embeddings = outputTensor.floatBuffer.array()
            
            // L2 Normalize the embeddings
            var sum = 0f
            for (v in embeddings) sum += v * v
            val norm = Math.sqrt(sum.toDouble()).toFloat()
            for (i in embeddings.indices) embeddings[i] /= norm
            
            return@withContext embeddings
        }
    }
}
