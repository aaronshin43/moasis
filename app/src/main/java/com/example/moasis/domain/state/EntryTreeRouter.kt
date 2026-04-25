package com.example.moasis.domain.state

import com.example.moasis.domain.model.DomainIntent
import com.example.moasis.domain.model.EntryIntent
import com.example.moasis.domain.nlu.NluResult

class EntryTreeRouter {
    fun route(nluResult: NluResult): String {
        return when {
            DomainIntent.CARDIAC_ARREST in nluResult.domainHints -> CARDIAC_ARREST_TREE
            nluResult.entryIntent == EntryIntent.PERSON_COLLAPSED -> COLLAPSED_PERSON_ENTRY
            nluResult.slots["patient_type"] == "infant" && DomainIntent.CHOKING in nluResult.domainHints -> INFANT_CHOKING_TREE
            DomainIntent.BURN in nluResult.domainHints -> BURN_TREE
            DomainIntent.BLEEDING in nluResult.domainHints -> BLEEDING_TREE
            DomainIntent.BREATHING_PROBLEM in nluResult.domainHints -> BREATHING_PROBLEM_TREE
            DomainIntent.CHOKING in nluResult.domainHints -> CHOKING_TREE
            DomainIntent.SEIZURE in nluResult.domainHints -> SEIZURE_TREE
            DomainIntent.CHEST_PAIN in nluResult.domainHints -> CHEST_PAIN_TREE
            DomainIntent.STROKE in nluResult.domainHints -> STROKE_FAST_TREE
            DomainIntent.ALLERGIC_REACTION in nluResult.domainHints -> ANAPHYLAXIS_TREE
            DomainIntent.POISONING in nluResult.domainHints -> POISONING_TREE
            DomainIntent.DROWNING in nluResult.domainHints -> DROWNING_TREE
            DomainIntent.ELECTRIC_SHOCK in nluResult.domainHints -> ELECTRIC_SHOCK_TREE
            DomainIntent.EYE_INJURY in nluResult.domainHints -> EYE_INJURY_TREE
            DomainIntent.FRACTURE in nluResult.domainHints -> FRACTURE_SUSPECTED_TREE
            DomainIntent.HEAD_INJURY in nluResult.domainHints -> HEAD_INJURY_TREE
            DomainIntent.HEAT_STROKE in nluResult.domainHints -> HEAT_STROKE_TREE
            DomainIntent.HYPOGLYCEMIA in nluResult.domainHints -> HYPOGLYCEMIA_TREE
            DomainIntent.HYPOTHERMIA in nluResult.domainHints -> HYPOTHERMIA_TREE
            DomainIntent.NOSEBLEED in nluResult.domainHints -> NOSEBLEED_TREE
            else -> GENERAL_ENTRY_TREE
        }
    }

    companion object {
        const val GENERAL_ENTRY_TREE = "general_assessment_tree"
        const val COLLAPSED_PERSON_ENTRY = "collapsed_person_entry"
        const val BURN_TREE = "burn_tree"
        const val BLEEDING_TREE = "bleeding_tree"
        const val BREATHING_PROBLEM_TREE = "breathing_problem_tree"
        const val CARDIAC_ARREST_TREE = "cardiac_arrest_tree"
        const val CHOKING_TREE = "choking_tree"
        const val INFANT_CHOKING_TREE = "infant_choking_tree"
        const val SEIZURE_TREE = "seizure_tree"
        const val CHEST_PAIN_TREE = "chest_pain_tree"
        const val STROKE_FAST_TREE = "stroke_fast_tree"
        const val ANAPHYLAXIS_TREE = "anaphylaxis_tree"
        const val POISONING_TREE = "poisoning_tree"
        const val DROWNING_TREE = "drowning_tree"
        const val ELECTRIC_SHOCK_TREE = "electric_shock_tree"
        const val EYE_INJURY_TREE = "eye_injury_tree"
        const val FRACTURE_SUSPECTED_TREE = "fracture_suspected_tree"
        const val HEAD_INJURY_TREE = "head_injury_tree"
        const val HEAT_STROKE_TREE = "heat_stroke_tree"
        const val HYPOGLYCEMIA_TREE = "hypoglycemia_tree"
        const val HYPOTHERMIA_TREE = "hypothermia_tree"
        const val NOSEBLEED_TREE = "nosebleed_tree"
    }
}
