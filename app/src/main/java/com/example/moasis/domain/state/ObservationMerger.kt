package com.example.moasis.domain.state

import com.example.moasis.domain.model.ObservedFact
import com.example.moasis.domain.model.UserTurn

class ObservationMerger {
    fun merge(
        turn: UserTurn,
        existingFacts: List<ObservedFact> = emptyList(),
        newFacts: List<ObservedFact> = emptyList(),
    ): List<ObservedFact> {
        return if (turn.imageUris.isEmpty()) {
            existingFacts + newFacts
        } else {
            existingFacts + newFacts
        }
    }
}
