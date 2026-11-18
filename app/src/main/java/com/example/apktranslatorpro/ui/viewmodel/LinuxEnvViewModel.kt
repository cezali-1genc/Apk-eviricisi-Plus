package com.example.apktranslatorpro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.apktranslatorpro.core.environment.LinuxDistributionConfig
import com.example.apktranslatorpro.core.environment.LinuxEnvironmentManager
import com.example.apktranslatorpro.core.environment.PackageManagerHealth
import com.example.apktranslatorpro.core.environment.PackageServiceSnapshot
import com.example.apktranslatorpro.core.environment.PermissionFixReport
import com.example.apktranslatorpro.core.environment.PermissionFixer
import com.example.apktranslatorpro.core.environment.TerminalSessionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LinuxEnvUiState(
    val distributions: List<LinuxDistributionConfig> = emptyList(),
    val activeSessions: List<TerminalSessionInfo> = emptyList(),
    val serviceSnapshot: PackageServiceSnapshot = PackageServiceSnapshot(
        health = PackageManagerHealth.EXTERNAL_TERMUX_REQUIRED,
        prootReady = false,
        bashReady = false,
        zshReady = false,
        detectedPackageManagers = emptyList(),
        detailMessageTr = "Ortam taranıyor..."
    ),
    val lastPermissionReport: PermissionFixReport? = null,
    val environmentVariables: Map<String, String> = emptyMap(),
    val isFixingPermissions: Boolean = false,
    val statusMessage: String? = null
)

class LinuxEnvViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val permissionFixer = PermissionFixer(application.applicationContext)
    private val envManager = LinuxEnvironmentManager(application.applicationContext, permissionFixer)

    private val _state = MutableStateFlow(LinuxEnvUiState())
    val state: StateFlow<LinuxEnvUiState> = _state.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.update { it.copy(isFixingPermissions = true) }
            val distros = envManager.loadDistributions()
            val (report, snapshot) = envManager.verifyPermissionsAndServices()
            val envVars = envManager.buildEnvironmentPreview()
            _state.update {
                it.copy(
                    distributions = distros,
                    lastPermissionReport = report,
                    serviceSnapshot = snapshot,
                    environmentVariables = envVars,
                    isFixingPermissions = false
                )
            }
        }
    }

    fun runPermissionFixerNow() {
        viewModelScope.launch {
            _state.update { it.copy(isFixingPermissions = true) }
            val (report, snapshot) = envManager.verifyPermissionsAndServices()
            val envVars = envManager.buildEnvironmentPreview()
            _state.update {
                it.copy(
                    lastPermissionReport = report,
                    serviceSnapshot = snapshot,
                    environmentVariables = envVars,
                    isFixingPermissions = false,
                    statusMessage = "PermissionFixer tamamlandı: ${report.summaryTr()}"
                )
            }
        }
    }

    fun toggleDistribution(distroId: String, enabled: Boolean) {
        viewModelScope.launch {
            val updated = envManager.toggleDistribution(distroId, enabled)
            _state.update {
                it.copy(
                    distributions = updated,
                    statusMessage = "$distroId ${if (enabled) "etkinleştirildi" else "devre dışı bırakıldı"}."
                )
            }
        }
    }

    fun updateDistributionConfig(distroId: String, defaultShell: String, prootFlags: String) {
        viewModelScope.launch {
            val updated = envManager.updateDistributionConfig(distroId, defaultShell, prootFlags)
            _state.update {
                it.copy(
                    distributions = updated,
                    statusMessage = "$distroId yapılandırması güncellendi."
                )
            }
        }
    }

    fun startDiagnosticShellSession(
        title: String = "Ortam & İzin Denetimi",
        command: String = "echo \"HOME=\$HOME\" && echo \"PREFIX=\$PREFIX\" && echo \"TMPDIR=\$TMPDIR\" && ls -la && sleep 2",
        distro: LinuxDistributionConfig? = null
    ) {
        runCatching {
            val launchedInfo = envManager.launchShellCommand(
                title = title,
                shellCommand = command,
                preferredDistro = distro,
                onOutputLine = { sid, line ->
                    _state.update { current ->
                        current.copy(
                            activeSessions = current.activeSessions.map { s ->
                                if (s.id == sid) {
                                    s.copy(outputLines = (s.outputLines + line).takeLast(14))
                                } else {
                                    s
                                }
                            }
                        )
                    }
                },
                onCompleted = { sid, exitCode ->
                    _state.update { current ->
                        current.copy(
                            activeSessions = current.activeSessions.map { s ->
                                if (s.id == sid) {
                                    s.copy(isRunning = false, exitCode = exitCode)
                                } else {
                                    s
                                }
                            }
                        )
                    }
                }
            )
            _state.update { current ->
                current.copy(
                    activeSessions = listOf(launchedInfo) + current.activeSessions.take(7),
                    statusMessage = "Terminal oturumu başlatıldı: ${launchedInfo.title}"
                )
            }
        }.onFailure { err ->
            _state.update {
                it.copy(statusMessage = "Terminal başlatılamadı: ${err.message}")
            }
        }
    }

    fun stopSession(sessionId: String) {
        val stopped = envManager.stopSession(sessionId)
        _state.update { current ->
            current.copy(
                activeSessions = current.activeSessions.map { s ->
                    if (s.id == sessionId) {
                        s.copy(
                            isRunning = false,
                            exitCode = -1,
                            outputLines = (s.outputLines + "[Durduruldu]").takeLast(14)
                        )
                    } else {
                        s
                    }
                },
                statusMessage = if (stopped) "Oturum durduruldu (#$sessionId)." else "Oturum zaten sonlandı."
            )
        }
    }

    fun clearFinishedSessions() {
        _state.update { current ->
            current.copy(activeSessions = current.activeSessions.filter { it.isRunning })
        }
    }

    fun clearBanner() {
        _state.update { it.copy(statusMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        envManager.stopAllSessions()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                LinuxEnvViewModel(app)
            }
        }
    }
}
