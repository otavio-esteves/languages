package br.com.idiomas.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CatalogRepository(private val api: LearningApi, private val cache: CatalogCache, private val vault: SessionVault = MemorySessionVault()) {
    private val active = MutableStateFlow<Session?>(vault.load())
    private val stateLock = Mutex()
    val session = active.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val catalog: Flow<List<CatalogItem>> = active.flatMapLatest { it?.let { cache.observe(it.account.id) } ?: flowOf(emptyList()) }
    suspend fun login(email: String, password: String) {
        val response = api.login(email, password)
        stateLock.withLock {
            val session = Session(response.user, response.token, response.expires_at)
            vault.save(session)
            active.value = session
        }
    }
    suspend fun refresh() {
        val snapshot = active.value ?: return
        try {
            val response = api.catalog(snapshot.token)
            stateLock.withLock {
                if (active.value == snapshot) cache.replace(snapshot.account.id, response.data)
            }
        } catch (failure: ApiFailure) {
            stateLock.withLock {
                if (failure.status == 401 && active.value == snapshot) {
                    active.value = null
                    vault.clear()
                    cache.clear(snapshot.account.id)
                }
            }
            throw failure
        }
    }
    suspend fun invalidate(expected: Session) = stateLock.withLock {
        if(active.value == expected) { vault.clear(); active.value=null }
    }
    suspend fun logout(): Boolean {
        val snapshot = stateLock.withLock {
            val current = active.value ?: return true
            active.value = null
            vault.clear()
            cache.clear(current.account.id)
            current
        }
        return try { api.logout(snapshot.token); true } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
    }
}
