package com.nomad.travel.tts

import android.content.Context
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.llm.ModelEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Routes [TtsEngine.speak] calls to the right backend based on the user's
 * preference and language support.
 *
 * Routing rules:
 *  - Korean (`ko`) — always system engine. Kokoro v1.0 has no Korean voice.
 *  - Other languages — Kokoro if user opted in AND the engine reports ready,
 *    otherwise fall back to system.
 */
class TtsManager(
    context: Context,
    private val prefs: UserPrefs,
    val systemEngine: SystemTtsEngine = SystemTtsEngine(context),
    val kokoroEngine: KokoroTtsEngine = KokoroTtsEngine(context)
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferredEngineId = MutableStateFlow(SystemTtsEngine.ID)
    val preferredEngine: StateFlow<String> = preferredEngineId.asStateFlow()

    private var lastEngine: TtsEngine = systemEngine

    /** External callers (e.g. conversation mode) listen here for utterance completion. */
    var onSpeakComplete: (() -> Unit)? = null

    init {
        scope.launch {
            prefs.ttsEngine.collect { id ->
                preferredEngineId.value = id ?: SystemTtsEngine.ID
            }
        }
        // Wire engine completion → manager-level callback.
        val forward: () -> Unit = { onSpeakComplete?.invoke() }
        systemEngine.onCompletion = forward
        kokoroEngine.onCompletion = forward
    }

    fun speak(text: String, languageCode: String) {
        if (text.isBlank()) return
        val engine = pickEngine(languageCode)
        lastEngine = engine
        engine.speak(text, languageCode)
    }

    fun stop() {
        systemEngine.stop()
        kokoroEngine.stop()
    }

    fun shutdown() {
        systemEngine.shutdown()
        kokoroEngine.shutdown()
    }

    suspend fun setPreferredEngine(id: String) {
        prefs.setTtsEngine(id)
    }

    /** Filesystem location where the downloader should place [entry]. */
    fun fileFor(entry: ModelEntry): File = kokoroEngine.fileFor(entry)

    fun isModelDownloaded(entry: ModelEntry): Boolean =
        kokoroEngine.isModelDownloaded(entry)

    fun deleteModel(entry: ModelEntry): Boolean = kokoroEngine.delete(entry)

    private fun pickEngine(languageCode: String): TtsEngine {
        val ko = languageCode.equals("ko", ignoreCase = true)
        if (ko) return systemEngine
        val pref = preferredEngineId.value
        if (pref == KokoroTtsEngine.ID &&
            kokoroEngine.isReady() &&
            kokoroEngine.supportsLanguage(languageCode)
        ) {
            return kokoroEngine
        }
        return systemEngine
    }
}
