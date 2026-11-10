package br.com.idiomas

import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.idiomas.data.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrailAppearanceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, value: String, exact: Boolean = false): AccessibilityNodeInfo? {
        if(node==null) return null
        if(if(exact) node.text?.toString()==value || node.contentDescription?.toString()==value
            else node.text?.toString()?.contains(value)==true || node.contentDescription?.toString()?.contains(value)==true) return node
        for(index in 0 until node.childCount) find(node.getChild(index),value,exact)?.let {return it}
        return null
    }
    private fun waitFor(value: String, exact: Boolean = false): AccessibilityNodeInfo {
        repeat(60) {
            find(instrumentation.uiAutomation.rootInActiveWindow,value,exact)?.let {return it}
            Thread.sleep(100)
        }
        error("UI did not show: $value")
    }
    private fun click(value: String) {
        var node=waitFor(value)
        while(!node.isClickable) node=node.parent ?: error("No clickable parent: $value")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun searchDays(value: String) {
        var node=waitFor("Dia ou assunto")
        while(!node.isEditable) node=node.parent ?: error("No editable parent for day search")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)
        }))
    }
    private fun screenshot(name: String) {
        val directory=File(instrumentation.targetContext.filesDir,"synthetic-ui-review").apply {mkdirs()}
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    private fun scrollTo(value:String) {
        fun scrollable(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.isScrollable) return node
            for(index in 0 until node.childCount) scrollable(node.getChild(index))?.let {return it}
            return null
        }
        repeat(60) {
            val root=instrumentation.uiAutomation.rootInActiveWindow
            if(find(root,value)?.isVisibleToUser==true) return
            scrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(100)
        }
        error("UI did not scroll to: $value")
    }

    @Test fun arrows_picker_progress_and_dark_theme_work_with_synthetic_content() {
        val context=instrumentation.targetContext
        assertNotNull(context.getDrawable(R.drawable.ic_launcher))
        val prefs=context.getSharedPreferences("appearance",0)
        prefs.edit().putBoolean("dark",false).putBoolean("sound-effects",false).commit()
        val day=mutableStateOf(0)
        val done=mutableStateOf(emptySet<String>())
        val phases=(0..2).map { d -> TrailPhase("day-$d","Conteúdo sintético", "",(0..2).map {TrailStep(d*3+it,"Atividade sintética")},"A1") }
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    IdiomasTheme {
                        Scaffold { padding ->
                            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                                LearningTrail(phases,day.value,done.value,false,{day.value=it},{done.value=done.value+phases[day.value].lessonKey(it)})
                            }
                        }
                    }
                }
            }
            waitFor("0 de 3 lições concluídas")
            click("Ativar efeitos sonoros")
            waitFor("Silenciar efeitos sonoros")
            assertTrue(prefs.getBoolean("sound-effects",false))
            click("Silenciar efeitos sonoros")
            waitFor("Ativar efeitos sonoros")
            assertFalse(prefs.getBoolean("sound-effects",true))
            click("Lição 1")
            waitFor("1 de 3 lições concluídas")
            click("Próximo dia")
            waitFor("Dia 2, selecionar dia")
            waitFor("0 de 3 lições concluídas")
            click("Dia anterior")
            waitFor("1 de 3 lições concluídas")
            screenshot("native-light")
            click("Dia 1, selecionar dia")
            waitFor("Escolha um dia")
            waitFor("A1")
            screenshot("native-picker")
            click("Dia 2")
            waitFor("Ir para o Dia 2")
            click("Ir para o Dia 2")
            waitFor("Dia 2, selecionar dia")
            waitFor("0 de 3 lições concluídas")
            click("Dia anterior")
            waitFor("1 de 3 lições concluídas")
            click("Ativar tema escuro")
            waitFor("Ativar tema claro")
            assertTrue(prefs.getBoolean("dark",false))
            screenshot("native-dark")
        } finally {
            instrumentation.runOnMainSync {activity.finish()}
            prefs.edit().clear().commit()
        }
    }

    @Test fun filtered_picker_keeps_day_indices_and_restores_the_current_day() {
        val context=instrumentation.targetContext
        val day=mutableStateOf(52)
        val phases=(0..52).map { d -> TrailPhase("day-$d",if(d==26) "Encontro no café" else "Conteúdo sintético $d", "",listOf(TrailStep(d,"Atividade sintética")),"A1") }
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync { activity.setContent {
                IdiomasTheme { Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                        LearningTrail(phases,day.value,emptySet(),false,{day.value=it},{})
                    }
                } }
            } }
            click("Dia 53, selecionar dia")
            waitFor("Ir para o Dia 53")
            searchDays("27")
            waitFor("Ir para o Dia 27")
            click("Ir para o Dia 27")
            waitFor("Dia 27, selecionar dia")
            click("Dia 27, selecionar dia")
            searchDays("cafe")
            waitFor("Encontro no café")
            waitFor("Ir para o Dia 27")
            searchDays("assunto inexistente")
            waitFor("Nenhum dia encontrado. Tente outro assunto ou número.")
            var confirm=waitFor("Nenhum dia encontrado",exact=true)
            while(confirm.isEnabled) confirm=confirm.parent ?: error("Missing disabled confirmation")
            assertFalse(confirm.isEnabled)
            searchDays("")
            waitFor("Ir para o Dia 27")
            click("Ir para o Dia 27")
            waitFor("Dia 27, selecionar dia")
            assertEquals(26,day.value)
        } finally { instrumentation.runOnMainSync {activity.finish()} }
    }

    @Test fun written_feedback_keeps_accepted_adjustment_and_unknown_states_distinct() {
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("appearance",0)
        prefs.edit().putBoolean("dark",true).putBoolean("sound-effects",false).commit()
        val app=context.applicationContext as IdiomasApplication
        val vm=PracticeViewModel(app.practice,app.repository)
        val reviewCues=mutableListOf<InteractionCue>()
        val cueCollector=vm.viewModelScope.launch(Dispatchers.Unconfined) {vm.cues.collect {reviewCues+=it}}
        val hash="0".repeat(64)
        val expected=Json.parseToJsonElement("""{"comparison":{"version":"text-variants-v1","accepted":["A tea, please."],"corrections":[{"response":"A coffee, please.","message":"Nesta mensagem, o pedido é de chá."}],"example":"A tea, please."}}""").jsonObject
        val support=MissionSupport("comparison-example","solution","A tea, please.",listOf("writing"),true)
        val exercise=MissionActivity("synthetic-write","written_recall","writing","Escreva um pedido curto de chá em inglês.",listOf("synthetic-target"),listOf(support),buildJsonObject {put("phase_id","synthetic-day");put("phase_title","Uma mensagem no café")},expected,"Synthetic feedback")
        val document=MissionDocument("1.0.0","synthetic-ui",1,"1.0.0","Synthetic UI review","draft",emptyList(),listOf(exercise))
        fun preview(response:String):OpenPractice {
            val session=PracticeSession(0,UUID.randomUUID().toString(),hash,0,response,0,null,false,0)
            val attempt=PracticeEvent(0,UUID.randomUUID().toString(),session.id,1,"attempt",buildJsonObject {
                put("activity_id",exercise.id);put("technical_status","valid");put("response",response);put("support_event_ids",JsonArray(emptyList()))
            }.toString())
            val example=PracticeEvent(0,UUID.randomUUID().toString(),session.id,2,"exposure",buildJsonObject {
                put("session_id",session.id);put("package_hash",hash);put("activity_id",exercise.id);put("support_id",support.id)
            }.toString())
            val events=if(response=="A tea, please.") listOf(attempt) else listOf(attempt,example)
            return OpenPractice(InstalledPackage(0,hash,"synthetic-ui",1,"Synthetic UI review",true,""),document,session,events)
        }
        val state=mutableStateOf(PracticeScreenState(open=preview("A tea, please."),trail=false))
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {activity.setContent {
                IdiomasTheme {Scaffold {padding -> Box(Modifier.padding(padding)) {PracticeScreen(vm,state.value,0,emptyList())}}}
            }}
            for((name,response,label) in listOf(Triple("correct","A tea, please.","Resposta aceita"),Triple("adjust","A coffee, please.","Vamos ajustar"),Triple("neutral","Could I get some tea?","Resposta registrada"))) {
                instrumentation.runOnMainSync {state.value=PracticeScreenState(open=preview(response),trail=false)}
                waitFor(label)
                waitFor("1 de 1 questões respondidas")
                waitFor("Ativar efeitos sonoros")
                screenshot("notebook-feedback-$name")
            }
            instrumentation.runOnMainSync {state.value=PracticeScreenState(open=preview("A coffee, please."),trail=false)}
            waitFor("Vamos ajustar")
            click("Ativar tema claro")
            waitFor("Ativar tema escuro")
            screenshot("notebook-feedback-adjust-light")
            for(status in listOf("pending","reviewed")) {
                val original=preview("A tea, please.")
                val attempt=original.events.single()
                val review=WritingReview(UUID.randomUUID().toString(),attempt.id,status,if(status=="reviewed") 1 else 0,true,"2026-11-09T12:00:00Z",
                    reviewed_at=if(status=="reviewed") "2026-11-09T12:30:00Z" else null,
                    feedback=if(status=="reviewed") "Retorno humano sintético: a mensagem comunica o pedido." else null,
                    rubric_id="synthetic-writing",rubric_version=1,scope_version="writing-review-v1",
                    dimensions=if(status=="reviewed") listOf(WritingReviewDimension("meaning","met","Julgamento sintético sobre esta escrita."),WritingReviewDimension("reading","not_observed","A escrita não comprova leitura.")) else emptyList(),
                    source=if(status=="reviewed") "human_review" else "not_assessed")
                val canonical=RemoteAssessment("correct",true,true,false,"automatic_rule","Retorno automático sintético",review,true)
                val events=listOf(attempt.copy(syncState="received",remoteResult=Json.encodeToString(canonical)))
                instrumentation.runOnMainSync {state.value=PracticeScreenState(open=original.copy(events=events),trail=false)}
                waitFor("Resposta aceita")
                scrollTo(if(status=="reviewed") "Revisão humana da escrita" else "Revisão humana pendente")
                if(status=="reviewed") {
                    scrollTo("Retorno humano sintético")
                    scrollTo("Leitura: não observado")
                }
                scrollTo("O servidor registrou contexto de apoio")
                scrollTo("Um retorno humano anterior foi disponibilizado")
                scrollTo("Atualizar revisão")
                waitFor("Atualizar revisão")
                screenshot("notebook-human-review-$status")
                assertEquals(events,state.value.open!!.events)
                assertTrue(state.value.open!!.completedLessons.isEmpty())
                assertEquals("automatic_rule",state.value.open!!.events.single().remoteAssessment()!!.source)
                assertTrue(reviewCues.isEmpty())
                assertFalse(prefs.getBoolean("sound-effects",true))
            }
        } finally {
            cueCollector.cancel()
            instrumentation.runOnMainSync {vm.viewModelScope.cancel();activity.finish()}
            prefs.edit().clear().commit()
        }
    }
}
