package com.example.apktranslatorpro.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.apktranslatorpro.ApkTranslatorApp
import com.example.apktranslatorpro.core.apk.BulkKeyRenameResult
import com.example.apktranslatorpro.core.apk.DecompiledWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class BuildProcessStep(val titleTr: String) {
    IDLE("Hazır"),
    DECOMPILING("APK Çözümleniyor (Apktool)"),
    TRANSLATING("Kaynaklar Çevriliyor"),
    REBUILDING_RESOURCES("Kaynaklar ve ARSC Derleniyor"),
    ZIPALIGNING("Zipalign Uygulanıyor"),
    SIGNING("v1/v2/v3 APK İmzalanıyor"),
    VERIFYING("İmza & Bütünlük Doğrulanıyor"),
    EXPORTING("Çıktı Dışa Aktarılıyor"),
    COMPLETED("İşlem Tamamlandı"),
    ERROR("Hata Oluştu")
}

data class BuildProcessUiState(
    val activeStep: BuildProcessStep = BuildProcessStep.IDLE,
    val progress: Float = 0f,
    val statusDetail: String = "",
    val isBusy: Boolean = false,
    val workspace: DecompiledWorkspace? = null,
    val lastBulkRenameResult: BulkKeyRenameResult? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val showGlobalProgress: Boolean
        get() = isBusy || (activeStep != BuildProcessStep.IDLE && activeStep != BuildProcessStep.COMPLETED)
}

/**
 * Orchestrates Apktool decompilation, cache file editing, bulk string key renaming,
 * ZIP export, and step-by-step build status updates observed by GlobalBuildProgressBar.
 */
class BuildProcessViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ApkTranslatorApp
    private val apktoolService = app.apktoolService

    private val _state = MutableStateFlow(BuildProcessUiState())
    val state: StateFlow<BuildProcessUiState> = _state.asStateFlow()

    fun syncPipelineStep(step: BuildProcessStep, progress: Float, detail: String, busy: Boolean) {
        _state.update {
            it.copy(
                activeStep = step,
                progress = progress.coerceIn(0f, 1f),
                statusDetail = detail,
                isBusy = busy
            )
        }
    }

    fun decompileFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    activeStep = BuildProcessStep.DECOMPILING,
                    progress = 0.05f,
                    statusDetail = "APK decompile başlatılıyor...",
                    isBusy = true,
                    errorMessage = null,
                    statusMessage = null
                )
            }
            runCatching {
                apktoolService.decompileFromUri(uri) { frac, msg ->
                    _state.update {
                        it.copy(
                            activeStep = BuildProcessStep.DECOMPILING,
                            progress = frac,
                            statusDetail = msg
                        )
                    }
                }
            }.onSuccess { ws ->
                _state.update {
                    it.copy(
                        activeStep = BuildProcessStep.COMPLETED,
                        progress = 1f,
                        statusDetail = "Decompile tamamlandı (${ws.resXmlFiles.size} XML, ${ws.smaliFiles.size} Smali)",
                        isBusy = false,
                        workspace = ws,
                        statusMessage = "Apktool çalışma alanı önbelleğe çıkarıldı."
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        activeStep = BuildProcessStep.ERROR,
                        progress = 0f,
                        statusDetail = err.message ?: "Decompile başarısız",
                        isBusy = false,
                        errorMessage = err.message ?: "Geçersiz veya bozuk APK dosyası."
                    )
                }
            }
        }
    }

    fun saveWorkingFile(file: File, newContent: String, onAfterStringsSaved: ((String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                apktoolService.saveWorkingFile(file, newContent)
                if (file.name == "strings.xml") {
                    onAfterStringsSaved?.invoke(newContent)
                }
            }.onSuccess {
                _state.update {
                    it.copy(statusMessage = "${file.name} önbellek çalışma dizinine kaydedildi.")
                }
            }.onFailure { err ->
                _state.update { it.copy(errorMessage = "Dosya kaydedilemedi: ${err.message}") }
            }
        }
    }

    fun performBulkKeyRename(oldKeyOrPrefix: String, newKeyOrPrefix: String, prefixMode: Boolean) {
        val ws = _state.value.workspace
        if (ws == null) {
            _state.update { it.copy(errorMessage = "Toplu yeniden adlandırma için önce APK decompile edin.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    activeStep = BuildProcessStep.REBUILDING_RESOURCES,
                    progress = 0.4f,
                    statusDetail = "XML anahtarları ve layout referansları güncelleniyor...",
                    isBusy = true,
                    errorMessage = null
                )
            }
            runCatching {
                apktoolService.bulkRenameStringKeys(
                    workDir = ws.workDir,
                    oldPrefixOrKey = oldKeyOrPrefix,
                    newPrefixOrKey = newKeyOrPrefix,
                    prefixMode = prefixMode
                )
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        activeStep = BuildProcessStep.COMPLETED,
                        progress = 1f,
                        statusDetail = "${result.renamedResourceCount} anahtar, ${result.updatedLayoutReferences} @string/ referansı güncellendi",
                        isBusy = false,
                        lastBulkRenameResult = result,
                        statusMessage = "${result.renamedResourceCount} string anahtarı ve ${result.updatedLayoutReferences} layout referansı güncellendi."
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        activeStep = BuildProcessStep.ERROR,
                        isBusy = false,
                        errorMessage = err.message ?: "Toplu anahtar değişikliği başarısız."
                    )
                }
            }
        }
    }

    fun exportWorkspaceZip(targetUri: Uri) {
        val ws = _state.value.workspace
        if (ws == null) {
            _state.update { it.copy(errorMessage = "Dışa aktarılacak decompile edilmiş çalışma alanı yok.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    activeStep = BuildProcessStep.EXPORTING,
                    progress = 0.5f,
                    statusDetail = "Çalışma alanı ZIP arşivi oluşturuluyor...",
                    isBusy = true
                )
            }
            runCatching {
                app.contentResolver.openOutputStream(targetUri)?.use { out ->
                    apktoolService.exportWorkspaceAsZip(ws.workDir, out)
                } ?: throw IllegalStateException("Hedef ZIP akışı açılamadı.")
            }.onSuccess { fileCount ->
                _state.update {
                    it.copy(
                        activeStep = BuildProcessStep.COMPLETED,
                        progress = 1f,
                        statusDetail = "$fileCount dosya ZIP olarak dışa aktarıldı.",
                        isBusy = false,
                        statusMessage = "Proje ZIP olarak kaydedildi ($fileCount dosya)."
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        activeStep = BuildProcessStep.ERROR,
                        isBusy = false,
                        errorMessage = "ZIP dışa aktarma hatası: ${err.message}"
                    )
                }
            }
        }
    }

    fun clearBanner() {
        _state.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                BuildProcessViewModel(app)
            }
        }
    }
}
