package com.example.moasis.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DomainIntent {
    BURN,
    BLEEDING,
    CHOKING,
    STROKE,
    BREATHING_PROBLEM,
    CARDIAC_ARREST,
    COLLAPSE,
    SEIZURE,
    CHEST_PAIN,
    POISONING,
    DROWNING,
    ELECTRIC_SHOCK,
    EYE_INJURY,
    FRACTURE,
    HEAD_INJURY,
    HEAT_STROKE,
    HYPOGLYCEMIA,
    HYPOTHERMIA,
    NOSEBLEED,
    TRAUMA,
    ALLERGIC_REACTION,
    GENERAL,
    UNKNOWN,
}
