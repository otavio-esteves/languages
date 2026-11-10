package br.com.idiomas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import br.com.idiomas.data.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Composable
fun PracticeScreen(vm: PracticeViewModel, state: PracticeScreenState, pending: Int, blocked: List<PracticeEvent>) {
    val sounds = LocalUiSounds.current
    val palette = MaterialTheme.colorScheme
    val value = state.open ?: return
    val activity = value.document.activities[value.session.cursor]
    val phases = remember(value.installed.hash) { value.document.phases() }
    val phase = phases.first { it.id == activity.phaseId() }
    var answer by remember(value.session.id, value.session.cursor, value.session.answeredIndex) { mutableStateOf(value.session.draft) }
    var selected by remember(value.session.id, value.session.cursor, value.session.answeredIndex) { mutableStateOf(value.session.draft.takeIf { activity.type == "information_question" && it in activity.content.strings("options") }) }
    val answered = value.session.answeredIndex == value.session.cursor
    val lesson = phase.lessonIndex(value.session.cursor)
    val lessonSteps = phase.lessons()[lesson]
    val questionIds = lessonSteps.map { value.document.activities[it.index] }.filter { it.type in listOf("information_question", "written_recall", "communicative_task") }.map { it.id }.toSet()
    val answeredQuestions = value.events.filter { it.kind == "attempt" }.map { Json.parseToJsonElement(it.payload).jsonObject }
        .filter { it.text("technical_status") == "valid" }.mapNotNull { it.text("activity_id") }.toSet().intersect(questionIds).size
    val meanings = remember(value.installed.hash, activity.id) { activity.wordMeanings() }
    var selectedMeaning by remember(value.session.id, value.session.cursor) { mutableStateOf<Pair<String, WordMeaning>?>(null) }
    val lookup: (String, WordMeaning) -> Unit = { word, meaning ->
        if (!state.busy) {
            selectedMeaning = word to meaning
            if (meaning.supportId !in state.revealed) vm.support(meaning.supportId)
        }
    }
    selectedMeaning?.takeIf { it.second.supportId in state.revealed }?.let { (word, meaning) ->
        AlertDialog(onDismissRequest = { selectedMeaning = null }, title = { Text(word) }, text = { Text(meaning.meaning) }, confirmButton = { JourneyTextButton(onClick = { selectedMeaning = null }) { Text("Fechar") } })
    }
    val scroll = rememberLazyListState()
    BackHandler { if (!state.busy) { if (state.trail) vm.exit() else vm.showTrail() } }
    LaunchedEffect(value.session.id, value.session.cursor, state.trail) { scroll.scrollToItem(0) }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (!state.trail) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick=vm::showTrail, enabled=!state.busy, modifier=Modifier.size(46.dp), contentPadding=PaddingValues(0.dp), shape=RoundedCornerShape(24.dp)) { Text("‹", style=MaterialTheme.typography.headlineSmall, modifier=Modifier.semantics { contentDescription="Voltar à trilha" }) }
                Column(Modifier.weight(1f), horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("Dia ${phases.indexOf(phase)+1}", style=MaterialTheme.typography.headlineSmall)
                    Text("Lição ${lesson+1}", style=MaterialTheme.typography.labelMedium, color=palette.onSurfaceVariant)
                }
                AppearanceControls()
            }
        }
        if (!value.document.isTextOnly()) {
            item { Text("Continue na trilha de texto.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            item { Text("Esta versão foi guardada no histórico. Baixe a versão atual na prévia editorial.") }
            item { JourneyButton("Voltar às missões", vm::exit, !state.busy, Modifier.fillMaxWidth()) }
        } else if (state.trail) {
            item { LearningTrail(phases, state.selectedDay, value.completedLessons, state.busy, vm::selectDay, vm::begin,
                resumingLesson=if (phase == phases[state.selectedDay] && (value.session.cursor > lessonSteps.first().index || value.session.draft.isNotBlank() || answered)) lesson else null) }
            val reviews=value.events.mapNotNull {event -> event.remoteAssessment()?.human_review?.let {event to it}}
            if(reviews.isNotEmpty()) item {Text("Revisões desta sessão",style=MaterialTheme.typography.titleLarge)}
            for((event,review) in reviews) item {
                val result=event.remoteAssessment()!!
                val payload=Json.parseToJsonElement(event.payload).jsonObject
                val reviewedActivity=value.document.activities.firstOrNull {it.id==payload.text("activity_id")}
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    reviewedActivity?.let { Text(it.content.text("label") ?: "Resposta escrita",style=MaterialTheme.typography.titleMedium);Text(it.prompt,style=MaterialTheme.typography.bodyMedium) }
                    payload.text("response")?.let { Text("Sua resposta: $it",style=MaterialTheme.typography.bodyMedium) }
                    WritingReviewPanel(review,result.supported,result.human_feedback_available)
                }
            }
            if(value.events.any {it.kind=="attempt" && it.syncState=="received"}) item {
                JourneyTextButton(onClick=vm::sync,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Atualizar revisões desta sessão")}
            }
        } else if (value.session.completed) {
            item { Text("Percurso concluído.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
            item { Text("Você chegou ao fim desta prática de texto.") }
            item { JourneyButton("Ver meu percurso", vm::showTrail, !state.busy, Modifier.fillMaxWidth()) }
        } else {
            if (questionIds.isNotEmpty()) item {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress={answeredQuestions.toFloat()/questionIds.size}, modifier=Modifier.fillMaxWidth().height(3.dp), trackColor=palette.outlineVariant, gapSize=0.dp, drawStopIndicator={})
                    Text("$answeredQuestions de ${questionIds.size} questões respondidas", style=MaterialTheme.typography.labelMedium, color=palette.onSurfaceVariant)
                }
            }
            if (activity.type != "summary") {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(when(activity.type) { "written_recall","communicative_task" -> "SUA VEZ DE ESCREVER"; "information_question" -> "ENCONTRE O SENTIDO"; else -> "LEIA E DESCUBRA" }, style=MaterialTheme.typography.labelSmall, color=palette.onPrimaryContainer)
                    }
                }
                item { Text(activity.prompt, style=MaterialTheme.typography.headlineSmall, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth().semantics { heading() }) }
            }
            if (activity.type !in listOf("information_question", "summary")) {
            activity.content["messages"]?.jsonArray?.let { turns ->
                for ((position, turn) in turns.withIndex()) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = if (position % 2 == 0) Alignment.CenterEnd else Alignment.CenterStart) {
                            Surface(shape = MaterialTheme.shapes.medium, color = if (position % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.widthIn(max = 285.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(turn.jsonObject.text("speaker") ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    GlossaryText(turn.jsonObject.text("text") ?: "", meanings, lookup, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
            activity.content["vocabulary"]?.jsonArray?.let { words ->
                for (word in words) item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        GlossaryText(word.jsonObject.text("word") ?: "", meanings, lookup, style = MaterialTheme.typography.titleLarge)
                        Text(word.jsonObject.text("meaning") ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            activity.content.text("study_text")?.let { text -> item { Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) { GlossaryText(text, meanings, lookup, Modifier.padding(20.dp), MaterialTheme.typography.titleMedium) } } }
            activity.content.text("pt_BR_note")?.let { note -> item { Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            for (example in activity.content.strings("examples")) item {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { GlossaryText(example, meanings, lookup, style = MaterialTheme.typography.titleMedium); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            }
            }
            for (support in activity.supports.filter { !it.id.startsWith("gloss-") && it.kind != "solution" }) item {
                if (support.id in state.revealed) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) { Text(support.content, Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium) }
                else JourneyTextButton(onClick = { vm.support(support.id) }, enabled = !state.busy) { Text("Consultar apoio") }
            }
            if (activity.type == "information_question" && !answered) {
                item {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for ((optionIndex,option) in activity.content.strings("options").withIndex()) {
                            val chosen = selected == option
                            Surface(shape = MaterialTheme.shapes.small, color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                Row(Modifier.fillMaxWidth().selectable(selected = chosen, enabled = !state.busy, role = Role.RadioButton, onClick = { if(selected != option) sounds?.play(InteractionCue.Tap); selected = option; vm.saveDraft(option) }).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    RadioButton(chosen, onClick = null)
                                    Text(('A'.code+optionIndex).toChar().toString(),style=MaterialTheme.typography.labelSmall,color=palette.onSurfaceVariant)
                                    if (activity.content.text("options_language") == "en") GlossaryText(option, meanings, lookup, style = MaterialTheme.typography.bodyLarge)
                                    else Text(option, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
                item { JourneyButton("Conferir", { selected?.let { vm.answer(it) } }, !state.busy && selected != null, Modifier.fillMaxWidth()) }
            }
            if (activity.type in listOf("written_recall", "communicative_task") && !answered) {
                item { OutlinedTextField(answer, { if (it.length <= 10000) { answer=it; vm.saveDraft(it) } }, label={Text("Escreva em inglês")}, placeholder={Text("Sua resposta começa aqui…")}, modifier=Modifier.fillMaxWidth(), minLines=4, supportingText={if(answer.isNotBlank()) Text("Rascunho · ainda não enviado")}, keyboardOptions=KeyboardOptions(autoCorrectEnabled=false), textStyle=MaterialTheme.typography.bodyLarge) }
                item { JourneyButton("Conferir resposta", { vm.answer(answer) }, !state.busy && answer.isNotBlank(), Modifier.fillMaxWidth()) }
            }
            if (answered) {
                val latest = value.events.lastOrNull { it.kind == "attempt" && Json.parseToJsonElement(it.payload).jsonObject.text("activity_id") == activity.id }
                val data = latest?.payload?.let { Json.parseToJsonElement(it).jsonObject }
                val canonical=latest?.remoteAssessment()
                val humanReview=canonical?.human_review
                val technicalIssue = data?.text("technical_status") != "valid"
                val comparison = if (technicalIssue) TextComparison("technical_issue", "Falha técnica registrada. Nenhuma conclusão linguística foi feita.") else compareAnswer(activity, value.session.draft)
                val feedbackColor=when(comparison.outcome) { "correct" -> palette.tertiaryContainer; "incorrect" -> palette.secondaryContainer; else -> palette.primaryContainer }
                val feedbackInk=when(comparison.outcome) { "correct" -> palette.onTertiaryContainer; "incorrect" -> palette.onSecondaryContainer; else -> palette.onPrimaryContainer }
                item {
                    Surface(color = feedbackColor, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp,feedbackInk.copy(alpha=.25f))) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text(when(comparison.outcome) { "correct" -> "✓"; "incorrect" -> "↺"; else -> "…" },color=feedbackInk,style=MaterialTheme.typography.titleLarge)
                                Text(when (comparison.outcome) { "correct" -> "Resposta aceita"; "incorrect" -> "Vamos ajustar"; else -> "Resposta registrada" }, color=feedbackInk,fontWeight = FontWeight.SemiBold)
                            }
                            Text(comparison.message)
                            Text("Sua resposta", style = MaterialTheme.typography.labelSmall)
                            GlossaryText(value.session.draft, meanings, lookup)
                            comparison.example?.let { example ->
                                Text("Um exemplo possível", style = MaterialTheme.typography.labelSmall)
                                GlossaryText(example, meanings, lookup, style = MaterialTheme.typography.titleMedium)
                            }
                            if (data?.strings("support_event_ids")?.isNotEmpty() == true) Text("O apoio consultado ficou registrado.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { JourneyButton("Continuar", { vm.next() }, !state.busy, Modifier.fillMaxWidth()) }
                item { JourneyTextButton(onClick = { vm.next(true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Tentar de novo") } }
                if(activity.modality=="writing" && activity.type in setOf("written_recall","communicative_task") && !technicalIssue) {
                    if(humanReview!=null) item { WritingReviewPanel(humanReview,canonical?.supported==true,canonical?.human_feedback_available==true) }
                    if(latest.syncState!="received") item { Text("Para pedir uma revisão humana, conecte-se e sincronize os registros primeiro. Você pode continuar praticando.",style=MaterialTheme.typography.bodySmall) }
                    if(humanReview==null) item {
                        JourneyTextButton(onClick={vm.requestWritingReview(latest.id)},enabled=!state.busy && latest.syncState=="received",modifier=Modifier.fillMaxWidth()) {Text("Pedir revisão humana")}
                        Text("O pedido precisa de internet. A revisão pode ficar pendente; não há prazo de retorno confirmado.",style=MaterialTheme.typography.bodySmall,color=palette.onSurfaceVariant)
                    } else item { JourneyTextButton(onClick=vm::sync,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Atualizar revisão") } }
                    if(latest.syncState!="received") item { JourneyTextButton(onClick=vm::sync,enabled=!state.busy) {Text("Sincronizar registros")} }
                }
            }
            if (activity.type in listOf("purpose", "dialogue", "explanation")) item { JourneyButton("Continuar", vm::passive, !state.busy, Modifier.fillMaxWidth()) }
            if (activity.type == "summary") {
                item { Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { Surface(modifier=Modifier.size(66.dp).rotate(-8f),color=palette.secondaryContainer,shape=RoundedCornerShape(33.dp,33.dp,14.dp,33.dp)) { Box(contentAlignment=Alignment.Center) { Text("✦",color=palette.secondary,style=MaterialTheme.typography.headlineMedium) } } } }
                item { Text("Lição praticada!", style=MaterialTheme.typography.headlineMedium, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth().semantics { heading() }) }
                item { Text(activity.content.text("label") ?: "Leitura e escrita", style=MaterialTheme.typography.titleMedium, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth()) }
                item { Text("Você pode voltar e praticar de novo quando quiser.", style=MaterialTheme.typography.bodyMedium, color=palette.onSurfaceVariant, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth()) }
                item { JourneyButton("Concluir lição", { vm.next() }, !state.busy, Modifier.fillMaxWidth()) }
            }
        }
        if (!state.trail && pending > 0) item {
            if (pending > 0) Text("Sua prática está salva neste aparelho. $pending registros aguardam envio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            JourneyTextButton(onClick = vm::sync, enabled = !state.busy) { Text("Sincronizar") }
        }
        if (state.busy && !state.trail) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (blocked.isNotEmpty()) item { SyncIssues(blocked, state.busy, vm::retryBlocked) }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
private fun WritingReviewPanel(review:WritingReview,supported:Boolean,feedbackAvailable:Boolean) {
    val palette=MaterialTheme.colorScheme
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(review.status=="reviewed") "Revisão humana da escrita" else "Revisão humana pendente",style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics {heading()})
            if(review.status=="reviewed") {
                review.feedback?.let {Text(it)}
                Text("Revisão ${review.revision} · ${review.reviewed_at?.let { runCatching {java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT))}.getOrDefault(it) } ?: ""}",style=MaterialTheme.typography.bodySmall,color=palette.onSurfaceVariant)
                Text("Critérios da atividade · versão ${review.rubric_version}",style=MaterialTheme.typography.bodySmall,color=palette.onSurfaceVariant)
                for(dimension in review.dimensions) {
                    Text("${when(dimension.id) {"meaning"->"Sentido da mensagem";"form"->"Forma escrita";"reading"->"Leitura";else->dimension.id}}: ${when(dimension.result) {"met"->"critério observado";"not_met"->"precisa de ajuste";"not_observed"->"não observado";else->"sem conclusão"}}",style=MaterialTheme.typography.labelLarge)
                    Text(dimension.reason,style=MaterialTheme.typography.bodySmall)
                }
            } else Text("O pedido foi registrado. Atualize quando houver conexão para consultar o retorno; você pode continuar praticando.")
            if(supported) Text("O servidor registrou contexto de apoio para esta tentativa.",style=MaterialTheme.typography.bodySmall)
            if(feedbackAvailable) Text("Um retorno humano anterior foi disponibilizado; isso não afirma que ele foi consultado.",style=MaterialTheme.typography.bodySmall)
            Text("Esta revisão descreve a escrita desta tentativa. Não confirma domínio, retenção ou outras modalidades.",style=MaterialTheme.typography.bodySmall,color=palette.onSurfaceVariant)
        }
    }
}

@Composable
fun SyncIssues(events: List<PracticeEvent>, busy: Boolean, retry: () -> Unit) {
    OutlinedCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${events.size} registros não foram aceitos", fontWeight = FontWeight.SemiBold)
            Text("Sua prática permanece neste aparelho. Esses registros ainda não foram confirmados pelo servidor.")
            for (reason in events.map { it.issue ?: "O servidor não confirmou este registro." }.distinct()) Text(reason)
            JourneyTextButton(onClick = retry, enabled = !busy) { Text("Tentar enviar novamente") }
        }
    }
}
