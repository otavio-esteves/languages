package br.com.idiomas.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class MissionSupport(val id: String, val kind: String, val content: String, val affected_modalities: List<String>, val reveals_answer: Boolean)
@Serializable data class MissionAsset(val id: String, val status: String, val path: String? = null, val sha256: String? = null, val script: String = "")
@Serializable data class MissionActivity(val id: String, val type: String, val modality: String, val prompt: String, val target_ids: List<String>, val supports: List<MissionSupport>, val content: JsonObject, val expected: JsonObject, val feedback: String)
@Serializable data class MissionDocument(val schema_version: String, val id: String, val version: Int, val minimum_reader_version: String, val title: String, val status: String, val assets: List<MissionAsset>, val activities: List<MissionActivity>) {
    fun validate() {
        require(schema_version == "1.0.0" && minimum_reader_version == "1.0.0") { "Este conteúdo exige outra versão do aplicativo." }
        require(id.matches(Regex("[a-zA-Z0-9._-]{1,100}")) && version > 0 && activities.isNotEmpty())
        require(activities.map { it.id }.distinct().size == activities.size)
        require(assets.all { it.id.matches(Regex("[a-zA-Z0-9._-]{1,100}")) && it.status == "ready" })
        require(activities.all { it.type in setOf("purpose","dialogue","explanation","information_question","written_recall","recorded_speech","communicative_task","summary") }) { "Tipo de atividade incompatível." }
    }
}
@Serializable data class ManifestAsset(val id: String, val sha256: String, val bytes: Long)
@Serializable data class PackageManifest(val schema_version: String, val id: String, val version: Int, val package_hash: String, val package_bytes: Long, val is_preview: Boolean, val assets: List<ManifestAsset>)
@Serializable data class EventReceipt(val id: String? = null, val status: String, val message: String? = null)
@Serializable data class BatchResult(val results: List<EventReceipt>)
@Serializable data class WritingReviewDimension(val id: String, val result: String, val reason: String)
@Serializable data class WritingReview(val id: String, val attempt_id: String, val status: String, val revision: Int, val is_preview: Boolean, val requested_at: String, val reviewed_at: String? = null, val feedback: String? = null, val rubric_id: String, val rubric_version: Int, val scope_version: String, val dimensions: List<WritingReviewDimension> = emptyList(), val source: String, val retention_confirmed: Boolean = false)
@Serializable data class RemoteAssessment(val outcome: String, val supported: Boolean, val is_preview: Boolean, val retention_confirmed: Boolean, val source: String, val message: String, val human_review: WritingReview? = null, val human_feedback_available: Boolean = false)
@Serializable data class RemoteEvent(val id: String, val kind: String, val assessment: RemoteAssessment? = null, val human_review: WritingReview? = null)
@Serializable data class RemoteSession(val session_id: String, val events: List<RemoteEvent>)
interface PracticeApi {
    suspend fun previews(token: String): CatalogResponse
    suspend fun manifest(token: String, id: String, version: Int, preview: Boolean): PackageManifest
    suspend fun document(token: String, hash: String): ByteArray
    suspend fun asset(token: String, hash: String, id: String): ByteArray
    suspend fun send(token: String, kind: String, events: List<JsonObject>): BatchResult
    suspend fun results(token: String, sessionId: String): RemoteSession
    suspend fun requestWritingReview(token: String, attemptId: String): WritingReview = throw UnsupportedOperationException("Revisão escrita indisponível neste serviço.")
}
fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
fun JsonObject.strings(key: String): List<String> = this[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
