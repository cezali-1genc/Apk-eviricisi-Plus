package com.example.apktranslatorpro.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apktranslatorpro.ui.components.SmaliSyntaxHighlighter
import com.example.apktranslatorpro.ui.viewmodel.BuildProcessUiState
import com.example.apktranslatorpro.ui.viewmodel.MainUiState
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

@Composable
fun ToolkitCodeEditorModuleScreen(
    module: ToolkitModule,
    uiState: MainUiState,
    buildState: BuildProcessUiState,
    themeMode: AppThemeMode,
    onDecompileApk: () -> Unit,
    onRebuildApk: () -> Unit,
    onSaveWorkingFile: (File, String) -> Unit,
    onUpdateResourceTranslation: (String, String) -> Unit,
    onOpenXmlEditor: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val workspace = buildState.workspace
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    var statusNote by remember { mutableStateOf<String?>(null) }

    val exportPatchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { targetUri: Uri? ->
        if (targetUri != null) {
            val changed = uiState.resources.filter { it.translatedText.isNotBlank() && it.translatedText != it.originalText }
            val json = buildString {
                appendLine("{")
                appendLine("  \"packageName\": \"${uiState.apkMetadata?.packageName ?: "unknown"}\",")
                appendLine("  \"patchEntries\": [")
                changed.forEachIndexed { idx, item ->
                    val comma = if (idx < changed.lastIndex) "," else ""
                    val orig = item.originalText.replace("\"", "\\\"").replace("\n", "\\n")
                    val tr = item.translatedText.replace("\"", "\\\"").replace("\n", "\\n")
                    appendLine("    {\"key\": \"${item.key}\", \"from\": \"$orig\", \"to\": \"$tr\"}$comma")
                }
                appendLine("  ]")
                appendLine("}")
            }
            runCatching {
                context.contentResolver.openOutputStream(targetUri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }.onSuccess {
                statusNote = "Yama JSON dosyası kaydedildi (${changed.size} kayıt)."
            }.onFailure {
                statusNote = "Yama kaydedilemedi: ${it.message}"
            }
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
                .testTag("toolkit_code_module_${module.id}"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("module_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                    Column {
                        Text(module.titleTr, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = module.subtitleTr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            statusNote?.takeIf { it.isNotBlank() }?.let { note ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            when (module) {
                ToolkitModule.SIMPLE_EDIT -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Hızlı APK Metin & Uygulama Adı Düzenleyici", style = MaterialTheme.typography.titleMedium)
                                if (uiState.resources.isEmpty()) {
                                    Text("Önce Çalışma sekmesinden bir APK seçin.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    uiState.resources.take(15).forEach { entry ->
                                        var draft by remember(entry.uniqueKey, entry.translatedText) {
                                            mutableStateOf(entry.translatedText.ifBlank { entry.originalText })
                                        }
                                        OutlinedTextField(
                                            value = draft,
                                            onValueChange = {
                                                draft = it
                                                onUpdateResourceTranslation(entry.uniqueKey, it)
                                            },
                                            label = { Text("${entry.key} (${entry.localeQualifier})") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Button(onClick = onRebuildApk, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Filled.Build, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Değişikliklerle APK Derle ve İmzala")
                                    }
                                }
                            }
                        }
                    }
                }

                ToolkitModule.APK_DECOMPILER -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Apktool Önbellek Çözümleyici", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Seçili APK'yı geçici önbelleğe çıkarır: resources.arsc, AndroidManifest.xml, res/*.xml ve classes*.dex -> Smali.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onDecompileApk,
                                    enabled = uiState.selectedApkUri != null && !buildState.isBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("run_decompile_button")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("APK'yı Decompile Et")
                                }
                                if (workspace != null) {
                                    HorizontalDivider()
                                    Text("Çıkarılan Dizin: ${workspace.workDir.absolutePath}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                    Text("• ${workspace.resXmlFiles.size} XML kaynağı", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${workspace.smaliFiles.size} Smali sınıfı", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${workspace.dexSummaries.size} DEX arşivi", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = onOpenXmlEditor, modifier = Modifier.fillMaxWidth()) {
                                        Text("XML Editörü Aç")
                                    }
                                }
                            }
                        }
                    }
                }

                ToolkitModule.APK_REBUILDER -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("APK Yeniden Derleme & İmzalama", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Güncel çeviri ve XML değişiklikleriyle resources.arsc yeniden oluşturulur, zipalign uygulanır ve RSA-2048 v1/v2/v3 ile imzalanır.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = onRebuildApk,
                                    enabled = uiState.apkMetadata != null && !uiState.isRunning,
                                    modifier = Modifier.fillMaxWidth().testTag("run_rebuild_button")
                                ) {
                                    Icon(Icons.Filled.Build, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("APK'yı Yeniden Derle & İmzala")
                                }
                            }
                        }
                    }
                }

                ToolkitModule.SMALI_EDITOR -> {
                    item {
                        SmaliEditorCard(
                            workspace = workspace,
                            isDark = isDark,
                            onDecompileApk = onDecompileApk,
                            hasApk = uiState.selectedApkUri != null,
                            onSaveSmali = onSaveWorkingFile
                        )
                    }
                }

                ToolkitModule.DEX_EDITOR, ToolkitModule.JAVA_VIEWER -> {
                    if (workspace == null || workspace.dexSummaries.isEmpty()) {
                        item {
                            RequireDecompileCard(
                                hasApk = uiState.selectedApkUri != null,
                                isBusy = buildState.isBusy,
                                onDecompileApk = onDecompileApk
                            )
                        }
                    } else {
                        workspace.dexSummaries.forEach { summary ->
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "${summary.dexName} • DEX ${summary.version} • ${summary.classCount} Sınıf • ${summary.methodCount} Metot",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${summary.stringCount} dize • ${summary.typeCount} tip",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            items(summary.classes.take(20)) { cls ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(cls.descriptor, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = if (module == ToolkitModule.JAVA_VIEWER) cls.javaSource else cls.smaliSource,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ToolkitModule.STRING_CREATOR -> {
                    item {
                        StringCreatorCard(
                            workspace = workspace,
                            hasApk = uiState.selectedApkUri != null,
                            isBusy = buildState.isBusy,
                            onDecompileApk = onDecompileApk,
                            onSaveFile = { file, content ->
                                onSaveWorkingFile(file, content)
                                statusNote = "Yeni string kaynağı strings.xml içine eklendi."
                            }
                        )
                    }
                }

                ToolkitModule.PATCH_CREATOR -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val changed = uiState.resources.filter { it.translatedText.isNotBlank() && it.translatedText != it.originalText }
                                Text("Yama (Patch) Oluşturucu", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Mevcut oturumda değişiklik yapılmış metin sayısı: ${changed.size}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = {
                                        val pkg = uiState.apkMetadata?.packageName ?: "app"
                                        exportPatchLauncher.launch("${pkg}_translation_patch.json")
                                    },
                                    enabled = changed.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth().testTag("export_patch_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("JSON Yama Dosyası Oluştur & Kaydet")
                                }
                            }
                        }
                    }
                }

                else -> Unit
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun RequireDecompileCard(
    hasApk: Boolean,
    isBusy: Boolean,
    onDecompileApk: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Önce APK'yı Decompile Edin", style = MaterialTheme.typography.titleMedium)
            Text("DEX, Smali, Java ve Manifest önbellek dosyalarını incelemek için decompile işlemini başlatın.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onDecompileApk, enabled = hasApk && !isBusy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (hasApk) "APK'yı Decompile Et" else "Önce APK Seçin")
            }
        }
    }
}

