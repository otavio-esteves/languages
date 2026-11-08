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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.idiomas.data.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
private fun LevelBadge(level: String?) {
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(10.dp),border=BorderStroke(2.dp,MaterialTheme.colorScheme.secondary)) {
        Text(level ?: "Inicial",Modifier.padding(horizontal=10.dp,vertical=5.dp),color=MaterialTheme.colorScheme.onSecondaryContainer,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.ExtraBold)
    }
}

@Composable
fun LearningTrail(phases: List<TrailPhase>, day: Int, completed: Set<String>, busy: Boolean, onDay: (Int) -> Unit, onBegin: (Int) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    val phase=phases[day]
    val lessons=phase.lessons()
    val count=phase.completedCount(completed)
    if(picker) DayPicker(phases,day,{picker=false}) { onDay(it);picker=false }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { BrandMark();ThemeToggle() }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,thickness=2.dp)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={onDay(day-1)},enabled=!busy && day>0,modifier=Modifier.size(48.dp).semantics { contentDescription="Dia anterior" },contentPadding=PaddingValues(0.dp),border=BorderStroke(2.dp,MaterialTheme.colorScheme.outlineVariant)) { Text("‹",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.secondary) }
            JourneyTextButton(onClick={picker=true},enabled=!busy,modifier=Modifier.weight(1f).semantics { contentDescription="Dia ${day+1}, selecionar dia" }) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("Dia ${day+1} ⌄",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.onSurface)
                    Text(phase.title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick={onDay(day+1)},enabled=!busy && day<phases.lastIndex,modifier=Modifier.size(48.dp).semantics { contentDescription="Próximo dia" },contentPadding=PaddingValues(0.dp),border=BorderStroke(2.dp,MaterialTheme.colorScheme.outlineVariant)) { Text("›",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.secondary) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            LevelBadge(phase.level)
            Text("$count de ${lessons.size} lições concluídas",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SessionProgress(count,lessons.size)
        phase.estimatedMinutes?.let { minutes ->
            Text("Cerca de ${minutes.first}–${minutes.last} min · no seu ritmo",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for((index,lesson) in lessons.withIndex()) {
            val done=phase.lessonKey(index) in completed
            val available=!busy && (index==0 || phase.lessonKey(index-1) in completed || done)
            val color=if(done) MaterialTheme.colorScheme.primaryContainer else if(available) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
            val accent=if(done) MaterialTheme.colorScheme.primary else if(available) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
            OutlinedCard(onClick={onBegin(index)},enabled=available,modifier=Modifier.fillMaxWidth().heightIn(min=118.dp),shape=RoundedCornerShape(20.dp),border=BorderStroke(2.dp,accent),colors=CardDefaults.outlinedCardColors(containerColor=color,disabledContainerColor=color)) {
                Row(Modifier.fillMaxWidth().padding(22.dp),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(accent,RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) { Text(if(done) "✓" else if(available) "★" else "•",style=MaterialTheme.typography.headlineMedium,color=if(done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary) }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("Lição ${index+1}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold)
                        Text(if(done) "Praticar novamente" else lesson.first().label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(count==lessons.size) Text("Dia concluído. Continue no seu ritmo!",color=MaterialTheme.colorScheme.onPrimaryContainer,fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun DayPicker(phases: List<TrailPhase>, initial: Int, dismiss: () -> Unit, select: (Int) -> Unit) {
    val wheel=rememberLazyListState(initialFirstVisibleItemIndex=initial)
    val scope=rememberCoroutineScope()
    val selected by remember { derivedStateOf {
        val layout=wheel.layoutInfo
        val center=(layout.viewportStartOffset+layout.viewportEndOffset)/2
        layout.visibleItemsInfo.minByOrNull { abs(it.offset+it.size/2-center) }?.index ?: initial
    } }
    AlertDialog(onDismissRequest=dismiss,title={Text("Escolha um dia",fontWeight=FontWeight.ExtraBold)},text={
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("${phases[selected].title} · conteúdo ${phases[selected].level ?: "inicial"}",color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(state=wheel,flingBehavior=rememberSnapFlingBehavior(wheel),modifier=Modifier.fillMaxWidth().height(216.dp),contentPadding=PaddingValues(vertical=72.dp)) {
                itemsIndexed(phases,key={_,phase->phase.id}) { index,phase ->
                    Row(Modifier.fillMaxWidth().height(72.dp).background(if(index==selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,RoundedCornerShape(14.dp)).selectable(selected=index==selected,onClick={scope.launch {wheel.animateScrollToItem(index)}}).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Dia ${index+1}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        LevelBadge(phase.level)
                    }
                }
            }
        }
    },confirmButton={JourneyTextButton(onClick={select(selected)}) { Text("Ir para o Dia ${selected+1}") }},dismissButton={JourneyTextButton(onClick=dismiss) { Text("Cancelar") }})
}
