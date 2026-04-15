package com.nomad.travel.tools

/**
 * Builds a (system instruction, user message) pair for the LiteRT-LM Engine.
 * Chat templating is handled by the runtime — we supply plain prose.
 *
 * Base persona + capability/tool-tag rules are always applied. The user's
 * custom prompt from Settings is appended as *additional* instructions so
 * side-effects like expense logging keep working.
 */
object Prompt {

    private const val BASE_PERSONA =
        "You are Nomad, a concise on-device travel assistant. Capabilities: translate menus (any source language), " +
            "explain dishes, suggest places, answer travel questions, log expenses."

    private const val TOOL_RULES =
        "When the user logs a spend, append exactly one tag at the end in the form: " +
            "<EXPENSE amount=\"NUMBER\" currency=\"ISO\" category=\"food|transport|stay|misc\" note=\"SHORT\">. " +
            "For menu OCR, list each item with: original · translation · 1-line description."

    data class Built(val systemInstruction: String, val userMessage: String)

    fun build(
        uiLanguage: String,
        userText: String,
        ocrBlock: String?,
        customSystemPrompt: String? = null
    ): Built {
        val lang = langName(uiLanguage)
        val extra = customSystemPrompt?.trim().orEmpty()

        val systemInstruction = buildString {
            append(BASE_PERSONA)
            append(' ')
            append("Always answer in ")
            append(lang)
            append(". ")
            append(TOOL_RULES)
            if (extra.isNotEmpty()) {
                append(' ')
                append("Additional user instructions: ")
                append(extra)
            }
        }

        val userMessage = if (!ocrBlock.isNullOrBlank()) {
            buildString {
                append("[MENU OCR]\n")
                append(ocrBlock.trim())
                append("\n\n")
                append(userText.ifBlank { "Translate and explain each item." })
            }
        } else {
            userText
        }

        return Built(systemInstruction = systemInstruction, userMessage = userMessage)
    }

    private fun langName(code: String): String = when (code.lowercase()) {
        "ko" -> "Korean"
        "en" -> "English"
        "zh" -> "Chinese"
        "ja" -> "Japanese"
        else -> "English"
    }
}
