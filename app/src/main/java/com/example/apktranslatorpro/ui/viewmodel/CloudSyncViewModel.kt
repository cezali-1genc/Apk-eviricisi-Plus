package com.example.apktranslatorpro.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apktranslatorpro.data.remote.ApkProjectRemote
import com.example.apktranslatorpro.data.remote.FirestoreSyncRepository
import com.example.apktranslatorpro.domain.model.ApkMetadata
import com.example.apktranslatorpro.domain.model.OutputApkResult
import com.example.apktranslatorpro.domain.model.TranslationMode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "CloudSyncViewModel"

sealed interface CloudProjectsUiState {
    data object Loading : CloudProjectsUiState
    data class Success(val projects: List<ApkProjectRemote>) : CloudProjectsUiState
    data class Error(val message: String) : CloudProjectsUiState
}

class CloudSyncViewModel(
    private val syncRepository: FirestoreSyncRepository,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String?
) : ViewModel() {

    private val _activeCloudProjectId = MutableStateFlow<String?>(null)
    val activeCloudProjectId: StateFlow<String?> = _activeCloudProjectId.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val cloudProjectsState: StateFlow<CloudProjectsUiState> =
        syncRepository.observeUserProjects(currentUserId)
            .map<List<ApkProjectRemote>, CloudProjectsUiState> { CloudProjectsUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing cloud APK projects", rootError)
                emit(
                    CloudProjectsUiState.Error(
                        rootError.localizedMessage ?: "Bulut projeleri yüklenemedi."
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = CloudProjectsUiState.Loading
            )

    init {
        viewModelScope.launch {
            syncRepository.ensureUserProfile(
                userId = currentUserId,
                displayName = userDisplayName,
                email = userEmail
            )
        }
    }

    fun recordInspectedApk(
        metadata: ApkMetadata,
        targetLangCode: String,
        translationMode: TranslationMode
    ) {
        viewModelScope.launch {
            val item = ApkProjectRemote(
                userId = currentUserId,
                fileName = metadata.fileName,
                packageName = metadata.packageName,
                appLabel = metadata.appLabel,
                versionName = metadata.versionName,
                inputSha256 = metadata.sha256,
                sourceLang = metadata.detectedSourceLanguage.code,
                targetLang = targetLangCode,
                translationEngine = translationMode.name,
                translatedCount = 0L,
                status = "inspected"
            )
            syncRepository.createProject(currentUserId, item)
                .onSuccess { docId ->
                    _activeCloudProjectId.value = docId
                }
                .onFailure { err ->
                    _syncMessage.value = "Bulut kaydı oluşturulamadı: ${err.localizedMessage}"
                }
        }
    }

    fun recordSignedOutput(
        metadata: ApkMetadata,
        output: OutputApkResult,
        targetLangCode: String,
        translationMode: TranslationMode
    ) {
        viewModelScope.launch {
            val totalTranslated = (output.translatedCount + output.cachedCount).toLong()
            val existingId = _activeCloudProjectId.value
            if (existingId != null) {
                syncRepository.markProjectSigned(
                    userId = currentUserId,
                    projectId = existingId,
                    outputSha256 = output.outputSha256,
                    targetLang = targetLangCode,
                    translationEngine = translationMode.name,
                    translatedCount = totalTranslated
                ).onFailure { err ->
                    _syncMessage.value = "Bulut güncelleme hatası: ${err.localizedMessage}"
                }
            } else {
                val item = ApkProjectRemote(
                    userId = currentUserId,
                    fileName = output.fileName,
                    packageName = metadata.packageName,
                    appLabel = metadata.appLabel,
                    versionName = metadata.versionName,
                    inputSha256 = metadata.sha256,
                    outputSha256 = output.outputSha256,
                    sourceLang = metadata.detectedSourceLanguage.code,
                    targetLang = targetLangCode,
                    translationEngine = translationMode.name,
                    translatedCount = totalTranslated,
                    status = "signed"
                )
                syncRepository.createProject(currentUserId, item)
                    .onSuccess { _activeCloudProjectId.value = it }
            }
        }
    }

    fun attachGeminiSummary(summary: String) {
        val projectId = _activeCloudProjectId.value ?: return
        viewModelScope.launch {
            syncRepository.attachAiSummary(currentUserId, projectId, summary)
                .onSuccess {
                    _syncMessage.value = "Gemini analizi bulut projesine kaydedildi."
                }
                .onFailure { err ->
                    _syncMessage.value = "Analiz buluta kaydedilemedi: ${err.localizedMessage}"
                }
        }
    }

    fun deleteCloudProject(projectId: String) {
        viewModelScope.launch {
            syncRepository.deleteProject(currentUserId, projectId)
                .onSuccess {
                    if (_activeCloudProjectId.value == projectId) {
                        _activeCloudProjectId.value = null
                    }
                    _syncMessage.value = "Bulut projesi silindi."
                }
                .onFailure { err ->
                    _syncMessage.value = "Silme hatası: ${err.localizedMessage}"
                }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
