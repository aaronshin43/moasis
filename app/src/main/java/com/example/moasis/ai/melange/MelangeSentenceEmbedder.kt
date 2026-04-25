package com.example.moasis.ai.melange

import android.content.res.AssetManager
import android.util.Log
import com.example.moasis.domain.nlu.SentenceEmbedder
import com.zeticai.mlange.core.common.DataUtils
import com.zeticai.mlange.core.model.ZeticMLangeModelMetadata
import com.zeticai.mlange.core.tensor.DataType
import com.zeticai.mlange.core.tensor.Tensor

class MelangeSentenceEmbedder(
    assetManager: AssetManager,
    private val modelManager: MelangeEmbeddingModelManager,
    tokenizerAssetPath: String = DEFAULT_TOKENIZER_ASSET_PATH,
) : SentenceEmbedder {
    private val tokenizer: BertWordPieceTokenizer by lazy {
        val vocabLines = assetManager.open(tokenizerAssetPath).bufferedReader().use { it.readLines() }
        BertWordPieceTokenizer(vocabLines)
    }

    override fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) {
            return emptyList()
        }
        Log.d(TAG, "Embedding request batchSize=${texts.size}")
        val session = modelManager.peekSession() ?: run {
            Log.d(TAG, "Embedding session not ready in memory. Falling back to regex path.")
            return emptyList()
        }
        val metadata = session.metadata
        val maxSequenceLength = metadata.profileResult.inputTensors.firstOrNull()?.shape?.lastOrNull()
            ?: run {
                Log.e(TAG, "Embedding metadata has no input tensor shape")
                return emptyList()
            }

        return texts.map { text ->
            runCatching {
                val tokenized = tokenizer.encode(text, maxSequenceLength)
                Log.d(
                    TAG,
                    "Tokenized text=\"${text.preview()}\" tokens=${tokenized.nonPaddingTokenCount} maxSeq=$maxSequenceLength",
                )
                val inputs = metadata.profileResult.inputTensors.map { tensorInfo ->
                    createInputTensor(tensorInfo.name, tensorInfo.dtype, tensorInfo.shape, tokenized)
                }.toTypedArray()
                Log.d(
                    TAG,
                    "Running embedding model inputs=${metadata.profileResult.inputTensors.map { "${it.name}:${it.dtype}:${it.shape}" }}",
                )

                val outputs = session.model.run(inputs)
                Log.d(
                    TAG,
                    "Embedding model returned outputs=${metadata.profileResult.outputTensors.map { "${it.name}:${it.dtype}:${it.shape}" }} actualCount=${outputs.size}",
                )
                extractSentenceEmbedding(
                    outputs = outputs,
                    metadata = metadata,
                    attentionMask = tokenized.attentionMask,
                ).also {
                    Log.d(TAG, "Extracted embedding length=${it.size} for text=\"${text.preview()}\"")
                }
            }.onFailure { throwable ->
                Log.e(TAG, "Embedding generation failed for text=\"${text.preview()}\"", throwable)
            }.getOrDefault(FloatArray(0))
        }
    }

    private fun createInputTensor(
        name: String,
        dtypeName: String,
        shape: List<Int>,
        tokenized: TokenizedText,
    ): Tensor {
        val elementCount = shape.reduce(Int::times)
        val values = when (name.lowercase()) {
            "input_ids", "input_id", "ids" -> tokenized.inputIds
            "attention_mask", "mask" -> tokenized.attentionMask
            "token_type_ids", "segment_ids" -> tokenized.tokenTypeIds
            else -> IntArray(elementCount)
        }.let { source ->
            if (source.size == elementCount) {
                source
            } else {
                IntArray(elementCount) { index -> source.getOrElse(index) { 0 } }
            }
        }

        return when (DataType.from(dtypeName)) {
            DataType.Int64 -> Tensor.of(values.map(Int::toLong).toLongArray(), DataType.Int64, shape.toIntArray())
            else -> Tensor.of(values, DataType.Int32, shape.toIntArray())
        }
    }

    private fun extractSentenceEmbedding(
        outputs: Array<Tensor>,
        metadata: ZeticMLangeModelMetadata,
        attentionMask: IntArray,
    ): FloatArray {
        val firstOutput = outputs.firstOrNull() ?: return FloatArray(0)
        val values = DataUtils.convertByteBufferToFloatArray(firstOutput.data.duplicate())
        val outputShape = metadata.profileResult.outputTensors.firstOrNull()?.shape.orEmpty()

        val pooled = when {
            outputShape.size == 2 && outputShape.firstOrNull() == 1 -> values
            outputShape.size == 3 && outputShape.firstOrNull() == 1 -> {
                val sequenceLength = outputShape[1]
                val hiddenSize = outputShape[2]
                meanPoolTokenEmbeddings(values, sequenceLength, hiddenSize, attentionMask)
            }
            else -> values
        }

        return normalize(pooled)
    }

    private fun meanPoolTokenEmbeddings(
        values: FloatArray,
        sequenceLength: Int,
        hiddenSize: Int,
        attentionMask: IntArray,
    ): FloatArray {
        val pooled = FloatArray(hiddenSize)
        var tokenCount = 0
        for (tokenIndex in 0 until sequenceLength) {
            if (attentionMask.getOrElse(tokenIndex) { 0 } == 0) {
                continue
            }
            tokenCount += 1
            val offset = tokenIndex * hiddenSize
            for (hiddenIndex in 0 until hiddenSize) {
                pooled[hiddenIndex] += values[offset + hiddenIndex]
            }
        }
        if (tokenCount == 0) {
            return pooled
        }
        for (index in pooled.indices) {
            pooled[index] /= tokenCount
        }
        return pooled
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var squaredSum = 0f
        vector.forEach { squaredSum += it * it }
        if (squaredSum == 0f) {
            return vector
        }
        val norm = kotlin.math.sqrt(squaredSum)
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }

    companion object {
        private const val TAG = "MelangeSentenceEmbedder"
        const val DEFAULT_TOKENIZER_ASSET_PATH = "tokenizers/all_minilm_l6_v2_vocab.txt"
    }
}

private fun String.preview(limit: Int = 80): String {
    return if (length <= limit) this else take(limit) + "..."
}
