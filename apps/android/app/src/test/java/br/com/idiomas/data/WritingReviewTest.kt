package br.com.idiomas.data

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WritingReviewTest {
    private val json=Json {ignoreUnknownKeys=true}
    @Test fun optional_review_preserves_legacy_assessments_and_round_trips_the_shared_fixture() {
        val legacy="""{"outcome":"unverified","supported":true,"is_preview":true,"retention_confirmed":false,"source":"not_assessed","message":"Synthetic automatic feedback"}"""
        val automatic=json.decodeFromString<RemoteAssessment>(legacy)
        assertNull(automatic.human_review)
        assertFalse(automatic.human_feedback_available)
        val review=json.decodeFromString<WritingReview>(File(checkNotNull(System.getProperty("contracts.dir")),"fixtures/writing-review.json").readText())
        val event=json.decodeFromJsonElement<RemoteEvent>(buildJsonObject {
            put("id",review.attempt_id);put("kind","attempt")
            put("assessment",json.parseToJsonElement(legacy))
            put("human_review",json.encodeToJsonElement(review))
        })
        val saved=event.assessment!!.copy(human_review=event.human_review)
        assertEquals(automatic,saved.copy(human_review=null))
        assertEquals(review,json.decodeFromString<RemoteAssessment>(json.encodeToString(saved)).human_review)
        assertFalse(review.retention_confirmed)
        assertEquals("not_observed",review.dimensions.single {it.id=="reading"}.result)
        assertEquals("abstained",review.dimensions.single {it.id=="form"}.result)
    }
}
