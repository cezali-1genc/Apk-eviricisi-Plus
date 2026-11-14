package com.example.apktranslatorpro.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.apktranslatorpro.core.security.ApkSecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilePickerUiState(
    val selectedUri: Uri? = null,
    val displayName: String = "",
    val sizeBytes: Long = 0L,
    val sha256Checksum: String = "",
    val isCalculatingChecksum: Boolean = false,
    val isVerifiedReadOnly: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Manages read-only SAF APK file picking and SHA-256 verification via [ApkSecurityUtils].
 */
class FilePickerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(FilePickerUiState())
    val state: StateFlow<FilePickerUiState> = _state.asStateFlow()

    fun onApkUriPicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    selectedUri = uri,
                    isCalculatingChecksum = true,
                    isVerifiedReadOnly = false,
                    errorMessage = null
                )
            }
            runCatching {
                ApkSecurityUtils.verifySafApkExistsReadOnly(getApplication(), uri)
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        selectedUri = uri,
                        displayName = result.displayName,
                        sizeBytes = result.sizeBytes,
                        sha256Checksum = result.sha256Hex,
                        isCalculatingChecksum = false,
                        isVerifiedReadOnly = result.exists,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isCalculatingChecksum = false,
                        isVerifiedReadOnly = false,
                        errorMessage = err.message ?: "SHA-256 hesaplanamadı."
                    )
                }
            }
        }
    }

    fun recalculateChecksum() {
        val current = _state.value.selectedUri ?: return
        onApkUriPicked(current)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                FilePickerViewModel(app)
            }
        }
    }
}
