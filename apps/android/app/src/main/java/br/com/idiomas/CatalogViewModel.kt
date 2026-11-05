package br.com.idiomas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.idiomas.data.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScreenState(val busy: Boolean = false, val message: String? = null)
class CatalogViewModel(private val repository: CatalogRepository) : ViewModel() {
    val session = repository.session
    val catalog = repository.catalog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val mutableScreen = MutableStateFlow(ScreenState())
    val screen = mutableScreen.asStateFlow()
    private fun action(block: suspend () -> String?) {
        if (mutableScreen.value.busy) return
        viewModelScope.launch {
            mutableScreen.value = ScreenState(busy = true)
            try { mutableScreen.value = ScreenState(message = block()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableScreen.value = ScreenState(message = if (failure is br.com.idiomas.data.ApiFailure) failure.message else if (session.value == null) "Não foi possível entrar. Verifique sua conexão e tente novamente." else "Não foi possível atualizar. O catálogo salvo continua disponível nesta sessão.") }
        }
    }
    fun login(email: String, password: String) = action { repository.login(email, password); repository.refresh(); null }
    fun refresh() = action { repository.refresh(); "Catálogo atualizado." }
    fun logout() = action { if (repository.logout()) null else "Saída local concluída. Sem conexão, a revogação remota não pôde ser confirmada; o acesso expira em até 24 horas." }
    companion object {
        fun factory(repository: CatalogRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = CatalogViewModel(repository) as T
        }
    }
}
