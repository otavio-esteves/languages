package br.com.idiomas.data

enum class InteractionCue { Tap, Correct, Adjust, Recorded, Complete }

fun feedbackCue(outcome: String): InteractionCue = when (outcome) {
    "correct" -> InteractionCue.Correct
    "incorrect" -> InteractionCue.Adjust
    else -> InteractionCue.Recorded
}
