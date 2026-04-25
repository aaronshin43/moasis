package com.example.moasis.domain.nlu

import com.example.moasis.domain.model.DomainIntent
import com.example.moasis.domain.model.EntryIntent

class RegexIntentMatcher : IntentClassifier {
    override
    fun match(text: String): IntentMatch {
        val normalized = text.lowercase().trim()

        return when {
            normalized.matchesAny(HEAT_STROKE_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.HEAT_STROKE),
                    confidence = 0.96f,
                )

            normalized.matchesAny(HYPOGLYCEMIA_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.HYPOGLYCEMIA),
                    confidence = 0.94f,
                )

            normalized.matchesAny(CARDIAC_ARREST_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.PERSON_COLLAPSED,
                    domainHints = setOf(DomainIntent.CARDIAC_ARREST),
                    confidence = 0.99f,
                )

            normalized.matchesAny(COLLAPSE_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.PERSON_COLLAPSED,
                    domainHints = setOf(DomainIntent.COLLAPSE),
                    confidence = 0.98f,
                )

            normalized.matchesAny(BREATHING_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.BREATHING_PROBLEM,
                    domainHints = setOf(DomainIntent.BREATHING_PROBLEM),
                    confidence = 0.96f,
                )

            normalized.matchesAny(BURN_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.INJURY_REPORT,
                    domainHints = setOf(DomainIntent.BURN),
                    confidence = 0.95f,
                )

            normalized.matchesAny(NOSEBLEED_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.BLEEDING,
                    domainHints = setOf(DomainIntent.NOSEBLEED),
                    confidence = 0.93f,
                )

            normalized.matchesAny(BLEEDING_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.BLEEDING,
                    domainHints = setOf(DomainIntent.BLEEDING),
                    confidence = 0.95f,
                )

            normalized.matchesAny(CHOKING_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.CHOKING,
                    domainHints = setOf(DomainIntent.CHOKING),
                    confidence = 0.95f,
                )

            normalized.matchesAny(SEIZURE_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.SEIZURE,
                    domainHints = setOf(DomainIntent.SEIZURE),
                    confidence = 0.95f,
                )

            normalized.matchesAny(CHEST_PAIN_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.CHEST_PAIN,
                    domainHints = setOf(DomainIntent.CHEST_PAIN),
                    confidence = 0.95f,
                )

