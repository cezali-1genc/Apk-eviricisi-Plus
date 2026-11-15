package com.example.apktranslatorpro.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apktranslatorpro.core.apk.DecompiledWorkspace
import com.example.apktranslatorpro.core.apk.EditableManifestDocument
import com.example.apktranslatorpro.core.apk.ManifestActivityItem
import com.example.apktranslatorpro.core.apk.ManifestXmlDocument
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

private val QUICK_ANDROID_PERMISSIONS = listOf(
    "android.permission.INTERNET",
    "android.permission.CAMERA",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.POST_NOTIFICATIONS"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManifestEditorScreen(
    workspace: DecompiledWorkspace?,
    hasApkSelected: Boolean,
    isBusy: Boolean,
    onDecompileApkClick: () -> Unit,
    onSaveManifestToWorkspace: (File, String) -> Unit,
    onExportManifestToUri: ((Uri, String) -> Unit)? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current

    var manifestDoc by remember(workspace?.workDir?.absolutePath, workspace?.manifestFile?.lastModified()) {
        mutableStateOf(
            if (workspace != null) {
                ManifestXmlDocument.loadFromWorkspace(workspace)
            } else {
                null
            }
        )
    }
    var packageDraft by remember(manifestDoc?.packageName) {
        mutableStateOf(manifestDoc?.packageName.orEmpty())
    }
    var newPermissionInput by remember { mutableStateOf("") }
    var newActivityInput by remember { mutableStateOf("") }
    var newActivityExported by remember { mutableStateOf(false) }
    var editingPermIndex by remember { mutableStateOf<Int?>(null) }
    var editingPermDraft by remember { mutableStateOf("") }
    var editingActivityIndex by remember { mutableStateOf<Int?>(null) }
    var editingActivityNameDraft by remember { mutableStateOf("") }
    var editingActivityExportedDraft by remember { mutableStateOf<Boolean?>(null) }
    var isRawMode by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun applyDocUpdate(updated: EditableManifestDocument, note: String? = null) {
        manifestDoc = updated
        packageDraft = updated.packageName
        if (note != null) statusMessage = note
    }

    val exportManifestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { targetUri: Uri? ->
        val currentXml = manifestDoc?.rawXml ?: return@rememberLauncherForActivityResult
        if (targetUri != null) {
            if (onExportManifestToUri != null) {
                onExportManifestToUri(targetUri, currentXml)
                statusMessage = "AndroidManifest.xml cihaz depolamasına dışa aktarıldı."
            } else {
                runCatching {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        out.write(currentXml.toByteArray(Charsets.UTF_8))
                    } ?: error("Hedef dosya açılamadı.")
                }.onSuccess {
                    statusMessage = "AndroidManifest.xml cihaz depolamasına dışa aktarıldı."
                }.onFailure { err ->
                    statusMessage = "Manifest dışa aktarılamadı: ${err.message}"
                }
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
                .widthIn(max = 880.dp)
                .testTag("manifest_editor_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("manifest_editor_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manifest Editör (AXMLPrinter2)",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "İkili AndroidManifest.xml paket adını, izinleri ve activity tanımlarını görüntüleyin, düzenleyin ve dışa aktarın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manifest_status_banner")
                    ) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (workspace == null || manifestDoc == null) {
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
                            Text("APK Çalışma Alanı Henüz Çıkarılmadı", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "AndroidManifest.xml dosyasını AXMLPrinter2 çözümleyicisi ile açmak için önce seçili APK'yı decompile edin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onDecompileApkClick,
                                enabled = hasApkSelected && !isBusy,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("manifest_decompile_button")
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (hasApkSelected) "APK'yı Decompile Et" else "Önce APK Seçin")
                            }
                        }
                    }
                }
            } else {
                val doc = manifestDoc!!

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
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(if (doc.parsedFromBinaryAxml) "Kaynak: İkili AXML" else "Kaynak: XML")
                                    }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Sürüm: ${doc.versionName.ifBlank { "-" }} (${doc.versionCode.ifBlank { "-" }})") }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text("SDK: ${doc.minSdk.ifBlank { "?" }} → ${doc.targetSdk.ifBlank { "?" }}") }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text("İzin: ${doc.permissions.size}") }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Activity: ${doc.activities.size}") }
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = !isRawMode,
                                    onClick = { isRawMode = false },
                                    label = { Text("Yapısal Düzenleyici") },
                                    modifier = Modifier.testTag("manifest_tab_structured")
                                )
                                FilterChip(
                                    selected = isRawMode,
                                    onClick = { isRawMode = true },
                                    label = { Text("Ham XML") },
                                    modifier = Modifier.testTag("manifest_tab_raw")
                                )
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = {
                                        val reloaded = ManifestXmlDocument.loadFromBinaryAxml(workspace)
                                        if (reloaded != null) {
                                            editingPermIndex = null
                                            editingActivityIndex = null
                                            applyDocUpdate(
                                                reloaded,
                                                "AndroidManifest.xml ikili AXML verisinden yeniden çözümlendi."
                                            )
                                        } else {
                                            statusMessage = "Ham AXML kaynağı bulunamadı; mevcut XML korundu."
                                        }
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("manifest_reparse_axml_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "AXML Yenile")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onSaveManifestToWorkspace(workspace.manifestFile, doc.rawXml)
                                        statusMessage = "Değişiklikler önbellekteki AndroidManifest.xml dosyasına kaydedildi."
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("save_manifest_button")
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Önbelleğe Kaydet", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Button(
                                    onClick = {
                                        exportManifestLauncher.launch("AndroidManifest.xml")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("export_manifest_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export Manifest", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                if (isRawMode) {
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
                                Text("Çözümlenmiş AndroidManifest.xml (AXMLPrinter2)", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = doc.rawXml,
                                    onValueChange = { updatedText ->
                                        manifestDoc = ManifestXmlDocument.parseFromXmlText(
                                            xmlText = updatedText,
                                            parsedFromBinaryAxml = doc.parsedFromBinaryAxml
                                        )
                                        packageDraft = manifestDoc?.packageName.orEmpty()
                                    },
                                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                                    minLines = 16,
                                    maxLines = 30,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manifest_raw_xml_input")
                                )
                            }
                        }
                    }
                } else {
                    // 1. Package Name Editor Card
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
                                    Icon(Icons.Filled.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Paket Adı (Package Name)", style = MaterialTheme.typography.titleMedium)
                                }
                                OutlinedTextField(
                                    value = packageDraft,
                                    onValueChange = { packageDraft = it },
                                    label = { Text("manifest package") },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manifest_package_input")
                                )
                                Button(
                                    onClick = {
                                        val updated = ManifestXmlDocument.updatePackageName(doc, packageDraft)
                                        applyDocUpdate(updated, "Paket adı güncellendi: ${updated.packageName}")
                                    },
                                    enabled = packageDraft.trim().isNotEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("manifest_apply_package_button")
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Paket Adını Güncelle")
                                }
                            }
                        }
                    }

                    // 2. Application Permissions Section
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
                                    Text("Uygulama İzinleri (${doc.permissions.size})", style = MaterialTheme.typography.titleMedium)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newPermissionInput,
                                        onValueChange = { newPermissionInput = it },
                                        label = { Text("Yeni izin ekle") },
                                        placeholder = { Text("android.permission.CAMERA") },
                                        singleLine = true,
                                        textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("manifest_add_permission_input")
                                    )
                                    Button(
                                        onClick = {
                                            val updated = ManifestXmlDocument.addPermission(doc, newPermissionInput)
                                            newPermissionInput = ""
                                            applyDocUpdate(updated, "İzin eklendi.")
                                        },
                                        enabled = newPermissionInput.trim().isNotEmpty(),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("manifest_add_permission_button")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ekle")
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    QUICK_ANDROID_PERMISSIONS.forEach { quickPerm ->
                                        val alreadyPresent = doc.permissions.contains(quickPerm)
                                        AssistChip(
                                            onClick = {
                                                if (!alreadyPresent) {
                                                    applyDocUpdate(
                                                        ManifestXmlDocument.addPermission(doc, quickPerm),
                                                        "Eklendi: ${quickPerm.substringAfterLast('.')}"
                                                    )
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = (if (alreadyPresent) "✓ " else "+ ") + quickPerm.substringAfterLast('.'),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (doc.permissions.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Manifest içinde tanımlı <uses-permission> bulunamadı.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(doc.permissions) { idx, perm ->
                            PermissionItemRow(
                                index = idx,
                                permission = perm,
                                isEditing = editingPermIndex == idx,
                                editDraft = if (editingPermIndex == idx) editingPermDraft else perm,
                                onEditStart = {
                                    editingPermIndex = idx
                                    editingPermDraft = perm
                                },
                                onEditDraftChange = { editingPermDraft = it },
                                onEditSave = {
                                    val updated = ManifestXmlDocument.updatePermission(doc, idx, editingPermDraft)
                                    editingPermIndex = null
                                    applyDocUpdate(updated, "İzin güncellendi.")
                                },
                                onEditCancel = { editingPermIndex = null },
                                onDelete = {
                                    if (editingPermIndex == idx) editingPermIndex = null
                                    val updated = ManifestXmlDocument.removePermission(doc, idx)
                                    applyDocUpdate(updated, "İzin kaldırıldı.")
                                }
                            )
                        }
                    }

                    // 3. Activities Section
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Activity Bileşenleri (${doc.activities.size})", style = MaterialTheme.typography.titleMedium)

                                OutlinedTextField(
                                    value = newActivityInput,
                                    onValueChange = { newActivityInput = it },
                                    label = { Text("Yeni Activity sınıf adı") },
                                    placeholder = { Text("com.example.MainActivity") },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manifest_add_activity_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("android:exported", style = MaterialTheme.typography.bodySmall)
                                        Switch(
                                            checked = newActivityExported,
                                            onCheckedChange = { newActivityExported = it },
                                            modifier = Modifier.testTag("manifest_add_activity_exported")
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val updated = ManifestXmlDocument.addActivity(
                                                doc,
                                                newActivityInput,
                                                newActivityExported
                                            )
                                            newActivityInput = ""
                                            applyDocUpdate(updated, "Activity eklendi.")
                                        },
                                        enabled = newActivityInput.trim().isNotEmpty(),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("manifest_add_activity_button")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Activity Ekle")
                                    }
                                }
                            }
                        }
                    }

                    if (doc.activities.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Manifest içinde tanımlı <activity> bulunamadı.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(doc.activities) { idx, activity ->
                            ActivityItemRow(
                                index = idx,
                                activity = activity,
                                isEditing = editingActivityIndex == idx,
                                nameDraft = if (editingActivityIndex == idx) editingActivityNameDraft else activity.name,
                                exportedDraft = if (editingActivityIndex == idx) editingActivityExportedDraft else activity.exported,
                                onEditStart = {
                                    editingActivityIndex = idx
                                    editingActivityNameDraft = activity.name
                                    editingActivityExportedDraft = activity.exported
                                },
                                onNameDraftChange = { editingActivityNameDraft = it },
                                onExportedDraftChange = { editingActivityExportedDraft = it },
                                onEditSave = {
                                    val updated = ManifestXmlDocument.updateActivity(
                                        doc = doc,
                                        index = idx,
                                        newName = editingActivityNameDraft,
                                        newExported = editingActivityExportedDraft
                                    )
                                    editingActivityIndex = null
                                    applyDocUpdate(updated, "Activity güncellendi.")
                                },
                                onEditCancel = { editingActivityIndex = null },
                                onQuickToggleExported = { nextExported ->
                                    val updated = ManifestXmlDocument.updateActivity(
                                        doc = doc,
                                        index = idx,
                                        newName = activity.name,
                                        newExported = nextExported
                                    )
                                    applyDocUpdate(updated, "android:exported=$nextExported güncellendi.")
                                },
                                onDelete = {
                                    if (editingActivityIndex == idx) editingActivityIndex = null
                                    val updated = ManifestXmlDocument.removeActivity(doc, idx)
                                    applyDocUpdate(updated, "Activity kaldırıldı.")
                                }
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
private fun PermissionItemRow(
    index: Int,
    permission: String,
    isEditing: Boolean,
    editDraft: String,
    onEditStart: () -> Unit,
    onEditDraftChange: (String) -> Unit,
    onEditSave: () -> Unit,
    onEditCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("manifest_permission_row_$index")
    ) {
        if (isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = onEditDraftChange,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manifest_edit_permission_input_$index")
                )
                IconButton(
                    onClick = onEditSave,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("manifest_save_permission_$index")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "İzni kaydet")
                }
                IconButton(
                    onClick = onEditCancel,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "İptal")
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = permission.substringAfterLast('.'),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = permission,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onEditStart,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("manifest_edit_permission_$index")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "İzni düzenle")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("manifest_remove_permission_$index")
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "İzni sil", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ActivityItemRow(
    index: Int,
    activity: ManifestActivityItem,
    isEditing: Boolean,
    nameDraft: String,
    exportedDraft: Boolean?,
    onEditStart: () -> Unit,
    onNameDraftChange: (String) -> Unit,
    onExportedDraftChange: (Boolean) -> Unit,
    onEditSave: () -> Unit,
    onEditCancel: () -> Unit,
    onQuickToggleExported: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("manifest_activity_row_$index")
    ) {
        if (isEditing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = onNameDraftChange,
                    label = { Text("Activity adı") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manifest_edit_activity_input_$index")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("exported: ${exportedDraft ?: false}", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = exportedDraft == true,
                            onCheckedChange = onExportedDraftChange
                        )
                    }
                    Row {
                        IconButton(
                            onClick = onEditSave,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("manifest_save_activity_$index")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Activity kaydet")
                        }
                        IconButton(
                            onClick = onEditCancel,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "İptal")
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.name.substringAfterLast('.').ifBlank { activity.name } +
                            if (activity.isAlias) " (alias)" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = activity.name,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "exported=${activity.exported?.toString() ?: "tanımsız"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(
                    checked = activity.exported == true,
                    onCheckedChange = onQuickToggleExported,
                    modifier = Modifier.testTag("manifest_activity_exported_$index")
                )
                IconButton(
                    onClick = onEditStart,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("manifest_edit_activity_$index")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Activity düzenle")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("manifest_remove_activity_$index")
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Activity sil", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
