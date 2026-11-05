package br.com.idiomas

import android.Manifest
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import br.com.idiomas.data.*
import java.io.File
import kotlinx.serialization.json.jsonObject

@Composable fun LocalAudio(path: String, autoplay: Boolean = false, requestId: Int = 0, allowReplay: Boolean = true, onFailure: ()->Unit = {}) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val failureCallback by rememberUpdatedState(onFailure)
    var playbackIssue by remember(path) { mutableStateOf(false) }
    val player=remember(path) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))));prepare() } }
    LaunchedEffect(path,autoplay,requestId) { if(autoplay) { player.seekTo(0);player.play() } }
    DisposableEffect(player,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) player.pause() }
        val listener=object:androidx.media3.common.Player.Listener { override fun onPlayerError(error:androidx.media3.common.PlaybackException) { playbackIssue=true;failureCallback() } }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);player.removeListener(listener);player.release() }
    }
    if(playbackIssue) Text("Não foi possível reproduzir o áudio. Nenhuma capacidade foi avaliada.")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(allowReplay) OutlinedButton(onClick={ player.seekTo(0);player.play() }) { Text("Ouvir") }
        TextButton(onClick={player.pause()}) { Text("Pausar") }
    }
}

@Composable private fun VoiceRecorder(vm: PracticeViewModel, value: OpenPractice, sessionKey: String, savedPath: String?) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var recorder by remember(sessionKey) { mutableStateOf<MediaRecorder?>(null) }
    var recording by remember(sessionKey) { mutableStateOf(false) }
    var output by remember(sessionKey) { mutableStateOf<File?>(null) }
    var permissionDenied by remember(sessionKey) { mutableStateOf(false) }
    fun stop() {
        val current=recorder ?: return
        recorder=null;recording=false
        try { current.stop(); output?.takeIf { it.exists() && it.length()>0 }?.let { vm.recorded(value,it) } ?: vm.recordingError(value) }
        catch(_: Exception) { output?.delete();vm.recordingError(value) }
        finally { current.release() }
    }
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied=!granted
        if(granted) {
            var current:MediaRecorder?=null
            try {
                val file=vm.recordingFile(value) ?: return@rememberLauncherForActivityResult
                @Suppress("DEPRECATION")
                val created=if(Build.VERSION.SDK_INT>=31) MediaRecorder(context) else MediaRecorder()
                current=created
                created.setAudioSource(MediaRecorder.AudioSource.MIC)
                created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                created.setOutputFile(file.absolutePath)
                created.prepare();created.start();output=file;recorder=created;recording=true
            } catch(_: Exception) { current?.release();vm.recordingError(value) }
        }
    }
    DisposableEffect(sessionKey,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);stop() }
    }
    if(recording) {
        Text("Gravando sua voz…",color=MaterialTheme.colorScheme.error)
        Button(onClick={stop()}) { Text("Parar gravação") }
    } else {
        OutlinedButton(onClick={request.launch(Manifest.permission.RECORD_AUDIO)}) { Text(if(savedPath==null) "Gravar minha voz" else "Gravar novamente") }
    }
    if(permissionDenied) Text("Microfone indisponível. Você pode continuar sem avaliação oral.")
    if(savedPath!=null && File(savedPath).exists()) LocalAudio(savedPath)
    Text("Sua gravação fica neste aparelho. Nenhum áudio será enviado.",style=MaterialTheme.typography.bodySmall)
}

