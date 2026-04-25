package com.example.moasis

import com.example.moasis.domain.nlu.SlotExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotExtractorTest {
    private val extractor = SlotExtractor()

    // --- Location extraction ---

    @Test
    fun extracts_arm_location() {
        val slots = extractor.extract("I burned my arm")
        assertEquals("arm", slots["location"])
    }

    @Test
    fun extracts_hand_location() {
        val slots = extractor.extract("blisters on my hand")
        assertEquals("hand", slots["location"])
    }

    @Test
    fun extracts_face_location() {
        val slots = extractor.extract("burned my face")
        assertEquals("face", slots["location"])
    }

    @Test
    fun no_location_when_absent() {
        val slots = extractor.extract("I feel sick")
        assertFalse(slots.containsKey("location"))
    }

    // --- Patient type extraction ---

    @Test
    fun extracts_adult_patient_type() {
        val slots = extractor.extract("the adult is choking")
        assertEquals("adult", slots["patient_type"])
    }

    @Test
    fun extracts_child_patient_type() {
        val slots = extractor.extract("my child is choking")
        assertEquals("child", slots["patient_type"])
    }

    @Test
    fun extracts_kid_as_child() {
        val slots = extractor.extract("the kid fell down")
        assertEquals("child", slots["patient_type"])
    }

    @Test
    fun no_patient_type_when_absent() {
        val slots = extractor.extract("burned my arm")
        assertFalse(slots.containsKey("patient_type"))
    }

    // --- Response extraction ---

    @Test
    fun extracts_yes_response_exact() {
        val slots = extractor.extract("yes")
        assertEquals("yes", slots["response"])
    }

    @Test
    fun cardiac_arrest_phrases_mark_confirmed_arrest() {
        val slots = extractor.extract("he is not breathing and has no pulse")
        assertEquals("yes", slots["cardiac_arrest_confirmed"])
        assertEquals("no", slots["breathing_normal"])
        assertEquals("no", slots["responsive"])
    }

    @Test
    fun extracts_yes_response_with_trailing_text() {
        val slots = extractor.extract("yes it is")
        assertEquals("yes", slots["response"])
    }

    @Test
    fun extracts_no_response_exact() {
        val slots = extractor.extract("no")
        assertEquals("no", slots["response"])
    }

    @Test
    fun extracts_no_response_with_trailing_text() {
        val slots = extractor.extract("no they are not")
        assertEquals("no", slots["response"])
    }

    @Test
    fun no_response_for_general_text() {
        val slots = extractor.extract("I burned my arm")
        assertFalse(slots.containsKey("response"))
    }

    // --- Burn severity ---

    @Test
    fun blister_indicates_burn_severity() {
        val slots = extractor.extract("there are blisters")
        assertEquals("yes", slots["burn_severity"])
    }

    @Test
    fun plain_burn_does_not_assume_second_degree() {
        val slots = extractor.extract("I have a burn on my arm")
        assertFalse(slots.containsKey("burn_severity"))
    }

    @Test
    fun facial_burn_sets_high_risk_flag() {
        val slots = extractor.extract("burn on face")
        assertEquals("yes", slots["burn_emergency_red_flags"])
    }

    // --- Bleeding ---

    @Test
    fun severe_bleeding_phrase_sets_has_massive_bleeding() {
        val slots = extractor.extract("the wound is bleeding a lot")
        assertEquals("yes", slots["has_massive_bleeding"])
    }

    @Test
    fun blood_everywhere_sets_has_massive_bleeding() {
        val slots = extractor.extract("there is blood everywhere")
        assertEquals("yes", slots["has_massive_bleeding"])
    }

    @Test
    fun generic_bleeding_does_not_auto_mark_massive() {
        val slots = extractor.extract("the wound is bleeding")
        assertFalse(slots.containsKey("has_massive_bleeding"))
    }

    // --- Breathing ---

    @Test
    fun cant_breathe_sets_has_breathing_problem() {
        val slots = extractor.extract("they can't breathe")
        assertEquals("yes", slots["has_breathing_problem"])
    }

    @Test
    fun trouble_breathing_sets_has_breathing_problem() {
        val slots = extractor.extract("trouble breathing")
        assertEquals("yes", slots["has_breathing_problem"])
    }

    @Test
    fun blue_lips_sets_breathing_red_flags() {
        val slots = extractor.extract("they have blue lips and cannot speak full sentences")
        assertEquals("yes", slots["breathing_red_flags"])
    }

    @Test
    fun can_cough_phrase_marks_partial_choking() {
        val slots = extractor.extract("he is choking but can cough")
        assertEquals("yes", slots["can_cough_or_speak"])
    }

    @Test
    fun cannot_speak_phrase_marks_complete_choking() {
        val slots = extractor.extract("she is choking and can't speak")
        assertEquals("no", slots["can_cough_or_speak"])
    }

    @Test
    fun seizure_stopped_phrase_marks_recovery_state() {
        val slots = extractor.extract("the seizure stopped and now he is breathing")
        assertEquals("no", slots["seizure_active_now"])
    }

    @Test
    fun unresponsive_diabetic_marks_cannot_swallow() {
        val slots = extractor.extract("unresponsive diabetic with low blood sugar")
        assertEquals("no", slots["can_swallow_safely"])
    }

    @Test
    fun chemical_in_eye_marks_contact_exposure() {
        val slots = extractor.extract("bleach in the eye")
        assertEquals("yes", slots["poison_contact_exposure"])
    }

    @Test
    fun inhaled_fumes_marks_poison_inhalation() {
        val slots = extractor.extract("she inhaled chemical fumes")
        assertEquals("yes", slots["poison_inhalation_exposure"])
    }

    // --- Multiple slots from one input ---

    @Test
    fun extracts_location_and_burn_severity_together() {
        val slots = extractor.extract("blisters on my arm")
        assertEquals("arm", slots["location"])
        assertEquals("yes", slots["burn_severity"])
    }

    @Test
    fun extracts_patient_type_and_location_without_overcalling_massive_bleeding() {
        val slots = extractor.extract("the child has a cut on the arm")
        assertEquals("child", slots["patient_type"])
        assertEquals("arm", slots["location"])
        assertFalse(slots.containsKey("has_massive_bleeding"))
    }

    // --- Case insensitivity ---

    @Test
    fun extraction_is_case_insensitive() {
        val slots = extractor.extract("BURNED MY ARM")
        assertEquals("arm", slots["location"])
        assertFalse(slots.containsKey("burn_severity"))
    }

    // --- Empty / no match ---

    @Test
    fun empty_input_returns_empty_slots() {
        val slots = extractor.extract("")
        assertTrue(slots.isEmpty())
    }

    @Test
    fun irrelevant_input_returns_empty_slots() {
        val slots = extractor.extract("hello world")
        assertTrue(slots.isEmpty())
    }
}
