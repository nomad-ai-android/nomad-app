package com.nomad.travel.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsService(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready: Boolean = false
    private var pending: Pair<String, String>? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                pending?.let { (text, lang) ->
                    pending = null
                    speakInternal(text, lang)
                }
            }
        }
    }

    fun speak(text: String, languageCode: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending = text to languageCode
            return
        }
        speakInternal(text, languageCode)
    }

    fun stop() {
        pending = null
        tts?.stop()
    }

    fun shutdown() {
        pending = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun speakInternal(text: String, languageCode: String) {
        val engine = tts ?: return
        val locale = resolveLocale(languageCode)
        val available = runCatching { engine.isLanguageAvailable(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (available >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = locale
        }
        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "nomad-tts-${System.currentTimeMillis()}"
        )
    }

    private fun resolveLocale(code: String): Locale = when (code) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "ko" -> Locale.KOREAN
        "ja" -> Locale.JAPANESE
        "en" -> Locale.US
        else -> Locale.forLanguageTag(code)
    }
}
