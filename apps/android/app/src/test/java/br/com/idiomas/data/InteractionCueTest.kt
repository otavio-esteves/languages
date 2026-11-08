package br.com.idiomas.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InteractionCueTest {
    @Test fun unknown_and_technical_results_never_use_success_or_error_sounds() {
        assertEquals(InteractionCue.Correct, feedbackCue("correct"))
        assertEquals(InteractionCue.Adjust, feedbackCue("incorrect"))
        for (outcome in listOf("unverified", "technical_issue", "pending", "")) {
            assertEquals(InteractionCue.Recorded, feedbackCue(outcome))
        }
    }
}
