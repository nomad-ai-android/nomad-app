package com.nomad.travel.ui.camera

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nomad.travel.NomadApp
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.tools.ToolRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import java.util.UUID

enum class QueueStatus { PENDING, PROCESSING, DONE, ERROR }

data class QueueItem(
    val id: String,
    val imagePath: String,
    val createdAtMs: Long,
    val status: QueueStatus,
    val answer: String = "",
    val errorMessage: String? = null
)

data class CameraSearchUiState(
    val queue: List<QueueItem> = emptyList(),
    val instantPreviewItemId: String? = null,
    val processingItemId: String? = null
)

class CameraSearchViewModel(
    private val router: ToolRouter,
    private val prefs: UserPrefs
) : ViewModel() {

    private val _state = MutableStateFlow(CameraSearchUiState())
    val state: StateFlow<CameraSearchUiState> = _state.asStateFlow()

    private var processorJob: Job? = null
    private var currentItemJob: Job? = null
    private var currentItemId: String? = null
    private var advanceSignal: CompletableDeferred<Unit>? = null

    /**
     * Serializes access to the LiteRT-LM engine. The engine cannot run two
     * inferences in parallel — a concurrent call during cancellation can
     * trigger a native crash. New items wait on this mutex while a cancelled
     * item is still releasing it.
     */
    private val engineMutex = Mutex()

    fun enqueueCapture(context: Context, imageFile: File) {
        viewModelScope.launch {
            val instant = prefs.cameraInstantPreviewBlocking()
            val item = QueueItem(
                id = UUID.randomUUID().toString(),
                imagePath = imageFile.absolutePath,
                createdAtMs = System.currentTimeMillis(),
                status = QueueStatus.PENDING
            )
            _state.update { s ->
                s.copy(
                    queue = s.queue + item,
                    instantPreviewItemId = if (instant) item.id else s.instantPreviewItemId
                )
            }
            ensureProcessor(context)
        }
    }

    fun deleteItem(id: String) {
        val wasProcessing = currentItemId == id
        _state.update { s ->
            val target = s.queue.firstOrNull { it.id == id }
            target?.let { runCatching { File(it.imagePath).delete() } }
            s.copy(
                queue = s.queue.filterNot { it.id == id },
                instantPreviewItemId = if (s.instantPreviewItemId == id) null else s.instantPreviewItemId,
                processingItemId = if (s.processingItemId == id) null else s.processingItemId
            )
        }
        if (wasProcessing) {
            // Advance the loop immediately so the UI moves on to the next
            // pending capture. We deliberately do NOT cancel the in-flight
            // coroutine — forcing LiteRT-LM to close its conversation
            // mid-stream can crash the JVM through the JNI layer. The
            // orphaned inference will drain in the background; its events
            // are dropped because the item is no longer in the queue, and
            // the next item waits on engineMutex until the conversation
            // closes naturally.
            currentItemId = null
            advanceSignal?.complete(Unit)
        }
    }

    fun moveUp(id: String) {
        _state.update { s ->
            val idx = s.queue.indexOfFirst { it.id == id }
            if (idx <= 0) return@update s
            val list = s.queue.toMutableList()
            val tmp = list[idx - 1]
            list[idx - 1] = list[idx]
            list[idx] = tmp
            s.copy(queue = list)
        }
    }

    fun moveDown(id: String) {
        _state.update { s ->
            val idx = s.queue.indexOfFirst { it.id == id }
            if (idx < 0 || idx >= s.queue.lastIndex) return@update s
            val list = s.queue.toMutableList()
            val tmp = list[idx + 1]
            list[idx + 1] = list[idx]
            list[idx] = tmp
            s.copy(queue = list)
        }
    }

    fun openInstantPreview(id: String) {
        _state.update { it.copy(instantPreviewItemId = id) }
    }

    fun closeInstantPreview() {
        _state.update { it.copy(instantPreviewItemId = null) }
    }

    private fun ensureProcessor(context: Context) {
        if (processorJob?.isActive == true) return
        val app = context.applicationContext
        processorJob = viewModelScope.launch {
            while (isActive) {
                val next = _state.value.queue.firstOrNull { it.status == QueueStatus.PENDING }
                    ?: break
                val signal = CompletableDeferred<Unit>()
                advanceSignal = signal
                currentItemId = next.id
                val job = launch {
                    try {
                        processItem(app, next.id)
                    } catch (_: CancellationException) {
                        // Expected when the item is deleted mid-flight.
                    } catch (e: Throwable) {
                        // Defensive: never let an unexpected failure crash
                        // the loop or the process. Mark the item failed.
                        runCatching {
                            updateItem(next.id) {
                                it.copy(
                                    status = QueueStatus.ERROR,
                                    errorMessage = e.message ?: "error"
                                )
                            }
                        }
                    } finally {
                        // Always release the loop once this item is fully
                        // resolved (success / error / cancellation).
                        signal.complete(Unit)
                    }
                }
                currentItemJob = job
                // The deferred is completed either by the child's finally
                // (natural completion) OR directly by deleteItem when the
                // active item is removed (immediate advance).
                signal.await()
            }
            _state.update { it.copy(processingItemId = null) }
            currentItemId = null
            currentItemJob = null
            advanceSignal = null
            processorJob = null
        }
    }

    private suspend fun processItem(context: Context, itemId: String) {
        val current = _state.value.queue.firstOrNull { it.id == itemId } ?: return
        val file = File(current.imagePath)
        if (!file.exists()) {
            updateItem(itemId) {
                it.copy(status = QueueStatus.ERROR, errorMessage = "Image file missing")
            }
            return
        }

        val uri: Uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrNull() ?: run {
            updateItem(itemId) {
                it.copy(status = QueueStatus.ERROR, errorMessage = "Cannot share image")
            }
            return
        }

        _state.update { s ->
            // Re-check that the item still exists; it may have been deleted
            // between dequeue and this point.
            if (s.queue.none { it.id == itemId }) return@update s
            s.copy(
                queue = s.queue.map { if (it.id == itemId) it.copy(status = QueueStatus.PROCESSING) else it },
                processingItemId = itemId
            )
        }
        if (_state.value.queue.none { it.id == itemId }) return

        val lang = runCatching { prefs.languageBlocking() }.getOrNull() ?: Locale.getDefault().language
        val prompt = defaultPromptFor(lang)

        try {
            // Serialize through the engine mutex so a previously cancelled
            // inference fully releases native resources before this one
            // starts. Without this, two coroutines can hit the LiteRT-LM
            // Engine concurrently and crash the JVM.
            engineMutex.withLock {
                // The item may have been deleted while we waited for the
                // mutex — drop the request entirely if so.
                if (_state.value.queue.none { it.id == itemId }) return@withLock
                router.handleStream(
                    context = context,
                    turn = ToolRouter.Turn(
                        userText = prompt,
                        imageUri = uri,
                        uiLanguage = lang,
                        history = emptyList()
                    )
                ).collect { evt ->
                    if (_state.value.queue.none { it.id == itemId }) return@collect
                    when (evt) {
                        is ToolRouter.StreamEvent.Delta -> {
                            updateItem(itemId) { it.copy(answer = evt.text) }
                        }
                        is ToolRouter.StreamEvent.Complete -> {
                            updateItem(itemId) {
                                it.copy(answer = evt.text, status = QueueStatus.DONE)
                            }
                        }
                    }
                }
            }
        } catch (ce: CancellationException) {
            // Item was deleted (or cancelled by the user). Don't try to
            // mutate the queue — the deleter already removed the item.
            // Re-throw so this child coroutine completes as cancelled.
            throw ce
        } catch (e: Throwable) {
            updateItem(itemId) {
                it.copy(status = QueueStatus.ERROR, errorMessage = e.message ?: "error")
            }
        } finally {
            _state.update { s ->
                if (s.processingItemId == itemId) s.copy(processingItemId = null) else s
            }
        }
    }

    private fun updateItem(id: String, block: (QueueItem) -> QueueItem) {
        _state.update { s ->
            s.copy(queue = s.queue.map { if (it.id == id) block(it) else it })
        }
    }

    override fun onCleared() {
        super.onCleared()
        processorJob?.cancel()
    }

    private fun defaultPromptFor(lang: String): String = when (lang) {
        "en" -> "Summarize what this photo is in one or two short sentences — key point only, no enumerating every detail. However, if foreign-language text in the photo looks like it needs translating, translate that text in detail into English. Friendly tone."
        "zh" -> "用一两句话简短概括这张照片是什么——只讲重点，不要逐项罗列细节。但如果照片里的外语文字看起来需要翻译，就把那部分内容用中文详细翻译出来。语气友好。"
        "ja" -> "この写真が何かを1〜2文の要点だけで簡潔に伝えてください — 細部を一つずつ並べないこと。ただし、写真内の外国語の文字が翻訳を必要としているように見える場合は、その部分は日本語で詳しく翻訳してください。親しみやすいトーンで。"
        else -> "이 사진이 무엇인지 한두 문장으로 핵심만 짧게 요약해줘. 보이는 걸 일일이 나열하지 말고 중요한 포인트만. 단, 사진 속 외국어 글자가 번역이 필요해 보이면 그 부분은 한국어로 자세히 번역해서 알려줘. 친근한 톤으로."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NomadApp
                return CameraSearchViewModel(
                    router = app.container.toolRouter,
                    prefs = app.container.prefs
                ) as T
            }
        }
    }
}
