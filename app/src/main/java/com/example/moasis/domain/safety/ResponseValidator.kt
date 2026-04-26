package com.example.moasis.domain.safety

interface ResponseValidator {
    fun validatePersonalizedStep(
        canonicalText: String,
        responseText: String = canonicalText,
        mustKeepKeywords: List<String> = emptyList(),
        forbiddenKeywords: List<String> = emptyList(),
    ): ValidationResult

    fun validatePersonalizedQuestion(
        canonicalText: String,
        responseText: String,
    ): ValidationResult

    fun validateQuestionAnswer(
        canonicalText: String,
        responseText: String,
        mustKeepKeywords: List<String> = emptyList(),
        forbiddenKeywords: List<String> = emptyList(),
    ): ValidationResult
}

class KeywordResponseValidator : ResponseValidator {
    override fun validatePersonalizedStep(
        canonicalText: String,
        responseText: String,
        mustKeepKeywords: List<String>,
        forbiddenKeywords: List<String>,
    ): ValidationResult {
        val normalized = responseText.lowercase()
        val missingKeyword = mustKeepKeywords.firstOrNull { keyword ->
            !normalized.contains(keyword.lowercase())
        }
        if (missingKeyword != null) {
            return ValidationResult(
                isValid = false,
                resolvedText = canonicalText,
                reason = "Missing required keyword: $missingKeyword",
            )
        }

        val forbiddenKeyword = forbiddenKeywords.firstOrNull { keyword ->
            normalized.contains(keyword.lowercase())
        }
        if (forbiddenKeyword != null) {
            return ValidationResult(
                isValid = false,
                resolvedText = canonicalText,
                reason = "Forbidden keyword detected: $forbiddenKeyword",
            )
        }

        if (responseText.length > canonicalText.length + 120) {
            return ValidationResult(
                isValid = false,
                resolvedText = canonicalText,
                reason = "Response exceeded safe length bound.",
            )
        }

        return ValidationResult(
            isValid = true,
            resolvedText = responseText.ifBlank { canonicalText },
        )
    }

    override fun validatePersonalizedQuestion(
        canonicalText: String,
        responseText: String,
    ): ValidationResult {
        val trimmed = responseText.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(
                isValid = false,
                resolvedText = canonicalText,
                reason = "Question rewrite was blank.",
            )
        }

        val canonicalIsQuestion = canonicalText.trim().endsWith("?")
        if (canonicalIsQuestion && !trimmed.endsWith("?")) {
            return ValidationResult(
                isValid = false,
                resolvedText = canonicalText,
                reason = "Question rewrite did not remain a question.",
            )
        }

        val normalized = trimmed.lowercase()
        val imperativeStarts = listOf(
            "check ",
            "assess ",
            "look for ",
            "inspect ",
            "determine ",
            "confirm ",
            "make sure ",
            "start ",
        )
        if (canonicalIsQuestion && imperativeStarts.any { normalized.startsWith(it) }) {
            return ValidationResult(
                isValid = false,
                resolvedText = canonicalText,
                reason = "Question rewrite turned into an instruction.",
            )
        }

        if (trimmed.length > canonicalText.length + 120) {
            return ValidationResult(
                isValid = false,
                resolvedText = canonicalText,
                reason = "Question rewrite exceeded safe length bound.",
            )
        }

        return ValidationResult(
            isValid = true,
            resolvedText = trimmed,
        )
    }

    override fun validateQuestionAnswer(
        canonicalText: String,
        responseText: String,
        mustKeepKeywords: List<String>,
        forbiddenKeywords: List<String>,
    ): ValidationResult {
        val normalized = responseText.lowercase().trim()
        if (normalized.isBlank()) {
            return ValidationResult(
                isValid = false,
                resolvedText = "I can't confirm that. Stay with the current step and use standard first-aid guidance only.",
                reason = "Question answer was blank.",
            )
        }

        val forbiddenKeyword = forbiddenKeywords.firstOrNull { keyword ->
            normalized.contains(keyword.lowercase())
        }
        if (forbiddenKeyword != null) {
            return ValidationResult(
                isValid = false,
                resolvedText = "I can't confirm that safely. Stay with the current step and avoid unsafe additions.",
                reason = "Forbidden keyword detected: $forbiddenKeyword",
            )
        }

        val missingAnchorKeyword = mustKeepKeywords.firstOrNull { keyword ->
            !normalized.contains(keyword.lowercase())
        }
        if (mustKeepKeywords.isNotEmpty() && missingAnchorKeyword != null) {
            return ValidationResult(
                isValid = false,
                resolvedText = "I can't confirm that safely from this step. Stay with the current step only.",
                reason = "Question answer did not include the required current-step keyword: $missingAnchorKeyword",
            )
        }

        val canonicalNormalized = canonicalText.lowercase().trim()
        if (normalized == canonicalNormalized || normalized.startsWith(canonicalNormalized)) {
            return ValidationResult(
                isValid = false,
                resolvedText = "I can't confirm that from this step alone. Follow the current step and avoid extra remedies.",
                reason = "Question answer repeated the canonical step instead of answering the question.",
            )
        }

        if (responseText.length > 240) {
            return ValidationResult(
                isValid = false,
                resolvedText = "I can't confirm that briefly and safely. Follow the current step and avoid extra remedies.",
                reason = "Question answer exceeded safe length bound.",
            )
        }

        return ValidationResult(
            isValid = true,
            resolvedText = responseText,
        )
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val resolvedText: String,
    val reason: String? = null,
)
