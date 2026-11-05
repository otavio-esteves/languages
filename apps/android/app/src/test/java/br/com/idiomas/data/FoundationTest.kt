package br.com.idiomas.data

import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FoundationTest {
    private class MemoryCache : CatalogCache {
        val rows = MutableStateFlow<Map<Long, List<CatalogItem>>>(emptyMap())
        override fun observe(ownerId: Long) = rows.map { it[ownerId] ?: emptyList() }
        override suspend fun replace(ownerId: Long, items: List<CatalogItem>) { rows.value = rows.value + (ownerId to items) }
        override suspend fun clear(ownerId: Long) { rows.value = rows.value - ownerId }
    }
    private class FakeApi : LearningApi {
        var failure: Exception? = null
        var entered: CompletableDeferred<Unit>? = null
        var pending: CompletableDeferred<CatalogResponse>? = null
        override suspend fun login(email: String, password: String) = LoginResponse(email, "2026-11-05T12:00:00Z", Account(if (email == "a") 1 else 2, email, "$email@example.test"))
        override suspend fun catalog(token: String): CatalogResponse {
            entered?.complete(Unit)
            failure?.let { throw it }
            return pending?.await() ?: CatalogResponse("1.0.0", listOf(CatalogItem("mission-$token", 1, "Mission", "pt-BR_en", listOf("capability"))))
        }
        override suspend fun logout(token: String) {}
    }

    @Test fun sharedCatalogDecodesAndInvalidVersionsAreRejected() {
        val file = File(System.getProperty("contracts.dir"), "fixtures/catalog.json")
        val parsed = Json.decodeFromString<CatalogResponse>(file.readText())
        assertEquals("synthetic-published-mission", parsed.data.single().id)
        assertThrows(IllegalArgumentException::class.java) { CatalogResponse("2.0.0", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { CatalogItem("bad", 0, "bad", "pt-BR_en", listOf("x")) }
    }
    @Test fun networkFailurePreservesOnlyTheActiveAccountCache() = runTest {
        val api = FakeApi(); val cache = MemoryCache(); val repo = CatalogRepository(api, cache)
        repo.login("a", "test"); repo.refresh()
        api.failure = IOException("offline")
        try { repo.refresh(); fail("must fail") } catch (_: IOException) {}
        assertEquals("mission-a", repo.catalog.first().single().id)
        repo.login("b", "test")
        assertTrue(repo.catalog.first().isEmpty())
        assertEquals("mission-a", cache.rows.value[1L]!!.single().id)
    }
    @Test fun unauthorizedResponseEndsSessionAndRemovesThatAccountCache() = runTest {
        val api = FakeApi(); val cache = MemoryCache(); val repo = CatalogRepository(api, cache)
        repo.login("a", "test"); repo.refresh()
        api.failure = ApiFailure(401)
        try { repo.refresh(); fail("must fail") } catch (_: ApiFailure) {}
        assertNull(repo.session.value)
        assertTrue(cache.rows.value.isEmpty())
    }
    @Test fun lateResponseAfterAccountSwitchCannotPopulateTheNewAccount() = runTest {
        val api = FakeApi(); val cache = MemoryCache(); val repo = CatalogRepository(api, cache)
        repo.login("a", "test")
        val pending = CompletableDeferred<CatalogResponse>(); val entered = CompletableDeferred<Unit>()
        api.pending = pending; api.entered = entered
        val task = launch { repo.refresh() }
        entered.await()
        repo.logout(); repo.login("b", "test")
        pending.complete(CatalogResponse("1.0.0", listOf(CatalogItem("old", 1, "Old", "pt-BR_en", listOf("capability")))))
        task.join()
        assertEquals(2L, repo.session.value!!.account.id)
        assertTrue(cache.rows.value.isEmpty())
    }
}
