package com.example.apktranslatorpro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.apktranslatorpro.data.local.AppUserSettings
import com.example.apktranslatorpro.domain.model.ApkMetadata
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.LanguageResourceScopeConfig
import com.example.apktranslatorpro.domain.model.LogEntry
import com.example.apktranslatorpro.domain.model.LogLevel
import com.example.apktranslatorpro.domain.model.OutputApkResult
import com.example.apktranslatorpro.domain.model.PipelineStage
import com.example.apktranslatorpro.domain.model.TranslationMode
import com.example.apktranslatorpro.ui.components.ApkSafFilePickerComponent
import com.example.apktranslatorpro.ui.components.UltraGlassCard
import com.example.apktranslatorpro.ui.viewmodel.MainUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceScreen(
    uiState: MainUiState,
    settings: AppUserSettings,
    onPickApkClick: () -> Unit,
    onPickBatchApksClick: () -> Unit = {},
    onRunBatchApksClick: () -> Unit = {},
    onVerifySafExistenceClick: () -> Unit = {},
    onPickOutputFolderClick: () -> Unit,
    onSelectSourceLanguage: (LanguageOption) -> Unit,
    onSelectTargetLanguage: (LanguageOption) -> Unit,
    onSelectTranslationMode: (TranslationMode) -> Unit,
    onUpdateResourceScopeConfig: (LanguageResourceScopeConfig) -> Unit = {},
    onStartPipelineClick: () -> Unit,
    onStartBackgroundWorkClick: () -> Unit = {},
    onCancelPipelineClick: () -> Unit,
    onConfirmOnlineConsent: () -> Unit,
    onDismissOnlineConsent: () -> Unit,
    onInstallOutputApk: (OutputApkResult) -> Unit,
    onShareOutputApk: (OutputApkResult) -> Unit,
    onSaveOutputAs: (OutputApkResult) -> Unit,
    onOpenPreviewTab: () -> Unit,
    onOpenRulesTab: () -> Unit,
    onOpenToolsTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (uiState.showOnlineConsentDialog) {
        AlertDialog(
            onDismissRequest = onDismissOnlineConsent,
            icon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
            title = { Text(stringResource(R.string.online_consent_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.online_consent_message))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Hedef API: ${settings.onlineApiConfig.endpointUrl.ifBlank { "Tanımsız" }}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmOnlineConsent,
                    modifier = Modifier.testTag("confirm_online_consent_button")
                ) {
                    Text("Onayla ve Çevir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissOnlineConsent,
                    modifier = Modifier.testTag("cancel_online_consent_button")
                ) {
                    Text("İptal")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("workspace_lazy_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StudioHeroBanner()
            }

            item {
                ApkSelectorCard(
                    uiState = uiState,
                    onPickApkClick = onPickApkClick,
                    onPickBatchApksClick = onPickBatchApksClick,
                    onRunBatchApksClick = onRunBatchApksClick,
                    onVerifySafExistenceClick = onVerifySafExistenceClick,
                    onOpenPreviewTab = onOpenPreviewTab,
                    onOpenToolsTab = onOpenToolsTab
                )
            }

            item {
                LanguageAndEngineCard(
                    uiState = uiState,
                    settings = settings,
                    onPickOutputFolderClick = onPickOutputFolderClick,
                    onSelectSourceLanguage = onSelectSourceLanguage,
                    onSelectTargetLanguage = onSelectTargetLanguage,
                    onSelectTranslationMode = onSelectTranslationMode,
                    onUpdateResourceScopeConfig = onUpdateResourceScopeConfig,
                    onOpenRulesTab = onOpenRulesTab
                )
            }

            item {
                PipelineControlCard(
                    uiState = uiState,
                    onStartClick = onStartPipelineClick,
                    onStartBackgroundWorkClick = onStartBackgroundWorkClick,
                    onCancelClick = onCancelPipelineClick
                )
            }

            if (uiState.outputResult != null) {
                item {
                    OutputVerificationCard(
                        result = uiState.outputResult,
                        onInstallClick = { onInstallOutputApk(uiState.outputResult) },
                        onShareClick = { onShareOutputApk(uiState.outputResult) },
                        onSaveAsClick = { onSaveOutputAs(uiState.outputResult) }
                    )
                }
            }

            item {
                LogConsoleCard(logs = uiState.logs)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StudioHeroBanner() {
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_studio_banner),
                contentDescription = "Apk Çeviricisi Plus stüdyo başlık görseli",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC090D16),
                                Color(0xE60B111E)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0x3322D3EE),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(
                            text = "CEZALI GENÇ (SG) • SERHAT GÜNER",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF67E8F9),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = Color(0x33A855F7),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(
                            text = "ULTRA • ARSC • GEMINI • v3",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE9D5FF),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "${stringResource(R.string.app_name)} Ultra",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "APK araçları, Gemini AI & ML Kit çeviri, binary resources.arsc derleme ve v1/v2/v3 imzalama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApkSelectorCard(
    uiState: MainUiState,
    onPickApkClick: () -> Unit,
    onPickBatchApksClick: () -> Unit,
    onRunBatchApksClick: () -> Unit,
    onVerifySafExistenceClick: () -> Unit,
    onOpenPreviewTab: () -> Unit,
    onOpenToolsTab: () -> Unit
) {
    val metadata = uiState.apkMetadata
    val isInspecting = uiState.isInspecting
    UltraGlassCard(
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. Kaynak APK Seçimi (SAF)",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (metadata != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onOpenToolsTab,
                            modifier = Modifier.testTag("jump_to_tools_button")
                        ) {
                            Text("Araçlar")
                        }
                        TextButton(
                            onClick = onOpenPreviewTab,
                            modifier = Modifier.testTag("jump_to_preview_button")
                        ) {
                            Text("Metinler")
                        }
                    }
                }
            }

            ApkSafFilePickerComponent(
                selectedApkUri = uiState.selectedApkUri,
                safFileVerified = uiState.safFileVerified,
                safVerificationSummary = uiState.safVerificationSummary,
                isInspecting = isInspecting,
                hasSelectedApk = metadata != null,
                primaryButtonLabel = if (metadata == null) stringResource(R.string.action_select_apk) else "Başka APK Seç (SAF)",
                onPickApkClick = onPickApkClick,
                onVerifyExistenceClick = onVerifySafExistenceClick
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPickBatchApksClick,
                    enabled = !uiState.isRunning && !isInspecting,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_batch_apks_button")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Çoklu APK Kuyruğu (${uiState.batchApkUris.size})")
                }
                if (uiState.batchApkUris.isNotEmpty()) {
                    Button(
                        onClick = onRunBatchApksClick,
                        enabled = !uiState.isRunning,
                        modifier = Modifier.testTag("run_batch_apks_button")
                    ) {
                        Text("Toplu Çevir")
                    }
                }
            }
            if (uiState.batchStatusSummary.isNotBlank()) {
                Text(
                    text = uiState.batchStatusSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isInspecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "APK bütünlüğü (SHA-256) ve binary resources.arsc çözümleniyor...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (metadata != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (uiState.appIconBitmap != null) {
                                Image(
                                    bitmap = uiState.appIconBitmap,
                                    contentDescription = "${metadata.appLabel} uygulama simgesi",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Android,
                                    contentDescription = "${metadata.appLabel} simgesi",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "${metadata.appLabel} (${metadata.versionName})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Paket: ${metadata.packageName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "Dosya: ${metadata.fileName} • ${formatBytes(metadata.fileSizeBytes)} • SDK ${metadata.minSdkVersion}–${metadata.targetSdkVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Girdi SHA-256: ${metadata.sha256.take(24)}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${metadata.totalStringCount} string") }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("${metadata.totalArrayItemCount} dizi öğesi") }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("${metadata.totalPluralItemCount} çoğul") }
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text("Dil: ${metadata.detectedSourceLanguage.displayNameTr} (%${(metadata.detectedConfidence * 100).toInt()})")
                                }
                            )
                        }
                        if (metadata.availableLocales.isNotEmpty()) {
                            Text(
                                text = "Mevcut Diller: ${metadata.availableLocales.take(10).joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                for (warning in metadata.securityWarnings) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LanguageAndEngineCard(
    uiState: MainUiState,
    settings: AppUserSettings,
    onPickOutputFolderClick: () -> Unit,
    onSelectSourceLanguage: (LanguageOption) -> Unit,
    onSelectTargetLanguage: (LanguageOption) -> Unit,
    onSelectTranslationMode: (TranslationMode) -> Unit,
    onUpdateResourceScopeConfig: (LanguageResourceScopeConfig) -> Unit,
    onOpenRulesTab: () -> Unit
) {
    var srcExpanded by remember { mutableStateOf(false) }
    var tgtExpanded by remember { mutableStateOf(false) }

    val quickTargets = remember {
        listOf(
            LanguageOption.TURKISH,
            LanguageOption.ENGLISH,
            LanguageOption.GERMAN,
            LanguageOption.FRENCH,
            LanguageOption.ARABIC,
            LanguageOption.RUSSIAN,
            LanguageOption.SPANISH,
            LanguageOption.ITALIAN,
            LanguageOption.JAPANESE,
            LanguageOption.KOREAN,
            LanguageOption.CHINESE_SIMPLIFIED
        )
    }

    UltraGlassCard(
        accentColor = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "2. Dil ve Çeviri Motoru",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = srcExpanded,
                    onExpandedChange = { srcExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    val srcLabel = if (uiState.selectedSourceLanguage == LanguageOption.AUTO && uiState.apkMetadata != null) {
                        "Oto (${uiState.apkMetadata.detectedSourceLanguage.displayNameTr})"
                    } else {
                        uiState.selectedSourceLanguage.displayNameTr
                    }
                    OutlinedTextField(
                        value = srcLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kaynak Dil") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = srcExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("source_language_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = srcExpanded,
                        onDismissRequest = { srcExpanded = false }
                    ) {
                        LanguageOption.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text("${lang.displayNameTr} (${lang.nativeName})") },
                                onClick = {
                                    onSelectSourceLanguage(lang)
                                    srcExpanded = false
                                }
                            )
                        }
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Hedef dile"
                )

                ExposedDropdownMenuBox(
                    expanded = tgtExpanded,
                    onExpandedChange = { tgtExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = uiState.targetLanguage.displayNameTr,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hedef Dil") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tgtExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("target_language_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = tgtExpanded,
                        onDismissRequest = { tgtExpanded = false }
                    ) {
                        LanguageOption.targetLanguages.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    val rtlMark = if (lang.isRtl) " [RTL]" else ""
                                    Text("${lang.displayNameTr}$rtlMark (${lang.nativeName})")
                                },
                                onClick = {
                                    onSelectTargetLanguage(lang)
                                    tgtExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickTargets.forEach { lang ->
                    FilterChip(
                        selected = uiState.targetLanguage == lang,
                        onClick = { onSelectTargetLanguage(lang) },
                        label = {
                            Text(if (lang.isRtl) "${lang.code.uppercase()} (RTL)" else lang.code.uppercase())
                        },
                        modifier = Modifier.testTag("quick_lang_${lang.code}")
                    )
                }
            }

            if (uiState.targetLanguage.isRtl) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "RTL (Sağdan Sola) dil seçildi: ${uiState.targetLanguage.displayNameTr}. Önizleme ve XML çıktısı RTL uyumlu korunacaktır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TranslationMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.translationMode == mode,
                        onClick = { onSelectTranslationMode(mode) },
                        label = { Text(mode.labelTr, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("mode_segment_${mode.name.lowercase()}")
                    )
                }
            }

            Text(
                text = settings.translationMode.descriptionTr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                val scopeCfg = settings.resourceScopeConfig
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sadece Uygulama Dili (strings.xml)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Diğer APK dosyalarına (DEX, Manifest, layout, assets, lib, array, plurals) dokunulmaz.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = scopeCfg.strictStringsOnly,
                            onCheckedChange = {
                                onUpdateResourceScopeConfig(scopeCfg.copy(strictStringsOnly = it))
                            },
                            modifier = Modifier.testTag("toggle_strict_strings_only")
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = scopeCfg.translateDefaultStrings,
                            onClick = {
                                onUpdateResourceScopeConfig(
                                    scopeCfg.copy(translateDefaultStrings = !scopeCfg.translateDefaultStrings)
                                )
                            },
                            label = { Text("res/values/strings.xml (Default)") },
                            modifier = Modifier.testTag("scope_chip_default")
                        )
                        FilterChip(
                            selected = scopeCfg.translateValuesTr,
                            onClick = {
                                onUpdateResourceScopeConfig(
                                    scopeCfg.copy(translateValuesTr = !scopeCfg.translateValuesTr)
                                )
                            },
                            label = { Text("res/values-tr/strings.xml") },
                            modifier = Modifier.testTag("scope_chip_tr")
                        )
                        FilterChip(
                            selected = scopeCfg.translateValuesEn,
                            onClick = {
                                onUpdateResourceScopeConfig(
                                    scopeCfg.copy(translateValuesEn = !scopeCfg.translateValuesEn)
                                )
                            },
                            label = { Text("res/values-en/strings.xml") },
                            modifier = Modifier.testTag("scope_chip_en")
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onPickOutputFolderClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("select_output_folder_button")
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Çıktı Klasörü: ${settings.outputTreeLabel}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(
                onClick = onOpenRulesTab,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("open_rules_shortcut_button")
            ) {
                Text(
                    text = "Koruma Kuralları: Yer tutucu=${yesNo(settings.protectionConfig.preservePlaceholders)} • Terim hariç=${if (settings.protectionConfig.excludeTechnicalTerms) settings.protectionConfig.excludedTerms.size else 0}"
                )
            }
        }
    }
}

