package br.com.idiomas.data

import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class TextComparison(val outcome: String, val message: String, val example: String? = null, val rule_version: String? = null)

fun normalizeAnswer(value: String): String = value.lowercase(Locale.ROOT).replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'')
    .replace(Regex("[.,!?]"), "").replace(Regex("[\\p{Z}\\s]+"), " ").trim()

fun compareAnswer(activity: MissionActivity, response: String?): TextComparison {
    val fallback = TextComparison("unverified", "Prática registrada. Respostas abertas precisam de avaliação independente.")
    if (activity.type == "information_question") {
        val correct = response in activity.expected.strings("accepted")
        return TextComparison(if (correct) "correct" else "incorrect", if (correct) "Você identificou a informação. Isso registra reconhecimento nesta atividade." else if (activity.modality == "reading") "Leia novamente e observe o sentido da mensagem." else "Ouça novamente e identifique a escolha comunicada.")
    }
    val rule = activity.expected["comparison"]?.jsonObject ?: return fallback
    if (rule.text("version") != "text-variants-v1" || activity.modality != "writing") return fallback
    val version = rule.text("version")
    val normalized = normalizeAnswer(response ?: "")
    if (normalized.isNotEmpty() && rule.strings("accepted").any { normalizeAnswer(it) == normalized }) return TextComparison("correct", "Resposta aceita nesta atividade.", rule_version = version)
    val correction = rule["corrections"]?.jsonArray?.firstOrNull { normalizeAnswer(it.jsonObject.text("response") ?: "") == normalized }?.jsonObject
    return TextComparison(if (correction == null) "unverified" else "incorrect", correction?.text("message") ?: "Ainda não confirmei esta forma. Compare com o exemplo; outra resposta também pode ser válida.", rule.text("example"), version)
}
