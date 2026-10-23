package app.bheem.eval.speech

import app.bheem.models.AudioForm

enum class SpeechEngine { GEMMA, MOONSHINE }

object SpeechOptions {
    private val names = mapOf("file" to AudioForm.FILE, "wav" to AudioForm.WAV_BYTES, "pcm" to AudioForm.PCM_BYTES)

    fun forms(text: String?): List<AudioForm> {
        val parts = text.orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return listOf(AudioForm.FILE)
        if ("all" in parts) return AudioForm.entries.toList()
        return parts.map { names[it] ?: throw IllegalArgumentException("unknown audio form '$it', use one of: file, wav, pcm, all") }.distinct()
    }

    fun engine(value: String?): SpeechEngine = when (value?.lowercase()) {
        null, "", "gemma" -> SpeechEngine.GEMMA
        "moonshine" -> SpeechEngine.MOONSHINE
        else -> throw IllegalArgumentException("unknown speech engine '$value', use one of: gemma, moonshine")
    }

    fun switch(value: String?, default: Boolean): Boolean = when (value) {
        null -> default
        "on" -> true
        "off" -> false
        else -> throw IllegalArgumentException("expected on or off but got '$value'")
    }
}
