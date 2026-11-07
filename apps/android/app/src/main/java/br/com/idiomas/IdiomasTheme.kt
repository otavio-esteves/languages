package br.com.idiomas

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.content.edit

private val LocalDarkTheme = compositionLocalOf { false }
private val LocalToggleTheme = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun IdiomasTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", 0) }
    var choice by remember { mutableStateOf(if (prefs.contains("dark")) prefs.getBoolean("dark", false) else null) }
    val dark = choice ?: isSystemInDarkTheme()
    val view = LocalView.current
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = if (dark) darkColorScheme(
        primary=Color(0xFF89E219), onPrimary=Color(0xFF173600), primaryContainer=Color(0xFF233D24), onPrimaryContainer=Color(0xFFA2EF45),
        secondary=Color(0xFF49C0FF), secondaryContainer=Color(0xFF173B50), onSecondaryContainer=Color(0xFFE1F4FF),
        background=Color(0xFF131F24), onBackground=Color(0xFFF1F7FA), surface=Color(0xFF18282F), onSurface=Color(0xFFF1F7FA),
        surfaceContainerHigh=Color(0xFF18282F), surfaceContainer=Color(0xFF18282F), surfaceVariant=Color(0xFF1D3039), onSurfaceVariant=Color(0xFFA8BAC3), outline=Color(0xFF718995), outlineVariant=Color(0xFF374B56),
    ) else lightColorScheme(
        primary=Color(0xFF58CC02), onPrimary=Color(0xFF173600), primaryContainer=Color(0xFFEFFFDC), onPrimaryContainer=Color(0xFF327400),
        secondary=Color(0xFF1CB0F6), secondaryContainer=Color(0xFFE5F6FF), onSecondaryContainer=Color(0xFF14516E),
        background=Color.White, onBackground=Color(0xFF3C3C3C), surface=Color.White, onSurface=Color(0xFF3C3C3C),
        surfaceContainerHigh=Color.White, surfaceContainer=Color.White, surfaceVariant=Color(0xFFF7F9FA), onSurfaceVariant=Color(0xFF777777), outline=Color(0xFF999999), outlineVariant=Color(0xFFE5E5E5),
    )
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalToggleTheme provides {
        choice = !dark; prefs.edit { putBoolean("dark", !dark) }
    }) {
        MaterialTheme(colorScheme=scheme, shapes=Shapes(small=RoundedCornerShape(14.dp), medium=RoundedCornerShape(20.dp), large=RoundedCornerShape(24.dp)), content=content)
    }
}

@Composable
fun ThemeToggle() {
    val dark = LocalDarkTheme.current
    OutlinedButton(onClick=LocalToggleTheme.current, modifier=Modifier.size(48.dp).semantics { contentDescription=if(dark) "Ativar tema claro" else "Ativar tema escuro" }, contentPadding=PaddingValues(0.dp), shape=RoundedCornerShape(14.dp), border=BorderStroke(2.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Text(if(dark) "☀" else "☾", color=MaterialTheme.colorScheme.onSurfaceVariant, style=MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun BrandMark() {
    Text("idiomas.", color=MaterialTheme.colorScheme.onPrimaryContainer, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.ExtraBold)
}

@Composable
fun JourneyButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Button(onClick=onClick, enabled=enabled, modifier=modifier.heightIn(min=54.dp), shape=RoundedCornerShape(16.dp), border=BorderStroke(2.dp,if(enabled) Color(0xFF46A302) else MaterialTheme.colorScheme.outlineVariant), contentPadding=PaddingValues(horizontal=24.dp,vertical=14.dp)) {
        Text(text,fontWeight=FontWeight.ExtraBold)
    }
}

@Composable
fun SessionProgress(completed: Int, total: Int) {
    val progress by animateFloatAsState(if(total==0) 0f else completed.toFloat()/total,label="Progresso do dia")
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(progress={progress},modifier=Modifier.weight(1f).height(14.dp),trackColor=MaterialTheme.colorScheme.outlineVariant,gapSize=0.dp,drawStopIndicator={})
        Text("$completed / $total",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun JourneyTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    TextButton(onClick=onClick, modifier=modifier, enabled=enabled, colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onPrimaryContainer), content=content)
}
