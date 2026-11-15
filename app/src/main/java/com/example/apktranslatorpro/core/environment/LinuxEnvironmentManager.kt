package com.example.apktranslatorpro.core.environment

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class PackageManagerHealth(val labelTr: String) {
    READY("Aktif ve Hazır"),
    NEEDS_PERMISSION_FIX("İzin Onarımı Gerekli"),
    EXTERNAL_TERMUX_REQUIRED("Harici İkili Kurulumu Bekleniyor")
}

data class LinuxDistributionConfig(
    val id: String,
    val displayName: String,
    val releaseLabel: String,
    val architecture: String,
    val rootfsRelativePath: String,
    val defaultShell: String,
    val prootFlags: String,
    val packageManagerName: String,
    val isEnabled: Boolean,
    val isRootfsPresentOnDisk: Boolean,
    val diskSizeBytes: Long
)

data class TerminalSessionInfo(
    val id: String,
    val title: String,
    val commandPreview: String,
    val shellPath: String,
    val startedAtMillis: Long,
    val isRunning: Boolean,
    val exitCode: Int? = null,
    val outputLines: List<String> = emptyList()
)

data class PackageServiceSnapshot(
    val health: PackageManagerHealth,
    val prootReady: Boolean,
    val bashReady: Boolean,
    val zshReady: Boolean,
    val detectedPackageManagers: List<String>,
    val detailMessageTr: String
)

/**
 * Manages installed Linux distributions under `filesDir/linux/distros`,
 * package management binary probing via [PermissionFixer], and real
 * active shell/command sessions launched via `ProcessBuilder`.
 */
