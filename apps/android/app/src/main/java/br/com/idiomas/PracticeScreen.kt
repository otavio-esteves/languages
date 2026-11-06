package br.com.idiomas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.idiomas.data.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Composable
fun PracticeScreen(vm: PracticeViewModel, state: PracticeScreenState, pending: Int) {
    val value = state.open ?: return
    val activity = value.document.activities[value.session.cursor]
    val phases = remember(value.installed.hash) { value.document.phases() }
    val phase = phases.first { it.id == activity.phaseId() }
    var answer by remember(value.session.id, value.session.cursor, value.session.answeredIndex) { mutableStateOf(value.session.draft) }
    var selected by remember(value.session.id, value.session.cursor, value.session.answeredIndex) { mutableStateOf<String?>(null) }
    val answered = value.session.answeredIndex == value.session.cursor
    val meanings = remember(value.installed.hash, activity.id) { activity.wordMeanings() }
    var selectedMeaning by remember(value.session.id, value.session.cursor) { mutableStateOf<Pair<String, WordMeaning>?>(null) }
    val lookup: (String, WordMeaning) -> Unit = { word, meaning ->
        if (!state.busy) {
            selectedMeaning = word to meaning
            if (meaning.supportId !in state.revealed) vm.support(meaning.supportId)
        }
    }
    selectedMeaning?.takeIf { it.second.supportId in state.revealed }?.let { (word, meaning) ->
        AlertDialog(onDismissRequest = { selectedMeaning = null }, title = { Text(word) }, text = { Text(meaning.meaning) }, confirmButton = { TextButton(onClick = { selectedMeaning = null }) { Text("Fechar") } })
    }
    val scroll = rememberLazyListState()
    BackHandler { if (!state.busy) { if (state.trail) vm.exit() else vm.showTrail() } }
    LaunchedEffect(value.session.id, value.session.cursor, state.trail) { scroll.scrollToItem(0) }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        if (!state.trail) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                TextButton(onClick = { if (state.trail) vm.exit() else vm.showTrail() }, enabled = !state.busy) { Text(if (state.trail) "Voltar" else "Trilha") }
            }
        }
        if (!value.document.isTextOnly()) {
            item { Text("Continue na trilha de texto.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            item { Text("Esta versão foi guardada no histórico. Baixe a versão atual na prévia editorial.") }
            item { JourneyButton("Voltar às missões", vm::exit, !state.busy, Modifier.fillMaxWidth()) }
        } else if (state.trail) {
            item { LearningTrail(phases, value.session.cursor, value.session.completed, state.busy, vm::begin) }
        } else if (value.session.completed) {
            item { Text("Percurso concluído.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
            item { Text("Você chegou ao fim desta prática de texto.") }
            item { JourneyButton("Ver meu percurso", vm::showTrail, !state.busy, Modifier.fillMaxWidth()) }
        } else {
            item { SessionProgress(phase.lessonIndex(value.session.cursor).coerceAtLeast(0), phase.lessons().size) }
            item { Text("DIA ${phases.indexOf(phase) + 1} · ${phase.title}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Text(activity.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() }) }
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
            for (support in activity.supports.filter { !it.id.startsWith("gloss-") && it.kind != "solution" }) item {
                if (support.id in state.revealed) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) { Text(support.content, Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium) }
                else TextButton(onClick = { vm.support(support.id) }, enabled = !state.busy) { Text("Consultar apoio") }
            }
            if (activity.type == "information_question" && !answered) {
                item {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (option in activity.content.strings("options")) {
                            val chosen = selected == option
                            Surface(shape = MaterialTheme.shapes.small, color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, border = BorderStroke(2.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                Row(Modifier.fillMaxWidth().selectable(selected = chosen, enabled = !state.busy, role = Role.RadioButton, onClick = { selected = option }).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    RadioButton(chosen, onClick = null)
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
                item { OutlinedTextField(answer, { answer = it; vm.saveDraft(it) }, label = { Text("Sua mensagem") }, placeholder = { Text("Escreva em inglês…") }, modifier = Modifier.fillMaxWidth(), minLines = 5) }
                item { JourneyButton("Registrar resposta", { vm.answer(answer) }, !state.busy && answer.isNotBlank(), Modifier.fillMaxWidth()) }
            }
            if (answered) {
                val latest = value.events.lastOrNull { it.kind == "attempt" && Json.parseToJsonElement(it.payload).jsonObject.text("activity_id") == activity.id }
                val data = latest?.payload?.let { Json.parseToJsonElement(it).jsonObject }
                val technicalIssue = data?.text("technical_status") != "valid"
                val comparison = if (technicalIssue) TextComparison("technical_issue", "Falha técnica registrada. Nenhuma conclusão linguística foi feita.") else compareAnswer(activity, value.session.draft)
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(when (comparison.outcome) { "correct" -> "Resposta aceita"; "incorrect" -> "Vamos ajustar"; else -> "Vamos conferir" }, fontWeight = FontWeight.SemiBold)
                            Text(comparison.message)
                            Text("Você escreveu:", style = MaterialTheme.typography.labelSmall)
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
                item { TextButton(onClick = { vm.next(true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Tentar de novo") } }
            }
            if (activity.type in listOf("purpose", "dialogue", "explanation")) item { JourneyButton("Continuar", vm::passive, !state.busy, Modifier.fillMaxWidth()) }
            if (activity.type == "summary") {
                item { Text("Você praticou palavras, pedidos curtos e frases com contrações. As respostas previstas receberam correção automática de treino. Formas não reconhecidas continuam sem confirmação independente.") }
                item { Text("Esta sessão não verifica escuta, fala ou retenção posterior.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { JourneyButton("Concluir prática", { vm.next() }, !state.busy, Modifier.fillMaxWidth()) }
            }
        }
        if (!state.trail) item {
            if (pending > 0) Text("$pending registros aguardam envio. Sua prática está salva neste aparelho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = vm::sync, enabled = !state.busy) { Text("Sincronizar") }
        }
        if (state.busy && !state.trail) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}
