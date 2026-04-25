package com.example.moasis

import com.example.moasis.domain.state.ControlIntent
import com.example.moasis.domain.state.InterruptionRouter
import com.example.moasis.domain.state.InterruptionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterruptionRouterTest {
    private val router = InterruptionRouter()

    // --- STATE_CHANGING_REPORT (life-threat, priority 1) ---

    @Test
    fun cant_breathe_is_state_changing() {
        val decision = router.classify("they can't breathe")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun cannot_breathe_is_state_changing() {
        val decision = router.classify("cannot breathe")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun trouble_breathing_is_state_changing() {
        val decision = router.classify("trouble breathing")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun collapsed_is_state_changing() {
        val decision = router.classify("the person collapsed")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun not_responding_is_state_changing() {
        val decision = router.classify("they are not responding")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun unconscious_is_state_changing() {
        val decision = router.classify("she is unconscious")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun heavy_bleeding_is_state_changing() {
        val decision = router.classify("there is heavy bleeding")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun bleeding_a_lot_is_state_changing() {
        val decision = router.classify("bleeding a lot")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun chest_pain_is_state_changing() {
        val decision = router.classify("I feel chest pain")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun seizure_is_state_changing() {
        val decision = router.classify("having a seizure")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun convulsing_is_state_changing() {
        val decision = router.classify("they are convulsing")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun throat_swelling_is_state_changing() {
        val decision = router.classify("throat swelling up")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun choking_is_state_changing() {
        val decision = router.classify("the child is choking")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun state_changing_report_has_no_control_intent() {
        val decision = router.classify("they can't breathe")
        assertNull(decision.controlIntent)
    }

    // --- CONTROL_INTENT (priority 2) ---

    @Test
    fun next_is_control_intent_next() {
        val decision = router.classify("next")
        assertEquals(InterruptionType.CONTROL_INTENT, decision.type)
        assertEquals(ControlIntent.NEXT, decision.controlIntent)
    }

    @Test
    fun done_is_control_intent_done() {
        val decision = router.classify("done")
        assertEquals(InterruptionType.CONTROL_INTENT, decision.type)
        assertEquals(ControlIntent.DONE, decision.controlIntent)
    }

    @Test
    fun repeat_is_control_intent_repeat() {
        val decision = router.classify("repeat")
        assertEquals(InterruptionType.CONTROL_INTENT, decision.type)
        assertEquals(ControlIntent.REPEAT, decision.controlIntent)
    }

    @Test
    fun say_that_again_is_control_intent_repeat() {
        val decision = router.classify("say that again")
        assertEquals(InterruptionType.CONTROL_INTENT, decision.type)
        assertEquals(ControlIntent.REPEAT, decision.controlIntent)
    }

    @Test
    fun stop_is_control_intent_stop() {
        val decision = router.classify("stop")
        assertEquals(InterruptionType.CONTROL_INTENT, decision.type)
        assertEquals(ControlIntent.STOP, decision.controlIntent)
    }

    // --- CLARIFICATION_QUESTION (priority 3) ---

    @Test
    fun question_mark_is_clarification() {
        val decision = router.classify("can I use ice?")
        assertEquals(InterruptionType.CLARIFICATION_QUESTION, decision.type)
    }

    @Test
    fun starts_with_why_is_clarification() {
        val decision = router.classify("why is that important")
        assertEquals(InterruptionType.CLARIFICATION_QUESTION, decision.type)
    }

    @Test
    fun starts_with_what_is_clarification() {
        val decision = router.classify("what should I do instead")
        assertEquals(InterruptionType.CLARIFICATION_QUESTION, decision.type)
    }

    @Test
    fun starts_with_how_is_clarification() {
        val decision = router.classify("how long should I keep doing this")
        assertEquals(InterruptionType.CLARIFICATION_QUESTION, decision.type)
    }

    @Test
    fun starts_with_should_is_clarification() {
        val decision = router.classify("should I call 911")
        assertEquals(InterruptionType.CLARIFICATION_QUESTION, decision.type)
    }

    @Test
    fun starts_with_is_is_clarification() {
        val decision = router.classify("is this normal")
        assertEquals(InterruptionType.CLARIFICATION_QUESTION, decision.type)
    }

    @Test
    fun starts_with_can_is_clarification() {
        val decision = router.classify("can I move them")
        assertEquals(InterruptionType.CLARIFICATION_QUESTION, decision.type)
    }

    // --- OUT_OF_DOMAIN (priority 4, fallback) ---

    @Test
    fun random_text_is_out_of_domain() {
        val decision = router.classify("the weather is nice today")
        assertEquals(InterruptionType.OUT_OF_DOMAIN, decision.type)
    }

    @Test
    fun empty_text_is_out_of_domain() {
        val decision = router.classify("")
        assertEquals(InterruptionType.OUT_OF_DOMAIN, decision.type)
    }

    // --- Priority: life-threat ALWAYS wins ---

    @Test
    fun life_threat_wins_over_question_mark() {
        // Contains "?" but also life-threat keyword — life-threat must win
        val decision = router.classify("they can't breathe?")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    @Test
    fun life_threat_wins_over_question_prefix() {
        val decision = router.classify("what do I do they are choking")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }

    // --- Case insensitivity ---

    @Test
    fun classification_is_case_insensitive() {
        val decision = router.classify("THEY CAN'T BREATHE")
        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
    }
}
