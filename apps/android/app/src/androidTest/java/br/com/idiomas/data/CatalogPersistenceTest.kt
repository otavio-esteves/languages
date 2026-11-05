package br.com.idiomas.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogPersistenceTest {
    @Test fun reopeningDatabasePreservesCatalogAndAccountsStayIsolated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "catalog-test-${UUID.randomUUID()}.db"
        var database = Room.databaseBuilder(context, CatalogDatabase::class.java, name).build()
        try {
            val first = CatalogItem("first", 1, "First", "pt-BR_en", listOf("capability"))
            val second = CatalogItem("second", 1, "Second", "pt-BR_en", listOf("capability"))
            val cache = RoomCatalogCache(database.catalogDao())
            cache.replace(1, listOf(first)); cache.replace(2, listOf(second))
            database.close()
            database = Room.databaseBuilder(context, CatalogDatabase::class.java, name).build()
            val reopened = RoomCatalogCache(database.catalogDao())
            assertEquals(listOf(first), reopened.observe(1).first())
            assertEquals(listOf(second), reopened.observe(2).first())
            reopened.clear(1)
            assertTrue(reopened.observe(1).first().isEmpty())
            assertEquals(listOf(second), reopened.observe(2).first())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
