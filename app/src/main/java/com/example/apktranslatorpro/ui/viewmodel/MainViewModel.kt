package com.example.apktranslatorpro.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import com.example.apktranslatorpro.ApkTranslatorApp
import com.example.apktranslatorpro.core.apk.ApkIconExtractor
import com.example.apktranslatorpro.core.apk.ValuesXmlCodec
import com.example.apktranslatorpro.core.text.TranslationProtectionConfig
import com.example.apktranslatorpro.core.text.TranslationQualityTools
import com.example.apktranslatorpro.core.work.BackgroundTranslationWorker
import com.example.apktranslatorpro.data.local.AppUiLanguage
import com.example.apktranslatorpro.data.local.AppUserSettings
import com.example.apktranslatorpro.data.local.JobHistoryEntity
import com.example.apktranslatorpro.data.local.RecentApkEntity
import com.example.apktranslatorpro.data.local.TranslationProjectEntity
import com.example.apktranslatorpro.data.local.UserGlossaryEntity
import com.example.apktranslatorpro.data.repository.InspectedApkSession
import com.example.apktranslatorpro.domain.model.ApkMetadata
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.LanguageResourceScopeConfig
import com.example.apktranslatorpro.domain.model.LogEntry
import com.example.apktranslatorpro.domain.model.LogLevel
import com.example.apktranslatorpro.domain.model.OfflineModelState
import com.example.apktranslatorpro.domain.model.OnlineApiConfig
import com.example.apktranslatorpro.domain.model.OutputApkResult
import com.example.apktranslatorpro.domain.model.PipelineStage
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.ResourceKind
import com.example.apktranslatorpro.domain.model.TranslationMode
import com.example.apktranslatorpro.domain.model.TranslationOrigin
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "MainViewModel"

sealed interface GeminiAnalysisUiState {
    data object Idle : GeminiAnalysisUiState
    data object Loading : GeminiAnalysisUiState
    data class Streaming(val text: String) : GeminiAnalysisUiState
    data class Success(val text: String) : GeminiAnalysisUiState
    data class Error(val message: String) : GeminiAnalysisUiState
}

