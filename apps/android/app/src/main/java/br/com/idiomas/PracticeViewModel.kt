package br.com.idiomas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.idiomas.data.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PracticeScreenState(val busy: Boolean = false, val message: String? = null, val previews: List<CatalogItem> = emptyList(), val open: OpenPractice? = null, val revealed: Set<String> = emptySet(), val playPath: String? = null, val playNonce: Int = 0, val trail: Boolean = true, val selectedDay: Int = 0)
class PracticeViewModel(private val practice: PracticeRepository, private val catalog: CatalogRepository) : ViewModel() {
    private val mutableCues = MutableSharedFlow<InteractionCue>(extraBufferCapacity=4)
    val cues = mutableCues.asSharedFlow()
    private fun cue(identity: Session?, cue: InteractionCue) { if (catalog.session.value == identity) mutableCues.tryEmit(cue) }
    private val mutable = MutableStateFlow(PracticeScreenState())
    val screen = mutable.asStateFlow()
    val installed = practice.installed.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val savedPackages = practice.savedPackages.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val pending = practice.pending.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0)
    val blocked = practice.blocked.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    init { viewModelScope.launch { catalog.session.collect { session -> mutable.value=PracticeScreenState();session?.let { practice.scheduleSync(it.account.id) } } } }
    private fun publish(expected: Session?, change: (PracticeScreenState)->PracticeScreenState) { if(catalog.session.value==expected) mutable.update(change) }
    private fun action(block: suspend (Session?)->Unit) {
        if(mutable.value.busy) return
        val identity=catalog.session.value
        viewModelScope.launch {
            publish(identity) { it.copy(busy=true,message=null,playPath=null) }
            try { block(identity) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) { publish(identity) { it.copy(message=when(error) { is ApiFailure -> error.message;is IllegalArgumentException -> "Conteúdo incompleto ou incompatível. Atualize ou baixe novamente.";else -> "Não foi possível concluir. Sua prática já salva permanece no aparelho." }) } }
            finally { publish(identity) { it.copy(busy=false) } }
        }
    }
    fun previews() = action { identity -> val values=practice.previews();publish(identity) { it.copy(previews=values) } }
    fun download(item: CatalogItem, preview: Boolean) = action { identity ->
        val installed=practice.download(item,preview);val open=practice.open(installed.hash)
        publish(identity) { it.copy(open=open,revealed=open.revealedSupports(),trail=true,selectedDay=dayOf(open)) }
    }
    fun open(installed: InstalledPackage) = action { identity -> val open=practice.open(installed.hash);publish(identity) { it.copy(open=open,revealed=open.revealedSupports(),trail=true,selectedDay=dayOf(open)) } }
    fun exit() { mutable.update { it.copy(open=null,revealed=emptySet(),playPath=null,trail=true) } }
    fun showTrail() { mutable.update { it.copy(trail=true,playPath=null) } }
    private fun dayOf(value: OpenPractice): Int = value.document.phases().indexOfFirst { phase -> phase.steps.any { it.index == value.session.cursor } }.coerceAtLeast(0)
    fun selectDay(day: Int) {
        val value = mutable.value.open ?: return
        if (!mutable.value.busy && day in value.document.phases().indices) mutable.update { it.copy(selectedDay=day) }
    }
    fun begin(lesson: Int) = action { identity ->
        val previous=mutable.value.open ?: return@action
        val value=practice.selectLesson(previous, mutable.value.selectedDay, lesson)
        check(value.document.isTextOnly())
        val activity=value.document.activities[value.session.cursor]
        val updated=if(activity.type in listOf("dialogue","explanation")) practice.exposure(value) else value
        publish(identity) { it.copy(open=updated,revealed=updated.revealedSupports(),trail=false) }
        cue(identity, InteractionCue.Tap)
    }
    private suspend fun presentNext(previous:OpenPractice,next:OpenPractice): Pair<OpenPractice,Boolean> {
        val oldPhase=previous.document.phases().first { it.id==previous.document.activities[previous.session.cursor].phaseId() }
        val changed=next.session.completed || oldPhase.id!=next.document.activities[next.session.cursor].phaseId() || oldPhase.lessonIndex(previous.session.cursor)!=oldPhase.lessonIndex(next.session.cursor)
        val shown=if(!changed && next.document.activities[next.session.cursor].type=="dialogue") practice.exposure(next) else next
        return shown to changed
    }
    fun support(id:String) = action { identity ->
        val value=mutable.value.open ?: return@action
        val support=value.document.activities[value.session.cursor].supports.single { it.id==id }
        val updated=practice.exposure(value,support);publish(identity) { it.copy(open=updated,revealed=it.revealed+id) }
    }
    fun playModel() = action { identity ->
        val value=mutable.value.open ?: return@action
        val asset=value.document.activities[value.session.cursor].content.text("asset_id") ?: return@action
        val updated=practice.exposure(value);publish(identity) { it.copy(open=updated,playPath=File(value.installed.directory,"$asset.wav").absolutePath,playNonce=it.playNonce+1) }
    }
    fun passive() = action { identity ->
        val value=mutable.value.open ?: return@action
        val current=value.document.activities[value.session.cursor]
        val exposed=if(current.type=="purpose") practice.exposure(value) else value
        val (next,trail)=presentNext(value,practice.advance(exposed))
        publish(identity) { it.copy(open=next,revealed=emptySet(),trail=trail) }
        if (trail) cue(identity, InteractionCue.Complete)
    }
    fun answer(text:String?,path:String?=null,technical:String="valid") = action { identity ->
        val value=mutable.value.open ?: return@action
        val updated=practice.answer(value,text,path,technical);publish(identity) { it.copy(open=updated) }
        cue(identity, feedbackCue(if (technical=="valid") compareAnswer(value.document.activities[value.session.cursor], text).outcome else "technical_issue"))
    }
    fun next(retry:Boolean=false) = action { identity ->
        val value=mutable.value.open ?: return@action
        val (updated,trail)=presentNext(value,practice.advance(value,retry));publish(identity) { it.copy(open=updated,revealed=emptySet(),trail=trail) }
        if (trail && !retry) cue(identity, InteractionCue.Complete)
    }
    fun saveDraft(text:String) { val value=mutable.value.open ?: return;viewModelScope.launch { practice.draft(value,text) } }
    fun recordingFile(value:OpenPractice):File?=if(catalog.session.value?.account?.id==value.session.ownerId && mutable.value.open?.session?.id==value.session.id) practice.recordingFile(value) else null
    fun recorded(value:OpenPractice,file:File) {
        val identity=catalog.session.value
        viewModelScope.launch {
            practice.recording(value,file.absolutePath)
            if(mutable.value.open?.session?.id==value.session.id) {
                val updated=practice.reload(value);publish(identity) { it.copy(open=updated) }
            }
        }
    }
    fun recordingError(value:OpenPractice) {
        val identity=catalog.session.value
        viewModelScope.launch {
            try { val updated=practice.answer(value,null,null,"failed");if(mutable.value.open?.session?.id==value.session.id)publish(identity) { it.copy(open=updated) } }
            catch(_:Exception) { /* Capture failed after navigation; never invent linguistic evidence. */ }
        }
    }
    fun clearLocal() = action { identity -> practice.clearLocal();publish(identity) { it.copy(open=null,message="Prática local removida. Registros já enviados permanecem no servidor.") } }
    fun retryBlocked() = action { identity ->
        practice.retryBlocked()
        publish(identity) { it.copy(message="Os registros serão reenviados quando houver conexão. Sua prática permanece salva.") }
    }
    fun sync() = action { identity ->
        val sent = practice.sync()
        val issues = practice.blocked.first()
        val message = when {
            issues.isNotEmpty() -> "Há registros que não foram aceitos. Consulte os detalhes abaixo."
            !sent -> "A sincronização ainda está pendente. Sua prática permanece salva."
            else -> "Registros sincronizados. Respostas abertas continuam sem avaliação independente."
        }
        val updated = mutable.value.open?.let { practice.reload(it) }
        publish(identity) { it.copy(open=updated,message=message) }
    }
    companion object {
        fun factory(practice:PracticeRepository,catalog:CatalogRepository):ViewModelProvider.Factory=object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=PracticeViewModel(practice,catalog) as T
        }
    }
}
