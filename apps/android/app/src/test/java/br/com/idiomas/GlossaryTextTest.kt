package br.com.idiomas

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GlossaryTextTest {
    @Test fun accented_words_and_contractions_keep_complete_ranges_and_keys() {
        val text = "CAFÉ, cafe\u0301! I’d like tea."
        val words = glossaryWords.findAll(text).toList()
        assertEquals(listOf("CAFÉ", "cafe\u0301", "I’d", "like", "tea"), words.map { it.value })
        assertEquals(listOf("café", "café", "i'd", "like", "tea"), words.map { glossaryKey(it.value) })
        assertEquals("CAFÉ", text.substring(words[0].range))
        assertEquals("cafe\u0301", text.substring(words[1].range))
        assertNotEquals(glossaryKey("café"), glossaryKey("cafè"))
    }

    @Test fun all_accented_curriculum_entries_resolve_their_authored_support() {
        val contracts = File(checkNotNull(System.getProperty("contracts.dir")))
        val packageFile = File(contracts.parentFile, "content/pt-BR_en/missions/order-a-drink.v5.json")
        val mission = Json.parseToJsonElement(packageFile.readText()).jsonObject
        var checked = 0
        for (activity in mission["activities"]!!.jsonArray) {
            val glossary = activity.jsonObject["content"]!!.jsonObject["glossary"]?.jsonObject ?: continue
            for ((word, meaning) in glossary) {
                if (word.all { it.code < 128 }) continue
                val tokens = glossaryWords.findAll(word.uppercase(java.util.Locale.ROOT)).toList()
                assertEquals(activity.jsonObject["id"].toString(), 1, tokens.size)
                assertEquals(meaning, glossary[glossaryKey(tokens.single().value)])
                checked++
            }
        }
        assertEquals(18, checked)
    }
}
