package com.example.apktranslatorpro.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.apktranslatorpro.core.environment.BinaryAvailabilityStatus
import com.example.apktranslatorpro.core.environment.LinuxDistributionConfig
import com.example.apktranslatorpro.core.environment.PackageManagerHealth
import com.example.apktranslatorpro.core.environment.TerminalSessionInfo
import com.example.apktranslatorpro.ui.viewmodel.LinuxEnvUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

/**
 * APK Editor Ultra card-based dashboard managing:
 * 1. Package management services & PermissionFixer binary checks (proot, bash, zsh)
 * 2. Installed Linux distributions (with toggle switches & quick config dialogs)
 * 3. Active terminal sessions (with live output & Stop buttons)
 * 4. Runtime device permission requester for the phone owner (Notifications, Install Packages, SAF folder)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkEditorDashboard(
    envState: LinuxEnvUiState,
    hasSafOutputFolder: Boolean,
    outputFolderLabel: String,
    onToggleDistro: (distroId: String, enabled: Boolean) -> Unit,
    onUpdateDistroConfig: (distroId: String, defaultShell: String, prootFlags: String) -> Unit,
    onRunPermissionFixer: () -> Unit,
    onStartTerminalSession: (title: String, command: String, distro: LinuxDistributionConfig?) -> Unit,
    onStopTerminalSession: (sessionId: String) -> Unit,
    onClearFinishedSessions: () -> Unit,
    onRequestSafOutputFolder: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var configuringDistro by remember { mutableStateOf<LinuxDistributionConfig?>(null) }
    var customCommandInput by remember { mutableStateOf("pwd && id && ls -la") }

    var notificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    val installPermissionGranted = remember(envState) {
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    }

    configuringDistro?.let { distro ->
        DistroConfigDialog(
            distro = distro,
            environmentPreview = envState.environmentVariables,
            onDismiss = { configuringDistro = null },
            onSave = { shell, flags ->
                onUpdateDistroConfig(distro.id, shell, flags)
                configuringDistro = null
            },
            onLaunchWithDistro = { shell, flags ->
                onUpdateDistroConfig(distro.id, shell, flags)
                onStartTerminalSession(
                    "${distro.displayName} Kabuğu",
                    "echo \"DISTRO=${distro.id}\" && echo \"SHELL=$shell\" && echo \"PROOT=$flags\" && pwd && sleep 2",
                    distro.copy(defaultShell = shell, prootFlags = flags)
                )
                configuringDistro = null
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("apk_editor_dashboard"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .testTag("apk_editor_dashboard_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (onBackClick != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.testTag("dashboard_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                            }
                            Column {
                                Text(
                                    text = "APK Editor Ultra • Linux & Terminal",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "CEZALI GENÇ (SG) • PRoot / Shell / Paket & İzin Yönetimi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        UltraStatusPill(text = "ULTRA ENGINE", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            // 1. Package Management Service & PermissionFixer Status Card
            item {
                PackageManagementStatusCard(
                    envState = envState,
                    onRunPermissionFixer = onRunPermissionFixer
                )
            }

            // 2. Installed Linux Distributions Card
            item {
                InstalledDistributionsCard(
                    distributions = envState.distributions,
                    onToggleDistro = onToggleDistro,
                    onConfigureDistro = { configuringDistro = it }
                )
            }

            // 3. Active Terminal Sessions Card
            item {
                ActiveTerminalSessionsCard(
                    sessions = envState.activeSessions,
                    customCommand = customCommandInput,
                    onCustomCommandChange = { customCommandInput = it },
                    onStartSession = {
                        onStartTerminalSession(
                            "Dahili Kabuk Oturumu",
                            customCommandInput.ifBlank { "pwd && echo PATH=\$PATH" },
                            envState.distributions.firstOrNull { it.isEnabled }
                        )
                    },
                    onStopSession = onStopTerminalSession,
                    onClearFinished = onClearFinishedSessions
                )
            }

            // 4. Phone Owner Runtime Permissions Card ("İZİN KENDİN İSTE TELEFON SAHİBİNDEN")
            item {
                PhoneOwnerPermissionsCard(
                    notificationGranted = notificationGranted,
                    installPermissionGranted = installPermissionGranted,
                    hasSafOutputFolder = hasSafOutputFolder,
                    outputFolderLabel = outputFolderLabel,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationGranted = true
                        }
                    },
                    onRequestInstallPermission = {
                        runCatching {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    },
                    onRequestSafOutputFolder = onRequestSafOutputFolder
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackageManagementStatusCard(
    envState: LinuxEnvUiState,
    onRunPermissionFixer: () -> Unit
) {
    val snapshot = envState.serviceSnapshot
    val healthColor = when (snapshot.health) {
        PackageManagerHealth.READY -> StatusSuccess
        PackageManagerHealth.NEEDS_PERMISSION_FIX -> StatusWarning
        PackageManagerHealth.EXTERNAL_TERMUX_REQUIRED -> StatusInfo
    }

    UltraGlassCard(
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("package_management_status_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Paket Yönetimi & İkili Durumu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                UltraStatusPill(text = snapshot.health.labelTr, color = healthColor)
            }

            Text(
                text = snapshot.detailMessageTr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val report = envState.lastPermissionReport
                listOf("proot", "bash", "zsh", "apt", "pkg", "apk", "nix").forEach { bin ->
                    val binResult = report?.coreBinaries?.get(bin)
                    val ready = binResult?.let { it.existed && it.isExecutableAfter } == true
                    val chipText = when {
                        binResult == null -> "$bin: ?"
                        ready -> "$bin: ✓ +x"
                        binResult.status == BinaryAvailabilityStatus.NOT_EXECUTABLE -> "$bin: ! +x"
                        else -> "$bin: Yok"
                    }
                    FilterChip(
                        selected = ready,
                        onClick = onRunPermissionFixer,
                        label = { Text(chipText, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (envState.lastPermissionReport != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = envState.lastPermissionReport.summaryTr(),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = onRunPermissionFixer,
                enabled = !envState.isFixingPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fix_permissions_button")
            ) {
                if (envState.isFixingPermissions) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("İzinler Doğrulanıyor...")
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("İzinleri Onar (+x / PermissionFixer)")
                }
            }
        }
    }
}

@Composable
private fun InstalledDistributionsCard(
    distributions: List<LinuxDistributionConfig>,
    onToggleDistro: (String, Boolean) -> Unit,
    onConfigureDistro: (LinuxDistributionConfig) -> Unit
) {
    UltraGlassCard(
        accentColor = MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("installed_distros_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    text = "Kurulu Linux Dağıtımları (${distributions.count { it.isEnabled }}/${distributions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            distributions.forEach { distro ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = distro.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                UltraStatusPill(
                                    text = distro.architecture,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "${distro.releaseLabel} • ${distro.packageManagerName} • ${distro.defaultShell}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (distro.isRootfsPresentOnDisk) {
                                    "RootFS hazır (${distro.diskSizeBytes / 1024} KB)"
                                } else {
                                    "RootFS yolu: linux/distros/${distro.rootfsRelativePath}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onConfigureDistro(distro) },
                                modifier = Modifier.testTag("configure_distro_${distro.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "${distro.displayName} yapılandır",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Switch(
                                checked = distro.isEnabled,
                                onCheckedChange = { onToggleDistro(distro.id, it) },
                                modifier = Modifier.testTag("distro_switch_${distro.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTerminalSessionsCard(
    sessions: List<TerminalSessionInfo>,
    customCommand: String,
    onCustomCommandChange: (String) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: (String) -> Unit,
    onClearFinished: () -> Unit
) {
    UltraGlassCard(
        accentColor = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_terminal_sessions_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = "Aktif Terminal Oturumları (${sessions.count { it.isRunning }})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (sessions.any { !it.isRunning }) {
                    TextButton(onClick = onClearFinished) {
                        Text("Bitenleri Temizle")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customCommand,
                    onValueChange = onCustomCommandChange,
                    label = { Text("Kabuk Komutu (ProcessBuilder)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_command_input")
                )
                Button(
                    onClick = onStartSession,
                    modifier = Modifier.testTag("start_terminal_session_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Çalıştır")
                }
            }

            AnimatedVisibility(visible = sessions.isEmpty()) {
                Text(
                    text = "Aktif terminal oturumu yok. Yukarıdan gerçek ProcessBuilder komutu başlatabilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            sessions.forEach { session ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("terminal_session_item_${session.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${session.title} (#${session.id})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${session.shellPath} • ${if (session.isRunning) "ÇALIŞIYOR" else "Bitti (kod=${session.exitCode ?: 0})"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (session.isRunning) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (session.isRunning) {
                                OutlinedButton(
                                    onClick = { onStopSession(session.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("stop_session_${session.id}")
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = "Durdur", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Durdur")
                                }
                            }
                        }

                        if (session.outputLines.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                text = session.outputLines.joinToString("\n"),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneOwnerPermissionsCard(
    notificationGranted: Boolean,
    installPermissionGranted: Boolean,
    hasSafOutputFolder: Boolean,
    outputFolderLabel: String,
    onRequestNotificationPermission: () -> Unit,
    onRequestInstallPermission: () -> Unit,
    onRequestSafOutputFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    UltraGlassCard(
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .testTag("phone_owner_permissions_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Telefon Sahibi İzin Merkezi (Çalışma Zamanı)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Google Play politikalarına tam uyumlu şekilde yalnızca gerekli sistem ve SAF izinleri kullanıcı onayıyla istenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionActionRow(
                icon = Icons.Filled.NotificationsActive,
                title = "Bildirim İzni (POST_NOTIFICATIONS)",
                subtitle = if (notificationGranted) "Verildi — APK arka plan bildirimleri aktif" else "Arka plan APK işlem durumu için telefon sahibinden iste",
                granted = notificationGranted,
                buttonLabel = if (notificationGranted) "Aktif" else "İzin İste",
                buttonTag = "request_notification_permission_button",
                onClick = onRequestNotificationPermission
            )

            PermissionActionRow(
                icon = Icons.Filled.InstallMobile,
                title = "APK Kurulum İzni (Bilinmeyen Kaynaklar)",
                subtitle = if (installPermissionGranted) "Verildi — Çevrilen APK doğrudan kurulabilir" else "Oluşturulan APK'yı yüklemek için sistem ayarından onayla",
                granted = installPermissionGranted,
                buttonLabel = if (installPermissionGranted) "Açık" else "İzin İste",
                buttonTag = "request_install_permission_button",
                onClick = onRequestInstallPermission
            )

            PermissionActionRow(
                icon = Icons.Filled.FolderOpen,
                title = "SAF Çıktı Klasörü İzni (Kapsamlı Depolama)",
                subtitle = if (hasSafOutputFolder) "Seçildi: $outputFolderLabel" else "Geniş depolama izni olmadan kalıcı çıktı klasörü seç",
                granted = hasSafOutputFolder,
                buttonLabel = if (hasSafOutputFolder) "Değiştir" else "Klasör Seç",
                buttonTag = "request_saf_folder_permission_button",
                onClick = onRequestSafOutputFolder
            )
        }
    }
}

@Composable
private fun PermissionActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    buttonTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle else icon,
                    contentDescription = title,
                    tint = if (granted) StatusSuccess else MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.testTag(buttonTag)
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun DistroConfigDialog(
    distro: LinuxDistributionConfig,
    environmentPreview: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (shell: String, flags: String) -> Unit,
    onLaunchWithDistro: (shell: String, flags: String) -> Unit
) {
    var shellInput by remember { mutableStateOf(distro.defaultShell) }
    var flagsInput by remember { mutableStateOf(distro.prootFlags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Build, contentDescription = null) },
        title = { Text("${distro.displayName} Yapılandırması") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Varsayılan Kabuk (bash / zsh / sh):",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("/bin/bash", "/bin/zsh", "/system/bin/sh").forEach { sh ->
                        FilterChip(
                            selected = shellInput == sh,
                            onClick = { shellInput = sh },
                            label = { Text(sh.substringAfterLast('/')) }
                        )
                    }
                }
                OutlinedTextField(
                    value = shellInput,
                    onValueChange = { shellInput = it },
                    label = { Text("Kabuk Yolu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = flagsInput,
                    onValueChange = { flagsInput = it },
                    label = { Text("PRoot Bayrakları") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val previewLines = listOf("HOME", "PREFIX", "TMPDIR", "SHELL")
                        .mapNotNull { key -> environmentPreview[key]?.let { "$key=$it" } }
                        .joinToString("\n")
                    Text(
                        text = "Dahili ProcessBuilder Ortamı:\n$previewLines",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLaunchWithDistro(shellInput, flagsInput) },
                modifier = Modifier.testTag("distro_config_save_launch_button")
            ) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kaydet & Çalıştır")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onDismiss) { Text("Kapat") }
                TextButton(onClick = { onSave(shellInput, flagsInput) }) { Text("Sadece Kaydet") }
            }
        }
    )
}