@Composable fun PracticeScreen(vm: PracticeViewModel,state: PracticeScreenState,pending:Int) {
    val value=state.open ?: return
    val activity=value.document.activities[value.session.cursor]
    var answer by remember(value.session.id,value.session.cursor,value.session.answeredIndex) { mutableStateOf(value.session.draft) }
    val answered=value.session.answeredIndex==value.session.cursor
    val listeningIssue=value.events.any { event -> if(event.kind!="attempt")false else {val payload=kotlinx.serialization.json.Json.parseToJsonElement(event.payload).jsonObject;payload.text("modality")=="listening" && payload.text("technical_status")!="valid"} }
    LazyColumn(modifier=Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick=vm::exit,enabled=!state.busy) { Text("Voltar às missões") } }
        item { Text(value.document.title,style=MaterialTheme.typography.headlineSmall) }
        if(value.installed.preview) item { Card { Text("Prévia editorial · material em revisão. Esta sessão não confirma capacidades.",Modifier.padding(16.dp)) } }
        item { Text("Etapa ${value.session.cursor+1} de ${value.document.activities.size}",style=MaterialTheme.typography.labelLarge) }
        item { Text(activity.prompt,style=MaterialTheme.typography.titleLarge) }
        if(activity.type=="summary" || value.session.completed) {
            item { Text("Você registrou ${value.events.count { it.kind=="attempt" }} tentativas e ${value.events.count { it.kind=="exposure" }} exposições ou apoios.") }
            item { Text("A prática ficou salva. Produções abertas permanecem sem avaliação independente; retenção posterior ainda não foi verificada.") }
            item { Text(if(pending==0) "Sem registros aguardando envio." else "$pending registros aguardando envio. Você pode encerrar agora.") }
            if(value.events.any { it.syncState=="blocked" }) item { Text("Alguns registros não foram aceitos. A prática permanece salva neste aparelho.") }
            item { Button(onClick={vm.next()},enabled=!state.busy && !value.session.completed) { Text("Encerrar sessão") } }
        } else {
            if(activity.content.text("asset_id")!=null) item { Button(onClick=vm::playModel,enabled=!state.busy) { Text(if(activity.type=="dialogue") "Ouvir o diálogo" else "Ouvir o atendente") } }
            state.playPath?.let { path -> item { LocalAudio(path,true,state.playNonce,false) { vm.recordingError(value) } } }
            if(activity.type=="explanation") {
                items(activity.content.strings("examples").size) { index -> Text(activity.content.strings("examples")[index]) }
                activity.content.text("pt_BR_note")?.let { note -> item { Text(note) } }
            }
            for(support in activity.supports) item {
                if(support.id in state.revealed) Text(support.content)
                else OutlinedButton(onClick={vm.support(support.id)},enabled=!state.busy) { Text(if(support.kind=="transcript") "Ver transcrição" else "Consultar apoio") }
            }
            if(activity.type=="information_question" && listeningIssue && !answered) item { Text("O áudio apresentou uma falha. Você pode continuar sem avaliação auditiva.");TextButton(onClick={vm.next()},enabled=!state.busy) {Text("Continuar sem avaliação auditiva")} }
            if(activity.type=="information_question" && !answered) {
                for(option in activity.content.strings("options")) item { OutlinedButton(onClick={vm.answer(option)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text(option) } }
            }
            if(activity.type=="written_recall" && !answered) {
                item { OutlinedTextField(answer,{answer=it;vm.saveDraft(it)},label={Text("Sua resposta")},modifier=Modifier.fillMaxWidth(),minLines=3) }
                item { Button(onClick={vm.answer(answer)},enabled=!state.busy && answer.isNotBlank()) { Text("Registrar resposta") } }
            }
            if(activity.type in listOf("recorded_speech","communicative_task") && !answered) {
                item { VoiceRecorder(vm,value,"${value.session.id}:${value.session.cursor}",value.session.recordingPath) }
                item { Button(onClick={vm.answer(null,value.session.recordingPath)},enabled=!state.busy && value.session.recordingPath!=null) { Text("Registrar prática oral") } }
                item { TextButton(onClick={vm.next()},enabled=!state.busy) { Text("Agora não posso falar · continuar") } }
            }
            if(answered) {
                val latest=value.events.lastOrNull { it.kind=="attempt" }
                val data=latest?.payload?.let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }
                val feedback=when {
                    data?.text("technical_status")!="valid" -> "Falha técnica registrada. Sua capacidade não foi reduzida."
                    activity.type=="information_question" && listeningIssue -> "Falha de reprodução nesta sessão. Sua compreensão auditiva não foi avaliada."
                    activity.type=="information_question" -> if(value.session.draft in activity.expected.strings("accepted")) "Você identificou a informação. Este acerto não confirma retenção posterior." else "Ouça novamente e identifique a escolha comunicada. Você pode tentar outra vez ou continuar."
                    else -> "Prática registrada. Uma resposta alternativa válida precisa ser julgada pelo sentido; a produção ainda não foi avaliada."
                }
                item { Text(feedback) }
                item { Row { TextButton(onClick={vm.next(true)},enabled=!state.busy) { Text("Nova tentativa") };Button(onClick={vm.next()},enabled=!state.busy) { Text("Continuar") } } }
            }
            if(activity.type in listOf("purpose","dialogue","explanation")) item { Button(onClick=vm::passive,enabled=!state.busy) { Text("Continuar") } }
        }
        item { OutlinedButton(onClick=vm::sync,enabled=!state.busy) { Text("Sincronizar registros") } }
        if(state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.message?.let { message -> item { Text(message) } }
    }
}
