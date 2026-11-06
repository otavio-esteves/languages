package br.com.idiomas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.idiomas.data.TrailPhase
import br.com.idiomas.data.lessons
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue

val WarmPaper = Color(0xFFFFF8E6)

@Composable
fun IdiomasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF17745B), onPrimary = Color.White,
            primaryContainer = Color(0xFFEDF5EF), onPrimaryContainer = Color(0xFF105D48),
            background = Color.White, onBackground = Color(0xFF263C33),
            surface = Color.White, onSurface = Color(0xFF263C33),
            surfaceVariant = Color(0xFFF5F7F3), onSurfaceVariant = Color(0xFF66736B),
            outline = Color(0xFF829689), outlineVariant = Color(0xFFE4E9E4),
        ),
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp)),
        content = content,
    )
}

@Composable
fun BrandMark() {
    Text("idiomas.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
}

@Composable
fun JourneyButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SessionProgress(completed: Int, total: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else completed.toFloat() / total },
            modifier = Modifier.weight(1f).height(8.dp),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            gapSize = 0.dp, drawStopIndicator = {},
        )
        Text("$completed / $total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LearningTrail(phases: List<TrailPhase>, cursor: Int, completed: Boolean, busy: Boolean, onBegin: () -> Unit) {
    val day = phases.indexOfFirst { phase -> phase.steps.any { it.index == cursor } }.coerceAtLeast(0)
    val lessons = phases[day].lessons()
    val done = lessons.count { lesson -> completed || lesson.all { it.index < cursor } }
    val progress by animateFloatAsState(done.toFloat() / lessons.size, label = "Lições concluídas")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(9.dp), trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
        Spacer(Modifier.height(12.dp))
        Text("Dia ${day + 1}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        for ((index, lesson) in lessons.withIndex()) {
            val current = !completed && lesson.any { it.index == cursor }
            val complete = completed || lesson.all { it.index < cursor }
            OutlinedCard(onClick = onBegin, enabled = current && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp), border = BorderStroke(1.dp, if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.outlinedCardColors(containerColor = if (complete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, disabledContainerColor = if (complete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Text("Lição ${index + 1}", Modifier.padding(28.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
