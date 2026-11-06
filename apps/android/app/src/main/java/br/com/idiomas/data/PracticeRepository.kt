package br.com.idiomas.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class OpenPractice(val installed: InstalledPackage, val document: MissionDocument, val session: PracticeSession, val events: List<PracticeEvent>)
class PracticeRepository(private val context: Context, private val database: CatalogDatabase, private val catalog: CatalogRepository, private val api: PracticeApi) {
    private val dao = database.practiceDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    var scheduleSync: (Long) -> Unit = {}
    private fun account() = catalog.session.value ?: error("Entre novamente para acessar sua prática.")
    @OptIn(ExperimentalCoroutinesApi::class)
    val installed = catalog.session.flatMapLatest { it?.let { dao.installed(it.account.id) } ?: flowOf(emptyList()) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val pending = catalog.session.flatMapLatest { it?.let { dao.pendingCount(it.account.id) } ?: flowOf(0) }
    private val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("practice-device", Context.MODE_PRIVATE)
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also { check(prefs.edit().putString("id", it).commit()) }
    }
    suspend fun previews() = api.previews(account().token).data
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun durable(file: File, bytes: ByteArray) { FileOutputStream(file).use { it.write(bytes); it.fd.sync() } }

    suspend fun download(item: CatalogItem, preview: Boolean): InstalledPackage = withContext(Dispatchers.IO) {
        val user = account()
        val manifest = api.manifest(user.token, item.id, item.version, preview)
        require(manifest.schema_version == "1.0.0" && manifest.package_hash.matches(Regex("[a-f0-9]{64}")))
        require(manifest.id == item.id && manifest.version == item.version && manifest.package_bytes in 1..2*1024*1024)
        require(manifest.assets.sumOf { it.bytes } <= 64*1024*1024)
        val root = File(context.filesDir, "packages/${user.account.id}").apply { mkdirs() }
        val stage = File(root, ".partial-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val bytes = api.document(user.token, manifest.package_hash)
            require(bytes.size.toLong() == manifest.package_bytes && sha(bytes) == manifest.package_hash) { "Conteúdo incompleto ou corrompido." }
            val doc = json.decodeFromString<MissionDocument>(bytes.toString(Charsets.UTF_8)).also { it.validate() }
            require(doc.id == manifest.id && doc.version == manifest.version)
            require(doc.assets.map { it.id }.toSet() == manifest.assets.map { it.id }.toSet())
            durable(File(stage,"package.json"), bytes)
            for (asset in manifest.assets) {
                require(asset.id.matches(Regex("[a-zA-Z0-9._-]{1,100}")) && asset.bytes in 1..16*1024*1024)
                require(doc.assets.single { it.id == asset.id }.sha256 == asset.sha256)
                val audio = api.asset(user.token, manifest.package_hash, asset.id)
                require(audio.size.toLong() == asset.bytes && sha(audio) == asset.sha256) { "Áudio incompleto ou corrompido." }
                durable(File(stage,"${asset.id}.wav"), audio)
            }
            check(catalog.session.value == user) { "A conta mudou durante o download." }
            val target = File(root, manifest.package_hash)
            if (target.exists()) stage.deleteRecursively() else check(stage.renameTo(target)) { "Não foi possível ativar o pacote." }
            InstalledPackage(user.account.id,manifest.package_hash,doc.id,doc.version,doc.title,manifest.is_preview,target.absolutePath).also { dao.install(it) }
        } finally { stage.deleteRecursively() }
    }

