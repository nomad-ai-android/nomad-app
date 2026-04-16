package com.nomad.travel.tools

enum class ContextStrategy {
    /** Drop oldest (user,assistant) pairs from the history window until it fits. */
    DROP_OLDEST,

    /** When full, wipe all prior history so the next turn starts fresh. */
    RESET,

    /** When full, summarize the dropped-out portion with the same model and
     *  prepend the summary as a single system-flavoured note. */
    COMPACT;

    companion object {
        fun from(raw: String?): ContextStrategy = when (raw?.lowercase()) {
            "reset" -> RESET
            "compact" -> COMPACT
            else -> DROP_OLDEST
        }

        fun toKey(s: ContextStrategy): String = when (s) {
            DROP_OLDEST -> "drop_oldest"
            RESET -> "reset"
            COMPACT -> "compact"
        }
    }
}

/** Rough character-to-token estimate covering Latin, Korean, Japanese, Chinese. */
internal fun estimateTokens(text: String): Int = (text.length / 3).coerceAtLeast(1)

/** Conservative input token budget for Gemma 4 E2B/E4B (~4K ctx, reserve for output). */
internal const val DEFAULT_INPUT_TOKEN_BUDGET = 3000
