package br.com.idiomas.data

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.idiomas.PracticeScreenState
import br.com.idiomas.PracticeViewModel
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeAuditRegressionTest {
    private class Api : LearningApi, PracticeApi {
        private fun raw(version: Int) = buildJsonObject {
            put("schema_version","1.0.0"); put("id","audit-fixture"); put("version",version)
            put("minimum_reader_version","1.0.0"); put("title","Synthetic audit regression"); put("status","draft")
            put("assets",JsonArray(emptyList()))
            put("activities",JsonArray((0..5).map { index -> buildJsonObject {
                put("id","write-$index"); put("type","written_recall"); put("modality","writing"); put("prompt","Synthetic prompt")
                put("target_ids",buildJsonArray {add("synthetic-target")})
                put("content",buildJsonObject {put("phase_id",if(index<3) "first" else "second")})
                put("expected",buildJsonObject {}); put("feedback","Synthetic feedback")
                put("supports",buildJsonArray {add(buildJsonObject {
                    put("id","shared-hint"); put("kind","hint"); put("content","Synthetic meaning"); put("reveals_answer",true)
                    put("affected_modalities",buildJsonArray {add("writing")})
                })})
            } }))
        }.toString().toByteArray()
        private fun hash(raw: ByteArray) = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {"%02x".format(it)}
        private val documents=(1..2).map(::raw).associateBy(::hash)
        val received=linkedMapOf<String,JsonObject>()
        var failResults=false
        var resultsCalls=0
        var beforeResults:(suspend () -> Unit)?=null
        override suspend fun login(email:String,password:String)=LoginResponse("synthetic-token-$email","2030-01-01T00:00:00Z",Account(if(email=="first") 1 else 2,"Synthetic",email,true))
        override suspend fun logout(token:String) {}
        override suspend fun catalog(token:String)=CatalogResponse("1.0.0",emptyList())
        override suspend fun previews(token:String)=catalog(token)
        override suspend fun manifest(token:String,id:String,version:Int,preview:Boolean):PackageManifest {
            val bytes=raw(version)
            return PackageManifest("1.0.0","audit-fixture",version,hash(bytes),bytes.size.toLong(),true,emptyList())
        }
        override suspend fun document(token:String,hash:String)=documents.getValue(hash)
        override suspend fun asset(token:String,hash:String,id:String)=error("No media in synthetic fixture")
        override suspend fun send(token:String,kind:String,events:List<JsonObject>)=BatchResult(events.map {event ->
            val id=event.text("id")!!
            val previous=received.putIfAbsent(id,event)
            check(previous==null || previous==event)
            EventReceipt(id,if(previous==null) "accepted" else "duplicate")
        })
        override suspend fun results(token:String,sessionId:String):RemoteSession {
            resultsCalls++
            if(failResults) {failResults=false;throw IOException("Synthetic results interruption")}
            beforeResults?.also { beforeResults=null }?.invoke()
            return RemoteSession(sessionId,received.values.filter {it.text("session_id")==sessionId && it.text("modality")!=null}.map {
                RemoteEvent(it.text("id")!!,"attempt",RemoteAssessment("correct",it.strings("support_event_ids").isNotEmpty(),true,false,"automatic_rule","Synthetic result"))
            })
        }
    }

    private class Fixture {
        private val base=InstrumentationRegistry.getInstrumentation().targetContext
        private val tag=UUID.randomUUID().toString()
        private val files=File(base.cacheDir,"audit-regression-$tag").apply {mkdirs()}
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=files
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("audit-regression-$tag-$name",mode)
        }
        val name="audit-regression-$tag.db"
        val api=Api()
        private val vault=MemorySessionVault()
        private fun database()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
        var db=database()
        var catalog=CatalogRepository(api,RoomCatalogCache(db.catalogDao()),vault)
        var repo=PracticeRepository(context,db,catalog,api)
        suspend fun install(version:Int=1)=repo.download(CatalogItem("audit-fixture",version,"Synthetic audit regression","pt-BR_en",listOf("synthetic-target")),true)
        fun reopen() {
            db.close(); db=database(); catalog=CatalogRepository(api,RoomCatalogCache(db.catalogDao()),vault); repo=PracticeRepository(context,db,catalog,api)
        }
        fun close() {db.close();context.deleteDatabase(name);files.deleteRecursively();base.deleteSharedPreferences("audit-regression-$tag-practice-device")}
    }

    @Test fun support_ids_are_scoped_to_the_current_session_and_activity()=runBlocking {
        val f=Fixture()
        var vm:PracticeViewModel?=null
        suspend fun settled(predicate:(PracticeScreenState)->Boolean) = withTimeout(10000) {vm!!.screen.first {!it.busy && predicate(it)}}
        try {
            f.catalog.login("first","synthetic")
            val installed=f.install()
            withContext(Dispatchers.Main) {vm=PracticeViewModel(f.repo,f.catalog);yield();vm!!.open(installed)}
            val original=settled {it.open!=null}.open!!
            withContext(Dispatchers.Main) {vm!!.begin(0)}
            settled {!it.trail}
            withContext(Dispatchers.Main) {vm!!.support("shared-hint")}
            val originalHint=settled {"shared-hint" in it.revealed}.open!!.events.single().id
            withContext(Dispatchers.Main) {vm!!.showTrail();vm!!.selectDay(1);vm!!.begin(0)}
            val next=settled {it.open?.session?.cursor==3 && !it.trail}
            val nextOpen=next.open!!
            assertNotEquals(original.session.id,nextOpen.session.id)
            assertFalse("shared-hint" in next.revealed)
            assertTrue(nextOpen.events.isEmpty())
            withContext(Dispatchers.Main) {vm!!.support("shared-hint")}
            val supported=settled {"shared-hint" in it.revealed}.open!!
            val newHint=supported.events.single()
            assertNotEquals(originalHint,newHint.id)
            assertEquals(supported.session.id,Json.parseToJsonElement(newHint.payload).jsonObject.text("session_id"))
            withContext(Dispatchers.Main) {vm!!.answer("water")}
            val answered=settled {it.open?.session?.answeredIndex==3}.open!!
            assertEquals(listOf(newHint.id),Json.parseToJsonElement(answered.events.last().payload).jsonObject.strings("support_event_ids"))
            withContext(Dispatchers.Main) {vm!!.showTrail();vm!!.selectDay(0);vm!!.begin(0)}
            val restored=settled {it.open?.session?.cursor==0 && !it.trail}
            assertEquals(original.session.id,restored.open!!.session.id)
            assertEquals(setOf("shared-hint"),restored.revealed)
        } finally {withContext(Dispatchers.Main) {vm?.viewModelScope?.cancel()};f.close()}
    }

    @Test fun reopen_uses_the_last_resumed_session_even_when_it_was_created_earlier()=runBlocking {
        val f=Fixture()
        try {
            f.catalog.login("first","synthetic")
            val installed=f.install()
            val first=f.repo.open(installed.hash)
            f.repo.draft(first,"Earlier day draft")
            val future=f.repo.selectLesson(first,1,0)
            val resumed=f.repo.selectLesson(future,0,0)
            f.repo.draft(resumed,"Latest edit in the earlier day")
            assertEquals(first.session.createdAt,resumed.session.createdAt)
            f.reopen()
            val opened=f.repo.open(installed.hash)
            assertEquals(first.session.id,opened.session.id)
            assertEquals(0,opened.session.cursor)
            assertEquals("Latest edit in the earlier day",opened.session.draft)
            f.catalog.logout();f.catalog.login("second","synthetic")
            assertTrue(f.repo.savedPackages.first().isEmpty())
            try {f.repo.open(installed.hash);fail("Another account must not resume this practice")} catch(_:IllegalStateException) {}
            f.catalog.logout();f.catalog.login("first","synthetic")
            assertEquals(first.session.id,f.repo.open(installed.hash).session.id)
        } finally {f.close()}
    }

    @Test fun downloading_an_update_keeps_old_practice_accessible_with_its_original_events_and_progress()=runBlocking {
        val f=Fixture()
        try {
            f.catalog.login("first","synthetic")
            val old=f.install(1)
            var practice=f.repo.open(old.hash)
            practice=f.repo.advance(f.repo.answer(practice,"water"))
            f.repo.draft(practice,"Old version draft")
            val current=f.install(2)
            assertTrue(f.repo.open(current.hash).completedLessons.isEmpty())
            assertEquals(setOf(old.hash,current.hash),f.repo.savedPackages.first().map {it.hash}.toSet())
            val resumed=f.repo.open(old.hash)
            assertEquals(setOf("first:0"),resumed.completedLessons)
            assertEquals("Old version draft",resumed.session.draft)
            val event=Json.parseToJsonElement(resumed.events.single().payload).jsonObject
            assertEquals(old.hash,event.text("package_hash"))
            assertEquals("1",event.text("package_version"))
        } finally {f.close()}
    }

    @Test fun a_results_failure_remains_pending_after_reopen_and_does_not_resend_confirmed_attempts()=runBlocking {
        val f=Fixture()
        try {
            f.catalog.login("first","synthetic")
            val installed=f.install()
            val practice=f.repo.answer(f.repo.open(installed.hash),"water")
            f.api.failResults=true
            try {f.repo.sync();fail("Results should be interrupted")} catch(_:IOException) {}
            assertTrue(f.db.practiceDao().pending(1).isEmpty())
            assertEquals(1,f.db.practiceDao().resultRequests(1).size)
            assertNull(f.db.practiceDao().events(1,practice.session.id).single().remoteResult)
            f.reopen()
            f.catalog.logout();f.catalog.login("second","synthetic")
            val previousCalls=f.api.resultsCalls
            assertTrue(f.repo.sync())
            assertEquals(previousCalls,f.api.resultsCalls)
            f.catalog.logout();f.catalog.login("first","synthetic")
            assertTrue(f.repo.sync())
            assertEquals(previousCalls+1,f.api.resultsCalls)
            assertEquals(1,f.api.received.size)
            assertTrue(f.db.practiceDao().resultRequests(1).isEmpty())
            assertNotNull(f.db.practiceDao().events(1,practice.session.id).single().remoteResult)
        } finally {f.close()}
    }

    @Test fun an_inflight_result_response_does_not_clear_a_newer_request()=runBlocking {
        val f=Fixture()
        try {
            f.catalog.login("first","synthetic")
            val installed=f.install()
            val practice=f.repo.answer(f.repo.open(installed.hash),"water")
            f.api.beforeResults={
                f.db.practiceDao().requestResults(PracticeResultRequest(1,practice.session.id,"newer-generation"))
                f.api.beforeResults={assertNull(f.db.practiceDao().events(1,practice.session.id).single().remoteResult)}
            }
            assertTrue(f.repo.sync())
            assertEquals(2,f.api.resultsCalls)
            assertTrue(f.db.practiceDao().resultRequests(1).isEmpty())
        } finally {f.close()}
    }

    @Test fun upgrading_room_v3_recovers_missing_results_without_changing_events_or_lesson_progress()=runBlocking {
        val f=Fixture()
        try {
            f.catalog.login("first","synthetic")
            val installed=f.install()
            val practice=f.repo.advance(f.repo.answer(f.repo.open(installed.hash),"water"))
            f.api.failResults=true
            try {f.repo.sync();fail("Results should be interrupted")} catch(_:IOException) {}
            val payload=f.db.practiceDao().events(1,practice.session.id).single().payload
            f.db.close()
            val old=f.context.openOrCreateDatabase(f.name,0,null)
            old.execSQL("DROP TABLE practice_result_requests")
            old.execSQL("ALTER TABLE practice_progress DROP COLUMN activeSessionId")
            old.version=3;old.close()
            f.reopen()
            assertEquals(1,f.db.practiceDao().resultRequests(1).size)
            val reopened=f.repo.open(installed.hash)
            assertEquals(setOf("first:0"),reopened.completedLessons)
            assertEquals(practice.session.id,reopened.session.id)
            assertEquals(payload,reopened.events.single().payload)
            assertTrue(f.repo.sync())
            assertNotNull(f.db.practiceDao().events(1,practice.session.id).single().remoteResult)
        } finally {f.close()}
    }
}
