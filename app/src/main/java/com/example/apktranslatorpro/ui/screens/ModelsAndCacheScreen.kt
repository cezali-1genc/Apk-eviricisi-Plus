package com.example.apktranslatorpro.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.apktranslatorpro.data.local.JobHistoryEntity
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.OfflineModelState
import com.example.apktranslatorpro.ui.viewmodel.LanguageManagerUiState
import com.example.apktranslatorpro.ui.viewmodel.MainUiState
import com.example.ui.theme.StatusSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelsAndCacheScreen(
    uiState: MainUiState,
    langManagerState: LanguageManagerUiState,
    availableXmlPaths: List<String>,
    cacheCount: Int,
    recentJobs: List<JobHistoryEntity>,
    onRefreshModels: () -> Unit,
    onDownloadModel: (LanguageOption) -> Unit,
    onDeleteModel: (LanguageOption) -> Unit,
    onDownloadActivePair: () -> Unit,
    onToggleQueueLocale: (LanguageOption) -> Unit,
    onToggleQueueFile: (String) -> Unit,
    onRunBulkQueue: () -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val modelsToShow = if (langManagerState.modelStates.isNotEmpty()) {
        langManagerState.modelStates
    } else {
        uiState.offlineModels
    }
    val installedCount = remember(modelsToShow) { modelsToShow.count { it.isDownloaded } }
    val missingCount = remember(modelsToShow) { modelsToShow.count { !it.isDownloaded } }
    val effectiveSource = remember(uiState.selectedSourceLanguage, uiState.apkMetadata) {
        if (uiState.selectedSourceLanguage == LanguageOption.AUTO) {
            uiState.apkMetadata?.detectedSourceLanguage ?: LanguageOption.ENGLISH
        } else {
            uiState.selectedSourceLanguage
        }
    }
    val sourceModelReady = remember(modelsToShow, effectiveSource) {
        modelsToShow.firstOrNull { it.language == effectiveSource }?.isDownloaded == true
    }
    val targetModelReady = remember(modelsToShow, uiState.targetLanguage) {
        modelsToShow.firstOrNull { it.language == uiState.targetLanguage }?.isDownloaded == true
    }
    val fileOptions = remember(availableXmlPaths) {
        (listOf("res/values/strings.xml", "res/values-en/strings.xml", "res/values-de/strings.xml") + availableXmlPaths)
            .distinct()
            .take(10)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("models_lazy_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bulk_queue_section")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Queue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.bulk_queue_title), style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "Çoklu kaynak XML dosyalarını seçili hedef diller için toplu kuyruğa alın (${langManagerState.availableMlKitCodes.size} ML Kit kodu desteklenir).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("1. Kuyruğa Alınacak XML Dosyaları:", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            fileOptions.forEach { filePath ->
                                val selected = filePath in langManagerState.queuedResourceFiles
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleQueueFile(filePath) },
                                    label = { Text(filePath.substringAfter("res/"), style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Text("2. Hedef Diller (Çoklu Seçim):", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LanguageOption.targetLanguages.take(10).forEach { lang ->
                                val selected = lang in langManagerState.selectedQueueLocales
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleQueueLocale(lang) },
                                    label = { Text("${lang.displayNameTr} (${lang.code.uppercase()})") },
                                    modifier = Modifier.testTag("queue_locale_${lang.code}")
                                )
                            }
                        }

                        if (langManagerState.isProcessingQueue || langManagerState.queueProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { langManagerState.queueProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = onRunBulkQueue,
                            enabled = !langManagerState.isProcessingQueue && uiState.resources.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_bulk_queue_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (langManagerState.isProcessingQueue) {
                                    "Kuyruk İşleniyor..."
                                } else {
                                    "Toplu Kuyruğu Başlat (${langManagerState.queuedResourceFiles.size} dosya × ${langManagerState.selectedQueueLocales.size} dil)"
                                }
                            )
                        }

                        if (langManagerState.bulkQueueJobs.isNotEmpty()) {
                            HorizontalDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                langManagerState.bulkQueueJobs.forEach { job ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${job.fileLabel} → ${job.targetLocale.code.uppercase()}",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Text(
                                                text = job.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (job.status.startsWith("Tamamlandı")) StatusSuccess else MaterialTheme.colorScheme.primary
                                            )
                                        }
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Cached, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Çeviri Önbelleği (Room)", style = MaterialTheme.typography.titleLarge)
                            }
                            TextButton(
                                onClick = onClearCache,
                                enabled = cacheCount > 0,
                                modifier = Modifier.testTag("clear_cache_button")
                            ) {
                                Text("Temizle")
                            }
                        }
                        Text(
                            text = "Kayıtlı önbellek öğesi: $cacheCount metin (tekrar çevirilerde internet/model çağrısını sıfırlar).",
                            style = MaterialTheme.typography.bodyMedium,
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("ML Kit Çevrimdışı Dil Paketleri", style = MaterialTheme.typography.titleLarge)
                            }
                            IconButton(
                                onClick = onRefreshModels,
                                modifier = Modifier.testTag("refresh_models_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Modelleri yenile")
                            }
                        }
                        Text(
                            text = "Çevrimdışı modda cihaz üzerinde çalışan ML Kit dil modellerini yönetin. Yüklü: $installedCount • Yüklü Değil: $missingCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Aktif Dil Çifti: ${effectiveSource.displayNameTr} (${if (sourceModelReady) "Yüklü" else "Yüklü Değil"}) → ${uiState.targetLanguage.displayNameTr} (${if (targetModelReady) "Yüklü" else "Yüklü Değil"})",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Button(
                                    onClick = onDownloadActivePair,
                                    enabled = !(sourceModelReady && targetModelReady),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("download_active_pair_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (sourceModelReady && targetModelReady) "Aktif Çift Hazır (Yüklü)" else "Aktif Dil Çiftini İndir")
                                }
                            }
                        }

                        HorizontalDivider()

                        if (uiState.isLoadingModels || langManagerState.isRefreshing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Yüklü dil paketleri kontrol ediliyor...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                modelsToShow.forEach { model ->
                                    OfflineModelRow(
                                        model = model,
                                        onDownload = { onDownloadModel(model.language) },
                                        onDelete = { onDeleteModel(model.language) }
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Son İşlem Geçmişi", style = MaterialTheme.typography.titleLarge)
                            }
                            if (recentJobs.isNotEmpty()) {
                                TextButton(onClick = onClearHistory) {
                                    Text("Temizle")
                                }
                            }
                        }

                        if (recentJobs.isEmpty()) {
                            Text(
                                text = "Henüz tamamlanmış APK çeviri kaydı bulunmuyor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(recentJobs, key = { it.id }) { job ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = job.apkFileName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${job.packageName} • ${job.sourceLang.uppercase()} → ${job.targetLang.uppercase()} (${job.translatedCount} metin)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Çıktı SHA-256: ${job.outputSha256.take(20)}... • ${dateFmt.format(Date(job.timestamp))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun OfflineModelRow(
    model: OfflineModelState,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${model.language.displayNameTr} (${model.language.nativeName})",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when {
                        model.isDownloading -> "İndiriliyor..."
                        model.statusMessage.isNotBlank() -> model.statusMessage
                        model.isDownloaded -> "Yüklü — Cihazda hazır (Çevrimdışı)"
                        else -> "Yüklü Değil (~30 MB)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (model.isDownloaded) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (model.isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (model.isDownloaded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "İndirildi",
                        tint = StatusSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_model_${model.language.code}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "${model.language.displayNameTr} paketini sil",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.testTag("download_model_${model.language.code}")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("İndir")
                }
            }
        }
    }
}
