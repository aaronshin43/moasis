package com.example.moasis

import com.example.moasis.domain.model.DomainIntent
import com.example.moasis.domain.model.EntryIntent
import com.example.moasis.domain.nlu.EmbeddingNearestLabelClassifier
import com.example.moasis.domain.nlu.IntentClassifier
import com.example.moasis.domain.nlu.IntentMatch
import com.example.moasis.domain.nlu.RegexIntentMatcher
import com.example.moasis.domain.nlu.SentenceEmbedder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingNearestLabelClassifierTest {
    @Test
    fun nearest_label_picks_bleeding_when_embedding_is_closest() {
        val classifier = EmbeddingNearestLabelClassifier(
            embedder = FakeSentenceEmbedder(
                mapOf(
                    "there is blood all over the floor from a cut" to floatArrayOf(0.95f, 0.05f, 0f),
                ),
                defaultVector = floatArrayOf(0f, 0f, 1f),
            ),
            fallbackMatcher = AlwaysFallbackMatcher(
                IntentMatch(entryIntent = EntryIntent.GENERAL_EMERGENCY, confidence = 0.4f),
            ),
        )

        val result = classifier.match("there is blood all over the floor from a cut")
        assertEquals(EntryIntent.BLEEDING, result.entryIntent)
        assertTrue(DomainIntent.BLEEDING in result.domainHints)
    }

    @Test
    fun very_high_precision_regex_match_overrides_embedding() {
        val classifier = EmbeddingNearestLabelClassifier(
            embedder = FakeSentenceEmbedder(
                values = mapOf("he is not breathing and has no pulse" to floatArrayOf(0f, 1f, 0f)),
                defaultVector = floatArrayOf(0f, 0f, 1f),
            ),
            fallbackMatcher = AlwaysFallbackMatcher(
                IntentMatch(
                    entryIntent = EntryIntent.PERSON_COLLAPSED,
                    domainHints = setOf(DomainIntent.CARDIAC_ARREST),
                    confidence = 0.99f,
                ),
            ),
        )

        val result = classifier.match("he is not breathing and has no pulse")
        assertEquals(EntryIntent.PERSON_COLLAPSED, result.entryIntent)
        assertTrue(DomainIntent.CARDIAC_ARREST in result.domainHints)
    }

    @Test
    fun low_similarity_falls_back_to_regex_matcher() {
        val fallback = RegexIntentMatcher()
        val classifier = EmbeddingNearestLabelClassifier(
            embedder = FakeSentenceEmbedder(
                values = mapOf("my friend collapsed" to floatArrayOf(0f, 0f, 0f)),
                defaultVector = floatArrayOf(0f, 0f, 0f),
            ),
            fallbackMatcher = fallback,
        )

        val result = classifier.match("my friend collapsed")
        assertEquals(EntryIntent.PERSON_COLLAPSED, result.entryIntent)
        assertTrue(DomainIntent.COLLAPSE in result.domainHints)
    }
}

private class FakeSentenceEmbedder(
    private val values: Map<String, FloatArray>,
    private val defaultVector: FloatArray,
) : SentenceEmbedder {
    override fun embed(texts: List<String>): List<FloatArray> {
        return texts.map { text ->
            values[text] ?: prototypeForText(text) ?: defaultVector
        }
    }

    private fun prototypeForText(text: String): FloatArray? {
        val normalized = text.lowercase()
        return when {
            "bleeding" in normalized || "blood" in normalized || "cut" in normalized -> floatArrayOf(1f, 0f, 0f)
            "not breathing" in normalized || "no pulse" in normalized || "cpr" in normalized -> floatArrayOf(0f, 1f, 0f)
            "collapsed" in normalized || "fainted" in normalized || "passed out" in normalized -> floatArrayOf(0f, 0f, 1f)
            else -> null
        }
    }
}

private class AlwaysFallbackMatcher(
    private val result: IntentMatch,
) : IntentClassifier {
    override fun match(text: String): IntentMatch = result
}
