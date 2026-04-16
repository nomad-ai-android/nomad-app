package com.nomad.travel.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nomad.travel.NomadApp
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.data.chat.ChatRepository
import com.nomad.travel.llm.DeviceCapability
import com.nomad.travel.llm.DownloadStatus
import com.nomad.travel.llm.GemmaEngine
import com.nomad.travel.llm.ModelCatalog
import com.nomad.travel.llm.ModelDownloader
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.tools.ContextStrategy
import com.nomad.travel.ui.setup.ModelRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val language: String = "ko",
    val systemPrompt: String = "",
    val activeModelId: String = ModelCatalog.recommended.id,
    val modelRows: List<ModelRow> = emptyList(),
    val contextStrategy: ContextStrategy = ContextStrategy.DROP_OLDEST
)

class SettingsViewModel(
    private val prefs: UserPrefs,
    private val gemma: GemmaEngine,
    private val downloader: ModelDownloader,
    private val chatRepo: ChatRepository,
    private val device: DeviceCapability
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    private val statusesFlow = combine(
        ModelCatalog.all.map { downloader.status(it) }
    ) { arr -> arr.toList() }

    val state: StateFlow<SettingsUiState> = combine(
        combine(
            prefs.language,
            prefs.systemPrompt,
            prefs.activeModelId,
            prefs.contextStrategy
        ) { lang, prompt, activeId, strategy ->
            listOf(lang, prompt, activeId, strategy)
        },
        statusesFlow,
        refreshTick
    ) { base, statuses, _ ->
        val lang = base[0] as String?
        val prompt = base[1] as String?
        val activeId = base[2] as String?
        val strategy = base[3] as String?
        val rows = ModelCatalog.all.mapIndexed { i, entry ->
            ModelRow(
                entry = entry,
                downloaded = gemma.isDownloaded(entry),
                status = statuses.getOrNull(i) ?: DownloadStatus.Idle,
                ramEligible = device.isEligible(entry),
                ramWarning = device.shouldWarn(entry)
            )
        }
        SettingsUiState(
            language = lang ?: "ko",
            systemPrompt = prompt.orEmpty(),
            activeModelId = activeId ?: ModelCatalog.recommended.id,
            modelRows = rows,
            contextStrategy = ContextStrategy.from(strategy)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setLanguage(code: String) {
        viewModelScope.launch { prefs.setLanguage(code) }
    }

    fun setSystemPrompt(text: String) {
        viewModelScope.launch { prefs.setSystemPrompt(text) }
    }

    fun resetSystemPrompt() {
        viewModelScope.launch { prefs.setSystemPrompt("") }
    }

    fun setContextStrategy(strategy: ContextStrategy) {
        viewModelScope.launch { prefs.setContextStrategy(ContextStrategy.toKey(strategy)) }
    }

    fun clearChats() {
        viewModelScope.launch { chatRepo.deleteAllSessions() }
    }

    fun startDownload(entry: ModelEntry) {
        if (!device.isEligible(entry)) return
        if (!gemma.isDownloaded(entry)) downloader.start(entry, gemma.fileFor(entry))
    }

    fun cancelDownload(entry: ModelEntry) = downloader.cancel(entry)

    fun deleteModel(entry: ModelEntry) {
        gemma.delete(entry)
        refreshTick.value++
    }

    fun selectModel(entry: ModelEntry) {
        if (!device.isEligible(entry)) return
        if (!gemma.isDownloaded(entry)) return
        viewModelScope.launch {
            prefs.setActiveModelId(entry.id)
            gemma.reload()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NomadApp
                return SettingsViewModel(
                    prefs = app.container.prefs,
                    gemma = app.container.gemma,
                    downloader = app.container.downloader,
                    chatRepo = app.container.chatRepository,
                    device = app.container.device
                ) as T
            }
        }
    }
}