@Composable
private fun PipelineControlCard(
    uiState: MainUiState,
    onStartClick: () -> Unit,
    onStartBackgroundWorkClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    UltraGlassCard(
        accentColor = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "3. Çeviri, Paketleme & İmzalama",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (uiState.isRunning) {
                    Text(
                        text = "%${(uiState.progress * 100).toInt()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = uiState.isRunning || uiState.stage != PipelineStage.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { uiState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("pipeline_progress_bar")
                    )
                    Text(
                        text = "${uiState.stage.labelTr}${if (uiState.progressDetail.isNotBlank()) " — ${uiState.progressDetail}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    enabled = uiState.apkMetadata != null && !uiState.isRunning && !uiState.isInspecting,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("start_translation_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_start_translation))
                }
                if (uiState.isRunning) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("cancel_pipeline_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "İşlemi durdur")
                    }
                }
            }
            OutlinedButton(
                onClick = onStartBackgroundWorkClick,
                enabled = uiState.apkMetadata != null && !uiState.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workspace_workmanager_button")
            ) {
                Text("Arka Planda Çalıştır (WorkManager: ${uiState.backgroundWorkStatus})")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Orijinal APK asla değiştirilmez; işlem öncesi ve sonrası SHA-256 doğrulanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OutputVerificationCard(
    result: OutputApkResult,
    onInstallClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveAsClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("output_result_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = StatusSuccess
                )
                Text(
                    text = "Çıktı APK Hazır ve Doğrulandı",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = "${result.fileName} (${formatBytes(result.fileSizeBytes)})",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = result.savedDisplayLocation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Çıktı SHA-256: ${result.outputSha256.take(28)}...",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "İmza Şemaları: v1=${yesNo(result.v1Verified)} • v2=${yesNo(result.v2Verified)} • v3=${yesNo(result.v3Verified)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusSuccess
                    )
                    Text(
                        text = "Sertifika SHA-256: ${result.signerCertSha256.take(24)}...",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "Orijinal APK Bütünlüğü: ${if (result.originalIntegrityOk) "KORUNDU (SHA-256 eşleşti)" else "UYARI: Değişti"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (result.originalIntegrityOk) StatusSuccess else StatusError
                    )
                    Text(
                        text = "Çevrilen: ${result.translatedCount} • Önbellek: ${result.cachedCount} • Korunan: ${result.skippedCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                text = stringResource(R.string.signing_warning_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onInstallClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("install_apk_button")
                ) {
                    Icon(Icons.Filled.InstallMobile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Yükle")
                }
                OutlinedButton(
                    onClick = onShareClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_apk_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Paylaş")
                }
                OutlinedButton(
                    onClick = onSaveAsClick,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("save_as_apk_button")
                ) {
                    Icon(Icons.Filled.SaveAs, contentDescription = stringResource(R.string.action_save_as))
                }
            }
        }
    }
}

@Composable
private fun LogConsoleCard(logs: List<LogEntry>) {
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
                Icon(
                    imageVector = Icons.Filled.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "İşlem Günlüğü (${logs.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            HorizontalDivider()

            Surface(
                color = Color(0xFF0A0F1A),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp, max = 240.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Henüz işlem kaydı yok. Başlamak için bir APK seçin.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        reverseLayout = true
                    ) {
                        items(logs.asReversed(), key = { it.id }) { entry ->
                            val color = when (entry.level) {
                                LogLevel.INFO -> StatusInfo
                                LogLevel.SUCCESS -> StatusSuccess
                                LogLevel.WARN -> StatusWarning
                                LogLevel.ERROR -> StatusError
                            }
                            Text(
                                text = "[${entry.timestamp}] [${entry.level.name}] ${entry.message}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun yesNo(v: Boolean): String = if (v) "✓" else "✗"

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.2f MB".format(mb)
}