@Composable
private fun SmaliEditorCard(
    workspace: com.example.apktranslatorpro.core.apk.DecompiledWorkspace?,
    isDark: Boolean,
    onDecompileApk: () -> Unit,
    hasApk: Boolean,
    onSaveSmali: (File, String) -> Unit
) {
    val sampleSmali = """
        .class public Lcom/example/app/MainScreen;
        .super Ljava/lang/Object;

        .method public static greet(Ljava/lang/String;)V
            .locals 2
            :cond_0
            const-string v0, "Apk Çeviricisi Plus"
            invoke-static {v0, p0}, Lcom/example/Logger;->log(Ljava/lang/String;Ljava/lang/String;)V
            goto :goto_exit
            :goto_exit
            return-void
        .end method
    """.trimIndent()

    val files = workspace?.smaliFiles.orEmpty()
    var selectedFile by remember(files) { mutableStateOf(files.firstOrNull()) }
    var codeText by remember(selectedFile) {
        mutableStateOf(selectedFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: sampleSmali)
    }
    var editMode by remember { mutableStateOf(false) }

    val scheme = if (isDark) SmaliSyntaxHighlighter.DarkSmaliScheme else SmaliSyntaxHighlighter.LightSmaliScheme
    val highlighted = remember(codeText, scheme) {
        SmaliSyntaxHighlighter.highlight(codeText, scheme)
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (files.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    files.take(25).forEach { file ->
                        FilterChip(
                            selected = selectedFile?.absolutePath == file.absolutePath,
                            onClick = {
                                selectedFile = file
                                codeText = file.readText(Charsets.UTF_8)
                            },
                            label = { Text(file.name) }
                        )
                    }
                }
            } else {
                OutlinedButton(onClick = onDecompileApk, enabled = hasApk, modifier = Modifier.fillMaxWidth()) {
                    Text("Seçili APK'dan Gerçek Smali Sınıflarını Çıkar (Aşağıdaki sözdizimi önizlemesi aktif)")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !editMode, onClick = { editMode = false }, label = { Text("Renkli Vurgulama") })
                FilterChip(selected = editMode, onClick = { editMode = true }, label = { Text("Düzenle") })
            }

            if (editMode) {
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                    minLines = 12,
                    maxLines = 22,
                    modifier = Modifier.fillMaxWidth().testTag("smali_editor_input")
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().testTag("smali_highlighted_code")
                ) {
                    Text(
                        text = highlighted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState())
                    )
                }
            }

            selectedFile?.let { fileToSave ->
                Button(
                    onClick = { onSaveSmali(fileToSave, codeText) },
                    modifier = Modifier.fillMaxWidth().testTag("save_smali_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Smali Dosyasını Önbelleğe Kaydet")
                }
            }
        }
    }
}

