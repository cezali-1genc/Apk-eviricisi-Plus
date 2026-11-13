package com.example.apktranslatorpro.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.ResourceKind
import com.example.apktranslatorpro.domain.model.TranslationOrigin
import com.example.apktranslatorpro.ui.viewmodel.MainUiState
import com.example.ui.theme.JetBrainsMonoFontFamily

private enum class PreviewFilter(val labelTr: String) {
    ALL("Tümü"),
    STRINGS("Metinler"),
    ARRAYS("Diziler"),
    PLURALS("Çoğullar"),
    TRANSLATED("Çevrilenler")
}

private enum class PreviewMode(val labelTr: String) {
    LIST("Liste"),
    STRINGS_XML("strings.xml"),
    BINARY_XML("İkili XML")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    uiState: MainUiState,
    generateXmlPreview: (Boolean) -> String,
    onEditTranslation: (uniqueKey: String, newText: String) -> Unit,
    onSelectBinaryXml: (entryPath: String) -> Unit,
    onRebuildWithEditsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PreviewFilter.ALL) }
    var viewMode by remember { mutableStateOf(PreviewMode.LIST) }
    var editingItem by remember { mutableStateOf<ResourceEntryItem?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var xmlDropdownExpanded by remember { mutableStateOf(false) }

    if (editingItem != null) {
        val current = editingItem!!
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Çeviriyi Düzenle: ${current.key}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Orijinal:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = current.originalText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    if (current.protectedTokens.isNotEmpty()) {
                        Text(
                            text = "Korunan parametreler: ${current.protectedTokens.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = editDraft,
                        onValueChange = { editDraft = it },
                        label = { Text("Hedef Dil (${uiState.targetLanguage.displayNameTr})") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_translation_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditTranslation(current.uniqueKey, editDraft)
                        editingItem = null
                    },
                    modifier = Modifier.testTag("save_translation_edit_button")
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("İptal")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (uiState.resources.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "Önizlenecek Kaynak Yok",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Çalışma Alanı sekmesinden bir APK seçtiğinizde çıkarılan string, string-array ve plurals kayıtları burada listelenir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Box
        }

        val filtered = remember(uiState.resources, searchQuery, filter) {
            val q = searchQuery.trim().lowercase()
            uiState.resources.filter { item ->
                val matchesFilter = when (filter) {
                    PreviewFilter.ALL -> true
                    PreviewFilter.STRINGS -> item.kind == ResourceKind.STRING
                    PreviewFilter.ARRAYS -> item.kind == ResourceKind.STRING_ARRAY
                    PreviewFilter.PLURALS -> item.kind == ResourceKind.PLURALS
                    PreviewFilter.TRANSLATED -> item.translatedText.isNotBlank()
                }
                val matchesSearch = q.isEmpty() ||
                    item.key.lowercase().contains(q) ||
                    item.originalText.lowercase().contains(q) ||
                    item.translatedText.lowercase().contains(q)
                matchesFilter && matchesSearch
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PreviewMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(idx, PreviewMode.entries.size),
                        modifier = Modifier.testTag("preview_mode_${mode.name.lowercase()}")
                    ) {
                        Text(mode.labelTr, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (uiState.resources.any { it.origin == TranslationOrigin.MANUAL_EDIT }) {
                Button(
                    onClick = onRebuildWithEditsClick,
                    enabled = !uiState.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rebuild_with_edits_button")
                ) {
                    Icon(Icons.Filled.Build, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Düzenlenen Metinlerle APK'yı Yeniden Paketle & İmzala")
                }
            }

            when (viewMode) {
                PreviewMode.LIST -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Anahtar veya metin ara...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PreviewFilter.entries.forEach { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text(f.labelTr) }
                            )
                        }
                    }

                    Text(
                        text = "${filtered.size} kayıt gösteriliyor (Toplam ${uiState.resources.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filtered.take(400), key = { it.uniqueKey }) { item ->
                            ResourcePreviewCard(
                                item = item,
                                isTargetRtl = uiState.targetLanguage.isRtl,
                                onEditClick = {
                                    editDraft = item.translatedText.ifBlank { item.originalText }
                                    editingItem = item
                                }
                            )
                        }
                    }
                }

                PreviewMode.STRINGS_XML -> {
                    val xmlText = remember(uiState.resources) { generateXmlPreview(true) }
                    XmlCodePreviewBox(
                        title = "Oluşturulan values-${uiState.targetLanguage.resourceFolderSuffix}/strings.xml Önizlemesi",
                        xmlText = xmlText,
                        modifier = Modifier.weight(1f)
                    )
                }

                PreviewMode.BINARY_XML -> {
                    val xmlEntries = uiState.apkMetadata?.binaryXmlFiles.orEmpty()
                        .ifEmpty { listOf("AndroidManifest.xml") }
                    ExposedDropdownMenuBox(
                        expanded = xmlDropdownExpanded,
                        onExpandedChange = { xmlDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedBinaryXmlPath,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("İkili XML Dosyası (${xmlEntries.size})") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = xmlDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("binary_xml_selector")
                        )
                        ExposedDropdownMenu(
                            expanded = xmlDropdownExpanded,
                            onDismissRequest = { xmlDropdownExpanded = false }
                        ) {
                            xmlEntries.take(80).forEach { entryPath ->
                                DropdownMenuItem(
                                    text = { Text(entryPath, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        xmlDropdownExpanded = false
                                        onSelectBinaryXml(entryPath)
                                    }
                                )
                            }
                        }
                    }

                    val displayedXml = uiState.selectedBinaryXmlPreview.ifBlank {
                        uiState.apkMetadata?.manifestXmlPreview ?: "İkili XML verisi yok."
                    }
                    XmlCodePreviewBox(
                        title = "${uiState.selectedBinaryXmlPath} (İkili AXML Çözümlemesi)",
                        xmlText = displayedXml,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourcePreviewCard(
    item: ResourceEntryItem,
    isTargetRtl: Boolean,
    onEditClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.kind.tagName}: ${item.key}${if (item.subKey.isNotEmpty()) " (${item.subKey})" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Düzenle",
                            modifier = Modifier.height(12.dp)
                        )
                        Text(
                            text = item.origin.labelTr,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Text(
                text = item.originalText,
                style = MaterialTheme.typography.bodyMedium
            )

            if (item.translatedText.isNotBlank()) {
                HorizontalDivider()
                val dir = if (isTargetRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    Text(
                        text = item.translatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun XmlCodePreviewBox(
    title: String,
    xmlText: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = xmlText,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
