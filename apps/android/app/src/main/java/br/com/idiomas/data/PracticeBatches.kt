package br.com.idiomas.data

import kotlinx.serialization.json.*

internal const val MAX_PRACTICE_BATCH_BYTES = 524288
internal const val MAX_PRACTICE_BATCH_EVENTS = 50

internal fun encodeEventBatch(events: List<JsonObject>): String = buildJsonObject {
    put("events", JsonArray(events))
}.toString()

internal data class PracticeEventBatch(val events: List<PracticeEvent>, val payloads: List<JsonObject>)
internal data class PracticeBatchPlan(val batches: List<PracticeEventBatch>, val oversized: List<PracticeEvent>)

/** Measure the same UTF-8 JSON envelope used by HTTP, including escaping and commas. */
internal fun planEventBatches(events: List<PracticeEvent>): PracticeBatchPlan {
    val batches = mutableListOf<PracticeEventBatch>()
    val oversized = mutableListOf<PracticeEvent>()
    val current = mutableListOf<PracticeEvent>()
    val payloads = mutableListOf<JsonObject>()
    val envelopeBytes = encodeEventBatch(emptyList()).toByteArray(Charsets.UTF_8).size
    var bytes = envelopeBytes
    fun flush() {
        if (current.isNotEmpty()) batches += PracticeEventBatch(current.toList(), payloads.toList())
        current.clear()
        payloads.clear()
        bytes = envelopeBytes
    }
    for (event in events) {
        val payload = Json.parseToJsonElement(event.payload).jsonObject
        val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8).size
        if (envelopeBytes + payloadBytes > MAX_PRACTICE_BATCH_BYTES) {
            oversized += event
            continue
        }
        val separator = if (current.isEmpty()) 0 else 1
        if (current.size == MAX_PRACTICE_BATCH_EVENTS || bytes + separator + payloadBytes > MAX_PRACTICE_BATCH_BYTES) flush()
        bytes += payloadBytes + if (current.isEmpty()) 0 else 1
        current += event
        payloads += payload
    }
    flush()
    return PracticeBatchPlan(batches, oversized)
}
