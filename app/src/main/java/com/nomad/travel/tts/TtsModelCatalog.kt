package com.nomad.travel.tts

import com.nomad.travel.R
import com.nomad.travel.llm.ModelEntry

/** Downloadable TTS voice models. Separate from the LLM [com.nomad.travel.llm.ModelCatalog]. */
object TtsModelCatalog {

    val kokoroV1: ModelEntry = ModelEntry(
        id = "kokoro-82m-onnx-v1",
        displayName = "Kokoro 82M (ONNX)",
        shortName = "Kokoro",
        // ~330 MB FP16 ONNX. Real number depends on the chosen quantization.
        sizeBytes = 330_000_000L,
        url = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model.onnx",
        fileName = "kokoro-v1.0.onnx",
        recommended = false,
        tagline = "사람 같은 자연스러운 목소리 (영어/일본어/중국어 등)",
        taglineResId = R.string.model_tts_kokoro_tagline,
        badges = listOf("뉴럴", "330 MB", "프리뷰"),
        minRamBytes = 0L,
        warnRamBytes = 0L
    )

    /**
     * Korean neural TTS. Points at MyShell's MeloTTS-Korean checkpoint, which
     * is publicly hosted on Hugging Face (no auth, ~198 MB). Verified 200 OK
     * with bare HTTP clients (no User-Agent gating).
     * Used when the user's UI language is Korean (Kokoro v1 has no Korean voice).
     */
    val koreanNeural: ModelEntry = ModelEntry(
        id = "ko-neural-melo-v1",
        displayName = "한국어 자연 음성 (MeloTTS)",
        displayNameResId = R.string.model_tts_korean_name,
        shortName = "Korean TTS",
        // MeloTTS-Korean checkpoint.pth measured at 207,860,748 bytes.
        sizeBytes = 207_860_748L,
        url = "https://huggingface.co/myshell-ai/MeloTTS-Korean/resolve/main/checkpoint.pth",
        fileName = "melotts-ko-v1.pth",
        recommended = false,
        tagline = "한국어 전용 뉴럴 TTS — 시스템 음성보다 자연스러워요",
        taglineResId = R.string.model_tts_korean_tagline,
        badges = listOf("뉴럴", "한국어", "200 MB", "프리뷰"),
        minRamBytes = 0L,
        warnRamBytes = 0L
    )

    val all: List<ModelEntry> = listOf(kokoroV1, koreanNeural)

    /**
     * Pick the neural TTS model that fits the current UI language.
     * - Korean (`ko`) → Korean-specific model (Kokoro has no Korean voice)
     * - Anything else → Kokoro
     */
    fun forLanguage(languageCode: String?): ModelEntry =
        if (languageCode.equals("ko", ignoreCase = true)) koreanNeural else kokoroV1
}
