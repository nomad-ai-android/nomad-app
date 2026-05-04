package com.nomad.travel.tts

import android.content.Context
import com.nomad.travel.llm.ModelEntry
import java.io.File

/**
 * On-device neural TTS via Kokoro 82M (ONNX). The model file ships through the
 * standard [com.nomad.travel.llm.ModelDownloader] pipeline.
 *
 * Status: the model file can be downloaded today, but actual inference is NOT
 * wired up yet. Kokoro requires a phonemizer (espeak-ng/misaki) on-device,
 * which still needs to be ported to Android. Until that lands, [isReady]
 * always returns false so [TtsManager] falls back to [SystemTtsEngine].
 *
 * To make this engine functional later:
 *   1. Add `com.microsoft.onnxruntime:onnxruntime-android` to gradle.
 *   2. Bundle a phonemizer (Kotlin/JNI port of misaki, or pre-tokenized lexicon).
 *   3. Load `voice` embeddings + run ONNX session in [speak].
 *   4. Pipe waveform to [android.media.AudioTrack].
 *
 * Korean voice is currently absent from Kokoro v1.0 official voices, so
 * `supportsLanguage("ko")` returns false even after the engine is functional.
 */
class KokoroTtsEngine(context: Context) : TtsEngine {

    override val id: String = ID

    private val appContext = context.applicationContext

    override var onCompletion: (() -> Unit)? = null

    /** True if the model file is present on disk. Independent of [isReady]. */
    fun isModelDownloaded(entry: ModelEntry): Boolean =
        fileFor(entry).exists()

    fun fileFor(entry: ModelEntry): File =
        File(appContext.filesDir, "tts/${entry.fileName}")

    fun delete(entry: ModelEntry): Boolean = fileFor(entry).delete()

    override fun isReady(): Boolean {
        // TODO: return true once phonemizer + ONNX runtime are wired up and
        // the model file exists. For now we always defer to system engine.
        return false
    }

    override fun supportsLanguage(languageCode: String): Boolean = when (languageCode) {
        "en", "ja", "zh", "es", "fr", "hi", "it", "pt" -> true
        else -> false
    }

    override fun speak(text: String, languageCode: String) {
        // Inference not implemented yet — silently no-op so callers can defer
        // to the system engine via TtsManager.
    }

    override fun stop() {
        // No-op until inference lands.
    }

    override fun shutdown() {
        // No-op until inference lands.
    }

    companion object {
        const val ID = "kokoro"
    }
}
