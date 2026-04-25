package com.example.moasis.domain.nlu

import android.util.Log
import com.example.moasis.domain.model.DomainIntent
import com.example.moasis.domain.model.EntryIntent
import kotlin.math.sqrt

class EmbeddingNearestLabelClassifier(
    private val embedder: SentenceEmbedder,
    private val fallbackMatcher: IntentClassifier = RegexIntentMatcher(),
    private val similarityThreshold: Float = 0.58f,
) : IntentClassifier {
    @Volatile
    private var cachedLabelEmbeddings: List<LabeledEmbedding>? = null

    override fun match(text: String): IntentMatch {
        val normalized = text.trim()
        val fallback = fallbackMatcher.match(normalized)
        if (normalized.isBlank()) {
            logDebug("Skipping embedding classification for blank input")
            return fallback
        }
        if (fallback.domainHints.isNotEmpty() && fallback.confidence >= 0.97f) {
            logDebug(
                "Regex hard override matched input=\"${normalized.preview()}\" intent=${fallback.entryIntent} domain=${fallback.domainHints} confidence=${"%.2f".format(fallback.confidence)}",
            )
            return fallback
        }

        val queryEmbedding = runCatching { embedder.embed(listOf(normalized)).firstOrNull() }.getOrNull()
            ?: run {
                logWarn(
                    "Embedding generation failed for input=\"${normalized.preview()}\". Falling back to regex intent=${fallback.entryIntent} domain=${fallback.domainHints}",
                )
                return fallback
            }
        val labelEmbeddings = ensureLabelEmbeddings()
        if (labelEmbeddings.isEmpty()) {
            logWarn("No cached label embeddings available. Falling back to regex.")
            return fallback
        }

        val ranked = labelEmbeddings
            .map { labeled ->
                ScoredLabel(
                    label = labeled,
                    similarity = cosineSimilarity(queryEmbedding, labeled.embedding),
                )
            }
            .sortedByDescending { it.similarity }

        val best = ranked.firstOrNull() ?: return fallback
        val bestSimilarity = best.similarity
        logDebug(
            "Embedding match input=\"${normalized.preview()}\" top=${ranked.take(3).joinToString { it.summary() }} fallbackIntent=${fallback.entryIntent} fallbackDomain=${fallback.domainHints}",
        )
        if (bestSimilarity < similarityThreshold) {
            logDebug(
                "Embedding similarity below threshold (${format(bestSimilarity)} < ${format(similarityThreshold)}). Falling back to regex intent=${fallback.entryIntent} domain=${fallback.domainHints}",
            )
            return fallback
        }

        val embeddingMatch = IntentMatch(
            entryIntent = best.label.entryIntent,
            domainHints = setOf(best.label.domainIntent),
            confidence = similarityToConfidence(bestSimilarity),
        )

        return when {
            best.label.domainIntent in fallback.domainHints -> {
                val resolved = embeddingMatch.copy(confidence = maxOf(embeddingMatch.confidence, fallback.confidence))
                logDebug(
                    "Embedding agreed with regex. Using domain=${best.label.domainIntent} confidence=${format(resolved.confidence)}",
                )
                resolved
            }

            fallback.domainHints.isEmpty() -> {
                logDebug(
                    "Using embedding result intent=${embeddingMatch.entryIntent} domain=${embeddingMatch.domainHints} confidence=${format(embeddingMatch.confidence)}",
                )
                embeddingMatch
            }

            fallback.confidence >= embeddingMatch.confidence + 0.08f -> {
                logDebug(
                    "Regex fallback won over embedding. regex=${fallback.domainHints}(${format(fallback.confidence)}) embedding=${embeddingMatch.domainHints}(${format(embeddingMatch.confidence)})",
                )
                fallback
            }

            else -> {
                logDebug(
                    "Using embedding over regex. embedding=${embeddingMatch.domainHints}(${format(embeddingMatch.confidence)}) regex=${fallback.domainHints}(${format(fallback.confidence)})",
                )
                embeddingMatch
            }
        }
    }

    private fun ensureLabelEmbeddings(): List<LabeledEmbedding> {
        cachedLabelEmbeddings?.let { return it }
        synchronized(this) {
            cachedLabelEmbeddings?.let { return it }
            val labels = LABEL_DEFINITIONS
            val examples = labels.flatMap { label ->
                label.examples.map { example -> label to example }
            }
            val embeddings = runCatching {
                embedder.embed(examples.map { it.second })
            }.getOrDefault(emptyList())
            val grouped = examples.zip(embeddings)
                .groupBy({ it.first.first }, { it.second })
                .mapNotNull { (label, vectors) ->
                    if (vectors.isEmpty()) {
                        null
                    } else {
                        LabeledEmbedding(
                            entryIntent = label.entryIntent,
                            domainIntent = label.domainIntent,
                            embedding = meanNormalize(vectors),
                        )
                    }
                }
            logDebug("Prepared ${grouped.size} label embeddings from ${examples.size} prototype examples")
            cachedLabelEmbeddings = grouped
            return grouped
        }
    }

    private fun similarityToConfidence(similarity: Float): Float {
        return when {
            similarity >= 0.82f -> 0.95f
            similarity >= 0.75f -> 0.91f
            similarity >= 0.68f -> 0.86f
            similarity >= 0.62f -> 0.8f
            else -> 0.72f
        }
    }

    private fun meanNormalize(vectors: List<FloatArray>): FloatArray {
        val size = vectors.first().size
        val averaged = FloatArray(size)
        vectors.forEach { vector ->
            for (index in 0 until size) {
                averaged[index] += vector[index]
            }
        }
        for (index in averaged.indices) {
            averaged[index] /= vectors.size
        }
        return normalize(averaged)
    }

    private fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size || left.isEmpty()) {
            return -1f
        }
        var dot = 0f
        var leftNorm = 0f
        var rightNorm = 0f
        for (index in left.indices) {
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        if (leftNorm == 0f || rightNorm == 0f) {
            return -1f
        }
        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).coerceIn(-1f, 1f)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        vector.forEach { value -> sum += value * value }
        if (sum == 0f) {
            return vector
        }
        val norm = sqrt(sum)
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }

    companion object {
        private const val TAG = "EmbeddingClassifier"

        private val LABEL_DEFINITIONS = listOf(
            label(EntryIntent.PERSON_COLLAPSED, DomainIntent.CARDIAC_ARREST, "person is not breathing and has no pulse", "collapsed and needs cpr", "heart stopped and unresponsive"),
            label(EntryIntent.PERSON_COLLAPSED, DomainIntent.COLLAPSE, "person fainted and will not wake up", "collapsed and passed out", "suddenly unconscious and not responding"),
            label(EntryIntent.BREATHING_PROBLEM, DomainIntent.BREATHING_PROBLEM, "cannot breathe and is gasping", "short of breath and wheezing", "struggling to breathe"),
            label(EntryIntent.INJURY_REPORT, DomainIntent.BURN, "burned skin with blisters", "scald burn on arm", "chemical burn on hand"),
            label(EntryIntent.BLEEDING, DomainIntent.NOSEBLEED, "heavy nosebleed that will not stop", "bleeding from the nose", "nose bleed for twenty minutes"),
            label(EntryIntent.BLEEDING, DomainIntent.BLEEDING, "deep cut with severe bleeding", "blood is spurting from wound", "bleeding a lot from injury"),
            label(EntryIntent.CHOKING, DomainIntent.CHOKING, "child is choking and cannot speak", "food stuck in throat", "silent choking and cannot cough"),
            label(EntryIntent.SEIZURE, DomainIntent.SEIZURE, "person is having a seizure", "convulsing and unresponsive", "seizure just started"),
            label(EntryIntent.CHEST_PAIN, DomainIntent.CHEST_PAIN, "crushing chest pain and pressure", "possible heart attack with chest tightness", "pain in chest with sweating"),
            label(EntryIntent.GENERAL_EMERGENCY, DomainIntent.STROKE, "face drooping and slurred speech", "one sided weakness and possible stroke", "arm weakness with stroke symptoms"),
            label(EntryIntent.ALLERGIC_REACTION, DomainIntent.ALLERGIC_REACTION, "anaphylaxis with swollen tongue", "allergic reaction and throat closing", "severe allergic swelling and trouble breathing"),
            label(EntryIntent.POISONING, DomainIntent.POISONING, "swallowed bleach or cleaner", "overdose after taking too many pills", "poisoning from toxic substance"),
            label(EntryIntent.GENERAL_EMERGENCY, DomainIntent.DROWNING, "pulled from water and nearly drowned", "drowning victim after submersion", "rescued from water and not breathing well"),
            label(EntryIntent.INJURY_REPORT, DomainIntent.ELECTRIC_SHOCK, "electric shock from power line", "electrocuted by live wire", "shocked from outlet"),
            label(EntryIntent.TRAUMA, DomainIntent.EYE_INJURY, "chemical in eye with pain", "glass stuck in eye", "scratched eye and cannot see well"),
            label(EntryIntent.TRAUMA, DomainIntent.FRACTURE, "broken bone with deformity", "bone sticking out from leg", "cannot bear weight after fall"),
            label(EntryIntent.TRAUMA, DomainIntent.HEAD_INJURY, "head injury after fall", "concussion with vomiting", "hit head and became confused"),
            label(EntryIntent.GENERAL_EMERGENCY, DomainIntent.HEAT_STROKE, "collapsed from heat with hot skin", "heat stroke and confusion", "overheated and passed out"),
            label(EntryIntent.GENERAL_EMERGENCY, DomainIntent.HYPOGLYCEMIA, "diabetic with very low blood sugar", "unresponsive diabetic after insulin", "blood sugar crashed and shaky"),
            label(EntryIntent.GENERAL_EMERGENCY, DomainIntent.HYPOTHERMIA, "very cold with hypothermia", "shivering uncontrollably after cold exposure", "drowsy and freezing outside"),
        )

        private fun label(
            entryIntent: EntryIntent,
            domainIntent: DomainIntent,
            vararg examples: String,
        ) = LabelDefinition(
            entryIntent = entryIntent,
            domainIntent = domainIntent,
            examples = examples.toList(),
        )

        private fun format(value: Float): String = "%.2f".format(value)

        private fun logDebug(message: String) {
            runCatching { Log.d(TAG, message) }
        }

        private fun logWarn(message: String) {
            runCatching { Log.w(TAG, message) }
        }
    }
}

private data class LabelDefinition(
    val entryIntent: EntryIntent,
    val domainIntent: DomainIntent,
    val examples: List<String>,
)

private data class LabeledEmbedding(
    val entryIntent: EntryIntent,
    val domainIntent: DomainIntent,
    val embedding: FloatArray,
)

private data class ScoredLabel(
    val label: LabeledEmbedding,
    val similarity: Float,
) {
    fun summary(): String {
        return "${label.domainIntent}:${"%.2f".format(similarity)}"
    }
}

private fun String.preview(limit: Int = 80): String {
    return if (length <= limit) this else take(limit) + "..."
}
