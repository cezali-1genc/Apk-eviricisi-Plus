package com.example.apktranslatorpro.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.apktranslatorpro.data.local.RecentApkEntity
import com.example.apktranslatorpro.data.local.TranslationProjectEntity
import com.example.apktranslatorpro.data.local.UserGlossaryEntity
import com.example.apktranslatorpro.data.remote.ApkProjectRemote
import com.example.apktranslatorpro.ui.viewmodel.BuildProcessUiState
import com.example.apktranslatorpro.ui.viewmodel.CloudProjectsUiState
import com.example.apktranslatorpro.ui.viewmodel.FilePickerUiState
import com.example.apktranslatorpro.ui.viewmodel.GeminiAnalysisUiState
import com.example.apktranslatorpro.ui.viewmodel.GitUiState
import com.example.apktranslatorpro.ui.viewmodel.MainUiState
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkToolsScreen(
    uiState: MainUiState,
    filePickerState: FilePickerUiState,
    buildState: BuildProcessUiState,
    gitState: GitUiState,
    recentApks: List<RecentApkEntity>,
    savedProjects: List<TranslationProjectEntity>,
    userGlossary: List<UserGlossaryEntity>,
    userDisplayName: String,
    themeMode: AppThemeMode,
    cloudProjectsState: CloudProjectsUiState,
    requestedModuleId: String? = null,
    onConsumeRequestedModule: () -> Unit = {},
    onPickApkClick: () -> Unit,
    onPickComparisonApkClick: () -> Unit,
    onVerifySafIntegrityClick: () -> Unit,
    onDecompileApkClick: () -> Unit,
    onRebuildApkClick: () -> Unit,
    onSaveWorkingFile: (File, String) -> Unit,
    onBulkRenameKeys: (String, String, Boolean) -> Unit,
    onUpdateResourceTranslation: (String, String) -> Unit,
    onExportWorkspaceZip: (Uri) -> Unit,
    onExportTextToUri: (Uri, String, String) -> Unit,
    onRegenerateSignerKey: () -> Unit,
    onDeleteRecentApk: (String) -> Unit,
    onClearRecentApks: () -> Unit,
    onRunGeminiAnalysisClick: () -> Unit,
    onDeleteCloudProject: (String) -> Unit,
    onOpenBinaryXmlTab: () -> Unit,
    onSaveProjectClick: (String, String) -> Unit,
    onLoadProjectClick: (String) -> Unit,
    onDuplicateProjectClick: (String) -> Unit,
    onDeleteProjectClick: (String) -> Unit,
    onAddGlossaryTerm: (String, String) -> Unit,
    onDeleteGlossaryTerm: (String) -> Unit,
    onStartWorkManagerClick: () -> Unit,
    onCancelWorkManagerClick: () -> Unit,
    onGitUrlChanged: (String) -> Unit,
    onGitBranchChanged: (String) -> Unit,
    onGitPatChanged: (String) -> Unit,
    onGitProbeRefs: () -> Unit,
    onGitClone: () -> Unit,
    onGitSelectRepo: (String) -> Unit,
    onGitInspectDiff: (String) -> Unit,
    onGitExportStrings: () -> Unit,
    onGitCommit: (String, String) -> Unit,
    onGitPushFile: (String, String) -> Unit,
    onGitDeleteRepo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeModule by remember(requestedModuleId) {
        mutableStateOf(
            if (requestedModuleId != null) {
                ToolkitModule.entries.firstOrNull { it.id == requestedModuleId }.also {
                    onConsumeRequestedModule()
                }
            } else {
                null
            }
        )
    }

    when (val mod = activeModule) {
        ToolkitModule.XML_EDITOR, ToolkitModule.RESOURCE_EDITOR -> {
            XmlEditorScreen(
                buildState = buildState,
                hasApkSelected = uiState.selectedApkUri != null,
                onDecompileApkClick = onDecompileApkClick,
                onSaveXmlFile = onSaveWorkingFile,
                onBulkRenameKeys = onBulkRenameKeys,
                onOpenXmlDiffComparison = { activeModule = ToolkitModule.XML_DIFF },
                onBackClick = { activeModule = null },
                modifier = modifier
            )
            return
        }
        ToolkitModule.XML_DIFF -> {
            XmlDiffComparisonScreen(
                workspace = buildState.workspace,
                hasApkSelected = uiState.selectedApkUri != null,
                isBusy = buildState.isBusy,
                onDecompileApkClick = onDecompileApkClick,
                onExportUnifiedPatch = { uri, content -> onExportTextToUri(uri, content, "XML Diff Yama") },
                onOpenXmlEditor = { activeModule = ToolkitModule.XML_EDITOR },
                onBackClick = { activeModule = null },
                modifier = modifier
            )
            return
        }
        ToolkitModule.GIT_INTEGRATION -> {
            GitIntegrationScreen(
                gitState = gitState,
                hasApkResources = uiState.resources.isNotEmpty(),
                targetLangCode = uiState.targetLanguage.code,
                userDisplayName = userDisplayName,
                onUrlChanged = onGitUrlChanged,
                onBranchChanged = onGitBranchChanged,
                onPatChanged = onGitPatChanged,
                onProbeRefsClick = onGitProbeRefs,
                onCloneRepoClick = onGitClone,
                onSelectRepoClick = onGitSelectRepo,
                onInspectDiffClick = onGitInspectDiff,
                onExportTranslatedStringsToRepo = onGitExportStrings,
                onCommitChangesClick = onGitCommit,
                onPushFileToGitHub = onGitPushFile,
                onDeleteRepoClick = onGitDeleteRepo,
                onBackClick = { activeModule = null },
                modifier = modifier
            )
            return
        }
        ToolkitModule.APK_COMPARE -> {
            ApkCompareScreen(
                primaryApk = uiState.apkMetadata,
                secondaryApk = uiState.comparisonApkMetadata,
                isInspectingSecondary = uiState.isInspectingComparisonApk,
                onPickSecondaryApkClick = onPickComparisonApkClick,
                onBackClick = { activeModule = null },
                modifier = modifier
            )
            return
        }
        ToolkitModule.PROJECT_MANAGER -> {
            ProjectAndGlossaryScreen(
                hasApkLoaded = uiState.apkMetadata != null,
                defaultProjectName = uiState.apkMetadata?.let { "${it.appLabel} (${it.versionName})" } ?: "Yeni Proje",
                projects = savedProjects,
                glossary = userGlossary,
                backgroundWorkStatus = uiState.backgroundWorkStatus,
                isBackgroundWorkRunning = uiState.isBackgroundWorkRunning,
                onSaveProjectClick = onSaveProjectClick,
                onLoadProjectClick = onLoadProjectClick,
                onDuplicateProjectClick = onDuplicateProjectClick,
                onDeleteProjectClick = onDeleteProjectClick,
                onAddGlossaryTerm = onAddGlossaryTerm,
                onDeleteGlossaryTerm = onDeleteGlossaryTerm,
                onStartWorkManagerClick = onStartWorkManagerClick,
                onCancelWorkManagerClick = onCancelWorkManagerClick,
                onBackClick = { activeModule = null },
                modifier = modifier
            )
            return
        }
        ToolkitModule.MANIFEST_EDITOR -> {
            ManifestEditorScreen(
                workspace = buildState.workspace,
                hasApkSelected = uiState.selectedApkUri != null,
                isBusy = buildState.isBusy,
                onDecompileApkClick = onDecompileApkClick,
                onSaveManifestToWorkspace = onSaveWorkingFile,
                onExportManifestToUri = { uri, content ->
                    onExportTextToUri(uri, content, "AndroidManifest.xml")
                },
                onBackClick = { activeModule = null },
                modifier = modifier
            )
            return
        }
        ToolkitModule.SIMPLE_EDIT,
        ToolkitModule.APK_DECOMPILER,
        ToolkitModule.APK_REBUILDER,
        ToolkitModule.SMALI_EDITOR,
        ToolkitModule.DEX_EDITOR,
        ToolkitModule.JAVA_VIEWER,
        ToolkitModule.STRING_CREATOR,
        ToolkitModule.PATCH_CREATOR -> {
            ToolkitCodeEditorModuleScreen(
                module = mod,
                uiState = uiState,
                buildState = buildState,
                themeMode = themeMode,
                onDecompileApk = onDecompileApkClick,
                onRebuildApk = onRebuildApkClick,
                onSaveWorkingFile = onSaveWorkingFile,
                onUpdateResourceTranslation = onUpdateResourceTranslation,
                onOpenXmlEditor = { activeModule = ToolkitModule.XML_EDITOR },
                onBackClick = { activeModule = null },
                modifier = modifier
            )
            return
        }
        ToolkitModule.DEVELOPER_BROWSER,
        ToolkitModule.APK_SIGNER,
        ToolkitModule.ZIP_MANAGER,
        ToolkitModule.BACKUP_RESTORE,
        ToolkitModule.NOTIFICATION_MAKER,
        ToolkitModule.TOAST_MAKER,
        ToolkitModule.BASE64_TOOLS,
        ToolkitModule.HTML_ENCODER -> {
            ToolkitUtilityModuleScreen(
                module = mod,
                uiState = uiState,
                buildState = buildState,
                recentApks = recentApks,
                onDecompileApk = onDecompileApkClick,
                onExportWorkspaceZip = onExportWorkspaceZip,
                onRebuildAndSignApk = onRebuildApkClick,
                onRegenerateSignerKey = onRegenerateSignerKey,
                onDeleteRecentApk = onDeleteRecentApk,
                onClearRecentApks = onClearRecentApks,
                onBackClick = { activeModule = null },
                modifier = modifier
            )
            return
        }
        ToolkitModule.FULL_EDIT -> {
            activeModule = null
            onOpenBinaryXmlTab()
        }
        null -> Unit
    }

    val metadata = uiState.apkMetadata
    val inspection = metadata?.fullInspection

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .testTag("apk_tools_lazy_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("SAF & ApkSecurityUtils Bütünlük Denetimi", style = MaterialTheme.typography.titleLarge)
                        }

                        Text(
                            text = uiState.selectedApkUri?.let { "Kayıtlı SAF URI: $it" }
                                ?: "Henüz bir APK dosyası seçilmedi. SAF ile salt okunur APK seçebilirsiniz.",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        val activeSha = filePickerState.sha256Checksum.ifBlank { metadata?.sha256 ?: "" }
                        if (activeSha.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("file_picker_sha256_badge")
                            ) {
                                Text(
                                    text = "FilePicker SHA-256: $activeSha",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (uiState.safVerificationSummary.isNotBlank()) {
                            Surface(
                                color = if (uiState.safFileVerified) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.safVerificationSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onPickApkClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tools_pick_apk_button")
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("APK Seç (SAF)")
                            }
                            OutlinedButton(
                                onClick = onVerifySafIntegrityClick,
                                enabled = uiState.selectedApkUri != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("verify_saf_integrity_button")
                            ) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("SHA-256 Doğrula")
                            }
                        }

                        if (inspection != null) {
                            HorizontalDivider()
                            Text("Kriptografik Özetler (Değiştirilmemiş Orijinal APK)", style = MaterialTheme.typography.titleSmall)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("SHA-256: ${inspection.sha256Hex}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                    Text("SHA-1:   ${inspection.sha1Hex}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                    Text("MD5:     ${inspection.md5Hex}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            item {
                ToolkitDashboardGridCard(
                    onModuleClick = { selected ->
                        if (selected == ToolkitModule.FULL_EDIT) {
                            onOpenBinaryXmlTab()
                        } else {
                            activeModule = selected
                        }
                    }
                )
            }

            if (metadata != null && inspection != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Gemini AI APK Güvenlik & Çeviri Denetçisi", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = "Firebase AI Logic (gemini-3.8-flash) ile izinleri, imza risklerini, mimariyi ve yerelleştirme hazırlığını canlı analiz edin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = onRunGeminiAnalysisClick,
                                enabled = uiState.geminiAnalysisState !is GeminiAnalysisUiState.Loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("analyze_with_gemini_button")
                            ) {
                                if (uiState.geminiAnalysisState is GeminiAnalysisUiState.Loading ||
                                    uiState.geminiAnalysisState is GeminiAnalysisUiState.Streaming
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Gemini Analiz Ediyor...")
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Gemini ile APK'yı Denetle")
                                }
                            }

                            when (val gState = uiState.geminiAnalysisState) {
                                GeminiAnalysisUiState.Idle -> Unit
                                GeminiAnalysisUiState.Loading -> {
                                    Text("Gemini 3.8 Flash modeli yanıt hazırlıyor...", style = MaterialTheme.typography.bodySmall)
                                }
                                is GeminiAnalysisUiState.Streaming -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = gState.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                                    }
                                }
                                is GeminiAnalysisUiState.Success -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = gState.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                                    }
                                }
                                is GeminiAnalysisUiState.Error -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = gState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
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
                                Text("İmza & Sertifika İnceleyici (apksig)", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = if (inspection.isSignatureVerified) {
                                    "İmza Geçerli • v1=${yn(inspection.v1Signed)} v2=${yn(inspection.v2Signed)} v3=${yn(inspection.v3Signed)} v4=${yn(inspection.v4Signed)}"
                                } else {
                                    "Uyarı: İmza doğrulanamadı veya APK imzasız"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (inspection.isSignatureVerified) StatusSuccess else StatusWarning
                            )
                            inspection.signerCertificates.forEachIndexed { idx, cert ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text("Sertifika #${idx + 1} (${cert.keyAlgorithm})", style = MaterialTheme.typography.titleSmall)
                                        Text("Konu: ${cert.subjectDn}", style = MaterialTheme.typography.bodySmall)
                                        Text("Geçerlilik: ${cert.validFrom} -> ${cert.validUntil}", style = MaterialTheme.typography.bodySmall)
                                        Text("SHA-256: ${cert.sha256Fingerprint}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
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
                                    Text("ZIP, DEX, Native ABI & Zipalign", style = MaterialTheme.typography.titleLarge)
                                }
                                OutlinedButton(onClick = onOpenBinaryXmlTab) {
                                    Text("İkili XML Aç")
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AssistChip(onClick = {}, label = { Text("${inspection.totalZipEntries} ZIP öğesi") })
                                AssistChip(onClick = {}, label = { Text("${inspection.dexFiles.size} DEX") })
                                AssistChip(onClick = {}, label = { Text("${inspection.resEntryCount} res/") })
                                AssistChip(onClick = {}, label = { Text("${inspection.assetCount} assets/") })
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(if (inspection.resourcesArscStored) "resources.arsc: STORED ✓" else "resources.arsc: DEFLATED !")
                                    }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(if (inspection.isDebuggable) "Debuggable: AÇIK ⚠" else "Release Mod ✓")
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Manifest İzinleri & Bileşenler", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = "Activity: ${inspection.activities.size} • Service: ${inspection.services.size} • Receiver: ${inspection.receivers.size} • Provider: ${inspection.providers.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (inspection.dangerousPermissions.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                        Text(
                                            text = "Hassas İzinler (${inspection.dangerousPermissions.size}): ${inspection.dangerousPermissions.joinToString { it.substringAfterLast('.') }}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Bulut APK Projelerim (Firestore)", style = MaterialTheme.typography.titleLarge)
                        }
                        when (cloudProjectsState) {
                            CloudProjectsUiState.Loading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Bulut projeleri yükleniyor...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is CloudProjectsUiState.Error -> {
                                Text(text = cloudProjectsState.message, style = MaterialTheme.typography.bodySmall, color = StatusError)
                            }
                            is CloudProjectsUiState.Success -> {
                                if (cloudProjectsState.projects.isEmpty()) {
                                    Text(
                                        text = "Henüz buluta kaydedilmiş APK projesi yok (Google hesabı ile oturum açıldığında senkronize edilir).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (cloudProjectsState is CloudProjectsUiState.Success && cloudProjectsState.projects.isNotEmpty()) {
                items(cloudProjectsState.projects, key = { it.id }) { proj ->
                    CloudProjectItemCard(project = proj, onDelete = { onDeleteCloudProject(proj.id) })
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ToolkitDashboardGridCard(
    onModuleClick: (ToolkitModule) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("toolkit_20_module_grid")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("APK Araç Kutusu — ${ToolkitModule.entries.size} Modül", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Decompile, Yan Yana XML Diff, Git & Git Clone, Smali renklendirici, Proje/Sözlük, APK Karşılaştırma ve WorkManager araçları:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = when {
                    maxWidth >= 660.dp -> 4
                    maxWidth >= 460.dp -> 3
                    else -> 2
                }
                val rows = ToolkitModule.entries.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowModules ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowModules.forEach { mod ->
                                ElevatedCard(
                                    onClick = { onModuleClick(mod) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("toolkit_tile_${mod.id}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = mod.icon,
                                            contentDescription = mod.titleTr,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = mod.titleTr,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = mod.subtitleTr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            repeat(columns - rowModules.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudProjectItemCard(
    project: ApkProjectRemote,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "${project.appLabel} (${project.versionName})", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${project.packageName} • ${project.sourceLang.uppercase()} → ${project.targetLang.uppercase()} • ${project.translationEngine}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (project.status == "signed") {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = "İmzalandı", tint = StatusSuccess, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Bulut projesini sil", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                text = "Durum: ${project.status} • Çevrilen: ${project.translatedCount} • Girdi SHA-256: ${project.inputSha256.take(16)}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!project.aiSecuritySummary.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = project.aiSecuritySummary.take(260) + if (project.aiSecuritySummary.length > 260) "..." else "",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

private fun yn(v: Boolean): String = if (v) "✓" else "✗"
