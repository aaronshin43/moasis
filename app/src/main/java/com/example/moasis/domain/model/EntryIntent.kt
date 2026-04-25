package com.example.moasis.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class EntryIntent {
    GENERAL_EMERGENCY,
    PERSON_COLLAPSED,
    INJURY_REPORT,
    BREATHING_PROBLEM,
    CHEST_PAIN,
    CHOKING,
    SEIZURE,
    POISONING,
    TRAUMA,
    ALTERED_CONSCIOUSNESS,
    ALLERGIC_REACTION,
    BLEEDING,
    UNKNOWN,
}
