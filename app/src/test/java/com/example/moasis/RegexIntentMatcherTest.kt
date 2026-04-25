package com.example.moasis

import com.example.moasis.domain.model.DomainIntent
import com.example.moasis.domain.model.EntryIntent
import com.example.moasis.domain.nlu.RegexIntentMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexIntentMatcherTest {
    private val matcher = RegexIntentMatcher()

    // --- PERSON_COLLAPSED ---

    @Test
    fun collapsed_matches_person_collapsed() {
        val result = matcher.match("my friend collapsed")
        assertEquals(EntryIntent.PERSON_COLLAPSED, result.entryIntent)
        assertTrue(DomainIntent.COLLAPSE in result.domainHints)
    }

    @Test
    fun passed_out_matches_person_collapsed() {
        val result = matcher.match("he passed out")
        assertEquals(EntryIntent.PERSON_COLLAPSED, result.entryIntent)
    }

    @Test
    fun unconscious_matches_person_collapsed() {
        val result = matcher.match("she is unconscious")
        assertEquals(EntryIntent.PERSON_COLLAPSED, result.entryIntent)
    }

    @Test
    fun not_responding_matches_person_collapsed() {
        val result = matcher.match("the person is not responding")
        assertEquals(EntryIntent.PERSON_COLLAPSED, result.entryIntent)
    }

    // --- BREATHING_PROBLEM ---

    @Test
    fun cant_breathe_matches_breathing_problem() {
        val result = matcher.match("they can't breathe")
        assertEquals(EntryIntent.BREATHING_PROBLEM, result.entryIntent)
        assertTrue(DomainIntent.BREATHING_PROBLEM in result.domainHints)
    }

    @Test
    fun trouble_breathing_matches_breathing_problem() {
        val result = matcher.match("he is having trouble breathing")
        assertEquals(EntryIntent.BREATHING_PROBLEM, result.entryIntent)
    }

    // --- BURN ---

    @Test
    fun burned_matches_injury_report_with_burn_hint() {
        val result = matcher.match("I burned my arm")
        assertEquals(EntryIntent.INJURY_REPORT, result.entryIntent)
        assertTrue(DomainIntent.BURN in result.domainHints)
    }

    @Test
    fun blister_matches_injury_report_with_burn_hint() {
        val result = matcher.match("there are blisters on my hand")
        assertEquals(EntryIntent.INJURY_REPORT, result.entryIntent)
        assertTrue(DomainIntent.BURN in result.domainHints)
    }

    @Test
    fun burnt_matches_injury_report_with_burn_hint() {
        val result = matcher.match("my child got burnt")
        assertEquals(EntryIntent.INJURY_REPORT, result.entryIntent)
        assertTrue(DomainIntent.BURN in result.domainHints)
    }

    // --- BLEEDING ---

    @Test
    fun bleeding_matches_bleeding() {
        val result = matcher.match("my arm is bleeding")
        assertEquals(EntryIntent.BLEEDING, result.entryIntent)
        assertTrue(DomainIntent.BLEEDING in result.domainHints)
    }

    @Test
    fun blood_everywhere_matches_bleeding() {
        val result = matcher.match("there is blood everywhere")
        assertEquals(EntryIntent.BLEEDING, result.entryIntent)
    }

    @Test
    fun cut_badly_matches_bleeding() {
        val result = matcher.match("I cut badly my finger")
        assertEquals(EntryIntent.BLEEDING, result.entryIntent)
    }

    // --- CHOKING ---

    @Test
    fun choking_matches_choking() {
        val result = matcher.match("the child is choking")
        assertEquals(EntryIntent.CHOKING, result.entryIntent)
        assertTrue(DomainIntent.CHOKING in result.domainHints)
    }

    @Test
    fun stuck_in_throat_matches_choking() {
        val result = matcher.match("something stuck in throat")
        assertEquals(EntryIntent.CHOKING, result.entryIntent)
    }

    // --- CHEST_PAIN ---

    @Test
    fun chest_pain_matches_chest_pain() {
        val result = matcher.match("I have chest pain")
        assertEquals(EntryIntent.CHEST_PAIN, result.entryIntent)
        assertTrue(DomainIntent.CHEST_PAIN in result.domainHints)
    }

    @Test
    fun seizure_matches_seizure_domain() {
        val result = matcher.match("my brother is having a seizure")
        assertEquals(EntryIntent.SEIZURE, result.entryIntent)
        assertTrue(DomainIntent.SEIZURE in result.domainHints)
    }

    @Test
    fun stroke_fast_signs_match_stroke_domain() {
        val result = matcher.match("his face droop and slurred speech started suddenly")
        assertEquals(EntryIntent.GENERAL_EMERGENCY, result.entryIntent)
        assertTrue(DomainIntent.STROKE in result.domainHints)
    }

    @Test
    fun anaphylaxis_matches_allergic_reaction_domain() {
        val result = matcher.match("she has anaphylaxis with swollen tongue")
        assertEquals(EntryIntent.ALLERGIC_REACTION, result.entryIntent)
        assertTrue(DomainIntent.ALLERGIC_REACTION in result.domainHints)
    }

    @Test
    fun poisoning_matches_poisoning_domain() {
        val result = matcher.match("he took too many pills and may be overdosing")
        assertEquals(EntryIntent.POISONING, result.entryIntent)
        assertTrue(DomainIntent.POISONING in result.domainHints)
    }

    // --- GENERAL_EMERGENCY (fallback) ---

    @Test
    fun unrecognized_input_falls_back_to_general() {
        val result = matcher.match("something happened")
        assertEquals(EntryIntent.GENERAL_EMERGENCY, result.entryIntent)
        assertTrue(result.domainHints.isEmpty())
    }

    @Test
    fun empty_input_falls_back_to_general() {
        val result = matcher.match("")
        assertEquals(EntryIntent.GENERAL_EMERGENCY, result.entryIntent)
    }

    // --- Case insensitivity ---

    @Test
    fun matching_is_case_insensitive() {
        val result = matcher.match("MY FRIEND COLLAPSED")
        assertEquals(EntryIntent.PERSON_COLLAPSED, result.entryIntent)
    }

    @Test
    fun mixed_case_matches() {
        val result = matcher.match("I Burned My Arm")
        assertEquals(EntryIntent.INJURY_REPORT, result.entryIntent)
    }

    // --- Priority ordering ---

    @Test
    fun collapsed_has_higher_priority_than_breathing() {
        // "collapsed" should match PERSON_COLLAPSED, not BREATHING_PROBLEM,
        // even if both keywords could be present
        val result = matcher.match("collapsed and can't breathe")
        assertEquals(EntryIntent.PERSON_COLLAPSED, result.entryIntent)
    }

    @Test
    fun breathing_has_higher_priority_than_burn() {
        val result = matcher.match("burned and can't breathe")
        assertEquals(EntryIntent.BREATHING_PROBLEM, result.entryIntent)
    }

    // --- Confidence ---

    @Test
    fun matched_intents_have_high_confidence() {
        val result = matcher.match("my friend collapsed")
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun fallback_has_low_confidence() {
        val result = matcher.match("something happened")
        assertTrue(result.confidence < 0.7f)
    }
}
