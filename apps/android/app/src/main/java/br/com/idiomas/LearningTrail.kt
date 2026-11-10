package br.com.idiomas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.idiomas.data.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
private fun LevelBadge(level: String?) {
    Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(9.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.semantics { contentDescription="Conteúdo ${level ?: "inicial"}; referência editorial" }) {
        Text(level ?: "Inicial",Modifier.padding(horizontal=10.dp,vertical=5.dp),color=MaterialTheme.colorScheme.onPrimaryContainer,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.ExtraBold)
    }
}

@Composable
fun LearningTrail(phases: List<TrailPhase>, day: Int, completed: Set<String>, busy: Boolean, onDay: (Int) -> Unit, onBegin: (Int) -> Unit, resumingLesson: Int? = null) {
    var picker by remember { mutableStateOf(false) }
    val phase=phases[day]
    val lessons=phase.lessons()
    val count=phase.completedCount(completed)
    if(picker) DayPicker(phases,day,{picker=false}) { onDay(it);picker=false }
    val palette = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) { BrandMark(); AppearanceControls() }
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            OutlinedButton(onClick={onDay(day-1)}, enabled=!busy && day>0, modifier=Modifier.size(46.dp).semantics { contentDescription="Dia anterior" }, contentPadding=PaddingValues(0.dp), shape=RoundedCornerShape(24.dp), border=BorderStroke(1.dp,palette.outlineVariant)) { Text("‹", style=MaterialTheme.typography.headlineSmall) }
            JourneyTextButton(onClick={picker=true}, enabled=!busy, modifier=Modifier.weight(1f).semantics { contentDescription="Dia ${day+1}, selecionar dia" }) {
                Text("Dia ${day+1} ⌄", style=MaterialTheme.typography.headlineSmall, color=palette.onSurface)
                Spacer(Modifier.width(10.dp)); LevelBadge(phase.level)
            }
            OutlinedButton(onClick={onDay(day+1)}, enabled=!busy && day<phases.lastIndex, modifier=Modifier.size(46.dp).semantics { contentDescription="Próximo dia" }, contentPadding=PaddingValues(0.dp), shape=RoundedCornerShape(24.dp), border=BorderStroke(1.dp,palette.outlineVariant)) { Text("›", style=MaterialTheme.typography.headlineSmall) }
        }
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(phase.title, style=MaterialTheme.typography.headlineMedium, color=palette.onSurface)
            if (phase.description.isNotBlank()) Text(phase.description, style=MaterialTheme.typography.bodySmall, color=palette.onSurfaceVariant)
            phase.estimatedMinutes?.let { Text("${it.first}–${it.last} min · no seu ritmo", style=MaterialTheme.typography.labelMedium, color=palette.onSurfaceVariant) }
        }
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("$count de ${lessons.size} lições concluídas", style=MaterialTheme.typography.labelMedium, color=palette.onSurfaceVariant)
            LinearProgressIndicator(progress={if(lessons.isEmpty()) 0f else count.toFloat()/lessons.size}, modifier=Modifier.fillMaxWidth().height(3.dp), trackColor=palette.outlineVariant, gapSize=0.dp, drawStopIndicator={})
        }
        for ((index,lesson) in lessons.withIndex()) {
            val done=phase.lessonKey(index) in completed
            val unlocked=index==0 || phase.lessonKey(index-1) in completed || done
            val active=unlocked && !done
            val color=if(active) palette.primaryContainer.copy(alpha=.45f) else if(done) palette.surface else palette.background
            val ink=if(done) palette.tertiary else if(active) palette.primary else palette.onSurfaceVariant
            OutlinedCard(onClick={onBegin(index)}, enabled=!busy && unlocked, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(18.dp), border=BorderStroke(1.dp,if(active) palette.primary else palette.outlineVariant), colors=CardDefaults.outlinedCardColors(containerColor=color,disabledContainerColor=color)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement=Arrangement.spacedBy(16.dp), verticalAlignment=Alignment.Top) {
                    Box(Modifier.size(36.dp,44.dp).background(if(done) palette.tertiaryContainer else palette.surfaceVariant,RoundedCornerShape(9.dp,9.dp,3.dp,9.dp)), contentAlignment=Alignment.Center) { Text(if(done) "✓" else (index+1).toString().padStart(2,'0'), fontFamily=NotebookDisplay, style=MaterialTheme.typography.titleLarge, color=ink) }
                    Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("Lição ${index+1}", style=MaterialTheme.typography.labelMedium, color=palette.onSurfaceVariant)
                        Text(lesson.first().label, style=if(active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, color=palette.onSurface)
                        if (active) {
                            Surface(color=palette.primary, shape=RoundedCornerShape(10.dp), modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {
                                Row(Modifier.padding(horizontal=14.dp,vertical=12.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(if(resumingLesson==index) "Continuar lição" else "Começar lição", style=MaterialTheme.typography.labelLarge, color=palette.onPrimary)
                                    Text("↗", color=palette.onPrimary)
                                }
                            }
                        } else Text(if(done) "Concluída · praticar novamente" else "Depois da Lição $index", style=MaterialTheme.typography.bodySmall, color=palette.onSurfaceVariant)
                    }
                }
            }
        }
        if(count==lessons.size) Surface(color=palette.tertiaryContainer, shape=MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Mandou bem!", style=MaterialTheme.typography.headlineSmall, color=palette.onTertiaryContainer)
                Text("Dia ${day+1} concluído.", color=palette.onTertiaryContainer)
                if(day<phases.lastIndex) JourneyButton("Ver Dia ${day+2}", {onDay(day+1)}, !busy, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DayPicker(phases: List<TrailPhase>, initial: Int, dismiss: () -> Unit, select: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    fun searchable(text: String) = java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").lowercase(java.util.Locale.ROOT).trim()
    val days=remember(phases,query) { phases.withIndex().filter { (index,phase) ->
        val search=searchable(query)
        search.isBlank() || if(search.all {it.isDigit()}) index+1==search.toIntOrNull() else searchable("${phase.title} ${phase.level ?: ""}").contains(search)
    } }
    val wheel=rememberLazyListState(initialFirstVisibleItemIndex=initial)
    val scope=rememberCoroutineScope()
    LaunchedEffect(query) { wheel.scrollToItem(if(query.isBlank()) initial else 0) }
    val selected by remember(wheel,days) { derivedStateOf {
        val layout=wheel.layoutInfo
        val center=(layout.viewportStartOffset+layout.viewportEndOffset)/2
        val position=layout.visibleItemsInfo.minByOrNull {abs(it.offset+it.size/2-center)}?.index ?: 0
        days.getOrNull(position)?.index
    } }
    AlertDialog(onDismissRequest=dismiss,title={Text("Escolha um dia",fontFamily=NotebookDisplay)},text={
        Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(query,{query=it},label={Text("Dia ou assunto")},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(days.isEmpty()) Text("Nenhum dia encontrado. Tente outro assunto ou número.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(state=wheel,flingBehavior=rememberSnapFlingBehavior(wheel),modifier=Modifier.fillMaxWidth().height(216.dp),contentPadding=PaddingValues(vertical=72.dp)) {
                itemsIndexed(days,key={_,day->day.value.id}) { index,day ->
                    val phase=day.value
                    Row(Modifier.fillMaxWidth().heightIn(min=72.dp).background(if(day.index==selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,RoundedCornerShape(12.dp)).selectable(selected=day.index==selected,onClick={scope.launch {wheel.scrollToItem(index)}}).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("Dia ${day.index+1}",style=MaterialTheme.typography.titleMedium)
                            Text(phase.title,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LevelBadge(phase.level)
                    }
                }
            }
        }
    },confirmButton={JourneyTextButton(onClick={selected?.let(select)},enabled=selected!=null) {Text(selected?.let { "Ir para o Dia ${it+1}" } ?: "Nenhum dia encontrado")}},dismissButton={JourneyTextButton(onClick=dismiss) {Text("Cancelar")}})
}
