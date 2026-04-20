package com.nomad.travel.llm

/** Static catalog of on-device models supported by Nomad. */
data class ModelEntry(
    val id: String,
    val displayName: String,
    val shortName: String,
    val sizeBytes: Long,
    val url: String,
    val fileName: String,
    val recommended: Boolean,
    val tagline: String,
    val badges: List<String>,
    /** Hard lower bound — below this the model must not be downloaded/loaded. 0 = no floor. */
    val minRamBytes: Long = 0L,
    /** Soft warning — below this the UI surfaces a performance advisory. 0 = no warning. */
    val warnRamBytes: Long = 0L
)

object ModelCatalog {

    private const val GB = 1024L * 1024L * 1024L

    val gemma4E2B = ModelEntry(
        id = "gemma-4-e2b",
        displayName = "기본형 · Gemma 2B",
        shortName = "기본형",
        sizeBytes = 2_580_000_000L,
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        fileName = "gemma-4-E2B-it.litertlm",
        recommended = true,
        tagline = "대부분의 기기에서 빠르고 안정적으로 작동해요",
        badges = listOf("추천", "2.4 GB", "빠름"),
        minRamBytes = 4L * GB,
        warnRamBytes = 6L * GB
    )

    val gemma4E4B = ModelEntry(
        id = "gemma-4-e4b",
        displayName = "고급형 · Gemma 4B",
        shortName = "고급형",
        sizeBytes = 3_654_467_584L,
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        fileName = "gemma-4-E4B-it.litertlm",
        recommended = false,
        tagline = "더 섬세하고 정확한 답변을 원할 때 좋아요",
        badges = listOf("3.4 GB", "고성능"),
        minRamBytes = 8L * GB,
        warnRamBytes = 12L * GB
    )

    val all: List<ModelEntry> = listOf(gemma4E2B, gemma4E4B)

    val recommended: ModelEntry get() = all.first { it.recommended }

    fun byId(id: String?): ModelEntry? = all.firstOrNull { it.id == id }
}
