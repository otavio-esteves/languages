package br.com.idiomas

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.edit
import br.com.idiomas.data.InteractionCue
import java.util.concurrent.ConcurrentHashMap

internal class UiSounds(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    var enabled by mutableStateOf(prefs.getBoolean("sound-effects", true))
        private set
    private val manager = runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()
    private val pool = runCatching {
        SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    }.getOrNull()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val sounds = mutableMapOf<InteractionCue, Int>()
    private var foreground = false
    private var stream = 0
    init {
        pool?.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        for ((cue, resource) in mapOf(InteractionCue.Tap to R.raw.ui_tap, InteractionCue.Correct to R.raw.ui_correct,
            InteractionCue.Adjust to R.raw.ui_adjust, InteractionCue.Recorded to R.raw.ui_recorded, InteractionCue.Complete to R.raw.ui_complete)) {
            runCatching { pool?.load(context, resource, 1)?.let { sounds[cue] = it } }
        }
    }
    fun play(cue: InteractionCue) {
        if (!enabled || !foreground) return
        val id = sounds[cue]?.takeIf { it in loaded } ?: return
        runCatching {
            if (manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return@runCatching
            pool?.stop(stream); stream = pool?.play(id, .55f, .55f, 1, 0, 1f) ?: 0
        }
    }
    fun toggle() {
        enabled = !enabled
        prefs.edit { putBoolean("sound-effects", enabled) }
        if (enabled) play(InteractionCue.Tap) else runCatching { pool?.autoPause() }
    }
    fun foreground(active: Boolean) {
        foreground = active
        if (!active) runCatching { pool?.autoPause() }
    }
    fun release() { runCatching { pool?.release() } }
}

internal val LocalUiSounds = staticCompositionLocalOf<UiSounds?> { null }

@Composable
internal fun ProvideUiSounds(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val sounds = remember(context) { UiSounds(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, sounds) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) sounds.foreground(true)
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) sounds.foreground(false)
        }
        sounds.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); sounds.release() }
    }
    CompositionLocalProvider(LocalUiSounds provides sounds, content = content)
}

@Composable
fun SoundToggle() {
    val sounds = LocalUiSounds.current ?: return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedButton(onClick = sounds::toggle, modifier = Modifier.size(44.dp).semantics {
        contentDescription = if (sounds.enabled) "Silenciar efeitos sonoros" else "Ativar efeitos sonoros"
        stateDescription = if (sounds.enabled) "Ligados" else "Desligados"
    }, contentPadding = PaddingValues(10.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Canvas(Modifier.size(22.dp)) {
            val x = size.width / 24; val y = size.height / 24
            val speaker = Path().apply { moveTo(11*x,5*y); lineTo(6*x,9*y); lineTo(3*x,9*y); lineTo(3*x,15*y); lineTo(6*x,15*y); lineTo(11*x,19*y); close() }
            drawPath(speaker, color, style = Stroke(1.8f*x))
            if (sounds.enabled) {
                drawLine(color, Offset(16*x,8*y), Offset(18*x,12*y), 1.8f*x)
                drawLine(color, Offset(18*x,12*y), Offset(16*x,16*y), 1.8f*x)
            } else {
                drawLine(color, Offset(16*x,9*y), Offset(21*x,15*y), 1.8f*x)
                drawLine(color, Offset(21*x,9*y), Offset(16*x,15*y), 1.8f*x)
            }
        }
    }
}

@Composable
fun AppearanceControls() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ThemeToggle(); SoundToggle() }
}
