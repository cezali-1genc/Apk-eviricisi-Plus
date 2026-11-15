package com.example.apktranslatorpro.core.environment

import android.content.Context
import java.io.File

enum class BinaryAvailabilityStatus(val labelTr: String) {
    READY("Hazır (Çalıştırılabilir)"),
    PERMISSION_FIXED("İzin Onarıldı"),
    NOT_EXECUTABLE("Çalıştırma İzni Eksik"),
    NOT_INSTALLED("Kurulu Değil")
}

data class BinaryVerificationResult(
    val binaryName: String,
    val resolvedPath: String?,
    val existed: Boolean,
    val wasExecutableBefore: Boolean,
    val isExecutableAfter: Boolean,
    val status: BinaryAvailabilityStatus
)

data class PermissionFixReport(
    val scannedRoots: List<String>,
    val totalFilesScanned: Int,
    val newlyMadeExecutableCount: Int,
    val alreadyExecutableCount: Int,
    val failedPaths: List<String>,
    val coreBinaries: Map<String, BinaryVerificationResult>,
    val internalPathEnv: String,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val allCoreBinariesExecutable: Boolean
        get() = listOf("proot", "bash", "zsh").all { name ->
            val info = coreBinaries[name]
            info != null && info.existed && info.isExecutableAfter
        }

    fun summaryTr(): String {
        val coreStatus = listOf("proot", "bash", "zsh").joinToString(" • ") { name ->
            val info = coreBinaries[name]
            val tag = when {
                info == null || !info.existed -> "Yok"
                info.isExecutableAfter -> "✓ Hazır"
                else -> "✗ Yetki Yok"
            }
            "$name: $tag"
        }
        return "Taranan dosya: $totalFilesScanned • Onarılan (+x): $newlyMadeExecutableCount • $coreStatus"
    }
}

/**
 * Verifies and enforces executable permission bits recursively across internal application
 * binary directories (especially `proot`, `bash`, `zsh`, and package managers) and prepares
 * isolated `ProcessBuilder` environments pointing strictly to internal app directories.
 */
