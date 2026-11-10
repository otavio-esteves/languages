package br.com.idiomas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IdiomasTheme {
                val app = application as IdiomasApplication
                val vm: CatalogViewModel = viewModel(factory = CatalogViewModel.factory(app.repository))
                val practice: PracticeViewModel = viewModel(factory = PracticeViewModel.factory(app.practice, app.repository))
                CatalogScreen(vm, practice)
            }
        }
    }
}

@Composable
private fun MissionCard(title: String, description: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            JourneyButton(action, onClick, enabled, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun CatalogScreen(vm: CatalogViewModel, practice: PracticeViewModel) {
    val sounds = LocalUiSounds.current
    LaunchedEffect(practice, sounds) { practice.cues.collect { sounds?.play(it) } }
    val practiceState by practice.screen.collectAsStateWithLifecycle()
    val installed by practice.installed.collectAsStateWithLifecycle()
    val savedPackages by practice.savedPackages.collectAsStateWithLifecycle()
    val pending by practice.pending.collectAsStateWithLifecycle()
    val blockedEvents by practice.blocked.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val blocked = blockedEvents.filter { it.ownerId == session?.account?.id }
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    var confirmClear by remember(session?.account?.id) { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    LaunchedEffect(session?.account?.id) { if(session != null) { password=""; showPassword=false } }

    if (confirmClear && session != null) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar prática deste aparelho?") },
            text = { Text("Serão removidos downloads, gravações e respostas locais desta conta. ${pending + blocked.size} registros ainda não confirmados serão descartados. Registros já enviados permanecem no servidor.") },
            confirmButton = { JourneyTextButton(onClick = { confirmClear = false; practice.clearLocal() }) { Text("Apagar dados locais") } },
            dismissButton = { JourneyTextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
    Scaffold { padding ->
        if (practiceState.open != null && session != null && practiceState.open?.session?.ownerId == session?.account?.id) {
            Box(Modifier.padding(padding)) { PracticeScreen(practice, practiceState, pending, blocked) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { BrandMark(); AppearanceControls() }
                        if (session != null) JourneyTextButton(onClick = vm::logout, enabled = !screen.busy, modifier=Modifier.align(Alignment.End)) { Text("Sair") }
                    }
                }
                if (session == null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("LEITURA E ESCRITA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Seu inglês, página por página.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                            Text("Leia. Escreva. Descubra o que você consegue dizer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                Text("Seu caderno espera.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Entre para acessar suas missões.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = if(showPassword) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon={TextButton(onClick={showPassword=!showPassword}) {Text(if(showPassword) "Ocultar" else "Mostrar")}}, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
                                JourneyButton(
                                    if (screen.busy) "Entrando…" else "Entrar",
                                    onClick = { vm.login(email, password) },
                                    enabled = !screen.busy && email.isNotBlank() && password.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    item { Text("Aprender para usar. No seu ritmo, com um objetivo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Olá, ${session!!.account.name}.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text("Sua trilha de inglês", style = MaterialTheme.typography.bodyLarge)
                            Text("Leitura e escrita · um dia de cada vez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (installed.isNotEmpty()) {
                        item { Text("Missões neste aparelho", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        val savedHashes = savedPackages.filter { it.ownerId == session?.account?.id }.map { it.hash }.toSet()
                        val visiblePackages = installed.filter { it.ownerId == session?.account?.id }.groupBy { it.packageId }.values.flatMap { versions ->
                            val latest = versions.maxBy { it.version }
                            listOf(latest) + versions.filter { it.hash != latest.hash && it.hash in savedHashes }.sortedByDescending { it.version }
                        }
                        items(visiblePackages, key = { "installed:${it.hash}" }) { mission ->
                            val previous = installed.any { it.packageId == mission.packageId && it.version > mission.version }
                            MissionCard(
                                title = mission.title,
                                description = if (previous) "Prática salva. Seu percurso e suas respostas continuam disponíveis." + if (mission.preview) " Rascunho para revisão." else "" else if (mission.preview) "Rascunho para revisão. A sessão não confirma capacidades." else "Disponível neste aparelho, mesmo sem internet.",
                                action = if (previous) "Retomar prática salva" else "Abrir trilha",
                                enabled = !practiceState.busy,
                                onClick = { practice.open(mission) },
                            )
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Missões disponíveis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            JourneyTextButton(onClick = vm::refresh, enabled = !screen.busy) { Text("Atualizar") }
                        }
                    }
                    if (catalog.isEmpty() && !screen.busy) {
                        item {
                            OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("EM PREPARAÇÃO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("As primeiras conversas vêm aí.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Ainda não há missões publicadas. Os materiais estão em revisão para você praticar com confiança.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(catalog, key = { "${it.id}:${it.version}" }) { mission ->
                        MissionCard(mission.title, "Leitura e escrita", "Baixar trilha", !practiceState.busy, { practice.download(mission, false) })
                    }
                    if (session!!.account.is_editor) {
                        item {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(20.dp))
                            Text("PRÉVIA EDITORIAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer), onClick = practice::previews, enabled = !practiceState.busy, modifier = Modifier.fillMaxWidth()) { Text("Carregar prévias para revisão") }
                        }
                        items(practiceState.previews, key = { "preview:${it.id}:${it.version}" }) { mission ->
                            MissionCard(mission.title, "Rascunho · sem confirmação de capacidades", "Abrir trilha de texto", !practiceState.busy, { practice.download(mission, true) })
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (pending == 0) "Nenhum registro aguardando envio automático." else "$pending registros aguardam envio. Sua prática está salva neste aparelho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            JourneyTextButton(onClick = { confirmClear = true }, enabled = !practiceState.busy) { Text("Apagar prática deste aparelho") }
                        }
                    }
                    if (blocked.isNotEmpty()) item { SyncIssues(blocked, practiceState.busy, practice::retryBlocked) }
                    practiceState.message?.let { message -> item { Text(message) } }
                    if (practiceState.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                if (screen.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                screen.message?.let { message -> item { Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Text(message, Modifier.padding(18.dp)) } } }
            }
        }
    }
}
