package com.example.moasis.domain.nlu

class SlotExtractor {
    fun extract(text: String): Map<String, String> {
        val normalized = text.lowercase()
        val slots = linkedMapOf<String, String>()

        if ("arm" in normalized) {
            slots["location"] = "arm"
        }
        if ("hand" in normalized) {
            slots["location"] = "hand"
        }
        if ("face" in normalized) {
            slots["location"] = "face"
        }
        if ("adult" in normalized) {
            slots["patient_type"] = "adult"
        }
        if ("child" in normalized || "kid" in normalized) {
            slots["patient_type"] = "child"
        }
        if (normalized == "yes" || normalized.startsWith("yes ")) {
            slots["response"] = "yes"
        }
        if (normalized == "no" || normalized.startsWith("no ")) {
            slots["response"] = "no"
        }
        if ("blister" in normalized) {
            slots["burn_severity"] = "yes"
        }
        if ("bleeding" in normalized || "blood" in normalized) {
            slots["has_massive_bleeding"] = "yes"
        }
        if ("can't breathe" in normalized || "cannot breathe" in normalized || "trouble breathing" in normalized) {
            slots["has_breathing_problem"] = "yes"
        }

        return slots
    }
}
