package com.example.apktranslatorpro.ui.screens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.TextUtils
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.apktranslatorpro.data.local.RecentApkEntity
import com.example.apktranslatorpro.ui.viewmodel.BuildProcessUiState
import com.example.apktranslatorpro.ui.viewmodel.MainUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ToolkitUtilityModuleScreen(
    module: ToolkitModule,
    uiState: MainUiState,
    buildState: BuildProcessUiState,
    recentApks: List<RecentApkEntity>,
    onDecompileApk: () -> Unit,
    onExportWorkspaceZip: (Uri) -> Unit,
    onRebuildAndSignApk: () -> Unit,
    onRegenerateSignerKey: () -> Unit,
    onDeleteRecentApk: (String) -> Unit,
    onClearRecentApks: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val workspace = buildState.workspace
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri ->
        if (targetUri != null) {
            onExportWorkspaceZip(targetUri)
            statusMessage = "Proje ZIP dışa aktarma başlatıldı."
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
                .testTag("toolkit_util_module_${module.id}"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("util_back_button")) {
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

            if (!statusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(statusMessage!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            when (module) {
                ToolkitModule.DEVELOPER_BROWSER -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Geliştirici Önbellek & APK Dosya Tarayıcısı", style = MaterialTheme.typography.titleMedium)
                                if (workspace == null) {
                                    Text("Önce APK Decompiler modülüyle çalışma alanını çıkarın.", style = MaterialTheme.typography.bodySmall)
                                    Button(onClick = onDecompileApk, enabled = uiState.selectedApkUri != null) {
                                        Text("APK'yı Decompile Et")
                                    }
                                } else {
                                    Text("Toplam Arşiv Öğesi: ${workspace.allEntries.size}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    if (workspace != null) {
                        items(workspace.allEntries.take(150)) { entry ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = entry,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                ToolkitModule.APK_SIGNER -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("APK Signature Scheme v1/v2/v3 İmzalayıcı", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    text = "Aktif PKCS#12 RSA-2048 SHA-256:\n${uiState.signerCertSha256.ifBlank { "Hazırlanıyor..." }}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Button(
                                    onClick = onRebuildAndSignApk,
                                    enabled = uiState.apkMetadata != null && !uiState.isRunning,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Mevcut APK'yı Yeniden İmzala")
                                }
                                OutlinedButton(onClick = onRegenerateSignerKey, modifier = Modifier.fillMaxWidth()) {
                                    Text("Yeni RSA-2048 İmza Anahtarı Üret")
                                }
                            }
                        }
                    }
                }

                ToolkitModule.ZIP_MANAGER -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("ZIP Arşiv & Proje Dışa Aktarıcı", style = MaterialTheme.typography.titleMedium)
                                if (workspace == null) {
                                    Text("Çıkarılmış proje dizinini ZIP olarak dışa aktarmak için önce APK'yı decompile edin.", style = MaterialTheme.typography.bodySmall)
                                    Button(onClick = onDecompileApk, enabled = uiState.selectedApkUri != null) {
                                        Text("APK'yı Decompile Et")
                                    }
                                } else {
                                    Text("Hazır çalışma alanı: ${workspace.allEntries.size} giriş", style = MaterialTheme.typography.bodySmall)
                                    Button(
                                        onClick = {
                                            val pkg = uiState.apkMetadata?.packageName ?: "apk_workspace"
                                            exportZipLauncher.launch("${pkg}_decompiled.zip")
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("export_workspace_zip_button")
                                    ) {
                                        Icon(Icons.Filled.FolderZip, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Decompile Projesini ZIP Olarak Kaydet")
                                    }
                                }
                            }
                        }
                    }
                }

                ToolkitModule.BACKUP_RESTORE -> {
                    item {
                        val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Room İşlem & SHA-256 Geçmişi (${recentApks.size})", style = MaterialTheme.typography.titleMedium)
                                    if (recentApks.isNotEmpty()) {
                                        TextButton(onClick = onClearRecentApks) { Text("Tümünü Sil") }
                                    }
                                }
                                if (recentApks.isEmpty()) {
                                    Text("Henüz Room veritabanında kayıtlı APK geçmişi yok.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    recentApks.forEach { apk ->
                                        HorizontalDivider()
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text("${apk.appLabel} (${apk.fileName})", style = MaterialTheme.typography.titleSmall)
                                                Text("${apk.packageName} • ${apk.lastAction}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                                Text(
                                                    text = "SHA-256: ${apk.sha256Hex.take(18)}... • ${fmt.format(Date(apk.modifiedAtMillis))}",
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            IconButton(onClick = { onDeleteRecentApk(apk.sha256Hex) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Kaydı sil", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ToolkitModule.NOTIFICATION_MAKER -> {
                    item {
                        NotificationMakerCard(onStatus = { statusMessage = it })
                    }
                }

                ToolkitModule.TOAST_MAKER -> {
                    item {
                        var message by remember { mutableStateOf("Apk Çeviricisi Plus — Test Toast Mesajı") }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = message,
                                    onValueChange = { message = it },
                                    label = { Text("Toast Mesajı") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        statusMessage = "Toast mesajı ekranda gösterildi."
                                    },
                                    enabled = message.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Toast Göster")
                                }
                            }
                        }
                    }
                }

                ToolkitModule.BASE64_TOOLS -> {
                    item {
                        var input by remember { mutableStateOf("") }
                        var output by remember { mutableStateOf("") }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    label = { Text("Girdi Metni veya Base64") },
                                    minLines = 3,
                                    modifier = Modifier.fillMaxWidth().testTag("base64_input")
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            output = Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                                        },
                                        modifier = Modifier.weight(1f).testTag("base64_encode_button")
                                    ) { Text("Encode") }
                                    OutlinedButton(
                                        onClick = {
                                            output = runCatching {
                                                String(Base64.decode(input.trim(), Base64.DEFAULT), Charsets.UTF_8)
                                            }.getOrElse { "Geçersiz Base64 verisi" }
                                        },
                                        modifier = Modifier.weight(1f).testTag("base64_decode_button")
                                    ) { Text("Decode") }
                                }
                                OutlinedTextField(
                                    value = output,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Sonuç") },
                                    trailingIcon = {
                                        IconButton(onClick = { copyToClipboard(context, "Base64", output); statusMessage = "Panoya kopyalandı." }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Kopyala")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                ToolkitModule.HTML_ENCODER -> {
                    item {
                        var input by remember { mutableStateOf("") }
                        var output by remember { mutableStateOf("") }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    label = { Text("HTML veya Düz Metin") },
                                    minLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { output = TextUtils.htmlEncode(input) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("HTML Escape") }
                                    OutlinedButton(
                                        onClick = {
                                            output = Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Unescape") }
                                }
                                OutlinedTextField(
                                    value = output,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Sonuç") },
                                    trailingIcon = {
                                        IconButton(onClick = { copyToClipboard(context, "HTML", output); statusMessage = "Panoya kopyalandı." }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Kopyala")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
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
private fun NotificationMakerCard(onStatus: (String) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("Apk Çeviricisi Plus") }
    var body by remember { mutableStateOf("APK araç kutusu test bildirimi başarıyla tetiklendi.") }

    fun sendNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "apk_toolkit_notifications"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Toolkit Bildirimleri", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        nm.notify(1042, notif)
        onStatus("Sistem bildirimi gönderildi.")
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendNotification() else onStatus("Bildirim izni verilmedi.")
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Bildirim Başlığı") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Bildirim İçeriği") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        sendNotification()
                    }
                },
                enabled = title.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Bildirim Gönder")
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
