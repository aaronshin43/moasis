package com.example.moasis.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DomainIntent {
    BURN,
    BLEEDING,
    CHOKING,
    BREATHING_PROBLEM,
    CARDIAC_ARREST,
    COLLAPSE,
    SEIZURE,
    CHEST_PAIN,
    POISONING,
    TRAUMA,
    ALLERGIC_REACTION,
    GENERAL,
    UNKNOWN,
}
