package com.example.apktranslatorpro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.apktranslatorpro.ApkTranslatorApp
import com.example.apktranslatorpro.core.translation.MlKitTranslationEngine
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.OfflineModelState
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class QueuedTranslationJob(
    val id: String,
    val fileLabel: String,
    val targetLocale: LanguageOption,
    val status: String = "Bekliyor",
    val translatedCount: Int = 0
)

data class LanguageManagerUiState(
    val availableMlKitCodes: List<String> = TranslateLanguage.getAllLanguages().sorted(),
    val modelStates: List<OfflineModelState> = emptyList(),
    val isRefreshing: Boolean = false,
    val selectedQueueLocales: Set<LanguageOption> = setOf(LanguageOption.TURKISH, LanguageOption.ENGLISH),
    val queuedResourceFiles: List<String> = listOf("res/values/strings.xml"),
    val bulkQueueJobs: List<QueuedTranslationJob> = emptyList(),
    val isProcessingQueue: Boolean = false,
    val queueProgress: Float = 0f,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * Manages ML Kit [TranslateLanguage] offline model status/downloads/deletions
 * and multi-locale bulk resource file translation queues.
 */
class LanguageManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ApkTranslatorApp
    private val mlKitEngine: MlKitTranslationEngine = app.mlKitEngine
    private val repository = app.repository
    private val settingsStore = app.settingsDataStore

    private val _state = MutableStateFlow(LanguageManagerUiState())
    val state: StateFlow<LanguageManagerUiState> = _state.asStateFlow()

    init {
        refreshModels()
    }

    fun refreshModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
            val states = runCatching { mlKitEngine.listOfflineModelStates() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    modelStates = states,
                    isRefreshing = false
                )
            }
        }
    }

    fun downloadLanguageModel(language: LanguageOption) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { cur ->
                cur.copy(
                    modelStates = cur.modelStates.map {
                        if (it.language == language) it.copy(isDownloading = true, statusMessage = "İndiriliyor...") else it
                    }
                )
            }
            runCatching { mlKitEngine.downloadModel(language) }
                .onSuccess {
                    _state.update { it.copy(statusMessage = "${language.displayNameTr} ML Kit modeli indirildi.") }
                    refreshModels()
                }
                .onFailure { err ->
                    _state.update { cur ->
                        cur.copy(
                            errorMessage = err.message ?: "Model indirilemedi.",
                            modelStates = cur.modelStates.map {
                                if (it.language == language) {
                                    it.copy(isDownloading = false, statusMessage = err.message ?: "İndirme hatası")
                                } else it
                            }
                        )
                    }
                }
        }
    }

    fun deleteLanguageModel(language: LanguageOption) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { mlKitEngine.deleteModel(language) }
                .onSuccess {
                    _state.update { it.copy(statusMessage = "${language.displayNameTr} modeli silindi.") }
                    refreshModels()
                }
                .onFailure { err ->
                    _state.update { it.copy(errorMessage = err.message ?: "Model silinemedi.") }
                }
        }
    }

    fun toggleQueueLocale(language: LanguageOption) {
        if (language == LanguageOption.AUTO) return
        _state.update { cur ->
            val next = if (language in cur.selectedQueueLocales) {
                cur.selectedQueueLocales - language
            } else {
                cur.selectedQueueLocales + language
            }
            cur.copy(selectedQueueLocales = next)
        }
    }

    fun setQueuedResourceFiles(filePaths: List<String>) {
        val cleaned = filePaths.distinct().ifEmpty { listOf("res/values/strings.xml") }
        _state.update { it.copy(queuedResourceFiles = cleaned) }
    }

    fun toggleQueuedResourceFile(filePath: String) {
        _state.update { cur ->
            val current = cur.queuedResourceFiles.toMutableList()
            if (filePath in current) {
                if (current.size > 1) current.remove(filePath)
            } else {
                current.add(filePath)
            }
            cur.copy(queuedResourceFiles = current)
        }
    }

    fun buildBulkQueuePreview() {
        val cur = _state.value
        val jobs = cur.queuedResourceFiles.flatMap { path ->
            cur.selectedQueueLocales.map { loc ->
                QueuedTranslationJob(
                    id = "$path::${loc.code}",
                    fileLabel = path,
                    targetLocale = loc,
                    status = "Kuyrukta bekliyor"
                )
            }
        }
        _state.update { it.copy(bulkQueueJobs = jobs, queueProgress = 0f) }
    }

    fun runBulkTranslationQueue(
        baseResources: List<ResourceEntryItem>,
        sourceLanguage: LanguageOption,
        workDir: File? = null
    ) {
        val cur = _state.value
        val locales = cur.selectedQueueLocales.filter { it != sourceLanguage && it != LanguageOption.AUTO }
        if (locales.isEmpty()) {
            _state.update { it.copy(errorMessage = "Lütfen kaynak dilden farklı en az bir hedef dil seçin.") }
            return
        }
        if (baseResources.isEmpty()) {
            _state.update { it.copy(errorMessage = "Toplu kuyruk için önce bir APK inceleyin.") }
            return
        }

        buildBulkQueuePreview()
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessingQueue = true, queueProgress = 0.05f, errorMessage = null) }
            val jobsSnapshot = _state.value.bulkQueueJobs.toMutableList()
            val total = jobsSnapshot.size.coerceAtLeast(1)

            val activeSettings = settingsStore.settingsFlow.first()

            jobsSnapshot.forEachIndexed { idx, job ->
                jobsSnapshot[idx] = job.copy(status = "Çevriliyor...")
                _state.update { it.copy(bulkQueueJobs = jobsSnapshot.toList()) }

                runCatching {
                    val translated = repository.translateResources(
                        items = baseResources.take(120),
                        sourceLanguage = sourceLanguage,
                        targetLanguage = job.targetLocale,
                        mode = activeSettings.translationMode,
                        onlineConfig = activeSettings.onlineApiConfig,
                        protectionConfig = activeSettings.protectionConfig,
                        scopeConfig = activeSettings.resourceScopeConfig,
                        onProgress = { _, _ -> },
                        onLog = { _, _, _ -> }
                    )
                    if (workDir != null) {
                        val outDir = File(workDir, "res/values-${job.targetLocale.code}")
                        outDir.mkdirs()
                        File(outDir, File(job.fileLabel).name.ifBlank { "strings.xml" }).writeText(
                            com.example.apktranslatorpro.core.apk.ValuesXmlCodec.generateStringsXml(
                                translated,
                                useTranslated = true
                            ),
                            Charsets.UTF_8
                        )
                    }
                    translated.count { it.translatedText.isNotBlank() }
                }.onSuccess { count ->
                    jobsSnapshot[idx] = job.copy(status = "Tamamlandı ($count metin)", translatedCount = count)
                }.onFailure { err ->
                    jobsSnapshot[idx] = job.copy(status = "Hata: ${err.message?.take(40)}")
                }

                val progress = (idx + 1).toFloat() / total.toFloat()
                _state.update {
                    it.copy(
                        bulkQueueJobs = jobsSnapshot.toList(),
                        queueProgress = progress
                    )
                }
            }

            _state.update {
                it.copy(
                    isProcessingQueue = false,
                    queueProgress = 1f,
                    statusMessage = "Toplu dil kuyruğu tamamlandı (${jobsSnapshot.size} görev)."
                )
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                LanguageManagerViewModel(app)
            }
        }
    }
}