class LinuxEnvironmentManager(
    private val context: Context,
    private val permissionFixer: PermissionFixer = PermissionFixer(context)
) {
    private val registryFile: File
        get() = File(permissionFixer.linuxRootDir, "distros_registry.json")

    private val runningProcesses = ConcurrentHashMap<String, Process>()

    suspend fun loadDistributions(): List<LinuxDistributionConfig> = withContext(Dispatchers.IO) {
        permissionFixer.ensureInternalDirectories()
        val stored = readOrInitializeRegistry()
        val diskSynced = stored.map { cfg ->
            val rootDir = File(permissionFixer.distrosDir, cfg.rootfsRelativePath)
            val existsOnDisk = rootDir.exists() && rootDir.isDirectory && !(rootDir.list().isNullOrEmpty())
            val sizeBytes = if (existsOnDisk) calculateDirSize(rootDir) else 0L
            cfg.copy(
                isRootfsPresentOnDisk = existsOnDisk,
                diskSizeBytes = sizeBytes
            )
        }
        diskSynced
    }

    suspend fun toggleDistribution(distroId: String, enabled: Boolean): List<LinuxDistributionConfig> =
        withContext(Dispatchers.IO) {
            val current = readOrInitializeRegistry().toMutableList()
            val idx = current.indexOfFirst { it.id == distroId }
            if (idx >= 0) {
                val target = current[idx]
                val rootDir = File(permissionFixer.distrosDir, target.rootfsRelativePath)
                if (enabled && !rootDir.exists()) {
                    rootDir.mkdirs()
                    File(rootDir, "bin").mkdirs()
                    File(rootDir, "etc").mkdirs()
                }
                current[idx] = target.copy(isEnabled = enabled)
                saveRegistry(current)
            }
            loadDistributions()
        }

    suspend fun updateDistributionConfig(
        distroId: String,
        defaultShell: String,
        prootFlags: String
    ): List<LinuxDistributionConfig> = withContext(Dispatchers.IO) {
        val current = readOrInitializeRegistry().toMutableList()
        val idx = current.indexOfFirst { it.id == distroId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(
                defaultShell = defaultShell.trim().ifBlank { "/bin/bash" },
                prootFlags = prootFlags.trim().ifBlank { "-0 -w /root" }
            )
            saveRegistry(current)
        }
        loadDistributions()
    }

    suspend fun verifyPermissionsAndServices(): Pair<PermissionFixReport, PackageServiceSnapshot> =
        withContext(Dispatchers.IO) {
            val report = permissionFixer.verifyAndFixExecutables()
            val prootReady = report.coreBinaries["proot"]?.let { it.existed && it.isExecutableAfter } == true
            val bashReady = report.coreBinaries["bash"]?.let { it.existed && it.isExecutableAfter } == true
            val zshReady = report.coreBinaries["zsh"]?.let { it.existed && it.isExecutableAfter } == true
            val pkgManagers = PermissionFixer.PACKAGE_MANAGER_BINARIES.filter { name ->
                report.coreBinaries[name]?.let { it.existed && it.isExecutableAfter } == true
            }
            val anyNeedsFix = report.coreBinaries.values.any { it.existed && !it.isExecutableAfter }

            val health = when {
                anyNeedsFix -> PackageManagerHealth.NEEDS_PERMISSION_FIX
                prootReady && (bashReady || zshReady) && pkgManagers.isNotEmpty() -> PackageManagerHealth.READY
                prootReady || bashReady || zshReady -> PackageManagerHealth.READY
                else -> PackageManagerHealth.EXTERNAL_TERMUX_REQUIRED
            }

            val msg = when (health) {
                PackageManagerHealth.READY ->
                    "Dahili ikililer doğrulandı (${listOfNotNull("proot".takeIf { prootReady }, "bash".takeIf { bashReady }, "zsh".takeIf { zshReady }).joinToString(", ")})."
                PackageManagerHealth.NEEDS_PERMISSION_FIX ->
                    "Bazı ikili dosyalarda çalıştırma (+x) izni engellendi. İzinleri tekrar onarın."
                PackageManagerHealth.EXTERNAL_TERMUX_REQUIRED ->
                    "Dahili PRoot rootfs dizinleri hazır (${permissionFixer.linuxRootDir.name}/); yerel kabuk (/system/bin/sh) aktif, PRoot ikilileri Termux/Nix üzerinden eklenebilir."
            }

            report to PackageServiceSnapshot(
                health = health,
                prootReady = prootReady,
                bashReady = bashReady,
                zshReady = zshReady,
                detectedPackageManagers = pkgManagers,
                detailMessageTr = msg
            )
        }

    fun buildEnvironmentPreview(): Map<String, String> =
        permissionFixer.buildInternalEnvironment()

    /**
     * Spawns a real OS process using [PermissionFixer.createConfiguredProcessBuilder] and streams
     * its output back cleanly. Returns the session ID immediately while the caller tracks it.
     */
    fun launchShellCommand(
        title: String,
        shellCommand: String,
        preferredDistro: LinuxDistributionConfig? = null,
        onOutputLine: (sessionId: String, line: String) -> Unit,
        onCompleted: (sessionId: String, exitCode: Int) -> Unit
    ): TerminalSessionInfo {
        permissionFixer.ensureInternalDirectories()
        val sessionId = UUID.randomUUID().toString().take(8)

        val resolvedShell = when {
            preferredDistro != null -> {
                val candidateName = File(preferredDistro.defaultShell).name
                permissionFixer.resolveInternalExecutable(candidateName)?.absolutePath
                    ?: permissionFixer.resolvePreferredShellPath()
            }
            else -> permissionFixer.resolvePreferredShellPath()
        }

        val safeCmd = shellCommand.ifBlank { "pwd && echo PATH=\$PATH && ls -la" }
        val fullCommand = listOf(resolvedShell, "-c", safeCmd)

        val extraEnv = buildMap {
            if (preferredDistro != null) {
                put("DISTRO_ID", preferredDistro.id)
                put("DISTRO_NAME", preferredDistro.displayName)
                put("PROOT_ARGS", preferredDistro.prootFlags)
            }
        }

        val processBuilder = permissionFixer.createConfiguredProcessBuilder(
            command = fullCommand,
            workingDirectory = permissionFixer.homeDir,
            extraEnv = extraEnv
        )

        val process = processBuilder.start()
        runningProcesses[sessionId] = process

        Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val current = line ?: continue
                        onOutputLine(sessionId, current)
                    }
                }
                val code = process.waitFor()
                runningProcesses.remove(sessionId)
                onCompleted(sessionId, code)
            } catch (ex: Exception) {
                runningProcesses.remove(sessionId)
                onOutputLine(sessionId, "Oturum sonlandı: ${ex.message ?: "kesildi"}")
                onCompleted(sessionId, -1)
            }
        }, "ApkEditorTerm-$sessionId").start()

        return TerminalSessionInfo(
            id = sessionId,
            title = title,
            commandPreview = safeCmd,
            shellPath = resolvedShell,
            startedAtMillis = System.currentTimeMillis(),
            isRunning = true,
            outputLines = listOf("$ $safeCmd")
        )
    }

    fun stopSession(sessionId: String): Boolean {
        val process = runningProcesses.remove(sessionId) ?: return false
        runCatching {
            process.destroy()
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
        return true
    }

    fun stopAllSessions() {
        val ids = runningProcesses.keys.toList()
        ids.forEach { stopSession(it) }
    }

    fun activeProcessCount(): Int = runningProcesses.count { it.value.isAlive }

    private fun readOrInitializeRegistry(): List<LinuxDistributionConfig> {
        if (!registryFile.exists()) {
            val defaults = defaultDistributions()
            saveRegistry(defaults)
            return defaults
        }
        return runCatching {
            val raw = registryFile.readText(Charsets.UTF_8)
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        LinuxDistributionConfig(
                            id = obj.getString("id"),
                            displayName = obj.getString("displayName"),
                            releaseLabel = obj.optString("releaseLabel", "ARM64"),
                            architecture = obj.optString("architecture", "aarch64"),
                            rootfsRelativePath = obj.getString("rootfsRelativePath"),
                            defaultShell = obj.optString("defaultShell", "/bin/bash"),
                            prootFlags = obj.optString("prootFlags", "-0 -w /root"),
                            packageManagerName = obj.optString("packageManagerName", "apt"),
                            isEnabled = obj.optBoolean("isEnabled", false),
                            isRootfsPresentOnDisk = false,
                            diskSizeBytes = 0L
                        )
                    )
                }
            }.ifEmpty { defaultDistributions() }
        }.getOrElse { defaultDistributions() }
    }

    private fun saveRegistry(items: List<LinuxDistributionConfig>) {
        permissionFixer.ensureInternalDirectories()
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("displayName", item.displayName)
                    put("releaseLabel", item.releaseLabel)
                    put("architecture", item.architecture)
                    put("rootfsRelativePath", item.rootfsRelativePath)
                    put("defaultShell", item.defaultShell)
                    put("prootFlags", item.prootFlags)
                    put("packageManagerName", item.packageManagerName)
                    put("isEnabled", item.isEnabled)
                }
            )
        }
        registryFile.writeText(arr.toString(2), Charsets.UTF_8)
    }

    private fun defaultDistributions(): List<LinuxDistributionConfig> = listOf(
        LinuxDistributionConfig(
            id = "ubuntu-noble",
            displayName = "Ubuntu 24.04 LTS",
            releaseLabel = "Noble Numbat • PRoot",
            architecture = "aarch64",
            rootfsRelativePath = "ubuntu-noble",
            defaultShell = "/bin/bash",
            prootFlags = "-0 -w /root -b /dev -b /proc -b /sys",
            packageManagerName = "apt / dpkg",
            isEnabled = true,
            isRootfsPresentOnDisk = false,
            diskSizeBytes = 0L
        ),
        LinuxDistributionConfig(
            id = "debian-bookworm",
            displayName = "Debian 12 Bookworm",
            releaseLabel = "Stable • APK Build RootFS",
            architecture = "aarch64",
            rootfsRelativePath = "debian-bookworm",
            defaultShell = "/bin/bash",
            prootFlags = "-0 -w /root --link2symlink",
            packageManagerName = "apt",
            isEnabled = false,
            isRootfsPresentOnDisk = false,
            diskSizeBytes = 0L
        ),
        LinuxDistributionConfig(
            id = "alpine-mini",
            displayName = "Alpine Linux 3.20",
            releaseLabel = "Ultra Lightweight",
            architecture = "aarch64",
            rootfsRelativePath = "alpine-mini",
            defaultShell = "/bin/zsh",
            prootFlags = "-0 -w /root",
            packageManagerName = "apk",
            isEnabled = false,
            isRootfsPresentOnDisk = false,
            diskSizeBytes = 0L
        ),
        LinuxDistributionConfig(
            id = "nixos-flake",
            displayName = "NixOS Flake Ortamı",
            releaseLabel = "Reproducible JDK17 + Gradle",
            architecture = "aarch64",
            rootfsRelativePath = "nixos-flake",
            defaultShell = "/bin/zsh",
            prootFlags = "-0 -w /workspace",
            packageManagerName = "nix",
            isEnabled = false,
            isRootfsPresentOnDisk = false,
            diskSizeBytes = 0L
        )
    )

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { calculateDirSize(it) } ?: 0L
    }
}
