package com.example.moasis.domain.nlu

class NluRouter(
    private val intentClassifier: IntentClassifier = RegexIntentMatcher(),
    private val slotExtractor: SlotExtractor = SlotExtractor(),
) {
    fun route(text: String): NluResult {
        val match = intentClassifier.match(text)
        val slots = slotExtractor.extract(text)

        return NluResult(
            entryIntent = match.entryIntent,
            domainHints = match.domainHints,
            slots = slots,
            confidence = match.confidence,
        )
    }
}
