package com.example.moasis.domain.state

data class ObjectPresenceQuery(
    val canonicalLabel: String,
    val spokenLabel: String,
)

class ObjectPresenceQueryParser {
    fun parse(text: String): ObjectPresenceQuery? {
        val normalized = text.lowercase()
        return aliasToCanonical.entries.firstNotNullOfOrNull { (alias, canonical) ->
            if (normalized.contains(alias)) {
                ObjectPresenceQuery(
                    canonicalLabel = canonical,
                    spokenLabel = alias,
                )
            } else {
                null
            }
        }
    }

    companion object {
        private val aliasToCanonical = linkedMapOf(
            "water bottle" to "bottle",
            "bottle" to "bottle",
            "cup" to "cup",
            "person" to "person",
            "people" to "person",
            "phone" to "cell phone",
            "cell phone" to "cell phone",
            "mobile phone" to "cell phone",
            "scissors" to "scissors",
            "knife" to "knife",
            "fork" to "fork",
            "spoon" to "spoon",
            "book" to "book",
            "clock" to "clock",
            "chair" to "chair",
            "couch" to "couch",
            "sofa" to "couch",
            "bed" to "bed",
            "sink" to "sink",
            "toilet" to "toilet",
            "backpack" to "backpack",
            "bag" to "handbag",
            "handbag" to "handbag",
            "suitcase" to "suitcase",
            "laptop" to "laptop",
            "keyboard" to "keyboard",
            "mouse" to "mouse",
            "remote" to "remote",
            "tv" to "tv",
            "monitor" to "tv",
            "gloves" to "baseball glove",
            "glove" to "baseball glove",
            "ball" to "sports ball",
            "sports ball" to "sports ball",
        )
    }
}
