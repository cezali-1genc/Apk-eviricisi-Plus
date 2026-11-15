package com.example.apktranslatorpro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apktranslatorpro.ui.viewmodel.BuildProcessUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

/**
 * Interactive XML Resource Editor for decompiled `res/values/strings.xml`, arrays, plurals,
 * layout XMLs, and AndroidManifest.xml inside the temporary Apktool cache directory.
 * Also supports bulk string key renaming with automatic `@string/` layout reference updates.
 */
@Composable
fun XmlEditorScreen(
    buildState: BuildProcessUiState,
    hasApkSelected: Boolean,
    onDecompileApkClick: () -> Unit,
    onSaveXmlFile: (File, String) -> Unit,
    onBulkRenameKeys: (oldKeyOrPrefix: String, newKeyOrPrefix: String, prefixMode: Boolean) -> Unit,
    onOpenXmlDiffComparison: () -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val workspace = buildState.workspace
    val availableFiles = remember(workspace) {
        if (workspace == null) {
            emptyList()
        } else {
            (listOf(workspace.manifestFile) + workspace.resXmlFiles).distinctBy { it.absolutePath }
        }
    }

    var selectedFile by remember(availableFiles) {
        mutableStateOf(availableFiles.firstOrNull { it.name == "strings.xml" } ?: availableFiles.firstOrNull())
    }
    var xmlText by remember(selectedFile) {
        mutableStateOf(selectedFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: "")
    }
    var oldPrefixOrKey by remember { mutableStateOf("") }
    var newPrefixOrKey by remember { mutableStateOf("") }
    var prefixMode by remember { mutableStateOf(true) }

    LaunchedEffect(buildState.lastBulkRenameResult, selectedFile) {
        val f = selectedFile
        if (f != null && f.exists()) {
            xmlText = f.readText(Charsets.UTF_8)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .testTag("xml_editor_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("xml_editor_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                    Column {
                        Text("XML Kaynak Düzenleyici", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "strings, string-array, plurals ve layout XML dosyalarını önbellek dizinine kaydedin.",
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
                                text = "XML dosyalarını doğrudan önbellekte düzenlemek için seçili APK'yı Apktool çalışma dizinine çıkarın.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onDecompileApkClick,
                                enabled = hasApkSelected && !buildState.isBusy,
                                modifier = Modifier.testTag("xml_editor_decompile_button")
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
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Önbellekteki XML Dosyaları (${availableFiles.size})", style = MaterialTheme.typography.titleMedium)
                            }
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
                                        onClick = {
                                            selectedFile = file
                                            xmlText = if (file.exists()) file.readText(Charsets.UTF_8) else ""
                                        },
                                        label = { Text(rel) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = xmlText,
                                onValueChange = { xmlText = it },
                                label = {
                                    Text(
                                        selectedFile?.let { f ->
                                            runCatching { f.relativeTo(workspace.workDir).path }.getOrDefault(f.name)
                                        } ?: "XML İçeriği"
                                    )
                                },
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                ),
                                minLines = 12,
                                maxLines = 22,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("xml_editor_textfield")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        selectedFile?.let { onSaveXmlFile(it, xmlText) }
                                    },
                                    enabled = selectedFile != null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_xml_file_button")
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Önbelleğe Kaydet")
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedFile?.let { f ->
                                            xmlText = if (f.exists()) f.readText(Charsets.UTF_8) else ""
                                        }
                                    },
                                    enabled = selectedFile != null
                                ) {
                                    Text("Geri Al")
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenXmlDiffComparison,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("xml_editor_open_diff_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Orijinal ile Yan Yana XML Karşılaştır (Diff)")
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_rename_section")
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
                                    Icons.Filled.DriveFileRenameOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("Toplu String Anahtarı Yeniden Adlandırma", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "strings.xml içindeki anahtarları veya ön ekleri toplu olarak yeniden adlandırır ve layout XML'lerdeki @string/ referanslarını otomatik günceller.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ön ek (Prefix) modu", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = prefixMode, onCheckedChange = { prefixMode = it })
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = oldPrefixOrKey,
                                    onValueChange = { oldPrefixOrKey = it },
                                    label = { Text(if (prefixMode) "Eski Ön Ek (örn: btn_)" else "Eski Anahtar") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("bulk_rename_old_input")
                                )
                                OutlinedTextField(
                                    value = newPrefixOrKey,
                                    onValueChange = { newPrefixOrKey = it },
                                    label = { Text(if (prefixMode) "Yeni Ön Ek (örn: action_)" else "Yeni Anahtar") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("bulk_rename_new_input")
                                )
                            }
                            Button(
                                onClick = { onBulkRenameKeys(oldPrefixOrKey, newPrefixOrKey, prefixMode) },
                                enabled = oldPrefixOrKey.isNotBlank() && newPrefixOrKey.isNotBlank() && !buildState.isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bulk_rename_execute_button")
                            ) {
                                Text("Anahtarları ve @string/ Referanslarını Güncelle")
                            }
                            buildState.lastBulkRenameResult?.let { res ->
                                HorizontalDivider()
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Güncellenen anahtar: ${res.renamedResourceCount} • Güncellenen @string/ layout referansı: ${res.updatedLayoutReferences} • Dosyalar: ${res.modifiedFiles.joinToString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
