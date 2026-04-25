package com.example.moasis.domain.nlu

import com.example.moasis.domain.model.DomainIntent
import com.example.moasis.domain.model.EntryIntent

data class NluResult(
    val entryIntent: EntryIntent,
    val domainHints: Set<DomainIntent> = emptySet(),
    val slots: Map<String, String> = emptyMap(),
    val confidence: Float,
)
