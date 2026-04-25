package com.example.moasis.domain.state

import com.example.moasis.domain.model.DomainIntent
import com.example.moasis.domain.model.EntryIntent
import com.example.moasis.domain.nlu.NluResult

class EntryTreeRouter {
    fun route(nluResult: NluResult): String {
        return when {
            nluResult.entryIntent == EntryIntent.PERSON_COLLAPSED -> COLLAPSED_PERSON_ENTRY
            DomainIntent.BURN in nluResult.domainHints -> BURN_TREE
            DomainIntent.BLEEDING in nluResult.domainHints -> BLEEDING_TREE
            else -> GENERAL_ENTRY_TREE
        }
    }

    companion object {
        const val GENERAL_ENTRY_TREE = "entry_general_emergency"
        const val COLLAPSED_PERSON_ENTRY = "collapsed_person_entry"
        const val BURN_TREE = "burn_tree"
        const val BLEEDING_TREE = "bleeding_tree"
    }
}
