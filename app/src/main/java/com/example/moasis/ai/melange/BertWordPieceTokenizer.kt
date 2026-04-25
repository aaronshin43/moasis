package com.example.moasis.ai.melange

import java.text.Normalizer
import java.util.Locale

class BertWordPieceTokenizer(
    vocabularyLines: List<String>,
) {
    private val vocabulary: Map<String, Int> = vocabularyLines
        .mapIndexed { index, token -> token.trim() to index }
        .filter { it.first.isNotEmpty() }
        .toMap()

    private val padId = requireTokenId("[PAD]")
    private val unkId = requireTokenId("[UNK]")
    private val clsId = requireTokenId("[CLS]")
    private val sepId = requireTokenId("[SEP]")

    fun encode(
        text: String,
        maxSequenceLength: Int,
    ): TokenizedText {
        require(maxSequenceLength >= 2) { "maxSequenceLength must be at least 2" }

        val tokens = mutableListOf(clsId)
        basicTokenize(text)
            .flatMap { wordPieceTokenize(it) }
            .take(maxSequenceLength - 2)
            .forEach(tokens::add)
        tokens += sepId

        val attentionMask = IntArray(maxSequenceLength)
        val tokenTypeIds = IntArray(maxSequenceLength)
        val inputIds = IntArray(maxSequenceLength) { index ->
            when {
                index < tokens.size -> {
                    attentionMask[index] = 1
                    tokens[index]
                }
                else -> padId
            }
        }

        return TokenizedText(
            inputIds = inputIds,
            attentionMask = attentionMask,
            tokenTypeIds = tokenTypeIds,
            nonPaddingTokenCount = attentionMask.count { it == 1 },
        )
    }

    private fun basicTokenize(text: String): List<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.US)

        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.setLength(0)
            }
        }

        normalized.forEach { char ->
            when {
                char.isLetterOrDigit() -> current.append(char)
                char.isWhitespace() -> flush()
                else -> {
                    flush()
                    tokens += char.toString()
                }
            }
        }
        flush()
        return tokens
    }

    private fun wordPieceTokenize(token: String): List<Int> {
        if (token.isBlank()) {
            return emptyList()
        }
        vocabulary[token]?.let { return listOf(it) }

        val pieces = mutableListOf<Int>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var matchedId: Int? = null
            while (start < end) {
                val candidate = buildString {
                    if (start > 0) {
                        append("##")
                    }
                    append(token.substring(start, end))
                }
                val vocabId = vocabulary[candidate]
                if (vocabId != null) {
                    matchedId = vocabId
                    break
                }
                end -= 1
            }
            if (matchedId == null) {
                return listOf(unkId)
            }
            pieces += matchedId
            start = end
        }
        return pieces
    }

    private fun requireTokenId(token: String): Int {
        return requireNotNull(vocabulary[token]) { "Tokenizer vocab is missing required token $token" }
    }
}

data class TokenizedText(
    val inputIds: IntArray,
    val attentionMask: IntArray,
    val tokenTypeIds: IntArray,
    val nonPaddingTokenCount: Int,
)
