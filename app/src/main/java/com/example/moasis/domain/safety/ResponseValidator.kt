package com.example.moasis.domain.safety

interface ResponseValidator {
    fun validate(
        canonicalText: String,
        responseText: String = canonicalText,
        mustKeepKeywords: List<String> = emptyList(),
        forbiddenKeywords: List<String> = emptyList(),
    ): ValidationResult
}

class KeywordResponseValidator : ResponseValidator {
    override fun validate(
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
}

data class ValidationResult(
    val isValid: Boolean,
    val resolvedText: String,
    val reason: String? = null,
)
