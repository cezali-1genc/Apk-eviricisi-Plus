package com.example.apktranslatorpro.ui.components

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusSuccess

@Composable
fun rememberApkSafPickerLauncher(
    onApkSelected: (Uri) -> Unit
): ManagedActivityResultLauncher<Array<String>, Uri?> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onApkSelected(uri)
        }
    }
}

fun ManagedActivityResultLauncher<Array<String>, Uri?>.launchApkDocumentPicker() {
    launch(
        arrayOf(
            "application/vnd.android.package-archive",
            "application/octet-stream",
            "*/*"
        )
    )
}

@Composable
fun ApkSafFilePickerComponent(
    selectedApkUri: String?,
    safFileVerified: Boolean,
    safVerificationSummary: String,
    isInspecting: Boolean,
    hasSelectedApk: Boolean,
    primaryButtonLabel: String,
    onPickApkClick: () -> Unit,
    onVerifyExistenceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPickApkClick,
                enabled = !isInspecting,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("select_apk_button")
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = primaryButtonLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (selectedApkUri != null || hasSelectedApk) {
                OutlinedButton(
                    onClick = onVerifyExistenceClick,
                    enabled = !isInspecting && selectedApkUri != null,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("saf_verify_existence_button")
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = "Dosya varlığını doğrula")
                    Spacer(Modifier.width(6.dp))
                    Text("Doğrula")
                }
            }
        }

        if (!selectedApkUri.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (safFileVerified) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (safFileVerified) StatusSuccess else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (safFileVerified) {
                                "SAF Salt Okunur Doğrulandı (Orijinal Dosya Değiştirilmedi)"
                            } else {
                                safVerificationSummary.ifBlank { "SAF URI doğrulaması bekleniyor" }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (safFileVerified) StatusSuccess else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = selectedApkUri,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else if (safVerificationSummary.isNotBlank()) {
            Text(
                text = safVerificationSummary,
                style = MaterialTheme.typography.labelSmall,
                color = if (safFileVerified) StatusSuccess else MaterialTheme.colorScheme.error
            )
        }
    }
}
