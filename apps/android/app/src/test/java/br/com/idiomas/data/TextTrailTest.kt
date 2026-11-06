package br.com.idiomas.data

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TextTrailTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun mission(version: Int): MissionDocument {
        val root = File(checkNotNull(System.getProperty("contracts.dir"))).parentFile
        return json.decodeFromString(File(root, "content/pt-BR_en/missions/order-a-drink.v$version.json").readText())
    }

    @Test fun text_trail_is_consumable_without_media_and_preserves_phase_order() {
        val document = mission(2)
        document.validate()
        assertTrue(document.isTextOnly())
        val phases = document.phases()
        assertEquals(listOf("understand", "build", "use"), phases.map { it.id })
        assertEquals(document.activities.indices.toList(), phases.flatMap { phase -> phase.steps.map { it.index } })
        assertTrue(phases.all { it.description.isNotBlank() && it.steps.isNotEmpty() })
        assertTrue(document.activities.filter { it.type in setOf("written_recall", "communicative_task") }.all { it.modality == "writing" })
        assertTrue(document.activities.flatMap { it.supports }.all { support -> support.affected_modalities.all { it in setOf("reading", "writing") } })
    }

    @Test fun beginner_version_introduces_vocabulary_before_long_forms_and_has_declared_meanings() {
        val document = mission(3)
        document.validate()
        assertTrue(document.isTextOnly())
        assertEquals(12, document.activities.size)
        assertEquals(listOf("words", "requests", "sentences"), document.phases().map { it.id })
        val first = document.activities.first()
        assertEquals(listOf("tea", "coffee", "water"), first.content["vocabulary"]!!.jsonArray.map { it.jsonObject.text("word") })
        for (activity in document.activities) {
            if (activity.phaseId() != "sentences") {
                val visible = activity.content.strings("examples").joinToString(" ") + (activity.content.text("study_text") ?: "")
                assertFalse(visible.contains("I'd"))
            }
            activity.content["glossary"]?.jsonObject?.values?.forEach { entry ->
                val support = activity.supports.single { it.id == entry.jsonObject.text("support_id") }
                assertEquals(entry.jsonObject.text("meaning"), support.content)
                assertTrue(activity.modality in support.affected_modalities)
            }
        }
    }

    @Test fun daily_cards_group_all_activities_into_three_lessons() {
        for (day in mission(3).phases()) {
            val lessons = day.lessons()
            assertEquals(3, lessons.size)
            assertEquals(day.steps, lessons.flatten())
            assertEquals(0, day.lessonIndex(lessons[0].last().index))
            assertEquals(1, day.lessonIndex(lessons[1].first().index))
        }
    }

    @Test fun historical_audio_mission_is_not_silently_reclassified_as_text() {
        val previous = mission(1)
        previous.validate()
        assertFalse(previous.isTextOnly())
        assertTrue(previous.activities.any { it.modality == "listening" })
        assertTrue(previous.activities.any { it.modality == "speaking" })
    }
}
