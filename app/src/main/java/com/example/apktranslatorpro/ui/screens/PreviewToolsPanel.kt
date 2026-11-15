package com.example.apktranslatorpro.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.apktranslatorpro.core.text.TranslationQualityTools
import com.example.apktranslatorpro.domain.model.ResourceCategory
import com.example.apktranslatorpro.domain.model.ResourceEntryItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewToolsPanel(
    resources: List<ResourceEntryItem>,
    selectedCategory: ResourceCategory?,
    onSelectCategory: (ResourceCategory?) -> Unit,
    onOpenSearchReplace: () -> Unit,
    onAutoSpellFixClick: () -> Unit,
    onTranslateUntranslatedClick: () -> Unit,
    onExportFormatClick: (format: String) -> Unit,
    onOpenSideBySideXmlDiff: () -> Unit
) {
    val report = remember(resources) { TranslationQualityTools.buildReport(resources) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kalite Puanı: ${report.averageQualityScore}/100 • Eksik: ${report.missingCount} • Yinelenen: ${report.duplicateSourceCount} • Kilitli: ${report.lockedCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("Tüm Kategoriler") }
                )
                ResourceCategory.entries.forEach { cat ->
                    val cnt = report.categoryBreakdown[cat] ?: 0
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onSelectCategory(if (selectedCategory == cat) null else cat) },
                        label = { Text("${cat.labelTr} ($cnt)") }
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = onOpenSearchReplace,
                    leadingIcon = { Icon(Icons.Filled.FindReplace, contentDescription = null) },
                    label = { Text("Bul & Değiştir") },
                    modifier = Modifier.testTag("preview_search_replace_chip")
                )
                AssistChip(
                    onClick = onAutoSpellFixClick,
                    leadingIcon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null) },
                    label = { Text("Otomatik Düzelt") },
                    modifier = Modifier.testTag("preview_autofix_chip")
                )
                AssistChip(
                    onClick = onTranslateUntranslatedClick,
                    leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null) },
                    label = { Text("Eksikleri Çevir") },
                    modifier = Modifier.testTag("preview_translate_missing_chip")
                )
                AssistChip(
                    onClick = onOpenSideBySideXmlDiff,
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null) },
                    label = { Text("XML Yan Yana") },
                    modifier = Modifier.testTag("preview_xml_diff_chip")
                )
                AssistChip(
                    onClick = { onExportFormatClick("json") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    label = { Text("JSON") }
                )
                AssistChip(
                    onClick = { onExportFormatClick("csv") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    label = { Text("CSV") }
                )
                AssistChip(
                    onClick = { onExportFormatClick("xml") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    label = { Text("XML") }
                )
            }
        }
    }
}

@Composable
fun SearchAndReplaceDialog(
    onDismiss: () -> Unit,
    onApply: (find: String, replace: String, matchCase: Boolean) -> Unit
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Toplu Metin Bul & Değiştir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text("Aranan Metin") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_replace_find_input")
                )
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Yeni Metin") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_replace_replace_input")
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Checkbox(checked = matchCase, onCheckedChange = { matchCase = it })
                    Text("Büyük/Küçük harf duyarlı", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(findText, replaceText, matchCase)
                    onDismiss()
                },
                enabled = findText.isNotEmpty(),
                modifier = Modifier.testTag("search_replace_apply_button")
            ) {
                Text("Tümünde Değiştir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
