package com.example.moasis.domain.safety

interface ResponseValidator {
    fun validate(canonicalText: String, responseText: String = canonicalText): ValidationResult
}

class PassThroughResponseValidator : ResponseValidator {
    override fun validate(canonicalText: String, responseText: String): ValidationResult {
        return ValidationResult(
            isValid = true,
            resolvedText = responseText.ifBlank { canonicalText },
        )
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val resolvedText: String,
)
