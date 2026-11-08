package br.com.idiomas

import android.content.Intent
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
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrailAppearanceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, value: String): AccessibilityNodeInfo? {
        if(node==null) return null
        if(node.text?.toString()?.contains(value)==true || node.contentDescription?.toString()==value) return node
        for(index in 0 until node.childCount) find(node.getChild(index),value)?.let {return it}
        return null
    }
    private fun waitFor(value: String): AccessibilityNodeInfo {
        repeat(60) {
            find(instrumentation.uiAutomation.rootInActiveWindow,value)?.let {return it}
            Thread.sleep(100)
        }
        error("UI did not show: $value")
    }
    private fun click(value: String) {
        var node=waitFor(value)
        while(!node.isClickable) node=node.parent ?: error("No clickable parent: $value")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun screenshot(name: String) {
        val directory=File(instrumentation.targetContext.filesDir,"synthetic-ui-review").apply {mkdirs()}
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }

    @Test fun arrows_picker_progress_and_dark_theme_work_with_synthetic_content() {
        val context=instrumentation.targetContext
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

    @Test fun written_feedback_keeps_accepted_adjustment_and_unknown_states_distinct() {
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("appearance",0)
        prefs.edit().putBoolean("dark",true).putBoolean("sound-effects",false).commit()
        val app=context.applicationContext as IdiomasApplication
        val vm=PracticeViewModel(app.practice,app.repository)
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
            for((name,response,label) in listOf(Triple("correct","A tea, please.","Resposta aceita"),Triple("adjust","A coffee, please.","Vamos ajustar"),Triple("neutral","Could I get some tea?","Vamos conferir"))) {
                instrumentation.runOnMainSync {state.value=PracticeScreenState(open=preview(response),trail=false)}
                waitFor(label)
                waitFor("Ativar efeitos sonoros")
                screenshot("notebook-feedback-$name")
            }
        } finally {
            instrumentation.runOnMainSync {vm.viewModelScope.cancel();activity.finish()}
            prefs.edit().clear().commit()
        }
    }
}
