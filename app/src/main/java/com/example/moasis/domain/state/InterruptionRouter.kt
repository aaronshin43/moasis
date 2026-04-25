package com.example.moasis.domain.state

class InterruptionRouter {
    fun classify(text: String): InterruptionDecision {
        val normalized = text.lowercase()

        return when {
            normalized.containsAny(
                "can't breathe",
                "cannot breathe",
                "trouble breathing",
                "breathing is strange",
                "collapsed",
                "not responding",
                "unconscious",
                "heavy bleeding",
                "bleeding a lot",
                "chest pain",
                "seizure",
                "convulsing",
                "throat swelling",
                "choking",
            ) -> InterruptionDecision(type = InterruptionType.STATE_CHANGING_REPORT)

            normalized in setOf("done", "next", "stop", "repeat", "say that again") ->
                InterruptionDecision(
                    type = InterruptionType.CONTROL_INTENT,
                    controlIntent = when (normalized) {
                        "next" -> ControlIntent.NEXT
                        "done" -> ControlIntent.DONE
                        "repeat", "say that again" -> ControlIntent.REPEAT
                        "stop" -> ControlIntent.STOP
                        else -> ControlIntent.UNKNOWN
                    },
                )

            normalized.contains("?") ||
                normalized.startsWith("can ") ||
                normalized.startsWith("can i ") ||
                normalized.startsWith("why ") ||
                normalized.startsWith("what ") ||
                normalized.startsWith("how ") ||
                normalized.startsWith("is ") ||
                normalized.startsWith("should ") ->
                InterruptionDecision(type = InterruptionType.CLARIFICATION_QUESTION)

            else -> InterruptionDecision(type = InterruptionType.OUT_OF_DOMAIN)
        }
    }

    private fun String.containsAny(vararg values: String): Boolean {
        return values.any { contains(it) }
    }
}

data class InterruptionDecision(
    val type: InterruptionType,
    val controlIntent: ControlIntent? = null,
)

enum class InterruptionType {
    CONTROL_INTENT,
    CLARIFICATION_QUESTION,
    STATE_CHANGING_REPORT,
    OUT_OF_DOMAIN,
}

enum class ControlIntent {
    NEXT,
    DONE,
    REPEAT,
    STOP,
    UNKNOWN,
}
