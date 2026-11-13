package com.example.apktranslatorpro.ui.screens

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.apktranslatorpro.data.remote.ApkProjectRemote
import com.example.apktranslatorpro.ui.viewmodel.CloudProjectsUiState
import com.example.apktranslatorpro.ui.viewmodel.GeminiAnalysisUiState
import com.example.apktranslatorpro.ui.viewmodel.MainUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkToolsScreen(
    uiState: MainUiState,
    cloudProjectsState: CloudProjectsUiState,
    onPickApkClick: () -> Unit,
    onVerifySafIntegrityClick: () -> Unit,
    onRunGeminiAnalysisClick: () -> Unit,
    onDeleteCloudProject: (String) -> Unit,
    onOpenBinaryXmlTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metadata = uiState.apkMetadata
    val inspection = metadata?.fullInspection

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
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
                                        Text(
                                            text = gState.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                is GeminiAnalysisUiState.Success -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = gState.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp)
                                        )
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

                            if (inspection.nativeAbis.isNotEmpty()) {
                                Text("Native Kütüphaneler (.so ABI):", style = MaterialTheme.typography.titleSmall)
                                inspection.nativeAbis.forEach { (abi, libs) ->
                                    Text(
                                        text = "• $abi (${libs.size} .so): ${libs.take(6).joinToString(", ")}${if (libs.size > 6) "..." else ""}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                Text("Saf Java/Kotlin DEX paketi (native .so kütüphanesi yok).", style = MaterialTheme.typography.bodySmall)
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
                            if (inspection.usesPermissions.isEmpty()) {
                                Text("Manifest içinde izin beyanı bulunamadı.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                inspection.usesPermissions.take(20).forEach { perm ->
                                    Text(
                                        text = "• $perm",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall
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
                                Text(
                                    text = cloudProjectsState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusError
                                )
                            }
                            is CloudProjectsUiState.Success -> {
                                if (cloudProjectsState.projects.isEmpty()) {
                                    Text(
                                        text = "Henüz buluta kaydedilmiş APK projesi yok. Bir APK seçtiğinizde otomatik senkronize edilir.",
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
                    CloudProjectItemCard(
                        project = proj,
                        onDelete = { onDeleteCloudProject(proj.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
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
                    Text(
                        text = "${project.appLabel} (${project.versionName})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${project.packageName} • ${project.sourceLang.uppercase()} → ${project.targetLang.uppercase()} • ${project.translationEngine}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (project.status == "signed") {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "İmzalandı",
                            tint = StatusSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Bulut projesini sil",
                            tint = MaterialTheme.colorScheme.error
                        )
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
