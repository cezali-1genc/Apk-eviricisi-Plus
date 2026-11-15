package com.example.apktranslatorpro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apktranslatorpro.ui.viewmodel.GitUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusSuccess

/**
 * Phone-native HTTPS Git & Git Clone screen supporting Smart HTTP ref discovery,
 * repo cloning over mobile/Wi-Fi connections, status/XML diff preview, local commits,
 * exporting APK strings.xml into cloned repos, and pushing to GitHub with a PAT.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitIntegrationScreen(
    gitState: GitUiState,
    hasApkResources: Boolean,
    targetLangCode: String,
    userDisplayName: String,
    onUrlChanged: (String) -> Unit,
    onBranchChanged: (String) -> Unit,
    onPatChanged: (String) -> Unit,
    onProbeRefsClick: () -> Unit,
    onCloneRepoClick: () -> Unit,
    onSelectRepoClick: (String) -> Unit,
    onInspectDiffClick: (String) -> Unit,
    onExportTranslatedStringsToRepo: () -> Unit,
    onCommitChangesClick: (message: String, author: String) -> Unit,
    onPushFileToGitHub: (relativePath: String, message: String) -> Unit,
    onDeleteRepoClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    var commitMessage by remember { mutableStateOf("Update Android localization XML") }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("git_integration_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("git_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                    Column {
                        Text("Git & Git Clone Entegrasyonu", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Harici git binary gerektirmeden telefon internetiyle HTTPS Smart HTTP clone, diff, commit ve push.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Uzak Depo Bağlantısı (HTTPS Git Clone)", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = gitState.repoUrl,
                            onValueChange = onUrlChanged,
                            label = { Text("Git Depo URL (https://github.com/kullanici/proje)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("git_repo_url_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = gitState.branchInput,
                                onValueChange = onBranchChanged,
                                label = { Text("Dal (Branch, boşsa oto)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("git_branch_input")
                            )
                            OutlinedTextField(
                                value = gitState.personalAccessToken,
                                onValueChange = onPatChanged,
                                label = { Text("PAT Token (İsteğe bağlı)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("git_pat_input")
                            )
                        }

                        if (gitState.discoveredBranches.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gitState.discoveredBranches.forEach { branch ->
                                    FilterChip(
                                        selected = gitState.branchInput == branch,
                                        onClick = { onBranchChanged(branch) },
                                        label = { Text(branch) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onProbeRefsClick,
                                enabled = !gitState.isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("git_probe_refs_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Dalları Tara")
                            }
                            Button(
                                onClick = onCloneRepoClick,
                                enabled = !gitState.isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("git_clone_button")
                            ) {
                                if (gitState.isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Çalışıyor...")
                                } else {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Git Clone")
                                }
                            }
                        }

                        if (gitState.progressLabel.isNotBlank()) {
                            Text(
                                text = gitState.progressLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (gitState.clonedRepos.isNotEmpty()) {
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
                            Text("Klonlanmış Yerel Depolar (${gitState.clonedRepos.size})", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                gitState.clonedRepos.forEach { repo ->
                                    FilterChip(
                                        selected = gitState.activeRepo?.metadata?.slug == repo.metadata.slug,
                                        onClick = { onSelectRepoClick(repo.metadata.slug) },
                                        label = { Text("${repo.metadata.slug} (${repo.metadata.branch})") }
                                    )
                                }
                            }

                            val active = gitState.activeRepo
                            if (active != null) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(active.metadata.remoteUrl, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "HEAD: ${active.metadata.headSha.take(12)} • Dal: ${active.metadata.branch} • ${active.fileCount} dosya • ${active.xmlFiles.size} XML",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = StatusSuccess
                                        )
                                    }
                                    IconButton(onClick = { onDeleteRepoClick(active.metadata.slug) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Yerel depoyu sil", tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                Button(
                                    onClick = onExportTranslatedStringsToRepo,
                                    enabled = hasApkResources && !gitState.isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("git_export_strings_button")
                                ) {
                                    Icon(Icons.Filled.Code, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Aktif APK Çevirilerini Repoya Aktar (values-$targetLangCode/strings.xml)")
                                }

                                Text("git status (${gitState.workingChanges.size} değişiklik)", style = MaterialTheme.typography.titleSmall)
                                if (gitState.workingChanges.isEmpty()) {
                                    Text(
                                        text = "Çalışma ağacı temiz (değişiklik yok).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        gitState.workingChanges.forEach { ch ->
                                            AssistChip(
                                                onClick = { onInspectDiffClick(ch.relativePath) },
                                                label = { Text("[${ch.status.name.take(1)}] ${ch.relativePath}") }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = commitMessage,
                                        onValueChange = { commitMessage = it },
                                        label = { Text("Commit Mesajı") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { onCommitChangesClick(commitMessage, userDisplayName) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("git_commit_button")
                                        ) {
                                            Icon(Icons.Filled.Commit, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Yerel Commit")
                                        }
                                        val firstChanged = gitState.selectedDiffPath ?: gitState.workingChanges.first().relativePath
                                        OutlinedButton(
                                            onClick = { onPushFileToGitHub(firstChanged, commitMessage) },
                                            enabled = gitState.personalAccessToken.isNotBlank() && !gitState.isBusy,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("git_push_button")
                                        ) {
                                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("GitHub Push")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (gitState.selectedDiffSummary != null && gitState.selectedDiffPath != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Git Diff Önizleme: ${gitState.selectedDiffPath}", style = MaterialTheme.typography.titleSmall)
                            val sum = gitState.selectedDiffSummary
                            Text(
                                text = "+${sum.addedCount} / -${sum.removedCount} / ~${sum.modifiedCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    sum.rows.take(80).forEach { row ->
                                        Text(
                                            text = "${row.status.name.take(3)} | ${row.modifiedText.ifBlank { row.originalText }}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val commits = gitState.activeRepo?.metadata?.commits.orEmpty()
            if (commits.isNotEmpty()) {
                item {
                    Text("Commit Geçmişi", style = MaterialTheme.typography.titleMedium)
                }
                items(commits.take(15)) { c ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("[${c.commitSha}] ${c.message}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${c.author} • ${c.changedFilesCount} dosya",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
