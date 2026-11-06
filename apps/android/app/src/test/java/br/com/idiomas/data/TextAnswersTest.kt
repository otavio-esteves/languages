package br.com.idiomas.data

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TextAnswersTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun shared_cases_keep_meaningful_differences_and_valid_alternatives() {
        val cases = json.parseToJsonElement(File(checkNotNull(System.getProperty("contracts.dir")), "fixtures/text-answer-cases.json").readText()).jsonObject["cases"]!!.jsonArray
        for (case in cases) {
            val data = case.jsonObject
            val minimal = data["activity"]!!.jsonObject
            val activity = MissionActivity("synthetic", minimal.text("type")!!, minimal.text("modality")!!, "Synthetic", listOf("synthetic-target"), emptyList(), buildJsonObject {}, minimal["expected"]!!.jsonObject, "Synthetic")
            val expected = json.decodeFromJsonElement<TextComparison>(data["result"]!!)
            assertEquals(data.text("id"), expected, compareAnswer(activity, data.text("response")))
        }
    }
}