    suspend fun open(hash: String, newSession: Boolean = false): OpenPractice = withContext(Dispatchers.IO) {
        val user = account()
        val installed = dao.packageByHash(user.account.id,hash) ?: error("Baixe a missão antes de abrir.")
        check(!installed.preview || user.account.is_editor)
        val bytes = File(installed.directory,"package.json").readBytes()
        require(sha(bytes) == hash) { "O pacote foi alterado. Baixe-o novamente." }
        val doc = json.decodeFromString<MissionDocument>(bytes.toString(Charsets.UTF_8)).also { it.validate() }
        for (asset in doc.assets) require(sha(File(installed.directory,"${asset.id}.wav").readBytes()) == asset.sha256) { "Áudio corrompido. Baixe novamente." }
        val session = (if (newSession) null else dao.unfinished(user.account.id,hash)) ?: PracticeSession(user.account.id,UUID.randomUUID().toString(),hash,0,"",-1,null,false,System.currentTimeMillis()).also { dao.saveSession(it) }
        check(catalog.session.value == user)
        OpenPractice(installed,doc,session,dao.events(user.account.id,session.id))
    }
    suspend fun reload(value: OpenPractice): OpenPractice {
        val user = account()
        check(user.account.id == value.session.ownerId)
        return value.copy(session=dao.session(user.account.id,value.session.id) ?: error("Sessão indisponível"), events=dao.events(user.account.id,value.session.id))
    }
    suspend fun draft(value: OpenPractice, text: String) = lock.withLock {
        val session = dao.session(value.session.ownerId,value.session.id) ?: return@withLock
        if (session.cursor == value.session.cursor && session.answeredIndex != session.cursor) dao.saveSession(session.copy(draft=text.take(10000)))
    }
    suspend fun recording(value: OpenPractice, path: String?) = lock.withLock {
        val session = dao.session(value.session.ownerId,value.session.id) ?: return@withLock
        if (session.cursor == value.session.cursor) dao.saveSession(session.copy(recordingPath=path))
    }
    fun recordingFile(value: OpenPractice): File = File(context.filesDir,"recordings/${value.session.ownerId}/${value.session.id}/${UUID.randomUUID()}.m4a").also { it.parentFile?.mkdirs() }