data class MainUiState(
    val isInspecting: Boolean = false,
    val isRunning: Boolean = false,
    val stage: PipelineStage = PipelineStage.IDLE,
    val progress: Float = 0f,
    val progressDetail: String = "",
    val selectedApkUri: String? = null,
    val safFileVerified: Boolean = false,
    val safVerificationSummary: String = "",
    val apkMetadata: ApkMetadata? = null,
    val resources: List<ResourceEntryItem> = emptyList(),
    val selectedSourceLanguage: LanguageOption = LanguageOption.AUTO,
    val targetLanguage: LanguageOption = LanguageOption.TURKISH,
    val logs: List<LogEntry> = emptyList(),
    val outputResult: OutputApkResult? = null,
    val errorMessage: String? = null,
    val showOnlineConsentDialog: Boolean = false,
    val offlineModels: List<OfflineModelState> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isTestingApi: Boolean = false,
    val apiTestMessage: String? = null,
    val signerCertSha256: String = "",
    val statusBannerMessage: String? = null,
    val selectedBinaryXmlPath: String = "AndroidManifest.xml",
    val selectedBinaryXmlPreview: String = "",
    val geminiAnalysisState: GeminiAnalysisUiState = GeminiAnalysisUiState.Idle,
    val appIconBitmap: ImageBitmap? = null,
    val batchApkUris: List<String> = emptyList(),
    val isBatchRunning: Boolean = false,
    val batchStatusSummary: String = "",
    val comparisonApkMetadata: ApkMetadata? = null,
    val isInspectingComparisonApk: Boolean = false,
    val backgroundWorkStatus: String = "Hazır",
    val isBackgroundWorkRunning: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ApkTranslatorApp
    private val repository = app.repository
    private val settingsStore = app.settingsDataStore
    private val recentApkRepository = app.recentApkRepository
    private val projectRepository = app.projectRepository
    private val mlKitEngine = app.mlKitEngine
    private val onlineEngine = app.onlineEngine
    private val signerEngine = app.signerEngine

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val settingsState: StateFlow<AppUserSettings> = settingsStore.settingsFlow
        .catch { emit(AppUserSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUserSettings())

    val cacheCountState: StateFlow<Int> = repository.cacheCountFlow
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentJobsState: StateFlow<List<JobHistoryEntity>> = repository.recentJobsFlow
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentApksState: StateFlow<List<RecentApkEntity>> = recentApkRepository.recentApksFlow
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedProjectsState: StateFlow<List<TranslationProjectEntity>> = projectRepository.projectsFlow
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userGlossaryState: StateFlow<List<UserGlossaryEntity>> = projectRepository.glossaryFlow
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentSession: InspectedApkSession? = null
    private var activeJob: Job? = null
    private var geminiJob: Job? = null
    private val logSeq = AtomicLong(0L)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        refreshSignerInfo()
        refreshOfflineModels()
        observeBackgroundWorkSafe()
    }

    private fun observeBackgroundWorkSafe() {
        viewModelScope.launch {
            runCatching {
                BackgroundTranslationWorker.observeWorkStatus(app).collect { info ->
                    if (info != null) {
                        val running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
                        val label = when (info.state) {
                            WorkInfo.State.ENQUEUED -> "Kuyrukta bekliyor"
                            WorkInfo.State.RUNNING -> info.progress.getString(BackgroundTranslationWorker.KEY_STATUS)
                                ?: "Arka planda çalışıyor..."
                            WorkInfo.State.SUCCEEDED -> info.outputData.getString(BackgroundTranslationWorker.KEY_STATUS)
                                ?: "Tamamlandı"
                            WorkInfo.State.FAILED -> info.outputData.getString(BackgroundTranslationWorker.KEY_STATUS)
                                ?: "Başarısız"
                            WorkInfo.State.CANCELLED -> "Duraklatıldı / İptal edildi"
                            WorkInfo.State.BLOCKED -> "Ağ / Sistem kısıtı bekleniyor"
                        }
                        _uiState.update {
                            it.copy(
                                isBackgroundWorkRunning = running,
                                backgroundWorkStatus = label
                            )
                        }
                    }
                }
            }
        }
    }

    fun refreshSignerInfo() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { signerEngine.getOrCreateSignerIdentity() }
            }.onSuccess { identity ->
                _uiState.update { it.copy(signerCertSha256 = identity.certSha256) }
            }
        }
    }

    fun regenerateSignerKey() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { signerEngine.regenerateSignerIdentity() }
            }
                .onSuccess { identity ->
                    _uiState.update {
                        it.copy(
                            signerCertSha256 = identity.certSha256,
                            statusBannerMessage = "Yeni RSA-2048 imzalama anahtarı oluşturuldu."
                        )
                    }
                    appendLog(LogLevel.INFO, PipelineStage.IDLE, "Yeni yerel PKCS#12 imza anahtarı üretildi.")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun onApkSelected(uri: Uri) {
        activeJob?.cancel()
        geminiJob?.cancel()
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        activeJob = viewModelScope.launch {
            settingsStore.setSelectedApkUri(uri.toString())
            _uiState.update {
                it.copy(
                    isInspecting = true,
                    selectedApkUri = uri.toString(),
                    safFileVerified = false,
                    safVerificationSummary = "SAF URI doğrulanıyor...",
                    stage = PipelineStage.VERIFYING_INPUT,
                    progress = 0.1f,
                    progressDetail = "APK inceleniyor...",
                    errorMessage = null,
                    outputResult = null,
                    statusBannerMessage = null,
                    geminiAnalysisState = GeminiAnalysisUiState.Idle
                )
            }
            try {
                val session = repository.inspectApkFromUri(uri) { level, stage, msg ->
                    appendLog(level, stage, msg)
                    _uiState.update { it.copy(stage = stage, progressDetail = msg) }
                }
                currentSession = session
                val meta = session.metadata
                val extractedIcon = runCatching { ApkIconExtractor.extractAppIcon(app, session.workingInputApk) }.getOrNull()
                recentApkRepository.recordApk(
                    RecentApkEntity(
                        sha256Hex = meta.sha256,
                        fileName = meta.fileName,
                        originalUriOrPath = uri.toString(),
                        packageName = meta.packageName,
                        appLabel = meta.appLabel,
                        fileSizeBytes = meta.fileSizeBytes,
                        lastAction = "İncelendi (${meta.versionName})"
                    )
                )
                val detected = meta.detectedSourceLanguage
                val defaultTarget = if (detected == LanguageOption.TURKISH) {
                    LanguageOption.ENGLISH
                } else {
                    LanguageOption.TURKISH
                }
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        safFileVerified = true,
                        safVerificationSummary = "SAF dosyası doğrulandı (değiştirilmedi) • SHA-256: ${session.metadata.sha256.take(16)}...",
                        stage = PipelineStage.IDLE,
                        progress = 0f,
                        progressDetail = "",
                        apkMetadata = session.metadata,
                        appIconBitmap = extractedIcon,
                        resources = session.resources,
                        selectedSourceLanguage = LanguageOption.AUTO,
                        targetLanguage = defaultTarget,
                        selectedBinaryXmlPath = "AndroidManifest.xml",
                        selectedBinaryXmlPreview = session.metadata.manifestXmlPreview
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                val msg = ex.message ?: "APK analizi sırasında beklenmeyen hata oluştu."
                appendLog(LogLevel.ERROR, PipelineStage.ERROR, msg)
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        safFileVerified = false,
                        safVerificationSummary = "SAF doğrulaması başarısız: $msg",
                        stage = PipelineStage.ERROR,
                        errorMessage = msg
                    )
                }
            }
        }
    }

    fun verifySelectedSafFileIntegrity() {
        val uriStr = _uiState.value.selectedApkUri ?: _uiState.value.apkMetadata?.uriString
        if (uriStr.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Doğrulanacak bir SAF APK URI seçilmedi.") }
            return
        }
        viewModelScope.launch {
            try {
                val uri = Uri.parse(uriStr)
                val expected = _uiState.value.apkMetadata?.sha256
                val check = repository.verifySafUriReadOnly(uri)
                val matches = expected.isNullOrBlank() || check.sha256Hex.equals(expected, ignoreCase = true)
                val summary = if (matches) {
                    "Bütünlük doğrulandı (${check.displayName}, ${check.sizeBytes} B) • SHA-256: ${check.sha256Hex.take(20)}..."
                } else {
                    "UYARI: Seçilen SAF dosyası değiştirilmiş görünüyor!"
                }
                appendLog(
                    if (matches) LogLevel.SUCCESS else LogLevel.WARN,
                    PipelineStage.IDLE,
                    summary
                )
                _uiState.update {
                    it.copy(
                        safFileVerified = matches,
                        safVerificationSummary = summary,
                        statusBannerMessage = summary
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                val msg = ex.message ?: "SAF dosyası doğrulanamadı."
                appendLog(LogLevel.ERROR, PipelineStage.ERROR, msg)
                _uiState.update {
                    it.copy(
                        safFileVerified = false,
                        safVerificationSummary = msg,
                        errorMessage = msg
                    )
                }
            }
        }
    }

    fun runGeminiApkAnalysis(onComplete: ((String) -> Unit)? = null) {
        val metadata = _uiState.value.apkMetadata
        if (metadata == null) {
            _uiState.update { it.copy(errorMessage = "Gemini analizi için önce bir APK seçin.") }
            return
        }
        geminiJob?.cancel()
        geminiJob = viewModelScope.launch {
            _uiState.update { it.copy(geminiAnalysisState = GeminiAnalysisUiState.Loading) }
            var latestText = ""
            try {
                repository.streamGeminiApkAnalysis(metadata).collect { accumulated ->
                    latestText = accumulated
                    _uiState.update { it.copy(geminiAnalysisState = GeminiAnalysisUiState.Streaming(accumulated)) }
                }
                if (latestText.isNotBlank()) {
                    _uiState.update { it.copy(geminiAnalysisState = GeminiAnalysisUiState.Success(latestText)) }
                    appendLog(LogLevel.SUCCESS, PipelineStage.IDLE, "Gemini AI APK güvenlik ve yerelleştirme analizi tamamlandı.")
                    onComplete?.invoke(latestText)
                } else {
                    _uiState.update {
                        it.copy(geminiAnalysisState = GeminiAnalysisUiState.Error("Gemini analiz yanıtı boş döndü."))
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Log.e(TAG, "Gemini APK analysis failed", ex)
                val errMsg = ex.localizedMessage ?: "Gemini AI analizi başarısız oldu."
                appendLog(LogLevel.ERROR, PipelineStage.IDLE, errMsg)
                _uiState.update {
                    it.copy(geminiAnalysisState = GeminiAnalysisUiState.Error(errMsg))
                }
            }
        }
    }

    fun inspectBinaryXmlEntry(entryPath: String) {
        val session = currentSession ?: return
        viewModelScope.launch {
            runCatching { repository.decodeBinaryXmlEntry(session, entryPath) }
                .onSuccess { xml ->
                    _uiState.update {
                        it.copy(
                            selectedBinaryXmlPath = entryPath,
                            selectedBinaryXmlPreview = xml
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = "XML çözümlenemedi: ${err.message}") }
                }
        }
    }

    fun onOutputFolderSelected(uri: Uri?, displayLabel: String) {
        viewModelScope.launch {
            if (uri != null) {
                runCatching {
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
            settingsStore.setOutputDirectory(uri?.toString(), displayLabel)
            appendLog(LogLevel.INFO, PipelineStage.IDLE, "Çıktı klasörü güncellendi: $displayLabel")
        }
    }

    fun setSourceLanguage(lang: LanguageOption) {
        _uiState.update { it.copy(selectedSourceLanguage = lang) }
    }

    fun setTargetLanguage(lang: LanguageOption) {
        if (lang != LanguageOption.AUTO) {
            _uiState.update { it.copy(targetLanguage = lang) }
        }
    }

    fun setTranslationMode(mode: TranslationMode) {
        viewModelScope.launch { settingsStore.setTranslationMode(mode) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setAppUiLanguage(language: AppUiLanguage) {
        viewModelScope.launch { settingsStore.setAppUiLanguage(language) }
    }

    fun enterGuestTrial() {
        viewModelScope.launch {
            if (!settingsState.value.isGuestTrialLimitReached) {
                settingsStore.setGuestTrialActive(true)
            }
        }
    }

    fun exitGuestTrial() {
        viewModelScope.launch { settingsStore.setGuestTrialActive(false) }
    }

    fun deleteRecentApk(sha256Hex: String) {
        viewModelScope.launch { recentApkRepository.deleteApk(sha256Hex) }
    }

    fun clearRecentApks() {
        viewModelScope.launch { recentApkRepository.clearAll() }
    }

    fun saveOnlineApiConfig(config: OnlineApiConfig) {
        viewModelScope.launch {
            settingsStore.saveOnlineApiConfig(config)
            _uiState.update { it.copy(statusBannerMessage = "API yapılandırması Android Keystore ile şifrelenerek kaydedildi.") }
        }
    }

    fun saveProtectionConfig(config: TranslationProtectionConfig) {
        viewModelScope.launch {
            settingsStore.saveProtectionConfig(config)
        }
    }

    fun saveResourceScopeConfig(config: LanguageResourceScopeConfig) {
        viewModelScope.launch {
            settingsStore.saveResourceScopeConfig(config)
            appendLog(
                LogLevel.INFO,
                PipelineStage.IDLE,
                if (config.strictStringsOnly) {
                    "Dil kapsamı güncellendi: Sadece (${config.describeScopes()}) çevrilecek."
                } else {
                    "Dil kapsamı: Tüm kaynak türleri (string, string-array, plurals)."
                }
            )
        }
    }

    fun addExcludedTerm(term: String) {
        val cleaned = term.trim()
        if (cleaned.isEmpty()) return
        val current = settingsState.value.protectionConfig
        if (current.excludedTerms.any { it.equals(cleaned, ignoreCase = true) }) return
        saveProtectionConfig(current.copy(excludedTerms = current.excludedTerms + cleaned))
    }

    fun removeExcludedTerm(term: String) {
        val current = settingsState.value.protectionConfig
        saveProtectionConfig(current.copy(excludedTerms = current.excludedTerms.filterNot { it == term }))
    }

    fun resetExcludedTermsToDefault() {
        val current = settingsState.value.protectionConfig
        saveProtectionConfig(current.copy(excludedTerms = TranslationProtectionConfig.DEFAULT_TECHNICAL_TERMS))
        _uiState.update { it.copy(statusBannerMessage = "Varsayılan teknik terim listesi geri yüklendi.") }
    }

    fun testOnlineApiConnection(config: OnlineApiConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingApi = true, apiTestMessage = null) }
            val result = onlineEngine.testConnection(config)
            _uiState.update {
                it.copy(
                    isTestingApi = false,
                    apiTestMessage = result.fold(
                        onSuccess = { msg -> msg },
                        onFailure = { err -> "Test başarısız: ${err.message}" }
                    )
                )
            }
        }
    }

    fun requestStartPipeline(isAuthenticatedUser: Boolean = false) {
        val session = currentSession
        if (session == null) {
            _uiState.update { it.copy(errorMessage = "Lütfen önce bir APK dosyası seçin.") }
            return
        }
        val settings = settingsState.value
        if (!isAuthenticatedUser && settings.isGuestTrialActive && settings.isGuestTrialLimitReached) {
            _uiState.update {
                it.copy(errorMessage = "Deneme sürümündeki 2 uygulama çeviri hakkınız doldu. Devam etmek için Google ile giriş yapın.")
            }
            viewModelScope.launch { settingsStore.setGuestTrialActive(false) }
            return
        }
        val needsConsent = settings.translationMode == TranslationMode.ONLINE_ONLY ||
            (settings.translationMode == TranslationMode.AUTO &&
                onlineEngine.isNetworkConnected() &&
                settings.onlineApiConfig.endpointUrl.isNotBlank())

        if (needsConsent) {
            _uiState.update { it.copy(showOnlineConsentDialog = true) }
        } else {
            runFullPipeline(isAuthenticatedUser = isAuthenticatedUser)
        }
    }

    fun confirmOnlineConsentAndStart(isAuthenticatedUser: Boolean = false) {
        _uiState.update { it.copy(showOnlineConsentDialog = false) }
        runFullPipeline(isAuthenticatedUser = isAuthenticatedUser)
    }

    fun dismissOnlineConsent() {
        _uiState.update { it.copy(showOnlineConsentDialog = false) }
    }

    fun cancelActivePipeline() {
        activeJob?.cancel()
        appendLog(LogLevel.WARN, PipelineStage.IDLE, "İşlem kullanıcı tarafından iptal edildi. Orijinal APK korundu.")
        _uiState.update {
            it.copy(
                isRunning = false,
                isInspecting = false,
                stage = PipelineStage.IDLE,
                progress = 0f,
                progressDetail = "İptal edildi"
            )
        }
    }

    private fun runFullPipeline(isAuthenticatedUser: Boolean = false) {
        val session = currentSession ?: return
        val state = _uiState.value
        val settings = settingsState.value
        val effectiveSource = if (state.selectedSourceLanguage == LanguageOption.AUTO) {
            session.metadata.detectedSourceLanguage
        } else {
            state.selectedSourceLanguage
        }
        val target = state.targetLanguage

        if (effectiveSource == target) {
            _uiState.update { it.copy(errorMessage = "Kaynak dil (${effectiveSource.displayNameTr}) ile hedef dil aynı olamaz.") }
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    stage = PipelineStage.TRANSLATING,
                    progress = 0.05f,
                    progressDetail = "Çeviri başlatılıyor (${effectiveSource.displayNameTr} -> ${target.displayNameTr})...",
                    errorMessage = null,
                    outputResult = null
                )
            }

            try {
                appendLog(
                    LogLevel.INFO,
                    PipelineStage.TRANSLATING,
                    "Çeviri başlatıldı: ${effectiveSource.displayNameTr} -> ${target.displayNameTr} [Mod: ${settings.translationMode.labelTr}]"
                )

                val translated = repository.translateResources(
                    items = state.resources,
                    sourceLanguage = effectiveSource,
                    targetLanguage = target,
                    mode = settings.translationMode,
                    onlineConfig = settings.onlineApiConfig,
                    protectionConfig = settings.protectionConfig,
                    scopeConfig = settings.resourceScopeConfig,
                    appName = session.metadata.appLabel,
                    packageName = session.metadata.packageName,
                    onProgress = { done, total ->
                        val frac = 0.05f + (done.toFloat() / total.toFloat()) * 0.65f
                        _uiState.update {
                            it.copy(
                                progress = frac,
                                progressDetail = "Çevriliyor: $done / $total"
                            )
                        }
                    },
                    onLog = { level, stage, msg -> appendLog(level, stage, msg) }
                )

                val glossaryMap = userGlossaryState.value.associate { it.sourceTerm to it.preferredTranslation }
                val finalTranslated = if (glossaryMap.isEmpty()) {
                    translated
                } else {
                    translated.map { entry ->
                        if (entry.isLocked || entry.translatedText.isBlank()) entry
                        else entry.copy(
                            translatedText = TranslationQualityTools.applyCustomGlossary(entry.translatedText, glossaryMap)
                        )
                    }
                }

                _uiState.update { it.copy(resources = finalTranslated) }

                val result = repository.rebuildSignAndVerifyApk(
                    session = session,
                    translatedItems = finalTranslated,
                    sourceLanguage = effectiveSource,
                    targetLanguage = target,
                    outputTreeUriString = settings.outputTreeUri,
                    scopeConfig = settings.resourceScopeConfig,
                    onStage = { stage ->
                        val p = when (stage) {
                            PipelineStage.REBUILDING_RESOURCES -> 0.74f
                            PipelineStage.PACKAGING_APK -> 0.82f
                            PipelineStage.SIGNING_APK -> 0.90f
                            PipelineStage.VERIFYING_OUTPUT -> 0.95f
                            PipelineStage.SAVING_OUTPUT -> 0.98f
                            PipelineStage.COMPLETED -> 1.0f
                            else -> _uiState.value.progress
                        }
                        _uiState.update {
                            it.copy(
                                stage = stage,
                                progress = p,
                                progressDetail = stage.labelTr
                            )
                        }
                    },
                    onLog = { level, stage, msg -> appendLog(level, stage, msg) }
                )

                recentApkRepository.recordApk(
                    RecentApkEntity(
                        sha256Hex = session.metadata.sha256,
                        fileName = result.fileName,
                        originalUriOrPath = session.metadata.uriString,
                        packageName = session.metadata.packageName,
                        appLabel = session.metadata.appLabel,
                        fileSizeBytes = session.metadata.fileSizeBytes,
                        outputPath = result.workspaceFilePath,
                        lastAction = "Çevrildi & İmzalandı (${target.code.uppercase()})"
                    )
                )

                var guestLimitNote: String? = null
                if (!isAuthenticatedUser && settings.isGuestTrialActive) {
                    settingsStore.incrementGuestTranslationsUsed()
                    val nextUsed = settings.guestTranslationsUsed + 1
                    guestLimitNote = if (nextUsed >= AppUserSettings.MAX_GUEST_TRIAL_TRANSLATIONS) {
                        "Deneme hakkınız doldu (${nextUsed}/2). Yeni çeviri için Google ile giriş yapmalısınız."
                    } else {
                        "Deneme çevirisi tamamlandı (${nextUsed}/2 kullanıldı)."
                    }
                }

                _uiState.update {
                    it.copy(
                        isRunning = false,
                        stage = PipelineStage.COMPLETED,
                        progress = 1f,
                        progressDetail = "APK başarıyla oluşturuldu ve imzalandı!",
                        outputResult = result,
                        statusBannerMessage = guestLimitNote
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                val msg = ex.message ?: "Çeviri veya APK oluşturma sırasında hata oluştu."
                appendLog(LogLevel.ERROR, PipelineStage.ERROR, "$msg (Orijinal APK korunuyor)")
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        stage = PipelineStage.ERROR,
                        errorMessage = msg
                    )
                }
            }
        }
    }

    fun updateResourceTranslation(uniqueKey: String, newTranslation: String) {
        _uiState.update { current ->
            val updated = current.resources.map { item ->
                if (item.uniqueKey == uniqueKey && !item.isLocked) {
                    item.copy(
                        previousTranslatedText = item.translatedText,
                        translatedText = newTranslation,
                        origin = TranslationOrigin.MANUAL_EDIT
                    )
                } else {
                    item
                }
            }
            current.copy(resources = updated)
        }
    }

    fun toggleLockEntry(uniqueKey: String) {
        _uiState.update { current ->
            val updated = current.resources.map { item ->
                if (item.uniqueKey == uniqueKey) item.copy(isLocked = !item.isLocked) else item
            }
            current.copy(resources = updated)
        }
    }

    fun revertToPreviousTranslation(uniqueKey: String) {
        _uiState.update { current ->
            val updated = current.resources.map { item ->
                if (item.uniqueKey == uniqueKey && !item.isLocked && item.previousTranslatedText.isNotBlank()) {
                    item.copy(
                        translatedText = item.previousTranslatedText,
                        previousTranslatedText = item.translatedText,
                        origin = TranslationOrigin.MANUAL_EDIT
                    )
                } else {
                    item
                }
            }
            current.copy(resources = updated, statusBannerMessage = "Önceki çeviriye dönüldü.")
        }
    }

    fun searchAndReplaceTranslations(find: String, replace: String, matchCase: Boolean = false) {
        if (find.isEmpty()) return
        var replacedCount = 0
        _uiState.update { current ->
            val updated = current.resources.map { item ->
                if (item.isLocked || item.translatedText.isBlank()) return@map item
                if (item.translatedText.contains(find, ignoreCase = !matchCase)) {
                    replacedCount++
                    val next = item.translatedText.replace(find, replace, ignoreCase = !matchCase)
                    item.copy(
                        previousTranslatedText = item.translatedText,
                        translatedText = next,
                        origin = TranslationOrigin.MANUAL_EDIT
                    )
                } else {
                    item
                }
            }
            current.copy(
                resources = updated,
                statusBannerMessage = "$replacedCount çeviri metninde bul ve değiştir uygulandı."
            )
        }
    }

    fun applyAutoSpellAndPunctuationFix() {
        var fixedCount = 0
        val glossary = userGlossaryState.value.associate { it.sourceTerm to it.preferredTranslation }
        _uiState.update { current ->
            val updated = current.resources.map { item ->
                if (item.isLocked || item.translatedText.isBlank()) return@map item
                var fixed = TranslationQualityTools.autoFixFormatting(item.translatedText)
                if (glossary.isNotEmpty()) {
                    fixed = TranslationQualityTools.applyCustomGlossary(fixed, glossary)
                }
                if (fixed != item.translatedText) {
                    fixedCount++
                    item.copy(
                        previousTranslatedText = item.translatedText,
                        translatedText = fixed,
                        origin = TranslationOrigin.MANUAL_EDIT
                    )
                } else {
                    item
                }
            }
            current.copy(
                resources = updated,
                statusBannerMessage = "$fixedCount çeviride yazım, noktalama ve özel sözlük iyileştirildi."
            )
        }
    }

    fun translateSingleOrUntranslatedEntries(onlyKey: String? = null) {
        val state = _uiState.value
        val settings = settingsState.value
        val effectiveSource = if (state.selectedSourceLanguage == LanguageOption.AUTO) {
            state.apkMetadata?.detectedSourceLanguage ?: LanguageOption.ENGLISH
        } else {
            state.selectedSourceLanguage
        }
        val target = state.targetLanguage
        if (effectiveSource == target) {
            _uiState.update { it.copy(errorMessage = "Kaynak ve hedef dil aynı olamaz.") }
            return
        }
        val targets = if (onlyKey != null) {
            state.resources.filter { it.uniqueKey == onlyKey && !it.isLocked }
        } else {
            state.resources.filter { it.isTranslatable && !it.isLocked && it.translatedText.isBlank() }
        }
        if (targets.isEmpty()) {
            _uiState.update { it.copy(statusBannerMessage = "Çevrilecek bekleyen metin bulunamadı.") }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.translateResources(
                    items = targets,
                    sourceLanguage = effectiveSource,
                    targetLanguage = target,
                    mode = settings.translationMode,
                    onlineConfig = settings.onlineApiConfig,
                    protectionConfig = settings.protectionConfig,
                    scopeConfig = settings.resourceScopeConfig.copy(strictStringsOnly = false),
                    appName = state.apkMetadata?.appLabel ?: "Android App",
                    packageName = state.apkMetadata?.packageName ?: "com.example.app",
                    onProgress = { _, _ -> },
                    onLog = { level, stage, msg -> appendLog(level, stage, msg) }
                )
            }.onSuccess { updatedSub ->
                val byKey = updatedSub.associateBy { it.uniqueKey }
                _uiState.update { cur ->
                    cur.copy(
                        resources = cur.resources.map { item -> byKey[item.uniqueKey] ?: item },
                        statusBannerMessage = "${updatedSub.size} seçili/eksik metin çevrildi."
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Seçili çeviri hatası: ${err.message}") }
            }
        }
    }

    fun rebuildApkFromCurrentPreview() {
        val session = currentSession ?: return
        val state = _uiState.value
        val settings = settingsState.value
        val effectiveSource = if (state.selectedSourceLanguage == LanguageOption.AUTO) {
            session.metadata.detectedSourceLanguage
        } else {
            state.selectedSourceLanguage
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    stage = PipelineStage.REBUILDING_RESOURCES,
                    progress = 0.7f,
                    progressDetail = "Düzenlenen metinlerle APK yeniden derleniyor...",
                    errorMessage = null
                )
            }
            try {
                val result = repository.rebuildSignAndVerifyApk(
                    session = session,
                    translatedItems = state.resources,
                    sourceLanguage = effectiveSource,
                    targetLanguage = state.targetLanguage,
                    outputTreeUriString = settings.outputTreeUri,
                    scopeConfig = settings.resourceScopeConfig,
                    onStage = { stage ->
                        _uiState.update { it.copy(stage = stage, progressDetail = stage.labelTr) }
                    },
                    onLog = { level, stage, msg -> appendLog(level, stage, msg) }
                )
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        stage = PipelineStage.COMPLETED,
                        progress = 1f,
                        progressDetail = "Güncellenmiş APK yeniden imzalandı!",
                        outputResult = result
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                val msg = ex.message ?: "Yeniden paketleme hatası."
                appendLog(LogLevel.ERROR, PipelineStage.ERROR, msg)
                _uiState.update {
                    it.copy(isRunning = false, stage = PipelineStage.ERROR, errorMessage = msg)
                }
            }
        }
    }

    fun saveOutputToDocumentUri(targetUri: Uri) {
        val path = _uiState.value.outputResult?.workspaceFilePath ?: return
        viewModelScope.launch {
            runCatching { repository.saveFileToCustomDocumentUri(File(path), targetUri) }
                .onSuccess {
                    appendLog(LogLevel.SUCCESS, PipelineStage.COMPLETED, "Çıktı APK seçilen konuma kaydedildi.")
                    _uiState.update { it.copy(statusBannerMessage = "APK seçilen konuma başarıyla kaydedildi.") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = "Kaydetme hatası: ${err.message}") }
                }
        }
    }

    fun generateStringsXmlPreview(useTranslated: Boolean): String {
        return ValuesXmlCodec.generateStringsXml(_uiState.value.resources.take(250), useTranslated)
    }

    fun refreshOfflineModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true) }
            val states = runCatching {
                withContext(Dispatchers.IO) { mlKitEngine.listOfflineModelStates() }
            }.getOrDefault(emptyList())
            _uiState.update { it.copy(offlineModels = states, isLoadingModels = false) }
        }
    }

    fun downloadOfflineModel(language: LanguageOption) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    offlineModels = current.offlineModels.map {
                        if (it.language == language) it.copy(isDownloading = true, statusMessage = "İndiriliyor...") else it
                    }
                )
            }
            runCatching { mlKitEngine.downloadModel(language) }
                .onSuccess {
                    appendLog(LogLevel.SUCCESS, PipelineStage.IDLE, "${language.displayNameTr} çevrimdışı dil paketi indirildi.")
                    refreshOfflineModels()
                }
                .onFailure { err ->
                    appendLog(LogLevel.ERROR, PipelineStage.IDLE, "${language.displayNameTr} paket indirme hatası: ${err.message}")
                    _uiState.update { current ->
                        current.copy(
                            offlineModels = current.offlineModels.map {
                                if (it.language == language) {
                                    it.copy(isDownloading = false, statusMessage = err.message ?: "İndirme başarısız")
                                } else it
                            }
                        )
                    }
                }
        }
    }

    fun downloadActiveLanguagePair() {
        val state = _uiState.value
        val effectiveSource = if (state.selectedSourceLanguage == LanguageOption.AUTO) {
            state.apkMetadata?.detectedSourceLanguage ?: LanguageOption.ENGLISH
        } else {
            state.selectedSourceLanguage
        }
        val target = state.targetLanguage
        if (effectiveSource.mlKitCode != null) downloadOfflineModel(effectiveSource)
        if (target.mlKitCode != null && target != effectiveSource) downloadOfflineModel(target)
    }

    fun deleteOfflineModel(language: LanguageOption) {
        viewModelScope.launch {
            runCatching { mlKitEngine.deleteModel(language) }
                .onSuccess {
                    appendLog(LogLevel.INFO, PipelineStage.IDLE, "${language.displayNameTr} çevrimdışı paketi silindi.")
                    refreshOfflineModels()
                }
        }
    }

    fun clearTranslationCache() {
        viewModelScope.launch {
            repository.clearCache()
            _uiState.update { it.copy(statusBannerMessage = "Çeviri önbelleği temizlendi.") }
        }
    }

    fun clearJobHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.update { it.copy(statusBannerMessage = "İşlem geçmişi temizlendi.") }
        }
    }

    fun enqueueBatchApks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            runCatching {
                app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val combined = (_uiState.value.batchApkUris + uris.map { it.toString() }).distinct()
        _uiState.update {
            it.copy(
                batchApkUris = combined,
                batchStatusSummary = "${combined.size} APK toplu içe aktarma kuyruğunda hazır.",
                statusBannerMessage = "${uris.size} APK toplu kuyruğa eklendi."
            )
        }
    }

    fun runBatchApks(isAuthenticatedUser: Boolean = false) {
        val uris = _uiState.value.batchApkUris
        if (uris.isEmpty()) return
        val settings = settingsState.value
        val target = _uiState.value.targetLanguage
        if (!isAuthenticatedUser && settings.isGuestTrialActive && settings.isGuestTrialLimitReached) {
            _uiState.update { it.copy(errorMessage = "Deneme sınırına ulaşıldı. Toplu işlem için Google ile giriş yapın.") }
            return
        }
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isBatchRunning = true, isRunning = true, progress = 0.05f) }
            var successCount = 0
            uris.forEachIndexed { idx, uriStr ->
                _uiState.update {
                    it.copy(
                        stage = PipelineStage.ANALYZING_APK,
                        progress = idx.toFloat() / uris.size.toFloat(),
                        progressDetail = "Toplu APK (${idx + 1}/${uris.size}) işleniyor...",
                        batchStatusSummary = "İşleniyor: ${idx + 1}/${uris.size}"
                    )
                }
                runCatching {
                    val session = repository.inspectApkFromUri(Uri.parse(uriStr)) { l, s, m -> appendLog(l, s, m) }
                    val src = session.metadata.detectedSourceLanguage
                    val effTarget = if (src == target) LanguageOption.ENGLISH else target
                    val translated = repository.translateResources(
                        items = session.resources,
                        sourceLanguage = src,
                        targetLanguage = effTarget,
                        mode = settings.translationMode,
                        onlineConfig = settings.onlineApiConfig,
                        protectionConfig = settings.protectionConfig,
                        scopeConfig = settings.resourceScopeConfig,
                        appName = session.metadata.appLabel,
                        packageName = session.metadata.packageName,
                        onProgress = { _, _ -> },
                        onLog = { l, s, m -> appendLog(l, s, m) }
                    )
                    repository.rebuildSignAndVerifyApk(
                        session = session,
                        translatedItems = translated,
                        sourceLanguage = src,
                        targetLanguage = effTarget,
                        outputTreeUriString = settings.outputTreeUri,
                        scopeConfig = settings.resourceScopeConfig,
                        onStage = { },
                        onLog = { l, s, m -> appendLog(l, s, m) }
                    )
                    successCount++
                }.onFailure { err ->
                    appendLog(LogLevel.ERROR, PipelineStage.ERROR, "Toplu APK hatası (#${idx + 1}): ${err.message}")
                }
            }
            _uiState.update {
                it.copy(
                    isBatchRunning = false,
                    isRunning = false,
                    stage = PipelineStage.COMPLETED,
                    progress = 1f,
                    batchStatusSummary = "Toplu işlem tamamlandı: $successCount / ${uris.size} APK imzalandı.",
                    statusBannerMessage = "$successCount / ${uris.size} APK toplu olarak çevrildi ve imzalandı."
                )
            }
        }
    }

    fun inspectComparisonApk(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isInspectingComparisonApk = true, errorMessage = null) }
            runCatching { repository.inspectComparisonMetadataOnly(uri) }
                .onSuccess { meta ->
                    _uiState.update {
                        it.copy(
                            isInspectingComparisonApk = false,
                            comparisonApkMetadata = meta,
                            statusBannerMessage = "Karşılaştırma APK'sı yüklendi: ${meta.appLabel} (${meta.versionName})"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isInspectingComparisonApk = false,
                            errorMessage = "Karşılaştırma APK'sı okunamadı: ${err.message}"
                        )
                    }
                }
        }
    }

    fun saveCurrentAsProject(projectName: String, profileName: String = "Standart Uygulama") {
        val meta = _uiState.value.apkMetadata
        if (meta == null) {
            _uiState.update { it.copy(errorMessage = "Proje kaydetmek için önce bir APK seçin.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val resources = _uiState.value.resources
            val serialized = serializeResourcesToJson(resources)
            val entity = TranslationProjectEntity(
                projectId = UUID.randomUUID().toString(),
                projectName = projectName.ifBlank { "${meta.appLabel} (${meta.versionName})" },
                packageName = meta.packageName,
                apkFileName = meta.fileName,
                sourceApkUri = meta.uriString,
                sourceSha256 = meta.sha256,
                sourceLangCode = _uiState.value.selectedSourceLanguage.code,
                targetLangCode = _uiState.value.targetLanguage.code,
                profileName = profileName.ifBlank { "Standart Uygulama" },
                serializedResourcesJson = serialized,
                entryCount = resources.size,
                translatedCount = resources.count { it.translatedText.isNotBlank() }
            )
            projectRepository.saveProject(entity)
            _uiState.update { it.copy(statusBannerMessage = "Proje kaydedildi: ${entity.projectName}") }
        }
    }

    fun loadSavedProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val proj = projectRepository.getProject(projectId) ?: return@launch
            val loadedResources = deserializeResourcesFromJson(proj.serializedResourcesJson)
            _uiState.update {
                it.copy(
                    selectedApkUri = proj.sourceApkUri,
                    selectedSourceLanguage = LanguageOption.fromCode(proj.sourceLangCode),
                    targetLanguage = LanguageOption.fromCode(proj.targetLangCode),
                    resources = loadedResources,
                    statusBannerMessage = "Proje yüklendi: ${proj.projectName} (${loadedResources.size} metin)"
                )
            }
        }
    }

    fun duplicateSavedProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val orig = projectRepository.getProject(projectId) ?: return@launch
            projectRepository.saveProject(
                orig.copy(
                    projectId = UUID.randomUUID().toString(),
                    projectName = "${orig.projectName} (Kopya)",
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(statusBannerMessage = "Proje çoğaltıldı.") }
        }
    }

    fun deleteSavedProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            projectRepository.deleteProject(projectId)
            _uiState.update { it.copy(statusBannerMessage = "Proje silindi.") }
        }
    }

    fun addGlossaryTerm(source: String, target: String, note: String = "Özel Sözlük") {
        viewModelScope.launch(Dispatchers.IO) {
            projectRepository.saveGlossaryTerm(source, target, note)
            _uiState.update { it.copy(statusBannerMessage = "Özel sözlük terimi eklendi: $source → $target") }
        }
    }

    fun deleteGlossaryTerm(source: String) {
        viewModelScope.launch(Dispatchers.IO) {
            projectRepository.deleteGlossaryTerm(source)
        }
    }

    fun exportReportContent(format: String): String {
        val pkg = _uiState.value.apkMetadata?.packageName ?: "com.example.app"
        val items = _uiState.value.resources
        return when (format.lowercase()) {
            "json" -> TranslationQualityTools.exportAsJson(pkg, items)
            "csv" -> TranslationQualityTools.exportAsCsv(items)
            else -> ValuesXmlCodec.generateStringsXml(items, useTranslated = true)
        }
    }

    fun writeTextExportToUri(targetUri: Uri, content: String, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openOutputStream(targetUri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Hedef akış açılamadı.")
            }.onSuccess {
                _uiState.update { it.copy(statusBannerMessage = "$label dosyası dışa aktarıldı.") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Dışa aktarma hatası: ${err.message}") }
            }
        }
    }

    fun enqueueBackgroundWorkManagerBuild() {
        val uriStr = _uiState.value.selectedApkUri
        if (uriStr.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Arka plan görevi için önce bir APK seçin.") }
            return
        }
        val needsNet = settingsState.value.translationMode != TranslationMode.OFFLINE_ONLY
        runCatching {
            BackgroundTranslationWorker.enqueueOneClickBuild(
                context = app,
                apkUriString = uriStr,
                targetLangCode = _uiState.value.targetLanguage.code,
                requireNetwork = needsNet
            )
        }.onSuccess {
            _uiState.update {
                it.copy(
                    isBackgroundWorkRunning = true,
                    backgroundWorkStatus = "WorkManager arka plan görevi kuyruğa alındı",
                    statusBannerMessage = "Tek tıklamayla WorkManager arka plan derlemesi başlatıldı."
                )
            }
        }.onFailure { err ->
            _uiState.update { it.copy(errorMessage = "WorkManager başlatılamadı: ${err.message}") }
        }
    }

    fun cancelBackgroundWorkManagerBuild() {
        runCatching { BackgroundTranslationWorker.cancelBackgroundBuild(app) }
        _uiState.update {
            it.copy(
                isBackgroundWorkRunning = false,
                backgroundWorkStatus = "Duraklatıldı / İptal edildi",
                statusBannerMessage = "WorkManager arka plan görevi durduruldu."
            )
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, statusBannerMessage = null) }
    }

    private fun serializeResourcesToJson(items: List<ResourceEntryItem>): String {
        val arr = JSONArray()
        items.take(800).forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("kind", item.kind.name)
                    put("key", item.key)
                    put("subKey", item.subKey)
                    put("locale", item.localeQualifier)
                    put("orig", item.originalText)
                    put("tr", item.translatedText)
                    put("locked", item.isLocked)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeResourcesFromJson(jsonStr: String): List<ResourceEntryItem> {
        return runCatching {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                ResourceEntryItem(
                    id = obj.optInt("id", idx),
                    kind = runCatching { ResourceKind.valueOf(obj.optString("kind", "STRING")) }
                        .getOrDefault(ResourceKind.STRING),
                    key = obj.optString("key", ""),
                    subKey = obj.optString("subKey", ""),
                    localeQualifier = obj.optString("locale", "default"),
                    originalText = obj.optString("orig", ""),
                    translatedText = obj.optString("tr", ""),
                    isLocked = obj.optBoolean("locked", false),
                    origin = if (obj.optString("tr").isNotBlank()) TranslationOrigin.MANUAL_EDIT else TranslationOrigin.PENDING
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun appendLog(level: LogLevel, stage: PipelineStage, message: String) {
        val entry = LogEntry(
            id = logSeq.incrementAndGet(),
            timestamp = timeFmt.format(Date()),
            level = level,
            stage = stage,
            message = message
        )
        _uiState.update { current ->
            val updated = (current.logs + entry).takeLast(300)
            current.copy(logs = updated)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainViewModel(app)
            }
        }
    }
}