@Composable
private fun StringCreatorCard(
    workspace: com.example.apktranslatorpro.core.apk.DecompiledWorkspace?,
    hasApk: Boolean,
    isBusy: Boolean,
    onDecompileApk: () -> Unit,
    onSaveFile: (File, String) -> Unit
) {
    var keyName by remember { mutableStateOf("") }
    var keyValue by remember { mutableStateOf("") }
    val stringsFile = workspace?.resXmlFiles?.firstOrNull { it.name == "strings.xml" }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Yeni <string> Kaynağı Oluştur", style = MaterialTheme.typography.titleMedium)
            if (stringsFile == null) {
                RequireDecompileCard(hasApk = hasApk, isBusy = isBusy, onDecompileApk = onDecompileApk)
            } else {
                OutlinedTextField(
                    value = keyName,
                    onValueChange = { keyName = it },
                    label = { Text("Anahtar Adı (örn: custom_welcome_msg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keyValue,
                    onValueChange = { keyValue = it },
                    label = { Text("Metin Değeri") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val existing = stringsFile.readText(Charsets.UTF_8)
                        val escaped = keyValue.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        val newEntry = "    <string name=\"${keyName.trim()}\">$escaped</string>\n</resources>"
                        val updated = if (existing.contains("</resources>")) {
                            existing.replace("</resources>", newEntry)
                        } else {
                            "$existing\n$newEntry"
                        }
                        onSaveFile(stringsFile, updated)
                        keyName = ""
                        keyValue = ""
                    },
                    enabled = Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(keyName.trim()) && keyValue.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("strings.xml Dosyasına Ekle")
                }
            }
        }
    }
}
