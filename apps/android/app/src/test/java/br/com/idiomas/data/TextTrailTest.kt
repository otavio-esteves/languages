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

    @Test fun day_progress_counts_explicit_lessons_and_keeps_content_levels_separate() {
        val days=mission(4).phases()
        assertEquals(listOf("A1","A1","A1"),days.map {it.level})
        val done=setOf("sentences:0","words:0","words:1")
        assertEquals(listOf(2,0,1),days.map {it.completedCount(done)})
        assertEquals(setOf("words:0","words:1","words:2"),days.legacyLessons(3,false))
        assertEquals(listOf(3,0,1),days.map {it.completedCount(done+"words:2")})
    }

    @Test fun expanded_curriculum_fits_the_reader_and_keeps_53_days_of_three_complete_lessons() {
        val document = mission(5)
        document.validate()
        assertTrue(document.isTextOnly())
        val root = File(checkNotNull(System.getProperty("contracts.dir"))).parentFile
        assertTrue(File(root, "content/pt-BR_en/missions/order-a-drink.v5.json").length() <= 2 * 1024 * 1024)
        val days = document.phases()
        assertEquals(53, days.size)
        assertEquals("day-53", days.last().id)
        assertEquals((1..53).map { "day-${it.toString().padStart(2, '0')}" }, days.map { it.id })
        for (day in days) {
            assertEquals(10..15, day.estimatedMinutes)
            assertEquals(listOf(8, 8, 8), day.lessons().map { it.size })
            assertEquals(780, day.steps.sumOf { document.activities[it.index].content["estimated_seconds"]!!.jsonPrimitive.int })
            for (lesson in day.lessons()) assertEquals("summary", document.activities[lesson.last().index].type)
        }
        assertEquals(List(13) { "A2" }, days.drop(40).map { it.level })
        assertEquals(List(52) { 0 } + 1, days.map { it.completedCount(setOf("day-53:2")) })
        for (activity in document.activities) {
            val rule = activity.expected["comparison"]?.jsonObject ?: continue
            for (variant in rule.strings("accepted")) assertEquals(activity.id, "correct", compareAnswer(activity, variant).outcome)
            for (item in rule["corrections"]!!.jsonArray) assertEquals(activity.id, "incorrect", compareAnswer(activity, item.jsonObject.text("response")).outcome)
            assertEquals("unverified", compareAnswer(activity, "An alternative outside the authored variants.").outcome)
        }
        assertNull(mission(4).phases().first().estimatedMinutes)
    }
}
