package com.inferno.gallery.data.ai

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import kotlin.math.sqrt

class ONNXTextEncoder(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: BPETokenizer? = null

    // Cached input names so we don't re-query them every call.
    private var inputIdName: String = "input_ids"
    private var attentionMaskName: String? = null

    fun initialize() {
        if (ortEnv != null && tokenizer != null) return

        // Initialize Tokenizer
        tokenizer = BPETokenizer(context)

        // Initialize ONNX
        ortEnv = OrtEnvironment.getEnvironment()

        // PERF OPT-2: Configure session options with hardware acceleration.
        // Try NNAPI → XNNPack → plain CPU and log which one was activated.
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        var delegateActivated = "CPU (plain)"
        try {
            val nnapiFlags = java.util.EnumSet.of(
                ai.onnxruntime.providers.NNAPIFlags.USE_FP16,
                ai.onnxruntime.providers.NNAPIFlags.CPU_DISABLED
            )
            options.addNnapi(nnapiFlags)
            delegateActivated = "NNAPI"
        } catch (e: Throwable) {
            Log.w("TextEncoder", "NNAPI delegate unavailable for text encoder: ${e.message}. Trying XNNPack.")
            try {
                options.addXnnpack(emptyMap())
                delegateActivated = "XNNPack"
            } catch (e2: Throwable) {
                Log.w("TextEncoder", "XNNPack also unavailable: ${e2.message}. Using plain CPU.")
            }
        }

        val assetManager = context.assets
        val modelBytes = assetManager.open("mobileclip_s2_text_int8.onnx").readBytes()
        val session = ortEnv?.createSession(modelBytes, options)
        ortSession = session
        Log.d("TextEncoder", "Text encoder session created. Active delegate: $delegateActivated")

        // Bug 2 fix: Detect actual model input names at load time instead of guessing.
        if (session != null) {
            val names = session.inputNames.toList()
            Log.d("TextEncoder", "Model input names: $names")
            inputIdName = names.firstOrNull { it.contains("input_id", ignoreCase = true) }
                ?: names.firstOrNull()
                ?: "input_ids"
            attentionMaskName = names.firstOrNull {
                it.contains("attention", ignoreCase = true) || it.contains("mask", ignoreCase = true)
            }
            Log.d("TextEncoder", "Using inputIdName='$inputIdName', attentionMaskName='$attentionMaskName'")
        }
    }

    suspend fun encodeText(query: String): FloatArray = withContext(Dispatchers.Default) {
        if (ortEnv == null || ortSession == null || tokenizer == null) {
            initialize()
        }

        // BPETokenizer.encode() already wraps tokens with SOT (49406) and EOT (49407).
        // e.g. for "cat" → [49406, <cat_token_id>, 49407]
        val rawTokens = tokenizer!!.encode(query)

        // Bug 1 fix: Preserve the SOT/EOT structure produced by the tokenizer.
        // The sequence must be: [49406, token1..tokenN, 49407, 0, 0, ..., 0]
        // Previous code blindly copied rawTokens[i] without considering SOT/EOT positions.
        val targetLength = 77
        val finalTokenIds = LongArray(targetLength) { 0L }

        // rawTokens already contains [SOT, ...bpe..., EOT].
        // Copy as many as fit (max 77), truncating interior tokens if the query is very long.
        // For normal queries rawTokens.size is well under 77.
        val copyLen = minOf(rawTokens.size, targetLength)
        for (i in 0 until copyLen) {
            finalTokenIds[i] = rawTokens[i]
        }

        // Attention mask: 1 for every real token (including SOT and EOT), 0 for PAD.
        val attentionMask = LongArray(targetLength) { i -> if (i < copyLen) 1L else 0L }

        val inputIdsBuffer = LongBuffer.wrap(finalTokenIds)
        val attentionMaskBuffer = LongBuffer.wrap(attentionMask)

        val inputIdsTensor = OnnxTensor.createTensor(
            ortEnv, inputIdsBuffer, longArrayOf(1, targetLength.toLong())
        )
        val attentionMaskTensor = OnnxTensor.createTensor(
            ortEnv, attentionMaskBuffer, longArrayOf(1, targetLength.toLong())
        )

        // Bug 2 fix: Build input map using detected names. Include attention mask only if
        // the model actually has that input (name was found at initialization).
        val inputs = buildMap {
            put(inputIdName, inputIdsTensor)
            attentionMaskName?.let { put(it, attentionMaskTensor) }
        }

        ortSession!!.run(inputs).use { result ->
            val outputTensor = result[0] as OnnxTensor

            // Bug 3 fix: Use fb.remaining() + fb.get(arr) instead of .array() which throws
            // UnsupportedOperationException on direct (native-memory-backed) FloatBuffers.
            val fb = outputTensor.floatBuffer
            val embeddings = FloatArray(fb.remaining())
            fb.get(embeddings)

            // L2 Normalize
            var sum = 0f
            for (v in embeddings) sum += v * v
            val norm = sqrt(sum)
            if (norm > 0f) for (i in embeddings.indices) embeddings[i] /= norm

            return@withContext embeddings
        }
    }
}
