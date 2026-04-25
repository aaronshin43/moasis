package com.example.moasis.domain.nlu

class SlotExtractor {
    fun extract(text: String): Map<String, String> {
        val normalized = text.lowercase()
        val slots = linkedMapOf<String, String>()
        val mentionsBurn = "burn" in normalized || "burned" in normalized || "burnt" in normalized

        if ("arm" in normalized) {
            slots["location"] = "arm"
        }
        if ("hand" in normalized) {
            slots["location"] = "hand"
        }
        if ("face" in normalized) {
            slots["location"] = "face"
        }
        if ("eye" in normalized || "eyes" in normalized) {
            slots["location"] = "eye"
        }
        if ("adult" in normalized) {
            slots["patient_type"] = "adult"
        }
        if ("infant" in normalized || "baby" in normalized) {
            slots["patient_type"] = "infant"
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
        if (
            "not breathing" in normalized ||
            "no pulse" in normalized ||
            "needs cpr" in normalized ||
            "cardiac arrest" in normalized
        ) {
            slots["cardiac_arrest_confirmed"] = "yes"
            slots["breathing_normal"] = "no"
            slots["responsive"] = "no"
        }
        if (
            "blister" in normalized ||
            "peeled skin" in normalized ||
            "peeling skin" in normalized ||
            "open burn" in normalized ||
            "charred" in normalized ||
            "deep burn" in normalized
        ) {
            slots["burn_severity"] = "yes"
        }
        if (
            (mentionsBurn && ("face" in normalized || "hand" in normalized || "foot" in normalized || "feet" in normalized || "genital" in normalized)) ||
            "large burn" in normalized ||
            "large area burn" in normalized ||
            "breathing affected" in normalized ||
            "smoke inhalation" in normalized
        ) {
            slots["burn_emergency_red_flags"] = "yes"
        }
        if (
            "bleeding a lot" in normalized ||
            "blood everywhere" in normalized ||
            "spurting blood" in normalized ||
            "spurting" in normalized ||
            "pooling blood" in normalized ||
            "won't stop bleeding" in normalized
        ) {
            slots["has_massive_bleeding"] = "yes"
        }
        if ("choking" in normalized || "something stuck in throat" in normalized) {
            slots["has_choking_signs"] = "yes"
        }
        if (
            "can cough" in normalized ||
            "still coughing" in normalized ||
            "can speak" in normalized ||
            "still talking" in normalized ||
            "can cry" in normalized ||
            "still crying" in normalized
        ) {
            slots["can_cough_or_speak"] = "yes"
        }
        if (
            "can't speak" in normalized ||
            "cannot speak" in normalized ||
            "can't cough" in normalized ||
            "cannot cough" in normalized ||
            "can't cry" in normalized ||
            "cannot cry" in normalized ||
            "silent choking" in normalized
        ) {
            slots["can_cough_or_speak"] = "no"
        }
        if ("seizure" in normalized || "convulsion" in normalized) {
            slots["has_seizure_signs"] = "yes"
        }
        if (
            "having a seizure" in normalized ||
            "is seizing" in normalized ||
            "convulsing" in normalized ||
            "shaking and unresponsive" in normalized
        ) {
            slots["seizure_active_now"] = "yes"
        }
        if (
            "seizure stopped" in normalized ||
            "just had a seizure" in normalized ||
            "after the seizure" in normalized
        ) {
            slots["seizure_active_now"] = "no"
        }
        if ("chest pain" in normalized || "heart attack" in normalized) {
            slots["has_chest_pain"] = "yes"
        }
        if (
            "face droop" in normalized ||
            "face drooping" in normalized ||
            "slurred speech" in normalized ||
            "speech is slurred" in normalized ||
            "arm weakness" in normalized ||
            "stroke" in normalized
        ) {
            slots["has_stroke_signs"] = "yes"
        }
        if ("allergic reaction" in normalized || "anaphylaxis" in normalized || "swollen tongue" in normalized || "swollen lips" in normalized) {
            slots["has_anaphylaxis_signs"] = "yes"
        }
        if ("low blood sugar" in normalized || "hypoglycemia" in normalized || "diabetic and shaky" in normalized) {
            slots["has_hypoglycemia_signs"] = "yes"
        }
        if (
            "can swallow" in normalized ||
            "awake and swallowing" in normalized ||
            "alert and swallowing" in normalized
        ) {
            slots["can_swallow_safely"] = "yes"
        }
        if (
            "cannot swallow" in normalized ||
            "can't swallow" in normalized ||
            "unresponsive diabetic" in normalized ||
            "unresponsive and diabetic" in normalized
        ) {
            slots["can_swallow_safely"] = "no"
        }
        if ("can't breathe" in normalized || "cannot breathe" in normalized || "trouble breathing" in normalized) {
            slots["has_breathing_problem"] = "yes"
        }
        if (
            "is breathing" in normalized ||
            "breathing now" in normalized ||
            "breathing normally" in normalized
        ) {
            slots["breathing_normal"] = "yes"
        }
        if (
            "blue lips" in normalized ||
            "blue around the lips" in normalized ||
            "cannot speak full sentences" in normalized ||
            "can't speak full sentences" in normalized ||
            "unable to speak" in normalized ||
            "too breathless to speak" in normalized ||
            "getting drowsy" in normalized ||
            "very drowsy" in normalized
        ) {
            slots["breathing_red_flags"] = "yes"
        }
        if (
            "in the eye" in normalized ||
            "in the eyes" in normalized ||
            "on the skin" in normalized ||
            "chemical on skin" in normalized ||
            "chemical in eye" in normalized
        ) {
            slots["poison_contact_exposure"] = "yes"
        }
        if (
            "breathed fumes" in normalized ||
            "inhaled fumes" in normalized ||
            "poison gas" in normalized ||
            "chemical fumes" in normalized
        ) {
            slots["poison_inhalation_exposure"] = "yes"
        }
        if ("chemical in eye" in normalized || "chemical splash" in normalized || "bleach in the eye" in normalized || "acid in eye" in normalized) {
            slots["eye_chemical_exposure"] = "yes"
        }
        if (
            "something stuck in eye" in normalized ||
            "object in eye" in normalized ||
            "metal in eye" in normalized ||
            "glass in eye" in normalized ||
            "embedded in eye" in normalized ||
            "vision loss" in normalized
        ) {
            slots["eye_embedded_or_vision_loss"] = "yes"
        }
        if (
            "lost consciousness" in normalized ||
            "passed out after hitting head" in normalized ||
            "vomiting after head injury" in normalized ||
            "repeated vomiting" in normalized ||
            "unequal pupils" in normalized ||
            "confused after hitting head" in normalized ||
            "fluid from ear" in normalized ||
            "blood from ear" in normalized
        ) {
            slots["head_red_flags"] = "yes"
        }
        if (
            "power line" in normalized ||
            "high voltage" in normalized ||
            "fallen wire" in normalized ||
            "industrial machine" in normalized
        ) {
            slots["high_voltage_source"] = "yes"
        }
        if (
            "bone sticking out" in normalized ||
            "open fracture" in normalized ||
            "neck injury" in normalized ||
            "back injury" in normalized ||
            "spine injury" in normalized
        ) {
            slots["fracture_red_flags"] = "yes"
        }
        if (
            "very hot skin" in normalized ||
            "hot and confused" in normalized ||
            "confused in the heat" in normalized ||
            "passed out from heat" in normalized ||
            "collapsed from heat" in normalized
        ) {
            slots["heat_stroke_red_flags"] = "yes"
        }
        if (
            "mild hypothermia" in normalized ||
            "shivering but alert" in normalized
        ) {
            slots["hypothermia_severe"] = "no"
        }
        if (
            "confused from cold" in normalized ||
            "drowsy and cold" in normalized ||
            "slow breathing from cold" in normalized ||
            "slurred speech from cold" in normalized
        ) {
            slots["hypothermia_severe"] = "yes"
        }
        if (
            "20 minutes" in normalized ||
            "won't stop nosebleed" in normalized ||
            "heavy nosebleed" in normalized ||
            "nosebleed after head injury" in normalized ||
            "blood thinners" in normalized
        ) {
            slots["nosebleed_red_flags"] = "yes"
        }

        return slots
    }
}
