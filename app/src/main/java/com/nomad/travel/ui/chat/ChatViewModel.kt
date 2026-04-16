package com.nomad.travel.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nomad.travel.NomadApp
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.Role
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.data.chat.ChatRepository
import com.nomad.travel.data.chat.ChatSessionEntity
import com.nomad.travel.tools.ToolRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatUiState(
    val sessions: List<ChatSessionEntity> = emptyList(),
    val currentSessionId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isResponding: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val router: ToolRouter,
    private val repo: ChatRepository,
    private val prefs: UserPrefs
) : ViewModel() {

    private val currentSessionId = MutableStateFlow<Long?>(null)
    private val responding = MutableStateFlow(false)
    /** In-memory streaming/pending message layered on top of persisted messages. */
    private val overlay = MutableStateFlow<ChatMessage?>(null)
    private var streamJob: Job? = null

    private val persistedMessages = currentSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeMessages(id)
    }

    val state: StateFlow<ChatUiState> = combine(
        repo.observeSessions(),
        currentSessionId,
        persistedMessages,
        responding,
        overlay
    ) { sessions, id, msgs, busy, live ->
        val combined = if (live != null) msgs + live else msgs
        ChatUiState(
            sessions = sessions,
            currentSessionId = id,
            messages = combined,
            isResponding = busy
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    init {
        viewModelScope.launch {
            val last = prefs.lastSessionIdBlocking()
            val resolved = last?.takeIf { repo.sessionExists(it) }
                ?: repo.mostRecentSessionId()
                ?: repo.createSession(DEFAULT_TITLE)
            currentSessionId.value = resolved
            prefs.setLastSessionId(resolved)
        }
    }

    fun switchSession(id: Long) {
        if (currentSessionId.value == id) return
        overlay.value = null
        currentSessionId.value = id
        viewModelScope.launch { prefs.setLastSessionId(id) }
    }

    fun newSession() {
        viewModelScope.launch {
            overlay.value = null
            val id = repo.createSession(DEFAULT_TITLE)
            currentSessionId.value = id
            prefs.setLastSessionId(id)
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repo.deleteSession(id)
            if (currentSessionId.value == id) {
                val next = repo.mostRecentSessionId() ?: repo.createSession(DEFAULT_TITLE)
                currentSessionId.value = next
                prefs.setLastSessionId(next)
                overlay.value = null
            }
        }
    }

    fun send(context: Context, text: String, image: Uri?) {
        if (text.isBlank() && image == null) return
        if (responding.value) return
        val sessionId = currentSessionId.value ?: return

        streamJob = viewModelScope.launch {
            val historySnapshot = repo.snapshotMessages(sessionId)
                .filter { it.toolTag != "cancelled" }
            val isFirstTurn = historySnapshot.isEmpty()
            repo.appendMessage(sessionId, Role.USER, text, image)
            overlay.value = ChatMessage(
                role = Role.ASSISTANT,
                text = "",
                pending = true,
                streaming = false
            )
            responding.value = true
            applyFallbackTitle(sessionId, isFirstTurn, text)

            try {
                router.handleStream(
                    context = context,
                    turn = ToolRouter.Turn(
                        userText = text,
                        imageUri = image,
                        uiLanguage = Locale.getDefault().language,
                        history = historySnapshot
                    )
                ).collect { evt ->
                    when (evt) {
                        is ToolRouter.StreamEvent.Delta -> {
                            overlay.update { cur ->
                                (cur ?: ChatMessage(role = Role.ASSISTANT, text = "")).copy(
                                    text = evt.text,
                                    pending = false,
                                    streaming = true
                                )
                            }
                        }
                        is ToolRouter.StreamEvent.Complete -> {
                            repo.appendMessage(
                                sessionId,
                                Role.ASSISTANT,
                                evt.text,
                                toolTag = evt.toolTag
                            )
                            overlay.value = null
                        }
                    }
                }
            } catch (ce: CancellationException) {
                val partial = overlay.value?.text.orEmpty()
                withContext(NonCancellable) {
                    repo.appendMessage(
                        sessionId,
                        Role.ASSISTANT,
                        partial,
                        toolTag = "cancelled"
                    )
                }
                overlay.value = null
                throw ce
            } catch (e: Throwable) {
                repo.appendMessage(
                    sessionId,
                    Role.ASSISTANT,
                    "⚠️ ${e.message ?: "error"}",
                    toolTag = "error"
                )
                overlay.value = null
            } finally {
                responding.value = false
            }
            if (isFirstTurn) {
                generateLlmTitle(sessionId, text)
            }
        }
    }

    fun cancelResponse() {
        streamJob?.cancel()
        streamJob = null
    }

    private suspend fun applyFallbackTitle(
        sessionId: Long,
        isFirstTurn: Boolean,
        firstUserText: String
    ) {
        if (!isFirstTurn) return
        val trimmed = firstUserText.trim()
        if (trimmed.isEmpty()) return
        val title = if (trimmed.length <= 28) trimmed else trimmed.take(28) + "…"
        repo.renameSession(sessionId, title)
    }

    private suspend fun generateLlmTitle(sessionId: Long, firstUserText: String) {
        val title = router.generateTitle(firstUserText)
        if (title.isNotBlank()) {
            repo.renameSession(sessionId, title)
        }
    }

    companion object {
        private const val DEFAULT_TITLE = "New chat"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NomadApp
                return ChatViewModel(
                    router = app.container.toolRouter,
                    repo = app.container.chatRepository,
                    prefs = app.container.prefs
                ) as T
            }
        }
    }
}
