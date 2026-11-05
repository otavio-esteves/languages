package br.com.idiomas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val vm: CatalogViewModel = viewModel(factory = CatalogViewModel.factory((application as IdiomasApplication).repository))
                val app = application as IdiomasApplication
                val practice: PracticeViewModel = viewModel(factory=PracticeViewModel.factory(app.practice,app.repository))
                CatalogScreen(vm,practice)
            }
        }
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
    if(confirmClear && session!=null) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Apagar prática deste aparelho?")},text={Text("Serão removidos downloads, gravações e respostas locais desta conta. $pending registros aguardam envio e serão descartados. Registros já enviados permanecem no servidor.")},confirmButton={TextButton(onClick={confirmClear=false;practice.clearLocal()}) {Text("Apagar dados locais")}},dismissButton={TextButton(onClick={confirmClear=false}) {Text("Cancelar")}})
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Scaffold { padding ->
        if(practiceState.open!=null && session!=null && practiceState.open?.session?.ownerId==session?.account?.id) {
            Box(Modifier.padding(padding)) { PracticeScreen(practice,practiceState,pending) }
        } else LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Idiomas", style = MaterialTheme.typography.headlineLarge) }
            item { Text("Aprender para usar.", style = MaterialTheme.typography.titleLarge) }
            if (session == null) {
                item { Text("Entre para consultar as missões disponíveis.") }
                item { OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { Button(onClick = { val secret = password; password = ""; vm.login(email, secret) }, enabled = !screen.busy && email.isNotBlank() && password.isNotBlank()) { Text("Entrar") } }
            } else {
                item { Text("Olá, ${session!!.account.name}.") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = vm::refresh, enabled = !screen.busy) { Text("Atualizar") }; TextButton(onClick = vm::logout, enabled = !screen.busy) { Text("Sair") } } }
                item { Text("Missões disponíveis", style = MaterialTheme.typography.titleLarge) }
                if (catalog.isEmpty() && !screen.busy) item { Text("Ainda não há missões publicadas. Os primeiros materiais estão em revisão.") }
                items(catalog, key = { "${it.id}:${it.version}" }) { mission ->
                    Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(mission.title, style = MaterialTheme.typography.titleMedium); Text("Inglês · versão ${mission.version}"); Button(onClick={practice.download(mission,false)},enabled=!practiceState.busy) { Text("Baixar e abrir") } } }
                }
                if(session!!.account.is_editor) {
                    item { OutlinedButton(onClick=practice::previews,enabled=!practiceState.busy) { Text("Carregar prévias editoriais") } }
                    items(practiceState.previews,key={"preview:${it.id}:${it.version}"}) { item ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(item.title);Text("Rascunho · sem confirmação de capacidades");Button(onClick={practice.download(item,true)},enabled=!practiceState.busy) { Text("Baixar prévia") } } }
                    }
                }
                if(installed.isNotEmpty()) item { Text("Missões neste aparelho",style=MaterialTheme.typography.titleLarge) }
                items(installed,key={"installed:${it.hash}"}) { item -> OutlinedButton(onClick={practice.open(item)},enabled=!practiceState.busy) { Text("Retomar · ${item.title}") } }
                item { TextButton(onClick={confirmClear=true},enabled=!practiceState.busy) { Text("Apagar prática deste aparelho") } }
                item { Text("$pending registros aguardando envio.",style=MaterialTheme.typography.bodySmall) }
                practiceState.message?.let { message -> item { Text(message) } }
                if(practiceState.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            if (screen.busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            screen.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        }
    }
}
