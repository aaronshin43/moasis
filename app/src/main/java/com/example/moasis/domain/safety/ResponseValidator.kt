package com.example.moasis.domain.safety

interface ResponseValidator {
    fun validatePersonalizedStep(
        canonicalText: String,
        responseText: String = canonicalText,
        mustKeepKeywords: List<String> = emptyList(),
        forbiddenKeywords: List<String> = emptyList(),
    ): ValidationResult

    fun validateQuestionAnswer(
        canonicalText: String,
        responseText: String,
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

    override fun validateQuestionAnswer(
        canonicalText: String,
        responseText: String,
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
