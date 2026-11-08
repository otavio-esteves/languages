package br.com.idiomas.data

import java.io.File
import java.util.UUID
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PracticeBatchesTest {
    private val fixture = Json.parseToJsonElement(File(checkNotNull(System.getProperty("contracts.dir")), "fixtures/attempt.json").readText()).jsonObject
    private fun event(sequence: Int, response: String): PracticeEvent {
        val id = UUID.randomUUID().toString()
        val payload = JsonObject(fixture.toMutableMap().apply {
            put("id", JsonPrimitive(id))
            put("local_sequence", JsonPrimitive(sequence))
            put("response", JsonPrimitive(response))
        })
        return PracticeEvent(1, id, fixture.text("session_id")!!, sequence.toLong(), "attempt", payload.toString())
    }

    private fun assertFits(plan: PracticeBatchPlan, originals: List<PracticeEvent>) {
        assertTrue(plan.oversized.isEmpty())
        assertEquals(originals, plan.batches.flatMap { it.events })
        for (batch in plan.batches) {
            assertTrue(batch.events.size in 1..50)
            val body = encodeEventBatch(batch.payloads)
            assertTrue(body.toByteArray(Charsets.UTF_8).size <= 524288)
            assertEquals(batch.events.map { Json.parseToJsonElement(it.payload).jsonObject }, Json.parseToJsonElement(body).jsonObject["events"]!!.jsonArray.toList())
        }
    }

    @Test fun fifty_maximum_length_responses_are_split_without_changing_payloads() {
        val events = (1..50).map { event(it, "a".repeat(10000)) }
        assertTrue(encodeEventBatch(events.map { Json.parseToJsonElement(it.payload).jsonObject }).toByteArray().size > 524288)
        val plan = planEventBatches(events)
        assertEquals(2, plan.batches.size)
        assertFits(plan, events)
        assertEquals(plan, planEventBatches(events))
    }

    @Test fun utf8_and_json_escaping_are_counted_as_bytes_in_the_http_body() {
        for (response in listOf("界".repeat(10000), "😀".repeat(5000), "\"\\\n".repeat(3333))) {
            val events = (1..50).map { event(it, response) }
            val plan = planEventBatches(events)
            assertTrue(plan.batches.size > 1)
            assertFits(plan, events)
        }
    }

    @Test fun exact_byte_limit_is_inclusive_and_one_more_byte_requires_a_new_batch() {
        val template = event(1, "")
        val payload = Json.parseToJsonElement(template.payload).jsonObject
        val overhead = encodeEventBatch(listOf(payload)).toByteArray().size
        val boundary = template.copy(payload = JsonObject(payload.toMutableMap().apply {
            put("response", JsonPrimitive("a".repeat(524288 - overhead)))
        }).toString())
        val next = event(2, "Small response")
        val plan = planEventBatches(listOf(boundary, next))
        assertFits(plan, listOf(boundary, next))
        assertEquals(2, plan.batches.size)
        assertEquals(524288, encodeEventBatch(plan.batches.first().payloads).toByteArray().size)
        val tooLarge = boundary.copy(payload = boundary.payload.replace("\"response\":\"", "\"response\":\"a"))
        val blocked = planEventBatches(listOf(tooLarge, next))
        assertEquals(listOf(tooLarge), blocked.oversized)
        assertEquals(listOf(next), blocked.batches.flatMap { it.events })
    }

    @Test fun event_count_limit_and_empty_outbox_do_not_produce_invalid_batches() {
        val events = (1..101).map { event(it, "A tea, please.") }
        val plan = planEventBatches(events)
        assertFits(plan, events)
        assertEquals(listOf(50, 50, 1), plan.batches.map { it.events.size })
        assertEquals(PracticeBatchPlan(emptyList(), emptyList()), planEventBatches(emptyList()))
    }
}
