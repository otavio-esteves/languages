package br.com.idiomas.data

import kotlinx.serialization.Serializable

@Serializable
data class CatalogItem(val id: String, val version: Int, val title: String, val language_pair: String, val capability_ids: List<String>) {
    init { require(id.isNotBlank() && version >= 1 && title.isNotBlank() && language_pair == "pt-BR_en" && capability_ids.isNotEmpty()) }
}
@Serializable
data class CatalogResponse(val schema_version: String, val data: List<CatalogItem>) {
    init { require(schema_version == "1.0.0") { "Versão do catálogo incompatível" } }
}
@Serializable data class Account(val id: Long, val name: String, val email: String, val is_editor: Boolean = false)
@Serializable data class LoginRequest(val email: String, val password: String, val device_name: String = "languages Android")
@Serializable data class LoginResponse(val token: String, val expires_at: String, val user: Account)
@Serializable data class Session(val account: Account, val token: String, val expiresAt: String = "")
class ApiFailure(val status: Int) : Exception(when(status) {
    401 -> "Sua sessão expirou. Entre novamente."
    422 -> "Confira o e-mail e a senha."
    429 -> "Muitas tentativas. Aguarde um minuto."
    else -> "Não foi possível acessar o serviço. Tente novamente."
})
interface LearningApi {
    suspend fun login(email: String, password: String): LoginResponse
    suspend fun catalog(token: String): CatalogResponse
    suspend fun logout(token: String)
}
