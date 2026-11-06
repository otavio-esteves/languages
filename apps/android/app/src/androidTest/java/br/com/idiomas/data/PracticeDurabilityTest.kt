package br.com.idiomas.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeDurabilityTest {
    private val tag=UUID.randomUUID().toString()
    private val base get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureRoot by lazy { File(base.cacheDir,"practice-fixture-$tag").apply {mkdirs()} }
    private val context:Context by lazy { object:ContextWrapper(base) {
        override fun getFilesDir():File=fixtureRoot
        override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("fixture-$tag-$name",mode)
        override fun deleteSharedPreferences(name:String)=base.deleteSharedPreferences("fixture-$tag-$name")
    } }
    private class FakeApi(private val rawOverride: ByteArray? = null) : LearningApi,PracticeApi {
        val raw=rawOverride ?: """{"schema_version":"1.0.0","id":"synthetic","version":1,"minimum_reader_version":"1.0.0","title":"Synthetic test","status":"draft","assets":[],"activities":[{"id":"intro","type":"purpose","modality":"reading","prompt":"Test","target_ids":["synthetic-target"],"supports":[],"content":{},"expected":{},"feedback":"Test"},{"id":"write","type":"written_recall","modality":"writing","prompt":"Test","target_ids":["synthetic-target"],"supports":[],"content":{},"expected":{},"feedback":"Test"}]}""".toByteArray()
        val hash=MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {"%02x".format(it)}
        val received=mutableSetOf<String>()
        var loseConfirmation=true
        var corrupt=false
        override suspend fun login(email:String,password:String)=LoginResponse("synthetic-token-$email","2030-01-01T00:00:00Z",Account(if(email=="first")1 else 2,"Synthetic",email,true))
        override suspend fun catalog(token:String)=CatalogResponse("1.0.0",emptyList())
        override suspend fun logout(token:String) {}
        override suspend fun previews(token:String)=catalog(token)
        override suspend fun manifest(token:String,id:String,version:Int,preview:Boolean)=PackageManifest("1.0.0","synthetic",1,hash,raw.size.toLong(),true,emptyList())
        override suspend fun document(token:String,hash:String)=if(corrupt) raw.copyOf().also {it[0]=0} else raw
        override suspend fun asset(token:String,hash:String,id:String)=error("No audio in storage fixture")
        override suspend fun send(token:String,kind:String,events:List<JsonObject>):BatchResult {
            val result=events.map {value -> val id=value.text("id")!!;EventReceipt(id,if(received.add(id)) "accepted" else "duplicate")}
            if(loseConfirmation) {loseConfirmation=false;throw IOException("Synthetic lost confirmation")}
            return BatchResult(result)
        }
        override suspend fun results(token:String,sessionId:String)=RemoteSession(sessionId,emptyList())
    }

    @Test fun drafts_attempts_and_outbox_survive_reopening_and_lost_confirmation()=runBlocking {
        val name="practice-test-${UUID.randomUUID()}.db"
        val userId=1L
        var database=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()
        val api=FakeApi();val vault=MemorySessionVault()
        var catalog=CatalogRepository(api,RoomCatalogCache(database.catalogDao()),vault)
        var repo=PracticeRepository(context,database,catalog,api)
        try {
            catalog.login("first","synthetic")
            val item=CatalogItem("synthetic",1,"Synthetic","pt-BR_en",listOf("synthetic-target"))
            val installed=repo.download(item,true)
            var open=repo.open(installed.hash)
            open=repo.exposure(open)
            open=repo.advance(open)
            repo.draft(open,"A large tea, please.")
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_failure BEFORE INSERT ON practice_sessions WHEN NEW.answeredIndex >= 0 BEGIN SELECT RAISE(ABORT,'synthetic interruption'); END")
            try {repo.answer(open,"A large tea, please.");fail("Atomic commit must fail") } catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(1,database.practiceDao().events(userId,open.session.id).size)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER synthetic_failure")
            open=repo.answer(open,"A large tea, please.")
            val id=open.session.id
            database.close()
            database=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()
            catalog=CatalogRepository(api,RoomCatalogCache(database.catalogDao()),vault)
            repo=PracticeRepository(context,database,catalog,api)
            open=repo.open(installed.hash)
            assertEquals(id,open.session.id);assertEquals(1,open.session.cursor)
            assertEquals("A large tea, please.",open.session.draft)
            assertEquals(2,open.events.size)
            try {repo.sync();fail("Confirmation should be lost") } catch(_:IOException) {}
            assertEquals(2,database.practiceDao().pending(userId).size)
            assertTrue(repo.sync());assertEquals(2,api.received.size)
            assertTrue(database.practiceDao().pending(userId).isEmpty())
            api.corrupt=true
            try {repo.download(item,true);fail("Corrupt package must fail") } catch(_:IllegalArgumentException) {}
            assertEquals(1,repo.installed.first().size)
            assertEquals(id,repo.open(installed.hash).session.id)
            catalog.logout();catalog.login("second","synthetic")
            assertTrue(repo.installed.first().isEmpty())
            try {repo.open(installed.hash);fail("Wrong account must not access session") } catch(_:IllegalStateException) {}
            assertEquals(2,database.practiceDao().events(userId,id).size)
            catalog.logout();catalog.login("first","synthetic")
            repo.clearLocal()
            assertTrue(repo.installed.first().isEmpty());assertTrue(database.practiceDao().events(userId,id).isEmpty())
            assertNotNull(database.practiceDao().clock(userId))
        } finally {
            database.close();context.deleteDatabase(name)
            fixtureRoot.deleteRecursively();context.deleteSharedPreferences("practice-device")
        }
    }

    @Test fun correction_example_and_attempt_commit_together_and_only_support_later_attempts()=runBlocking {
        val root=Json.parseToJsonElement(FakeApi().raw.toString(Charsets.UTF_8)).jsonObject.toMutableMap()
        val activities=root["activities"]!!.jsonArray.toMutableList()
        val writing=activities[1].jsonObject.toMutableMap()
        writing["expected"]=buildJsonObject { put("comparison",buildJsonObject {
            put("version","text-variants-v1");put("accepted",buildJsonArray {add("water")})
            put("corrections",buildJsonArray {add(buildJsonObject {put("response","coffee");put("message","Outra bebida.")})})
            put("example","water")
        }) }
        writing["supports"]=buildJsonArray {add(buildJsonObject {
            put("id","comparison-example");put("kind","solution");put("content","water")
            put("affected_modalities",buildJsonArray {add("writing")});put("reveals_answer",true)
        })}
        activities[1]=JsonObject(writing);root["activities"]=JsonArray(activities)
        val api=FakeApi(JsonObject(root).toString().toByteArray())
        val name="comparison-test-${UUID.randomUUID()}.db"
        var database=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()
        val vault=MemorySessionVault()
        var catalog=CatalogRepository(api,RoomCatalogCache(database.catalogDao()),vault)
        var repo=PracticeRepository(context,database,catalog,api)
        try {
            catalog.login("first","synthetic")
            val installed=repo.download(CatalogItem("synthetic",1,"Synthetic","pt-BR_en",listOf("synthetic-target")),true)
            var open=repo.advance(repo.open(installed.hash))
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_example_failure BEFORE INSERT ON practice_events WHEN NEW.kind='exposure' BEGIN SELECT RAISE(ABORT,'synthetic interruption'); END")
            try {repo.answer(open,"coffee");fail("Attempt and example must roll back together") } catch(_:android.database.sqlite.SQLiteException) {}
            assertTrue(database.practiceDao().events(1,open.session.id).isEmpty())
            assertEquals(-1,database.practiceDao().session(1,open.session.id)!!.answeredIndex)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER synthetic_example_failure")
            open=repo.answer(open,"coffee")
            assertEquals(listOf("attempt","exposure"),open.events.map {it.kind})
            assertTrue(Json.parseToJsonElement(open.events[0].payload).jsonObject.strings("support_event_ids").isEmpty())
            val solutionId=open.events[1].id
            val sessionId=open.session.id
            database.close()
            database=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()
            catalog=CatalogRepository(api,RoomCatalogCache(database.catalogDao()),vault)
            repo=PracticeRepository(context,database,catalog,api)
            open=repo.open(installed.hash)
            assertEquals(sessionId,open.session.id);assertEquals(2,open.events.size)
            open=repo.answer(repo.advance(open,true),"WATER!")
            assertEquals(3,open.events.size)
            val retry=Json.parseToJsonElement(open.events.last().payload).jsonObject
            assertEquals(listOf(solutionId),retry.strings("support_event_ids"))
            assertTrue(open.events.all {it.syncState=="pending"})
        } finally { database.close();context.deleteDatabase(name);fixtureRoot.deleteRecursively();context.deleteSharedPreferences("practice-device") }
    }

    @Test fun migration_from_v1_preserves_catalog()=runBlocking {
        val name="migration-test-${UUID.randomUUID()}.db"
        val old=context.openOrCreateDatabase(name,0,null)
        val expected=CatalogItem("preserved",1,"Preserved","pt-BR_en",listOf("capability"))
        old.execSQL("CREATE TABLE catalog (ownerId INTEGER NOT NULL, missionId TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(ownerId,missionId))")
        old.execSQL("INSERT INTO catalog VALUES(?,?,?)",arrayOf<Any>(1,"preserved",Json.encodeToString(expected)))
        old.version=1;old.close()
        val database=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()
        try {assertEquals(listOf(expected),RoomCatalogCache(database.catalogDao()).observe(1).first());assertTrue(database.practiceDao().pending(1).isEmpty())}
        finally {database.close();context.deleteDatabase(name)}
    }

    @Test fun credentials_restore_from_encrypted_storage_without_plaintext() {
        val name="vault-test-${UUID.randomUUID()}"
        val value=Session(Account(1,"Synthetic","synthetic@example.test",true),"test-only-secret","2030-01-01T00:00:00Z")
        val vault=EncryptedSessionVault(context,name)
        try {
            vault.save(value)
            assertEquals(value,EncryptedSessionVault(context,name).load())
            val stored=context.getSharedPreferences(name,0).getString("ciphertext",null)!!
            assertFalse(stored.contains(value.token))
            vault.clear();assertNull(vault.load())
        } finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply {load(null);deleteEntry("idiomas-$name-v1")}
        }
    }
}
