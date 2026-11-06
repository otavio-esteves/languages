package br.com.idiomas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        JourneyButton(action, onClick, enabled, Modifier.fillMaxWidth())
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun CatalogScreen(vm: CatalogViewModel, practice: PracticeViewModel) {
    val practiceState by practice.screen.collectAsStateWithLifecycle()
    val installed by practice.installed.collectAsStateWithLifecycle()
    val pending by practice.pending.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    var confirmClear by remember(session?.account?.id) { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    if (confirmClear && session != null) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar prática deste aparelho?") },
            text = { Text("Serão removidos downloads, gravações e respostas locais desta conta. $pending registros aguardam envio e serão descartados. Registros já enviados permanecem no servidor.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; practice.clearLocal() }) { Text("Apagar dados locais") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
    Scaffold { padding ->
        if (practiceState.open != null && session != null && practiceState.open?.session?.ownerId == session?.account?.id) {
            Box(Modifier.padding(padding)) { PracticeScreen(practice, practiceState, pending) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        BrandMark()
                        if (session != null) TextButton(onClick = vm::logout, enabled = !screen.busy) { Text("Sair") }
                    }
                }
                if (session == null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("LEITURA E ESCRITA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Um passo de cada vez.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                            Text("Aprenda inglês com uma trilha de leitura e escrita.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        OutlinedCard(border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                Text("Vamos começar?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Entre para acessar suas missões.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
                                JourneyButton(
                                    if (screen.busy) "Entrando…" else "Entrar",
                                    onClick = { val secret = password; password = ""; vm.login(email, secret) },
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
                        items(installed.groupBy { it.packageId }.values.map { versions -> versions.maxBy { it.version } }, key = { "installed:${it.hash}" }) { mission ->
                            MissionCard(
                                title = mission.title,
                                description = if (mission.preview) "Rascunho para revisão. A sessão não confirma capacidades." else "Disponível neste aparelho, mesmo sem internet.",
                                action = "Abrir trilha",
                                enabled = !practiceState.busy,
                                onClick = { practice.open(mission) },
                            )
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Missões disponíveis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::refresh, enabled = !screen.busy) { Text("Atualizar") }
                        }
                    }
                    if (catalog.isEmpty() && !screen.busy) {
                        item {
                            OutlinedCard(border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)) {
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
                            OutlinedButton(onClick = practice::previews, enabled = !practiceState.busy, modifier = Modifier.fillMaxWidth()) { Text("Carregar prévias para revisão") }
                        }
                        items(practiceState.previews, key = { "preview:${it.id}:${it.version}" }) { mission ->
                            MissionCard(mission.title, "Rascunho · sem confirmação de capacidades", "Abrir trilha de texto", !practiceState.busy, { practice.download(mission, true) })
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (pending == 0) "Nenhum registro aguardando envio automático." else "$pending registros aguardam envio. Sua prática está salva neste aparelho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { confirmClear = true }, enabled = !practiceState.busy) { Text("Apagar prática deste aparelho") }
                        }
                    }
                    practiceState.message?.let { message -> item { Text(message) } }
                    if (practiceState.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                if (screen.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                screen.message?.let { message -> item { Surface(color = WarmPaper, shape = MaterialTheme.shapes.medium) { Text(message, Modifier.padding(18.dp)) } } }
            }
        }
    }
}