            normalized.matchesAny(STROKE_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.STROKE),
                    confidence = 0.95f,
                )

            normalized.matchesAny(ALLERGIC_REACTION_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.ALLERGIC_REACTION,
                    domainHints = setOf(DomainIntent.ALLERGIC_REACTION),
                    confidence = 0.95f,
                )

            normalized.matchesAny(POISONING_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.POISONING,
                    domainHints = setOf(DomainIntent.POISONING),
                    confidence = 0.95f,
                )

            normalized.matchesAny(DROWNING_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.DROWNING),
                    confidence = 0.94f,
                )

            normalized.matchesAny(ELECTRIC_SHOCK_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.INJURY_REPORT,
                    domainHints = setOf(DomainIntent.ELECTRIC_SHOCK),
                    confidence = 0.94f,
                )

            normalized.matchesAny(EYE_INJURY_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.TRAUMA,
                    domainHints = setOf(DomainIntent.EYE_INJURY),
                    confidence = 0.93f,
                )

            normalized.matchesAny(FRACTURE_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.TRAUMA,
                    domainHints = setOf(DomainIntent.FRACTURE),
                    confidence = 0.93f,
                )

            normalized.matchesAny(HEAD_INJURY_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.TRAUMA,
                    domainHints = setOf(DomainIntent.HEAD_INJURY),
                    confidence = 0.93f,
                )

            normalized.matchesAny(HYPOTHERMIA_PATTERNS) ->
                IntentMatch(
                    entryIntent = EntryIntent.GENERAL_EMERGENCY,
                    domainHints = setOf(DomainIntent.HYPOTHERMIA),
                    confidence = 0.93f,
                )

            else -> IntentMatch(
                entryIntent = EntryIntent.GENERAL_EMERGENCY,
                confidence = 0.5f,
            )
        }
    }

    private fun String.matchesAny(patterns: List<Regex>): Boolean {
        return patterns.any { it.containsMatchIn(this) }
    }

    companion object {
        private val HEAT_STROKE_PATTERNS = listOf(
            Regex("""\bheat stroke\b"""),
            Regex("""\bcollapsed from heat\b"""),
            Regex("""\bvery hot skin\b"""),
            Regex("""\bhot and confused\b"""),
            Regex("""\boverheated\b"""),
            Regex("""\boverheat(?:ed|ing)\b"""),
        )

        private val CARDIAC_ARREST_PATTERNS = listOf(
            Regex("""\bnot breathing\b"""),
            Regex("""\bno pulse\b"""),
            Regex("""\bneeds cpr\b"""),
            Regex("""\bcardiac arrest\b"""),
            Regex("""\bheart stopped\b"""),
            Regex("""\bstopped breathing\b"""),
        )

        private val COLLAPSE_PATTERNS = listOf(
            Regex("""\bcollaps(?:e|ed|ing)\b"""),
            Regex("""\bpassed out\b"""),
            Regex("""\bunconscious\b"""),
            Regex("""\bnot responding\b"""),
            Regex("""\bunresponsive\b"""),
            Regex("""\bwon[' ]?t wake up\b"""),
            Regex("""\bblack(?:ed)? out\b"""),
        )

        private val BREATHING_PATTERNS = listOf(
            Regex("""\bcan(?:not|'t) breathe\b"""),
            Regex("""\btrouble breathing\b"""),
            Regex("""\bshort(?:ness)? of breath\b"""),
            Regex("""\bwheez(?:e|ing)\b"""),
            Regex("""\basthma attack\b"""),
            Regex("""\bgasping\b"""),
            Regex("""\bcan[' ]?t catch (?:his|her|their|my)? ?breath\b"""),
        )

        private val BURN_PATTERNS = listOf(
            Regex("""\bburn(?:ed|t|ing)?\b"""),
            Regex("""\bblister(?:s|ing)?\b"""),
            Regex("""\bscald(?:ed|ing)?\b"""),
            Regex("""\bsteam burn\b"""),
            Regex("""\bhot oil\b"""),
            Regex("""\bchemical burn\b"""),
        )

        private val BLEEDING_PATTERNS = listOf(
            Regex("""\bbleed(?:ing)?\b"""),
            Regex("""\bblood everywhere\b"""),
            Regex("""\bspurting blood\b"""),
            Regex("""\bwon[' ]?t stop bleeding\b"""),
            Regex("""\bhemorrhag(?:e|ing)\b"""),
            Regex("""\bcut badly\b"""),
        )

        private val CHOKING_PATTERNS = listOf(
            Regex("""\bchok(?:e|ing)\b"""),
            Regex("""\bsomething stuck in (?:the )?throat\b"""),
            Regex("""\bfood stuck in (?:the )?throat\b"""),
            Regex("""\bcan[' ]?t swallow and breathe\b"""),
        )

        private val SEIZURE_PATTERNS = listOf(
            Regex("""\bseiz(?:e|ure|ing)\b"""),
            Regex("""\bconvuls(?:ion|ing)\b"""),
            Regex("""\bshaking and unresponsive\b"""),
            Regex("""\bepileptic fit\b"""),
            Regex("""\bhaving a fit\b"""),
        )

        private val CHEST_PAIN_PATTERNS = listOf(
            Regex("""\bchest pain\b"""),
            Regex("""\bheart attack\b"""),
            Regex("""\bpressure in (?:the )?chest\b"""),
            Regex("""\bchest pressure\b"""),
            Regex("""\bchest tight(?:ness)?\b"""),
            Regex("""\bpain in (?:the )?chest\b"""),
            Regex("""\bcrushing chest pain\b"""),
        )

        private val STROKE_PATTERNS = listOf(
            Regex("""\bstroke\b"""),
            Regex("""\bface droop(?:ing)?\b"""),
            Regex("""\bdrooping face\b"""),
            Regex("""\bslurred speech\b"""),
            Regex("""\bspeech is slurred\b"""),
            Regex("""\barm weakness\b"""),
            Regex("""\bone[- ]sided weakness\b"""),
            Regex("""\bfast signs\b"""),
        )

        private val ALLERGIC_REACTION_PATTERNS = listOf(
            Regex("""\ballergic reaction\b"""),
            Regex("""\banaphylaxis\b"""),
            Regex("""\bepi pen\b"""),
            Regex("""\bepipen\b"""),
            Regex("""\bepinephrine\b"""),
            Regex("""\bswollen tongue\b"""),
            Regex("""\bswollen lips\b"""),
            Regex("""\bthroat (?:is )?closing\b"""),
            Regex("""\bhives\b.*\b(swelling|breathing)\b"""),
        )

        private val POISONING_PATTERNS = listOf(
            Regex("""\bpoison(?:ed|ing)?\b"""),
            Regex("""\boverdos(?:e|ing)\b"""),
            Regex("""\btook too many pills\b"""),
            Regex("""\bswallowed bleach\b"""),
            Regex("""\bdrank bleach\b"""),
            Regex("""\bdrank cleaner\b"""),
            Regex("""\bpoison control\b"""),
        )

        private val DROWNING_PATTERNS = listOf(
            Regex("""\bdrown(?:ing|ed)\b"""),
            Regex("""\bnear drowning\b"""),
            Regex("""\bnearly drowned\b"""),
            Regex("""\bpulled from water\b"""),
            Regex("""\bsubmerged in water\b"""),
        )

        private val ELECTRIC_SHOCK_PATTERNS = listOf(
            Regex("""\belectric shock\b"""),
            Regex("""\belectrocut(?:ed|ion)\b"""),
            Regex("""\bshocked by (?:a )?wire\b"""),
            Regex("""\bshocked from (?:an )?outlet\b"""),
            Regex("""\bhigh voltage(?: line)?\b"""),
            Regex("""\bpower line\b"""),
            Regex("""\blive wire\b"""),
        )

        private val EYE_INJURY_PATTERNS = listOf(
            Regex("""\beye injury\b"""),
            Regex("""\bchemical in (?:my |the )?eye\b"""),
            Regex("""\bsomething in (?:my |the )?eye\b"""),
            Regex("""\bglass in (?:my |the )?eye\b"""),
            Regex("""\bmetal in (?:my |the )?eye\b"""),
            Regex("""\bpoked in the eye\b"""),
            Regex("""\bscratched (?:my |the )?eye\b"""),
        )

        private val FRACTURE_PATTERNS = listOf(
            Regex("""\bfracture\b"""),
            Regex("""\bbroken bone\b"""),
            Regex("""\bsprain\b"""),
            Regex("""\btwisted (?:ankle|wrist)\b"""),
            Regex("""\bbone sticking out\b"""),
            Regex("""\bcan[' ]?t bear weight\b"""),
        )

        private val HEAD_INJURY_PATTERNS = listOf(
            Regex("""\bhead injury\b"""),
            Regex("""\bhit (?:his|her|their|my) head\b"""),
            Regex("""\bhit the head\b"""),
            Regex("""\bconcussion\b"""),
            Regex("""\bhead trauma\b"""),
            Regex("""\bknocked (?:his|her|their|my) head\b"""),
        )

        private val HYPOGLYCEMIA_PATTERNS = listOf(
            Regex("""\blow blood sugar\b"""),
            Regex("""\bhypoglyc(?:emia|emic)\b"""),
            Regex("""\bdiabetic and shaky\b"""),
            Regex("""\bdiabetic and confused\b"""),
            Regex("""\binsulin reaction\b"""),
            Regex("""\bblood sugar crashed\b"""),
            Regex("""\bunresponsive diabetic\b"""),
            Regex("""\bdiabetic .* (?:unresponsive|passed out|collapsed)\b"""),
        )

        private val HYPOTHERMIA_PATTERNS = listOf(
            Regex("""\bhypothermia\b"""),
            Regex("""\bfreezing\b"""),
            Regex("""\bcold exposure\b"""),
            Regex("""\btoo cold\b"""),
            Regex("""\bshivering but alert\b"""),
            Regex("""\bshivering uncontrollably\b"""),
        )

        private val NOSEBLEED_PATTERNS = listOf(
            Regex("""\bnosebleed\b"""),
            Regex("""\bnose bleed\b"""),
            Regex("""\bbleeding from (?:the )?nose\b"""),
        )
    }
}

data class IntentMatch(
    val entryIntent: EntryIntent,
    val domainHints: Set<DomainIntent> = emptySet(),
    val confidence: Float,
)
