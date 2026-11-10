package br.com.idiomas

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import br.com.idiomas.data.MissionActivity
import br.com.idiomas.data.text
import kotlinx.serialization.json.jsonObject
import java.text.Normalizer
import java.util.Locale

data class WordMeaning(val meaning: String, val supportId: String)
fun MissionActivity.wordMeanings(): Map<String, WordMeaning> = content["glossary"]?.jsonObject?.mapValues { (_, value) ->
    WordMeaning(value.jsonObject.text("meaning") ?: "", value.jsonObject.text("support_id") ?: "")
} ?: emptyMap()

internal val glossaryWords = Regex("\\p{L}[\\p{L}\\p{M}]*(?:['’‘ʼ]\\p{L}[\\p{L}\\p{M}]*)*")
internal fun glossaryKey(word: String): String = Normalizer.normalize(word, Normalizer.Form.NFC)
    .lowercase(Locale.ROOT).replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'')

@Composable
fun GlossaryText(text: String, meanings: Map<String, WordMeaning>, onLookup: (String, WordMeaning) -> Unit, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    val annotated = buildAnnotatedString {
        var cursor = 0
        for (match in glossaryWords.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            val key = glossaryKey(match.value)
            val meaning = meanings[key]
            if (meaning == null) append(match.value) else {
                withLink(LinkAnnotation.Clickable(tag = key, styles = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)), linkInteractionListener = { onLookup(match.value, meaning) })) { append(match.value) }
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    Text(annotated, modifier = modifier, style = style)
}
