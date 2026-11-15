package com.example.apktranslatorpro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.apktranslatorpro.domain.model.ApkMetadata
import com.example.ui.theme.JetBrainsMonoFontFamily

/**
 * APK-to-APK Comparison Screen highlighting differences in metadata, SDK targets,
 * cryptographic SHA-256 digests, permissions, DEX/ZIP entries, and resource string counts.
 */
@Composable
fun ApkCompareScreen(
    primaryApk: ApkMetadata?,
    secondaryApk: ApkMetadata?,
    isInspectingSecondary: Boolean,
    onPickSecondaryApkClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .testTag("apk_compare_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("apk_compare_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                    Column {
                        Text("APK Dosya Karşılaştırma", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "İki APK arasındaki sürüm, SDK, SHA-256, izin ve kaynak farklarını yan yana inceleyin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        Button(
                            onClick = onPickSecondaryApkClick,
                            enabled = !isInspectingSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_comparison_apk_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (secondaryApk == null) "Karşılaştırılacak 2. APK Seç (SAF)" else "Farklı 2. APK Seç")
                        }
                        if (isInspectingSecondary) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (primaryApk == null || secondaryApk == null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Compare, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = when {
                                    primaryApk == null -> "Önce Çalışma Alanı'ndan 1. (ana) APK'yı seçin."
                                    else -> "Karşılaştırma tablosu için yukarıdan 2. APK dosyasını seçin."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompareRow("Özellik", "APK #1 (${primaryApk.fileName})", "APK #2 (${secondaryApk.fileName})", header = true)
                            HorizontalDivider()
                            CompareRow("Uygulama Adı", primaryApk.appLabel, secondaryApk.appLabel)
                            CompareRow("Paket Adı", primaryApk.packageName, secondaryApk.packageName)
                            CompareRow("Sürüm", "${primaryApk.versionName} (${primaryApk.versionCode})", "${secondaryApk.versionName} (${secondaryApk.versionCode})")
                            CompareRow("SDK (min–target)", "${primaryApk.minSdkVersion}–${primaryApk.targetSdkVersion}", "${secondaryApk.minSdkVersion}–${secondaryApk.targetSdkVersion}")
                            CompareRow("Boyut", "${primaryApk.fileSizeBytes / 1024} KB", "${secondaryApk.fileSizeBytes / 1024} KB")
                            CompareRow("Metinler", "${primaryApk.totalStringCount} str / ${primaryApk.totalArrayItemCount} arr", "${secondaryApk.totalStringCount} str / ${secondaryApk.totalArrayItemCount} arr")
                            CompareRow("Algılanan Dil", primaryApk.detectedSourceLanguage.displayNameTr, secondaryApk.detectedSourceLanguage.displayNameTr)
                            CompareRow("Mevcut Diller", "${primaryApk.availableLocales.size} dil", "${secondaryApk.availableLocales.size} dil")
                            CompareRow("SHA-256", primaryApk.sha256.take(14) + "...", secondaryApk.sha256.take(14) + "...")
                        }
                    }
                }

                val perms1 = primaryApk.fullInspection?.usesPermissions.orEmpty().toSet()
                val perms2 = secondaryApk.fullInspection?.usesPermissions.orEmpty().toSet()
                val addedPerms = perms2 - perms1
                val removedPerms = perms1 - perms2
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("İzin Farkları (Permissions Diff)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "+2. APK'da Eklenen İzinler (${addedPerms.size}): ${addedPerms.joinToString { p -> p.substringAfterLast('.') }.ifEmpty { "Yok" }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "-1. APK'da Olup 2.'de Kaldırılanlar (${removedPerms.size}): ${removedPerms.joinToString { p -> p.substringAfterLast('.') }.ifEmpty { "Yok" }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CompareRow(label: String, v1: String, v2: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    val differs = !header && v1 != v2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = style, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.8f))
        Text(v1, style = style, fontWeight = weight, fontFamily = if (!header) JetBrainsMonoFontFamily else null, modifier = Modifier.weight(1f))
        Text(
            text = v2,
            style = style,
            fontWeight = weight,
            color = if (differs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontFamily = if (!header) JetBrainsMonoFontFamily else null,
            modifier = Modifier.weight(1f)
        )
    }
}
