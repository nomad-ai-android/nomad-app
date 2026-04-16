package com.nomad.travel.tools

import android.content.Context
import android.net.Uri
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.data.expense.Expense
import com.nomad.travel.data.expense.ExpenseRepository
import com.nomad.travel.llm.GemmaEngine
import com.nomad.travel.ocr.OcrService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ToolRouter(
    private val gemma: GemmaEngine,
    private val ocr: OcrService,
    private val expenses: ExpenseRepository,
    private val prefs: UserPrefs
) {

    data class Turn(
        val userText: String,
        val imageUri: Uri? = null,
        val uiLanguage: String = "ko",
        /** Persisted prior messages for the current session, chronological. */
        val history: List<ChatMessage> = emptyList()
    )

    data class Reply(
        val visibleText: String,
        val toolTag: String? = null
    )

    sealed interface StreamEvent {
        data class Delta(val text: String) : StreamEvent
        data class Complete(val text: String, val toolTag: String?) : StreamEvent
    }

    fun handleStream(context: Context, turn: Turn): Flow<StreamEvent> = flow {
        if (!gemma.ensureLoaded()) {
            emit(StreamEvent.Complete(FALLBACK_NO_MODEL, "error"))
            return@flow
        }

        val ocrBlock: String? = turn.imageUri?.let { uri ->
            runCatching { ocr.recognize(context, uri) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        val baseTag = when {
            ocrBlock != null -> "menu_translate"
            looksLikeExpense(turn.userText) -> "expense"
            else -> "chat"
        }

        val customPrompt = prefs.systemPromptBlocking()?.takeIf { it.isNotBlank() }
        val strategy = ContextStrategy.from(prefs.contextStrategyBlocking())

        val window = Prompt.buildWindow(
            strategy = strategy,
            history = turn.history,
            pendingInputText = turn.userText,
            ocrBlock = ocrBlock,
            summarize = { dropped -> summarize(dropped) }
        )

        val built = Prompt.build(
            uiLanguage = turn.uiLanguage,
            userText = turn.userText,
            ocrBlock = ocrBlock,
            customSystemPrompt = customPrompt,
            window = window
        )

        var lastCumulative = ""
        gemma.generateStream(built.systemInstruction, built.userMessage).collect { cumulative ->
            lastCumulative = cumulative
            val cleaned = EXPENSE_TAG.replace(cumulative, "").trimEnd()
            emit(StreamEvent.Delta(cleaned))
        }

        val (visible, executed) = postProcess(lastCumulative)
        emit(
            StreamEvent.Complete(
                text = visible.ifBlank { lastCumulative },
                toolTag = executed ?: baseTag
            )
        )
    }

    suspend fun generateTitle(firstUserText: String): String {
        val trimmed = firstUserText.trim()
        if (trimmed.isEmpty()) return ""
        if (!gemma.ensureLoaded()) return ""
        return runCatching {
            gemma.generate(
                systemInstruction = "You generate chat titles. Read the user's first message and " +
                    "output a single short title (max 6 words) that captures its topic. " +
                    "Reply in the same language as the message. " +
                    "No quotes, no trailing punctuation, no preamble, no markdown.",
                userMessage = trimmed
            )
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
                .trim('"', '\'', ' ', '.', '。', '!', '?', '·', '-')
                .take(40)
        }.getOrDefault("")
    }

    private suspend fun summarize(messages: List<com.nomad.travel.data.ChatMessage>): String {
        if (messages.isEmpty()) return ""
        val transcript = messages.joinToString("\n") { m ->
            val who = if (m.role == com.nomad.travel.data.Role.USER) "User" else "Assistant"
            "$who: ${m.text.trim()}"
        }
        return runCatching {
            gemma.generate(
                systemInstruction = "You compress conversations. Output a compact 2-5 sentence " +
                    "summary that preserves user intents, names, places, dates, and decisions. " +
                    "No preamble, no markdown, no bullets.",
                userMessage = transcript
            ).trim()
        }.getOrDefault("")
    }

    private suspend fun postProcess(raw: String): Pair<String, String?> {
        val match = EXPENSE_TAG.find(raw) ?: return raw to null
        val attrs = parseAttrs(match.value)
        val amount = attrs["amount"]?.toDoubleOrNull() ?: return raw to null
        val currency = attrs["currency"]?.uppercase() ?: "USD"
        val category = attrs["category"] ?: "misc"
        val note = attrs["note"].orEmpty()
        expenses.add(Expense(amount = amount, currency = currency, category = category, note = note))
        val cleaned = raw.replace(match.value, "").trim()
        return cleaned to "expense"
    }

    private fun parseAttrs(tag: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        ATTR.findAll(tag).forEach { m ->
            out[m.groupValues[1]] = m.groupValues[2]
        }
        return out
    }

    private fun looksLikeExpense(text: String): Boolean {
        val lower = text.lowercase()
        return EXPENSE_HINTS.any { lower.contains(it) }
    }

    private fun looksLikeMenuSearch(text: String): Boolean {
        val lower = text.lowercase()
        return MENU_HINTS.any { lower.contains(it) }
    }

    companion object {
        private val EXPENSE_TAG = Regex("<EXPENSE[^>]*>")
        private val ATTR = Regex("(\\w+)=\"([^\"]*)\"")
        private val EXPENSE_HINTS = listOf(
            "지출", "썼어", "결제", "샀어", "환율",
            "spent", "paid", "bought", "expense",
            "花了", "支出",
            "使った", "払った", "支出"
        )
        private val MENU_HINTS = listOf(
            "메뉴", "음식", "요리",
            "menu", "dish", "food",
            "菜", "料理",
            "メニュー", "料理"
        )
        private const val FALLBACK_NO_MODEL =
            "온디바이스 모델이 아직 준비되지 않았습니다. 설정에서 Gemma 모델 파일을 추가해주세요."
    }
}
