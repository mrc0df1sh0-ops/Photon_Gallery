package com.inferno.gallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.sqrt

class ONNXImageEncoder(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    companion object {
        private const val TAG = "ImageEncoder"
    }

    fun initialize() {
        if (ortEnv != null) return
        ortEnv = OrtEnvironment.getEnvironment()

        // PERF OPT-2: Configure session options with hardware acceleration.
        // Try NNAPI → XNNPack → plain CPU in that order and log which one was activated.
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        var delegateActivated = "CPU (plain)"
        try {
            // PERF OPT-2: Try XNNPack delegate — highly optimized for CPU inference on Android.
            // (NNAPI is skipped as its driver often crashes with ORT_FAIL 14 on quantized INT8 models).
            options.addXnnpack(emptyMap())
            delegateActivated = "XNNPack"
        } catch (e: Throwable) {
            android.util.Log.w("ImageEncoder", "XNNPack unavailable: ${e.message}. Using plain CPU.")
        }

        val assetManager = context.assets
        val modelBytes = assetManager.open("mobileclip_s2_image_int8.onnx").readBytes()
        ortSession = ortEnv?.createSession(modelBytes, options)
        Log.d(TAG, "Image encoder session created. Active delegate: $delegateActivated")
    }

    suspend fun encodeImage(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        if (ortEnv == null || ortSession == null) {
            initialize()
        }

        val targetSize = 256

        // PERF OPT-3: Scale to 256×256, then immediately recycle the source bitmap
        // so it doesn't stay alive while the FloatBuffer is being filled.
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        // Source bitmap is recycled by the caller (AIIndexWorker) right after encodeImage returns.
        // scaledBitmap is recycled below, immediately after pixel extraction.

        // MobileCLIP normalization — standard CLIP values
        val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val std  = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

        // Shape: [1, 3, 256, 256] — planar RGB
        val floatBuffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize)
        val pixels = IntArray(targetSize * targetSize)
        scaledBitmap.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

        // PERF OPT-3: Recycle scaled bitmap immediately after pixels are copied out.
        scaledBitmap.recycle()

        for (i in 0 until targetSize * targetSize) {
            val pixel = pixels[i]
            val r = ((pixel shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
            val g = ((pixel shr 8  and 0xFF) / 255.0f - mean[1]) / std[1]
            val b = ((pixel        and 0xFF) / 255.0f - mean[2]) / std[2]
            // Planar format: RRR… GGG… BBB…
            floatBuffer.put(i,                           r)
            floatBuffer.put(i + targetSize * targetSize, g)
            floatBuffer.put(i + 2 * targetSize * targetSize, b)
        }

        val inputName = ortSession!!.inputNames.iterator().next()
        val tensor = OnnxTensor.createTensor(
            ortEnv, floatBuffer,
            longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
        )

        ortSession!!.run(Collections.singletonMap(inputName, tensor)).use { result ->
            val outputTensor = result[0] as OnnxTensor

            // Bug 3 fix: fb.remaining() + fb.get() — safe for ONNX Runtime direct buffers.
            val fb = outputTensor.floatBuffer
            val embeddings = FloatArray(fb.remaining())
            fb.get(embeddings)

            // L2 normalize
            var sum = 0f
            for (v in embeddings) sum += v * v
            val norm = sqrt(sum)
            if (norm > 0f) for (i in embeddings.indices) embeddings[i] /= norm

            return@withContext embeddings
        }
    }

    /**
     * PERF OPT-5: Accepts a pre-built FloatBuffer (filled by Stage 1 of the pipeline)
     * and runs ONNX inference directly, skipping bitmap allocation entirely.
     * Keeps [encodeImage] intact so all existing callers are unaffected.
     */
    suspend fun encodeFromBuffer(floatBuffer: java.nio.FloatBuffer, targetSize: Int): FloatArray =
        withContext(Dispatchers.Default) {
            if (ortEnv == null || ortSession == null) initialize()

            val inputName = ortSession!!.inputNames.iterator().next()
            val tensor = ai.onnxruntime.OnnxTensor.createTensor(
                ortEnv, floatBuffer,
                longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
            )

            ortSession!!.run(Collections.singletonMap(inputName, tensor)).use { result ->
                val outputTensor = result[0] as ai.onnxruntime.OnnxTensor
                val fb = outputTensor.floatBuffer
                val embeddings = FloatArray(fb.remaining())
                fb.get(embeddings)

                var sum = 0f
                for (v in embeddings) sum += v * v
                val norm = sqrt(sum)
                if (norm > 0f) for (i in embeddings.indices) embeddings[i] /= norm

                return@withContext embeddings
            }
        }
}
