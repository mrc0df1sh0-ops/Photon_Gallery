package com.inferno.gallery.data.ai

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import java.util.Collections

class ONNXTextEncoder(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: BPETokenizer? = null

    fun initialize() {
        if (ortEnv != null && tokenizer != null) return
        
        // Initialize Tokenizer
        tokenizer = BPETokenizer(context)

        // Initialize ONNX
        ortEnv = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            // Use CPU via XNNPACK/default instead of NNAPI to prevent hardware driver DEAD_OBJECT crashes
            setIntraOpNumThreads(4)
        }
        val assetManager = context.assets
        val modelBytes = assetManager.open("mobileclip_s2_text_int8.onnx").readBytes()
        ortSession = ortEnv?.createSession(modelBytes, options)
    }

    suspend fun encodeText(query: String): FloatArray = withContext(Dispatchers.Default) {
        if (ortEnv == null || ortSession == null || tokenizer == null) {
            initialize()
        }

        // Tokenize
        val tokenIds = tokenizer!!.encode(query)
        
        // Pad or truncate to exactly 77 tokens (CLIP standard)
        val targetLength = 77
        val finalTokenIds = LongArray(targetLength)
        for (i in 0 until targetLength) {
            if (i < tokenIds.size) {
                finalTokenIds[i] = tokenIds[i]
            } else {
                // PAD token (usually 0 for CLIP, but we check if attention mask matters)
                finalTokenIds[i] = 0L 
            }
        }
        
        // Attention Mask
        val attentionMask = LongArray(targetLength)
        for (i in 0 until targetLength) {
            attentionMask[i] = if (i < tokenIds.size) 1L else 0L
        }

        val inputIdsBuffer = LongBuffer.wrap(finalTokenIds)
        val attentionMaskBuffer = LongBuffer.wrap(attentionMask)
        
        val inputIdsTensor = OnnxTensor.createTensor(ortEnv, inputIdsBuffer, longArrayOf(1, targetLength.toLong()))
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnv, attentionMaskBuffer, longArrayOf(1, targetLength.toLong()))
        
        // Map to inputs. Most optimum exported text models take input_ids and attention_mask
        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        try {
            ortSession!!.run(inputs).use { result ->
                val outputTensor = result[0] as OnnxTensor
                val embeddings = outputTensor.floatBuffer.array()
                
                // L2 Normalize
                var sum = 0f
                for (v in embeddings) sum += v * v
                val norm = Math.sqrt(sum.toDouble()).toFloat()
                for (i in embeddings.indices) embeddings[i] /= norm
                
                return@withContext embeddings
            }
        } catch (e: Exception) {
            // Some models only take input_ids
            val fallbackInputs = mapOf("input_ids" to inputIdsTensor)
            ortSession!!.run(fallbackInputs).use { result ->
                val outputTensor = result[0] as OnnxTensor
                val embeddings = outputTensor.floatBuffer.array()
                
                // L2 Normalize
                var sum = 0f
                for (v in embeddings) sum += v * v
                val norm = Math.sqrt(sum.toDouble()).toFloat()
                for (i in embeddings.indices) embeddings[i] /= norm
                
                return@withContext embeddings
            }
        }
    }
}
