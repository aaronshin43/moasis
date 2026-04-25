package com.example.moasis.domain.nlu

import com.example.moasis.domain.model.DomainIntent
import com.example.moasis.domain.model.EntryIntent

class RegexIntentMatcher {
    fun match(text: String): IntentMatch {
        val normalized = text.lowercase()

        return when {
            normalized.containsPattern("collapsed", "passed out", "unconscious", "not responding") ->
                IntentMatch(
                    entryIntent = EntryIntent.PERSON_COLLAPSED,
                    domainHints = setOf(DomainIntent.COLLAPSE),
                    confidence = 0.98f,
                )

            normalized.containsPattern("can't breathe", "cannot breathe", "trouble breathing", "breathing") ->
                IntentMatch(
                    entryIntent = EntryIntent.BREATHING_PROBLEM,
                    domainHints = setOf(DomainIntent.BREATHING_PROBLEM),
                    confidence = 0.96f,
                )

            normalized.containsPattern("blister", "burned", "burnt", "burn") ->
                IntentMatch(
                    entryIntent = EntryIntent.INJURY_REPORT,
                    domainHints = setOf(DomainIntent.BURN),
                    confidence = 0.95f,
                )

            normalized.containsPattern("bleeding", "bleeding a lot", "blood everywhere", "cut badly") ->
                IntentMatch(
                    entryIntent = EntryIntent.BLEEDING,
                    domainHints = setOf(DomainIntent.BLEEDING),
                    confidence = 0.95f,
                )

            normalized.containsPattern("choking", "something stuck in throat") ->
                IntentMatch(
                    entryIntent = EntryIntent.CHOKING,
                    domainHints = setOf(DomainIntent.CHOKING),
                    confidence = 0.95f,
                )

            normalized.containsPattern("chest pain") ->
                IntentMatch(
                    entryIntent = EntryIntent.CHEST_PAIN,
                    domainHints = setOf(DomainIntent.CHEST_PAIN),
                    confidence = 0.95f,
                )

            else -> IntentMatch(
                entryIntent = EntryIntent.GENERAL_EMERGENCY,
                confidence = 0.5f,
            )
        }
    }

    private fun String.containsPattern(vararg patterns: String): Boolean {
        return patterns.any { contains(it) }
    }
}

data class IntentMatch(
    val entryIntent: EntryIntent,
    val domainHints: Set<DomainIntent> = emptySet(),
    val confidence: Float,
)