class PermissionFixer(
    private val context: Context,
    private val internalRootOverride: File? = null
) {
    companion object {
        val PRIORITY_BINARIES = listOf("proot", "bash", "zsh")
        val PACKAGE_MANAGER_BINARIES = listOf("apt", "apt-get", "pkg", "apk", "dpkg", "nix")
        val ALL_TRACKED_BINARIES = PRIORITY_BINARIES + PACKAGE_MANAGER_BINARIES
    }

    val internalRootDir: File
        get() = internalRootOverride ?: context.filesDir

    val linuxRootDir: File
        get() = File(internalRootDir, "linux")

    val usrDir: File
        get() = File(linuxRootDir, "usr")

    val usrBinDir: File
        get() = File(usrDir, "bin")

    val usrLibDir: File
        get() = File(usrDir, "lib")

    val binDir: File
        get() = File(linuxRootDir, "bin")

    val prootDir: File
        get() = File(linuxRootDir, "proot")

    val distrosDir: File
        get() = File(linuxRootDir, "distros")

    val homeDir: File
        get() = File(linuxRootDir, "home")

    val tmpDir: File
        get() = File(context.cacheDir, "linux_tmp")

    val prootTmpDir: File
        get() = File(context.cacheDir, "proot_tmp")

    fun ensureInternalDirectories() {
        listOf(
            linuxRootDir,
            usrDir,
            usrBinDir,
            usrLibDir,
            binDir,
            prootDir,
            distrosDir,
            homeDir,
            tmpDir,
            prootTmpDir
        ).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    /**
     * Returns all internal binary candidate directories to scan and add to PATH.
     */
    fun defaultBinaryDirectories(): List<File> {
        val candidates = mutableListOf(
            usrBinDir,
            binDir,
            prootDir,
            File(internalRootDir, "usr/bin"),
            File(internalRootDir, "bin"),
            File(internalRootDir, "proot")
        )
        if (distrosDir.exists() && distrosDir.isDirectory) {
            distrosDir.listFiles()?.filter { it.isDirectory }?.forEach { distro ->
                candidates.add(File(distro, "bin"))
                candidates.add(File(distro, "usr/bin"))
                candidates.add(File(distro, "usr/local/bin"))
            }
        }
        val nativeLibPath = context.applicationInfo?.nativeLibraryDir
        if (!nativeLibPath.isNullOrBlank()) {
            candidates.add(File(nativeLibPath))
        }
        return candidates.distinctBy { it.absolutePath }
    }

    /**
     * Recursively verifies and applies File.setExecutable(true) across internal binary directories,
     * with focused tracking on `proot`, `bash`, and `zsh` as well as package managers.
     */
    fun verifyAndFixExecutables(
        extraDirectories: List<File> = emptyList()
    ): PermissionFixReport {
        ensureInternalDirectories()

        val scanRoots = (defaultBinaryDirectories() + extraDirectories)
            .distinctBy { it.absolutePath }

        var totalChecked = 0
        var newlyFixed = 0
        var alreadyExecutable = 0
        val failedPaths = mutableListOf<String>()
        val discoveredPriority = linkedMapOf<String, MutableList<Pair<File, Boolean>>>()

        fun processFileRecursively(target: File) {
            if (!target.exists()) return
            if (target.isDirectory) {
                target.setExecutable(true, true)
                val children = target.listFiles() ?: return
                for (child in children) {
                    processFileRecursively(child)
                }
                return
            }

            if (!target.isFile) return
            totalChecked++
            val wasExec = target.canExecute()
            val fixed = if (wasExec) {
                alreadyExecutable++
                true
            } else {
                val ownerOnlyResult = target.setExecutable(true, true)
                val nowExec = target.canExecute() || ownerOnlyResult || target.setExecutable(true, false)
                if (nowExec && target.canExecute()) {
                    newlyFixed++
                    true
                } else if (nowExec) {
                    newlyFixed++
                    true
                } else {
                    failedPaths.add(target.absolutePath)
                    false
                }
            }

            val lowerName = target.name.lowercase()
            if (lowerName in ALL_TRACKED_BINARIES || lowerName.startsWith("libproot")) {
                val canonical = if (lowerName.startsWith("libproot")) "proot" else lowerName
                discoveredPriority.getOrPut(canonical) { mutableListOf() }
                    .add(target to wasExec)
            }
        }

        for (root in scanRoots) {
            if (root.exists()) {
                processFileRecursively(root)
            }
        }

        val coreMap = linkedMapOf<String, BinaryVerificationResult>()
        for (binary in ALL_TRACKED_BINARIES) {
            val matches = discoveredPriority[binary].orEmpty()
            val best = matches.firstOrNull { it.first.canExecute() } ?: matches.firstOrNull()
            if (best == null) {
                coreMap[binary] = BinaryVerificationResult(
                    binaryName = binary,
                    resolvedPath = null,
                    existed = false,
                    wasExecutableBefore = false,
                    isExecutableAfter = false,
                    status = BinaryAvailabilityStatus.NOT_INSTALLED
                )
            } else {
                val (file, wasBefore) = best
                val isAfter = file.canExecute()
                val status = when {
                    !wasBefore && isAfter -> BinaryAvailabilityStatus.PERMISSION_FIXED
                    isAfter -> BinaryAvailabilityStatus.READY
                    else -> BinaryAvailabilityStatus.NOT_EXECUTABLE
                }
                coreMap[binary] = BinaryVerificationResult(
                    binaryName = binary,
                    resolvedPath = file.absolutePath,
                    existed = true,
                    wasExecutableBefore = wasBefore,
                    isExecutableAfter = isAfter,
                    status = status
                )
            }
        }

        val envMap = buildInternalEnvironment()
        return PermissionFixReport(
            scannedRoots = scanRoots.filter { it.exists() }.map { it.absolutePath },
            totalFilesScanned = totalChecked,
            newlyMadeExecutableCount = newlyFixed,
            alreadyExecutableCount = alreadyExecutable,
            failedPaths = failedPaths.distinct(),
            coreBinaries = coreMap,
            internalPathEnv = envMap["PATH"].orEmpty()
        )
    }

    /**
     * Builds an internal-only environment variable map pointing PATH, HOME, PREFIX, TMPDIR,
     * PROOT_TMP_DIR, LD_LIBRARY_PATH, and SHELL cleanly to this app's internal storage paths.
     */
    fun buildInternalEnvironment(extraEnv: Map<String, String> = emptyMap()): Map<String, String> {
        ensureInternalDirectories()

        val internalBins = listOf(
            usrBinDir.absolutePath,
            binDir.absolutePath,
            prootDir.absolutePath,
            File(internalRootDir, "usr/bin").absolutePath,
            File(internalRootDir, "bin").absolutePath
        ).distinct()

        val nativeLibDir = context.applicationInfo?.nativeLibraryDir
        val pathSegments = buildList {
            addAll(internalBins)
            if (!nativeLibDir.isNullOrBlank()) add(nativeLibDir)
            add("/system/bin")
            add("/system/xbin")
        }.distinct()

        val ldPaths = buildList {
            add(usrLibDir.absolutePath)
            add(File(linuxRootDir, "lib").absolutePath)
            add(File(internalRootDir, "usr/lib").absolutePath)
            if (!nativeLibDir.isNullOrBlank()) add(nativeLibDir)
        }.distinct()

        val preferredShell = resolvePreferredShellPath()

        val env = linkedMapOf(
            "HOME" to homeDir.absolutePath,
            "PREFIX" to usrDir.absolutePath,
            "TMPDIR" to tmpDir.absolutePath,
            "TEMP" to tmpDir.absolutePath,
            "PROOT_TMP_DIR" to prootTmpDir.absolutePath,
            "APK_EDITOR_LINUX_ROOT" to linuxRootDir.absolutePath,
            "PATH" to pathSegments.joinToString(File.pathSeparator),
            "LD_LIBRARY_PATH" to ldPaths.joinToString(File.pathSeparator),
            "SHELL" to preferredShell,
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "ANDROID_DATA" to "/data"
        )

        val prootLoader = File(prootDir, "loader")
        if (prootLoader.exists()) {
            prootLoader.setExecutable(true, true)
            env["PROOT_LOADER"] = prootLoader.absolutePath
        }

        extraEnv.forEach { (k, v) -> env[k] = v }
        return env
    }

    /**
     * Configures a ProcessBuilder instance with verified internal binary paths and environment variables.
     */
    fun createConfiguredProcessBuilder(
        command: List<String>,
        workingDirectory: File? = null,
        extraEnv: Map<String, String> = emptyMap()
    ): ProcessBuilder {
        require(command.isNotEmpty()) { "Komut listesi boş olamaz." }
        ensureInternalDirectories()

        val resolvedCommand = command.toMutableList()
        val firstToken = resolvedCommand.first()
        if (!firstToken.contains(File.separatorChar)) {
            resolveInternalExecutable(firstToken)?.let { resolved ->
                resolvedCommand[0] = resolved.absolutePath
            }
        } else {
            val direct = File(firstToken)
            if (direct.exists() && !direct.canExecute()) {
                direct.setExecutable(true, true)
            }
        }

        return ProcessBuilder(resolvedCommand).apply {
            directory(workingDirectory ?: homeDir)
            redirectErrorStream(true)
            val env = environment()
            env.clear()
            env.putAll(buildInternalEnvironment(extraEnv))
        }
    }

    fun resolveInternalExecutable(name: String): File? {
        val candidates = listOf(
            File(usrBinDir, name),
            File(binDir, name),
            File(prootDir, name),
            File(internalRootDir, "usr/bin/$name"),
            File(internalRootDir, "bin/$name"),
            File(internalRootDir, "proot/$name")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile) {
                if (!candidate.canExecute()) {
                    candidate.setExecutable(true, true)
                }
                if (candidate.canExecute()) {
                    return candidate
                }
            }
        }
        return null
    }

    fun resolvePreferredShellPath(): String {
        val zsh = resolveInternalExecutable("zsh")
        if (zsh != null) return zsh.absolutePath
        val bash = resolveInternalExecutable("bash")
        if (bash != null) return bash.absolutePath
        return "/system/bin/sh"
    }
}
