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

            normalized.containsPattern("not breathing", "no pulse", "needs cpr", "cardiac arrest") ->
                IntentMatch(
                    entryIntent = EntryIntent.PERSON_COLLAPSED,
                    domainHints = setOf(DomainIntent.CARDIAC_ARREST),
                    confidence = 0.99f,
                )

            normalized.containsPattern("can't breathe", "cannot breathe", "trouble breathing", "short of breath", "wheezing", "asthma attack") ->
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

            normalized.containsPattern("seizure", "convulsion", "shaking and unresponsive") ->
                IntentMatch(
                    entryIntent = EntryIntent.SEIZURE,
                    domainHints = setOf(DomainIntent.SEIZURE),
                    confidence = 0.95f,
                )

            normalized.containsPattern("chest pain", "heart attack", "pressure in chest") ->
                IntentMatch(
                    entryIntent = EntryIntent.CHEST_PAIN,
                    domainHints = setOf(DomainIntent.CHEST_PAIN),
                    confidence = 0.95f,
                )

            normalized.containsPattern("stroke", "face droop", "face drooping", "slurred speech", "speech is slurred", "arm weakness") ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.STROKE),
                    confidence = 0.95f,
                )

            normalized.containsPattern("allergic reaction", "anaphylaxis", "epi pen", "epinephrine", "swollen tongue", "swollen lips") ->
                IntentMatch(
                    entryIntent = EntryIntent.ALLERGIC_REACTION,
                    domainHints = setOf(DomainIntent.ALLERGIC_REACTION),
                    confidence = 0.95f,
                )

            normalized.containsPattern("poison", "poisoning", "overdose", "took too many pills") ->
                IntentMatch(
                    entryIntent = EntryIntent.POISONING,
                    domainHints = setOf(DomainIntent.POISONING),
                    confidence = 0.95f,
                )

            normalized.containsPattern("drowning", "drowned", "near drowning", "nearly drowned", "pulled from water") ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.DROWNING),
                    confidence = 0.94f,
                )

            normalized.containsPattern("electric shock", "electrocuted", "shocked by wire") ->
                IntentMatch(
                    entryIntent = EntryIntent.INJURY_REPORT,
                    domainHints = setOf(DomainIntent.ELECTRIC_SHOCK),
                    confidence = 0.94f,
                )

            normalized.containsPattern("eye injury", "chemical in eye", "something in eye") ->
                IntentMatch(
                    entryIntent = EntryIntent.TRAUMA,
                    domainHints = setOf(DomainIntent.EYE_INJURY),
                    confidence = 0.93f,
                )

            normalized.containsPattern("fracture", "broken bone", "sprain", "twisted ankle") ->
                IntentMatch(
                    entryIntent = EntryIntent.TRAUMA,
                    domainHints = setOf(DomainIntent.FRACTURE),
                    confidence = 0.93f,
                )

            normalized.containsPattern("head injury", "hit their head", "concussion") ->
                IntentMatch(
                    entryIntent = EntryIntent.TRAUMA,
                    domainHints = setOf(DomainIntent.HEAD_INJURY),
                    confidence = 0.93f,
                )

            normalized.containsPattern("heat stroke", "overheated", "hot and confused") ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.HEAT_STROKE),
                    confidence = 0.93f,
                )

            normalized.containsPattern("low blood sugar", "hypoglycemia", "diabetic and shaky") ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.HYPOGLYCEMIA),
                    confidence = 0.93f,
                )

            normalized.containsPattern("hypothermia", "freezing", "too cold exposure") ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.HYPOTHERMIA),
                    confidence = 0.93f,
                )

            normalized.containsPattern("nosebleed", "nose bleed") ->
                IntentMatch(
                    entryIntent = EntryIntent.BLEEDING,
                    domainHints = setOf(DomainIntent.NOSEBLEED),
                    confidence = 0.92f,
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
