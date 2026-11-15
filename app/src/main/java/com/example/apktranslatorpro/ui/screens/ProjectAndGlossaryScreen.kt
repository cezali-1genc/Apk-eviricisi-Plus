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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.apktranslatorpro.data.local.TranslationProjectEntity
import com.example.apktranslatorpro.data.local.UserGlossaryEntity

/**
 * Manages saved Room translation projects (save/load/duplicate/delete/profiles),
 * user custom dictionary/glossary terms, Jetpack WorkManager background automation,
 * and built-in installation & usage guidance.
 */
@Composable
fun ProjectAndGlossaryScreen(
    hasApkLoaded: Boolean,
    defaultProjectName: String,
    projects: List<TranslationProjectEntity>,
    glossary: List<UserGlossaryEntity>,
    backgroundWorkStatus: String,
    isBackgroundWorkRunning: Boolean,
    onSaveProjectClick: (name: String, profile: String) -> Unit,
    onLoadProjectClick: (String) -> Unit,
    onDuplicateProjectClick: (String) -> Unit,
    onDeleteProjectClick: (String) -> Unit,
    onAddGlossaryTerm: (source: String, target: String) -> Unit,
    onDeleteGlossaryTerm: (source: String) -> Unit,
    onStartWorkManagerClick: () -> Unit,
    onCancelWorkManagerClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    var projectName by remember(defaultProjectName) { mutableStateOf(defaultProjectName) }
    var selectedProfile by remember { mutableStateOf("Standart Uygulama") }
    var glossarySource by remember { mutableStateOf("") }
    var glossaryTarget by remember { mutableStateOf("") }

    val profiles = listOf("Standart Uygulama", "Oyun & RPG", "E-Ticaret", "Geliştirici Aracı")

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("project_glossary_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("project_glossary_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                    Column {
                        Text("Proje, Özel Sözlük & WorkManager", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Çeviri projelerini kaydedin/çoğaltın, özel sözlük terimlerini kilitleyin ve arka plan derlemesi çalıştırın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // WorkManager Card
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
                            Icon(Icons.Filled.WorkHistory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("WorkManager Arka Plan Otomasyonu", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "Durum: $backgroundWorkStatus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onStartWorkManagerClick,
                                enabled = hasApkLoaded,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("workmanager_start_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Tek Tıkla Arka Plan")
                            }
                            OutlinedButton(
                                onClick = onCancelWorkManagerClick,
                                enabled = isBackgroundWorkRunning,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("workmanager_cancel_button")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Durdur")
                            }
                        }
                    }
                }
            }

            // Save Project Card
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
                            Icon(Icons.Filled.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Çeviri Projesi & Profiller", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedTextField(
                            value = projectName,
                            onValueChange = { projectName = it },
                            label = { Text("Proje Adı") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("project_name_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            profiles.forEach { prof ->
                                FilterChip(
                                    selected = selectedProfile == prof,
                                    onClick = { selectedProfile = prof },
                                    label = { Text(prof) }
                                )
                            }
                        }
                        Button(
                            onClick = { onSaveProjectClick(projectName, selectedProfile) },
                            enabled = hasApkLoaded,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_project_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Aktif APK Projesini Kaydet (Room)")
                        }
                    }
                }
            }

            items(projects, key = { it.projectId }) { proj ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(proj.projectName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${proj.profileName} • ${proj.sourceLangCode.uppercase()}→${proj.targetLangCode.uppercase()} • ${proj.translatedCount}/${proj.entryCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                TextButton(onClick = { onLoadProjectClick(proj.projectId) }) { Text("Yükle") }
                                IconButton(onClick = { onDuplicateProjectClick(proj.projectId) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Çoğalt")
                                }
                                IconButton(onClick = { onDeleteProjectClick(proj.projectId) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Custom User Glossary Card
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
                        Text("Özel Kullanıcı Sözlüğü (${glossary.size})", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = glossarySource,
                                onValueChange = { glossarySource = it },
                                label = { Text("Kaynak Terim") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("glossary_source_input")
                            )
                            OutlinedTextField(
                                value = glossaryTarget,
                                onValueChange = { glossaryTarget = it },
                                label = { Text("Sabit Çeviri") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("glossary_target_input")
                            )
                        }
                        Button(
                            onClick = {
                                onAddGlossaryTerm(glossarySource, glossaryTarget)
                                glossarySource = ""
                                glossaryTarget = ""
                            },
                            enabled = glossarySource.isNotBlank() && glossaryTarget.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_glossary_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Sözlük Terimini Sabitle")
                        }
                        glossary.forEach { term ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${term.sourceTerm} → ${term.preferredTranslation}", style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = { onDeleteGlossaryTerm(term.sourceTerm) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Terimi sil", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Built-in Kurulum & Kullanım Rehberi
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Kısa Kullanım Rehberi", style = MaterialTheme.typography.titleSmall)
                        Text("1. Çalışma Alanı'ndan tek veya toplu APK seçin (orijinal dosya SAF üzerinde salt okunur korunur).", style = MaterialTheme.typography.bodySmall)
                        Text("2. 105+ dilden hedef dilinizi ve çeviri motorunu (Otomatik / ML Kit / Gemini AI / REST API) seçin.", style = MaterialTheme.typography.bodySmall)
                        Text("3. Önizleme sekmesinde çevirileri düzenleyin, kilitleyin, kalite raporunu görün veya JSON/CSV/XML dışa aktarın.", style = MaterialTheme.typography.bodySmall)
                        Text("4. Araçlar > Yan Yana XML Karşılaştırma ve Git Clone ile değişiklikleri inceleyin, tek dokunuşla v1/v2/v3 imzalı APK alın.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
