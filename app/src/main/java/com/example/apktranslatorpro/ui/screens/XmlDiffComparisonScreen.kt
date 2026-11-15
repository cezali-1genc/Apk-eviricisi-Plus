package com.example.apktranslatorpro.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apktranslatorpro.core.apk.DecompiledWorkspace
import com.example.apktranslatorpro.core.apk.DiffLineStatus
import com.example.apktranslatorpro.core.apk.XmlDiffEngine
import com.example.apktranslatorpro.core.apk.XmlDiffRow
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

/**
 * Side-by-side XML File Comparison Tool comparing pristine decompiled resource files
 * (`originals/`) against the user's modified working copies (`decompiled/`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun XmlDiffComparisonScreen(
    workspace: DecompiledWorkspace?,
    hasApkSelected: Boolean,
    isBusy: Boolean,
    onDecompileApkClick: () -> Unit,
    onExportUnifiedPatch: (Uri, String) -> Unit,
    onOpenXmlEditor: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val availableFiles = remember(workspace) {
        if (workspace == null) emptyList()
        else (listOf(workspace.manifestFile) + workspace.resXmlFiles).distinctBy { it.absolutePath }
    }

    var selectedFile by remember(availableFiles) {
        mutableStateOf(availableFiles.firstOrNull { it.name == "strings.xml" } ?: availableFiles.firstOrNull())
    }
    var showOnlyChanges by remember { mutableStateOf(false) }

    val diffPair = remember(workspace, selectedFile) {
        val file = selectedFile
        if (workspace == null || file == null) {
            null
        } else {
            val rel = runCatching { file.relativeTo(workspace.workDir).path.replace('\\', '/') }.getOrDefault(file.name)
            val origFile = File(workspace.originalsDir, rel)
            val origText = if (origFile.exists()) origFile.readText(Charsets.UTF_8) else ""
            val modText = if (file.exists()) file.readText(Charsets.UTF_8) else ""
            rel to XmlDiffEngine.computeSideBySideDiff(origText, modText)
        }
    }

    val exportPatchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val pair = diffPair ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val patch = XmlDiffEngine.generateUnifiedPatch(pair.first, pair.second)
            onExportUnifiedPatch(uri, patch)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
                .testTag("xml_diff_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("xml_diff_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Yan Yana XML Karşılaştırma", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Orijinal decompile edilmiş kaynaklar ile düzenlenen kopyalar arasındaki farkları satır satır inceleyin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (workspace == null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Decompile Çalışma Alanı Hazır Değil", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Orijinal ve düzenlenmiş XML dosyalarını yan yana karşılaştırmak için önce seçili APK'yı decompile edin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onDecompileApkClick,
                                enabled = hasApkSelected && !isBusy,
                                modifier = Modifier.testTag("xml_diff_decompile_button")
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (hasApkSelected) "APK'yı Decompile Et" else "Önce APK Seçin")
                            }
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableFiles.forEach { file ->
                                    val rel = runCatching { file.relativeTo(workspace.workDir).path }.getOrDefault(file.name)
                                    FilterChip(
                                        selected = selectedFile?.absolutePath == file.absolutePath,
                                        onClick = { selectedFile = file },
                                        label = { Text(rel) }
                                    )
                                }
                            }

                            val summary = diffPair?.second
                            if (summary != null) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AssistChip(onClick = {}, label = { Text("+${summary.addedCount} Eklendi") })
                                    AssistChip(onClick = {}, label = { Text("-${summary.removedCount} Silindi") })
                                    AssistChip(onClick = {}, label = { Text("~${summary.modifiedCount} Değişti") })
                                    AssistChip(onClick = {}, label = { Text("=${summary.unchangedCount} Aynı") })
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Yalnızca Değişen Satırlar", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = showOnlyChanges,
                                            onCheckedChange = { showOnlyChanges = it },
                                            modifier = Modifier.testTag("xml_diff_only_changes_switch")
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = onOpenXmlEditor) {
                                            Text("XML Düzenle")
                                        }
                                        Button(
                                            onClick = { exportPatchLauncher.launch("changes.diff") },
                                            enabled = summary.hasChanges,
                                            modifier = Modifier.testTag("xml_diff_export_patch_button")
                                        ) {
                                            Icon(Icons.Filled.Download, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text(".diff")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Side-by-Side Header
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Orijinal (Decompile)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Düzenlenen (Önbellek)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                val rowsToShow = diffPair?.second?.rows.orEmpty().let { list ->
                    if (showOnlyChanges) list.filter { it.status != DiffLineStatus.UNCHANGED } else list
                }

                if (rowsToShow.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (showOnlyChanges) {
                                    "Bu dosyada orijinal sürümle fark bulunmuyor (tüm satırlar aynı)."
                                } else {
                                    "XML içeriği boş."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(rowsToShow.take(500)) { row ->
                        SideBySideDiffRowCard(row = row)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SideBySideDiffRowCard(row: XmlDiffRow) {
    val leftBg = when (row.status) {
        DiffLineStatus.REMOVED -> Color(0xFFEF4444).copy(alpha = 0.18f)
        DiffLineStatus.MODIFIED -> Color(0xFFF59E0B).copy(alpha = 0.16f)
        DiffLineStatus.ADDED -> Color.Transparent
        DiffLineStatus.UNCHANGED -> MaterialTheme.colorScheme.surface
    }
    val rightBg = when (row.status) {
        DiffLineStatus.ADDED -> Color(0xFF10B981).copy(alpha = 0.18f)
        DiffLineStatus.MODIFIED -> Color(0xFFF59E0B).copy(alpha = 0.16f)
        DiffLineStatus.REMOVED -> Color.Transparent
        DiffLineStatus.UNCHANGED -> MaterialTheme.colorScheme.surface
    }
    val marker = when (row.status) {
        DiffLineStatus.ADDED -> "+"
        DiffLineStatus.REMOVED -> "-"
        DiffLineStatus.MODIFIED -> "~"
        DiffLineStatus.UNCHANGED -> " "
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(leftBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${row.originalLineNumber?.toString()?.padStart(3) ?: "   "} $marker ${row.originalText}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(rightBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${row.modifiedLineNumber?.toString()?.padStart(3) ?: "   "} $marker ${row.modifiedText}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}
