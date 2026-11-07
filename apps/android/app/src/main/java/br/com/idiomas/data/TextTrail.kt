package br.com.idiomas.data

/** Phase metadata belongs to the versioned content; it does not represent mastery. */
data class TrailStep(val index: Int, val label: String)
data class TrailPhase(val id: String, val title: String, val description: String, val steps: List<TrailStep>, val level: String? = null)

fun MissionActivity.phaseId(): String = content.text("phase_id") ?: "practice"
fun MissionActivity.stepLabel(): String = content.text("label") ?: "Atividade"
fun MissionDocument.isTextOnly(): Boolean = assets.isEmpty() && activities.all { it.modality in setOf("reading", "writing") }
fun MissionDocument.phases(): List<TrailPhase> = activities.withIndex().groupBy { it.value.phaseId() }.map { (id, steps) ->
    val metadata = steps.first().value.content
    TrailPhase(id, metadata.text("phase_title") ?: "Praticar", metadata.text("phase_description") ?: "", steps.map { TrailStep(it.index, it.value.stepLabel()) }, metadata.text("phase_level"))
}

fun TrailPhase.lessons(): List<List<TrailStep>> {
    val count = minOf(3, steps.size)
    return (0 until count).map { index -> steps.filterIndexed { position, _ -> position * count / steps.size == index } }
}
fun TrailPhase.lessonIndex(cursor: Int): Int = lessons().indexOfFirst { lesson -> lesson.any { it.index == cursor } }
fun TrailPhase.lessonKey(index: Int): String = "$id:$index"
fun TrailPhase.completedCount(completed: Set<String>): Int = lessons().indices.count { lessonKey(it) in completed }
fun List<TrailPhase>.legacyLessons(cursor: Int, completed: Boolean): Set<String> = flatMap { day ->
    day.lessons().mapIndexedNotNull { index, steps -> if (completed || steps.all { it.index < cursor }) day.lessonKey(index) else null }
}.toSet()
