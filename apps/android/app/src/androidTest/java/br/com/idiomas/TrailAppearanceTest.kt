package br.com.idiomas

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.idiomas.data.*
import java.io.File
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
        prefs.edit().putBoolean("dark",false).commit()
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
}