    private suspend fun append(value: OpenPractice, kind: String, extra: JsonObject): PracticeEvent {
        val user = account(); check(user.account.id == value.session.ownerId)
        val current = dao.session(user.account.id,value.session.id) ?: error("Sessão ausente")
        check(current.cursor == value.session.cursor && !current.completed)
        val activity = value.document.activities[current.cursor]
        val sequence = (dao.clock(user.account.id) ?: 0) + 1
        dao.saveClock(PracticeClock(user.account.id,sequence))
        val id = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("schema_version","1.0.0");put("id",id);put("session_id",current.id);put("device_id",deviceId);put("local_sequence",sequence)
            put("occurred_at",Instant.now().toString());put("time_quality","uncertain");put("activity_id",activity.id)
            put("package_id",value.document.id);put("package_version",value.document.version);put("package_hash",value.installed.hash)
            put("target_ids",JsonArray(activity.target_ids.map(::JsonPrimitive)))
            extra.forEach { (key,element) -> put(key,element) }
        }
        return PracticeEvent(user.account.id,id,current.id,sequence,kind,payload.toString()).also { dao.addEvent(it) }
    }
    suspend fun exposure(value: OpenPractice, support: MissionSupport? = null): OpenPractice = lock.withLock {
        database.withTransaction {
            val activity = value.document.activities[value.session.cursor]
            append(value,"exposure",buildJsonObject {
                put("support_id",support?.id ?: "activity-${activity.id}");put("kind",support?.kind ?: "training_use")
                put("affected_modalities",JsonArray((support?.affected_modalities ?: listOf(activity.modality)).map(::JsonPrimitive)))
                put("reveals_answer",support?.reveals_answer ?: false)
            })
        }
        scheduleSync(value.session.ownerId)
        reload(value)
    }
    suspend fun answer(value: OpenPractice, response: String?, audioPath: String? = null, technical: String = "valid"): OpenPractice = lock.withLock {
        database.withTransaction {
            val session = dao.session(value.session.ownerId,value.session.id) ?: error("Sessão ausente")
            check(session.answeredIndex != session.cursor) { "A resposta já foi registrada." }
            val activity = value.document.activities[session.cursor]
            val supports = dao.events(session.ownerId,session.id).filter { it.kind == "exposure" }.filter {
                val payload = json.parseToJsonElement(it.payload).jsonObject
                payload.text("kind") != "training_use" && activity.modality in payload.strings("affected_modalities") && activity.target_ids.any { target -> target in payload.strings("target_ids") }
            }
            append(value,"attempt",buildJsonObject {
                put("modality",activity.modality);put("support_event_ids",JsonArray(supports.map { JsonPrimitive(it.id) }))
                put("technical_status",technical);put("response",response?.take(10000));put("audio_reference",if(audioPath==null) null else "local-only:${UUID.randomUUID()}")
                put("assessment_status",if(activity.type == "information_question" || activity.expected["comparison"] != null) "pending" else "not_requested")
            })
            dao.saveSession(session.copy(draft=response ?: "",recordingPath=audioPath,answeredIndex=session.cursor))
            // A displayed correction is a later exposure, never prior support for this attempt.
            if (technical == "valid" && compareAnswer(activity, response).example != null) {
                val solution = activity.supports.firstOrNull { it.id == "comparison-example" && it.kind == "solution" }
                if (solution != null) append(value, "exposure", buildJsonObject {
                    put("support_id", solution.id); put("kind", solution.kind)
                    put("affected_modalities", JsonArray(solution.affected_modalities.map(::JsonPrimitive)))
                    put("reveals_answer", solution.reveals_answer)
                })
            }
        }
        scheduleSync(value.session.ownerId)
        reload(value)
    }
    suspend fun advance(value: OpenPractice, retry: Boolean = false): OpenPractice = lock.withLock {
        val user = account();check(user.account.id == value.session.ownerId)
        val session = dao.session(user.account.id,value.session.id) ?: error("Sessão ausente")
        check(session.cursor == value.session.cursor)
        val last = session.cursor >= value.document.activities.lastIndex
        val next=if(retry) session.copy(draft="",answeredIndex=-1,recordingPath=null) else if(last) session.copy(completed=true) else session.copy(cursor=session.cursor+1,draft="",answeredIndex=-1,recordingPath=null)
        database.withTransaction {
            dao.saveSession(next)
            if(!retry && !next.completed && value.document.activities[next.cursor].type=="explanation" && value.document.activities[next.cursor].phaseId()==value.document.activities[session.cursor].phaseId() && value.document.phases().first { it.id==value.document.activities[session.cursor].phaseId() }.let { it.lessonIndex(session.cursor)==it.lessonIndex(next.cursor) }) {
                val target=value.document.activities[next.cursor]
                append(value.copy(session=next),"exposure",buildJsonObject {
                    put("support_id","activity-${target.id}");put("kind","training_use");put("affected_modalities",JsonArray(listOf(JsonPrimitive(target.modality))));put("reveals_answer",false)
                })
            }
        }
        scheduleSync(user.account.id)
        reload(value)
    }
    suspend fun clearLocal() = lock.withLock {
        val owner=account().account.id
        database.withTransaction { dao.deleteEvents(owner);dao.deleteSessions(owner);dao.deletePackages(owner) }
        withContext(Dispatchers.IO) {
            for(kind in listOf("recordings","packages")) {
                val directory=File(context.filesDir,"$kind/$owner")
                check(!directory.exists() || directory.deleteRecursively()) { "Alguns arquivos não puderam ser removidos. Tente novamente." }
            }
        }
    }
    suspend fun sync(): Boolean {
        val user = account()
        try { repeat(8) {
            if(catalog.session.value != user) return false
            val batch = dao.pending(user.account.id)
            if(batch.isEmpty()) return true
            for(kind in listOf("exposure","attempt")) {
                val events = batch.filter { it.kind == kind }
                if(events.isEmpty()) continue
                if(catalog.session.value != user) return false
                val receipts = api.send(user.token,kind,events.map { json.parseToJsonElement(it.payload).jsonObject })
                for(receipt in receipts.results) {
                    if(events.none { it.id == receipt.id }) continue
                    val state = when(receipt.status) { "accepted","duplicate" -> "received"; "rejected","conflict" -> "blocked"; else -> continue }
                    dao.receipt(user.account.id,receipt.id!!,state,receipt.message)
                }
            }
            for(id in batch.map { it.sessionId }.distinct()) {
                if(catalog.session.value != user) return false
                val remote = api.results(user.token,id)
                remote.events.forEach { event -> event.assessment?.let { dao.assessment(user.account.id,event.id,json.encodeToString(it)) } }
            }
        }
        return false
        } catch(failure: ApiFailure) {
            if(failure.status == 401) catalog.invalidate(user)
            throw failure
        }
    }
}
