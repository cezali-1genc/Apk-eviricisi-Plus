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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.apktranslatorpro.core.text.PlaceholderProtector
import com.example.apktranslatorpro.core.text.TranslationProtectionConfig
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslationSettingsScreen(
    protectionConfig: TranslationProtectionConfig,
    onUpdateConfig: (TranslationProtectionConfig) -> Unit,
    onAddExcludedTerm: (String) -> Unit,
    onRemoveExcludedTerm: (String) -> Unit,
    onResetDefaultTerms: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newTermInput by remember { mutableStateOf("") }
    var samplePreviewInput by remember {
        mutableStateOf("Merhaba <b>%1\$s</b>!\\nBluetooth, API ve APK paketi {count} dosya için hazır.")
    }

    val liveProtectionResult = remember(samplePreviewInput, protectionConfig) {
        PlaceholderProtector.protect(samplePreviewInput, protectionConfig)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("translation_rules_lazy_list"),
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Çeviri Kuralları & Özelleştirme",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = "Çeviri motoruna gönderilmeden önce korunacak yer tutucuları, XML/HTML etiketlerini ve çevrilmeyecek teknik terimleri buradan özelleştirin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()

                        RuleToggleRow(
                            title = "Yer Tutucuları Otomatik Koru",
                            subtitle = "%1\$s, %d, {var}, {{var}}, \${var}, URL, @string/... ve \\n ifadelerini bozulmadan korur.",
                            checked = protectionConfig.preservePlaceholders,
                            onCheckedChange = { checked ->
                                onUpdateConfig(protectionConfig.copy(preservePlaceholders = checked))
                            },
                            switchTestTag = "toggle_preserve_placeholders"
                        )

                        HorizontalDivider()

                        RuleToggleRow(
                            title = "XML / HTML Etiketlerini Koru",
                            subtitle = "<b>, <i>, <xliff:g>, <font ...> gibi biçimlendirme etiketlerini çeviri dışında tutar.",
                            checked = protectionConfig.preserveMarkupTags,
                            onCheckedChange = { checked ->
                                onUpdateConfig(protectionConfig.copy(preserveMarkupTags = checked))
                            },
                            switchTestTag = "toggle_preserve_markup"
                        )

                        HorizontalDivider()

                        RuleToggleRow(
                            title = "Teknik Terimleri Çeviri Dışında Bırak",
                            subtitle = "Aşağıdaki özel listedeki kelime ve marka/teknik ifadeleri kaynak dilde olduğu gibi bırakır.",
                            checked = protectionConfig.excludeTechnicalTerms,
                            onCheckedChange = { checked ->
                                onUpdateConfig(protectionConfig.copy(excludeTechnicalTerms = checked))
                            },
                            switchTestTag = "toggle_exclude_terms"
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Hariç Tutulan Terimler (${protectionConfig.excludedTerms.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            TextButton(
                                onClick = onResetDefaultTerms,
                                modifier = Modifier.testTag("reset_excluded_terms_button")
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Varsayılana Sıfırla")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newTermInput,
                                onValueChange = { newTermInput = it },
                                label = { Text("Özel terim veya ifade ekle") },
                                placeholder = { Text("Örn: Cezalı 1Genç, TensorFlow") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("excluded_term_input")
                            )
                            Button(
                                onClick = {
                                    if (newTermInput.isNotBlank()) {
                                        onAddExcludedTerm(newTermInput)
                                        newTermInput = ""
                                    }
                                },
                                enabled = newTermInput.isNotBlank(),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("add_excluded_term_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Ekle")
                            }
                        }

                        if (protectionConfig.excludedTerms.isEmpty()) {
                            Text(
                                text = "Henüz hariç tutulan terim yok. Yukarıdan teknik terim veya marka adı ekleyebilirsiniz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                protectionConfig.excludedTerms.forEach { term ->
                                    InputChip(
                                        selected = protectionConfig.excludeTechnicalTerms,
                                        onClick = { onRemoveExcludedTerm(term) },
                                        label = { Text(term) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "$term terimini kaldır",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("excluded_term_chip_${term.lowercase()}")
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
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Canlı Koruma Simülasyonu",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        OutlinedTextField(
                            value = samplePreviewInput,
                            onValueChange = { samplePreviewInput = it },
                            label = { Text("Test Metni") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("protection_sample_input")
                        )

                        Text(
                            text = "Motora Gönderilen Maskelenmiş Metin:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = liveProtectionResult.maskedText,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (liveProtectionResult.tokens.isEmpty()) {
                                    "Korunan parça: Yok"
                                } else {
                                    "Korunan (${liveProtectionResult.tokens.size}): ${liveProtectionResult.tokens.joinToString(" • ")}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
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
private fun RuleToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}
