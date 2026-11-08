package br.com.idiomas

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.content.edit
import br.com.idiomas.data.InteractionCue

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
        primary=Color(0xFFB9AAFA), onPrimary=Color(0xFF28203E), primaryContainer=Color(0xFF34304B), onPrimaryContainer=Color(0xFFC7BBFF),
        secondary=Color(0xFFEDB092), onSecondary=Color(0xFF40231B), secondaryContainer=Color(0xFF443029), onSecondaryContainer=Color(0xFFEDB092),
        tertiary=Color(0xFFA1D1BB), onTertiary=Color(0xFF17382D), tertiaryContainer=Color(0xFF263E36), onTertiaryContainer=Color(0xFFA1D1BB),
        background=Color(0xFF191923), onBackground=Color(0xFFF5F0E7), surface=Color(0xFF242430), onSurface=Color(0xFFF5F0E7),
        surfaceContainerHigh=Color(0xFF242430), surfaceContainer=Color(0xFF242430), surfaceVariant=Color(0xFF2D2C3B), onSurfaceVariant=Color(0xFFB8B3C6), outline=Color(0xFF827C94), outlineVariant=Color(0xFF484554),
    ) else lightColorScheme(
        primary=Color(0xFF6553B5), onPrimary=Color.White, primaryContainer=Color(0xFFEEEBFA), onPrimaryContainer=Color(0xFF5946A3),
        secondary=Color(0xFFAC5336), onSecondary=Color.White, secondaryContainer=Color(0xFFFBE9DF), onSecondaryContainer=Color(0xFF8E422B),
        tertiary=Color(0xFF346D60), onTertiary=Color.White, tertiaryContainer=Color(0xFFE4F0E8), onTertiaryContainer=Color(0xFF346D60),
        background=Color(0xFFF5F2EA), onBackground=Color(0xFF29283E), surface=Color(0xFFFFFEF9), onSurface=Color(0xFF29283E),
        surfaceContainerHigh=Color(0xFFFFFEF9), surfaceContainer=Color(0xFFFFFEF9), surfaceVariant=Color(0xFFEEEBE4), onSurfaceVariant=Color(0xFF686578), outline=Color(0xFF85808D), outlineVariant=Color(0xFFDCD7CE),
    )
    val base = Typography()
    val typography = Typography(
        headlineLarge=base.headlineLarge.copy(fontFamily=FontFamily.Serif,fontWeight=FontWeight.Normal,fontSize=38.sp,lineHeight=42.sp,letterSpacing=(-1).sp),
        headlineMedium=base.headlineMedium.copy(fontFamily=FontFamily.Serif,fontWeight=FontWeight.Normal,fontSize=36.sp,lineHeight=40.sp,letterSpacing=(-1).sp),
        headlineSmall=base.headlineSmall.copy(fontWeight=FontWeight.SemiBold,letterSpacing=(-.5).sp),
        labelSmall=base.labelSmall.copy(letterSpacing=.8.sp),
    )
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalToggleTheme provides {
        choice = !dark; prefs.edit { putBoolean("dark", !dark) }
    }) {
        MaterialTheme(colorScheme=scheme, typography=typography, shapes=Shapes(small=RoundedCornerShape(14.dp), medium=RoundedCornerShape(20.dp), large=RoundedCornerShape(24.dp))) { ProvideUiSounds(content) }
    }
}

@Composable
fun ThemeToggle() {
    val dark = LocalDarkTheme.current
    OutlinedButton(onClick=LocalToggleTheme.current, modifier=Modifier.size(44.dp).semantics { contentDescription=if(dark) "Ativar tema claro" else "Ativar tema escuro" }, contentPadding=PaddingValues(0.dp), shape=RoundedCornerShape(14.dp), border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Text(if(dark) "☀" else "☾", color=MaterialTheme.colorScheme.onSurfaceVariant, style=MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun BrandMark() {
    val ink=MaterialTheme.colorScheme.primary
    val page=MaterialTheme.colorScheme.onPrimary
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(24.dp,30.dp)) {
            drawRoundRect(ink.copy(alpha=.18f),topLeft=Offset(0f,2.dp.toPx()),size=Size(19.dp.toPx(),25.dp.toPx()),cornerRadius=CornerRadius(4.dp.toPx()))
            drawRoundRect(ink,topLeft=Offset(4.dp.toPx(),3.dp.toPx()),size=Size(19.dp.toPx(),25.dp.toPx()),cornerRadius=CornerRadius(4.dp.toPx()))
            for((index,width) in listOf(9,9,5).withIndex()) drawLine(page,Offset(9.dp.toPx(),(10+index*5).dp.toPx()),Offset((9+width).dp.toPx(),(10+index*5).dp.toPx()),strokeWidth=1.5.dp.toPx())
        }
        Row { Text("idiomas",color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(".",color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
    }
}

@Composable
fun JourneyButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val sounds = LocalUiSounds.current
    Button(onClick={sounds?.play(InteractionCue.Tap);onClick()}, enabled=enabled, modifier=modifier.heightIn(min=52.dp), shape=RoundedCornerShape(14.dp), elevation=ButtonDefaults.buttonElevation(defaultElevation=1.dp,pressedElevation=0.dp), contentPadding=PaddingValues(horizontal=24.dp,vertical=14.dp)) {
        Text(text,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
fun SessionProgress(completed: Int, total: Int) {
    val progress by animateFloatAsState(if(total==0) 0f else completed.toFloat()/total,label="Progresso do dia")
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(progress={progress},modifier=Modifier.weight(1f).height(7.dp),trackColor=MaterialTheme.colorScheme.outlineVariant,gapSize=0.dp,drawStopIndicator={})
        Text("$completed / $total",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun JourneyTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val sounds=LocalUiSounds.current
    TextButton(onClick={sounds?.play(InteractionCue.Tap);onClick()}, modifier=modifier, enabled=enabled, colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onPrimaryContainer), content=content)
}
