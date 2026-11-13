package com.example.apktranslatorpro.ui.screens

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
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.apktranslatorpro.data.local.AppUserSettings
import com.example.apktranslatorpro.domain.model.OnlineApiConfig
import com.example.apktranslatorpro.domain.model.OnlineProviderPreset
import com.example.apktranslatorpro.ui.viewmodel.MainUiState
import com.example.ui.theme.AppThemeMode

@Composable
fun SettingsAndBrandScreen(
    uiState: MainUiState,
    settings: AppUserSettings,
    userDisplayName: String = "",
    userEmail: String? = null,
    onSignOutClick: () -> Unit = {},
    onSelectTheme: (AppThemeMode) -> Unit,
    onSaveOnlineApiConfig: (OnlineApiConfig) -> Unit,
    onTestOnlineApiConnection: (OnlineApiConfig) -> Unit,
    onRegenerateSignerKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPreset by remember { mutableStateOf(settings.onlineApiConfig.preset) }
    var endpointUrl by remember { mutableStateOf(settings.onlineApiConfig.endpointUrl) }
    var apiKey by remember { mutableStateOf(settings.onlineApiConfig.apiKey) }
    var customHeader by remember { mutableStateOf(settings.onlineApiConfig.customHeaderName) }
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(settings.onlineApiConfig) {
        selectedPreset = settings.onlineApiConfig.preset
        endpointUrl = settings.onlineApiConfig.endpointUrl
        apiKey = settings.onlineApiConfig.apiKey
        customHeader = settings.onlineApiConfig.customHeaderName
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("settings_lazy_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (userDisplayName.isNotBlank() || !userEmail.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Google Hesabı & Bulut Senkronizasyonu", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = userDisplayName.ifBlank { "Oturum Açık" },
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onSignOutClick,
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Text("Hesaptan Çıkış Yap")
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Görünüm ve Tema", style = MaterialTheme.typography.titleLarge)
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AppThemeMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { onSelectTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, AppThemeMode.entries.size),
                                    modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                                ) {
                                    Text(mode.labelTr)
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
                            Icon(Icons.Filled.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Çevrimiçi Çeviri API Yapılandırması", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "API adresi ve anahtarı cihazınızda Android Keystore AES-256-GCM ile şifrelenerek saklanır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OnlineProviderPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = selectedPreset == preset,
                                    onClick = {
                                        selectedPreset = preset
                                        if (preset.defaultUrl.isNotBlank()) {
                                            endpointUrl = preset.defaultUrl
                                        }
                                    },
                                    label = { Text(preset.displayName) }
                                )
                            }
                        }

                        Text(
                            text = selectedPreset.descriptionTr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = endpointUrl,
                            onValueChange = { endpointUrl = it },
                            label = { Text("API Uç Nokta Adresi (HTTPS)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_endpoint_input")
                        )

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Anahtarı (İsteğe bağlı / Sağlayıcıya göre)") },
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Anahtarı göster/gizle"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input")
                        )

                        if (selectedPreset == OnlineProviderPreset.CUSTOM_JSON) {
                            OutlinedTextField(
                                value = customHeader,
                                onValueChange = { customHeader = it },
                                label = { Text("Yetkilendirme Başlık Adı (örn: Authorization veya X-API-Key)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val draftConfig = OnlineApiConfig(
                            preset = selectedPreset,
                            endpointUrl = endpointUrl,
                            apiKey = apiKey,
                            customHeaderName = customHeader
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onSaveOnlineApiConfig(draftConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_api_config_button")
                            ) {
                                Text("Şifreli Kaydet")
                            }
                            OutlinedButton(
                                onClick = { onTestOnlineApiConnection(draftConfig) },
                                enabled = !uiState.isTestingApi,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_api_button")
                            ) {
                                Text(if (uiState.isTestingApi) "Test Ediliyor..." else "Bağlantıyı Test Et")
                            }
                        }

                        if (!uiState.apiTestMessage.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.apiTestMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
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
                            Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Yerel APK İmzalama Anahtarı (PKCS#12)", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "Çıktı APK dosyaları cihazınızda üretilen RSA-2048 X.509 sertifikasıyla APK Signature Scheme v1, v2 ve v3 üzerinden imzalanır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Sertifika SHA-256:\n${uiState.signerCertSha256.ifBlank { "Henüz oluşturulmadı" }}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = onRegenerateSignerKey,
                            modifier = Modifier.testTag("regenerate_key_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Yeni İmza Anahtarı Üret")
                        }
                    }
                }
            }

            item {
                CezaliCorporateCard()
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CezaliCorporateCard() {
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
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("CEZALI 1GENÇ • Kurumsal & Lisanslar", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "DİJİTAL TEKNOLOJİ • YAZILIM • ANDROID • PROJE GELİŞTİRME",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "İşletme Sahibi: Serhat Güner\nCezalı 1Genç; Android yazılımları, mobil uygulamalar, teknik arşivler ve yazılım geliştirme projeleri üreten bağımsız dijital teknoloji platformudur.",
                style = MaterialTheme.typography.bodySmall
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "TEKNOLOJİYİ KEŞFET. YAZILIMI GELİŞTİR. DİJİTAL DÜNYAYI YÖNET.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            HorizontalDivider()
            Text("Açık Kaynak Bağımlılık ve Lisans Bildirimleri", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "• Android apksig (Apache License 2.0) — APK v1/v2/v3 imzalama ve doğrulama\n" +
                    "• Firebase AI Logic (Gemini 3.8 Flash) & Cloud Firestore — Akıllı çeviri, APK analizi ve bulut senkronizasyonu\n" +
                    "• Google ML Kit Translate & Language ID (Google APIs Terms) — Cihaz içi çevrimdışı çeviri ve dil algılama\n" +
                    "• Jetpack Compose, Material 3, Room, DataStore, Navigation (Apache 2.0)\n" +
                    "• OkHttp & Moshi (Apache 2.0) — Çevrimiçi çeviri API istemcisi\n" +
                    "• Özel Binary ARSC & AXML Çözümleyici — Saf Kotlin uygulaması (Cezalı 1Genç)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
