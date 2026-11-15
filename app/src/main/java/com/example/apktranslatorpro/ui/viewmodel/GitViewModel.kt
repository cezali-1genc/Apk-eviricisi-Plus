package com.example.apktranslatorpro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.apktranslatorpro.ApkTranslatorApp
import com.example.apktranslatorpro.core.apk.XmlDiffSummary
import com.example.apktranslatorpro.core.git.ClonedGitRepo
import com.example.apktranslatorpro.core.git.GitRepositoryService
import com.example.apktranslatorpro.core.git.GitWorkingChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class GitUiState(
    val repoUrl: String = "https://github.com/",
    val branchInput: String = "",
    val personalAccessToken: String = "",
    val discoveredBranches: List<String> = emptyList(),
    val clonedRepos: List<ClonedGitRepo> = emptyList(),
    val activeRepo: ClonedGitRepo? = null,
    val workingChanges: List<GitWorkingChange> = emptyList(),
    val selectedDiffPath: String? = null,
    val selectedDiffSummary: XmlDiffSummary? = null,
    val isBusy: Boolean = false,
    val progressLabel: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel managing phone-friendly HTTPS Git Smart HTTP ref discovery,
 * pure-Kotlin `git clone`, status/XML diff tracking, local commits,
 * XML import/export between cloned repos and APK translations, and GitHub push.
 */
class GitViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ApkTranslatorApp
    private val gitService: GitRepositoryService = app.gitRepositoryService

    private val _state = MutableStateFlow(GitUiState())
    val state: StateFlow<GitUiState> = _state.asStateFlow()

    init {
        refreshRepos()
    }

    fun updateRepoUrl(value: String) {
        _state.update { it.copy(repoUrl = value) }
    }

    fun updateBranchInput(value: String) {
        _state.update { it.copy(branchInput = value) }
    }

    fun updatePatToken(value: String) {
        _state.update { it.copy(personalAccessToken = value) }
    }

    fun refreshRepos(selectSlug: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val repos = gitService.listClonedRepositories()
                val selected = if (selectSlug != null) {
                    repos.firstOrNull { it.metadata.slug == selectSlug }
                } else {
                    _state.value.activeRepo?.let { cur -> repos.firstOrNull { it.metadata.slug == cur.metadata.slug } }
                        ?: repos.firstOrNull()
                }
                val changes = selected?.let { gitService.computeStatus(it.metadata.slug) }.orEmpty()
                repos to (selected to changes)
            }.onSuccess { (repos, pair) ->
                val (selected, changes) = pair
                _state.update {
                    it.copy(
                        clonedRepos = repos,
                        activeRepo = selected,
                        workingChanges = changes
                    )
                }
            }
        }
    }

    fun probeRemoteRefs() {
        val url = _state.value.repoUrl.trim()
        val pat = _state.value.personalAccessToken.trim().ifBlank { null }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(isBusy = true, progressLabel = "Uzak Git dalları taranıyor...", errorMessage = null)
            }
            runCatching { gitService.probeRemoteRefs(url, pat) }
                .onSuccess { refs ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            discoveredBranches = refs.branches,
                            branchInput = it.branchInput.ifBlank { refs.defaultBranch },
                            statusMessage = "Bağlantı başarılı! Varsayılan dal: ${refs.defaultBranch} (${refs.branches.size} dal)"
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.message ?: "Git sunucusuna bağlanılamadı."
                        )
                    }
                }
        }
    }

    fun cloneRepository() {
        val url = _state.value.repoUrl.trim()
        val branch = _state.value.branchInput.trim().ifBlank { null }
        val pat = _state.value.personalAccessToken.trim().ifBlank { null }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(isBusy = true, progressLabel = "Depo klonlanıyor...", errorMessage = null, statusMessage = null)
            }
            runCatching {
                gitService.cloneRepository(url, branch, pat) { msg ->
                    _state.update { it.copy(progressLabel = msg) }
                }
            }.onSuccess { cloned ->
                val repos = gitService.listClonedRepositories()
                _state.update {
                    it.copy(
                        isBusy = false,
                        progressLabel = "",
                        clonedRepos = repos,
                        activeRepo = cloned,
                        workingChanges = emptyList(),
                        statusMessage = "Git Clone tamamlandı: ${cloned.metadata.slug} (${cloned.fileCount} dosya)"
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        progressLabel = "",
                        errorMessage = err.message ?: "Git Clone başarısız."
                    )
                }
            }
        }
    }

    fun selectActiveRepo(slug: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = gitService.loadRepo(slug)
            val changes = loaded?.let { gitService.computeStatus(it.metadata.slug) }.orEmpty()
            _state.update {
                it.copy(
                    activeRepo = loaded,
                    workingChanges = changes,
                    selectedDiffPath = null,
                    selectedDiffSummary = null
                )
            }
        }
    }

    fun inspectDiffForFile(relativePath: String) {
        val repo = _state.value.activeRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val diff = gitService.diffWorkingFile(repo.metadata.slug, relativePath)
            _state.update {
                it.copy(selectedDiffPath = relativePath, selectedDiffSummary = diff)
            }
        }
    }

    fun exportTranslatedStringsToActiveRepo(targetLangCode: String, xmlContent: String) {
        val repo = _state.value.activeRepo
        if (repo == null) {
            _state.update { it.copy(errorMessage = "Önce bir Git deposu klonlayın veya seçin.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val outFile = gitService.exportTranslatedStringsIntoRepo(
                    slug = repo.metadata.slug,
                    targetLangCode = targetLangCode,
                    xmlContent = xmlContent
                )
                outFile.relativeTo(repo.workingDir).path.replace('\\', '/')
            }.onSuccess { rel ->
                refreshRepos(repo.metadata.slug)
                val diff = gitService.diffWorkingFile(repo.metadata.slug, rel)
                _state.update {
                    it.copy(
                        selectedDiffPath = rel,
                        selectedDiffSummary = diff,
                        statusMessage = "Çeviriler repoya yazıldı: $rel"
                    )
                }
            }.onFailure { err ->
                _state.update { it.copy(errorMessage = "Repo yazma hatası: ${err.message}") }
            }
        }
    }

    fun commitLocalChanges(message: String, author: String) {
        val repo = _state.value.activeRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                gitService.commitLocalChanges(repo.metadata.slug, message, author)
            }.onSuccess { commit ->
                refreshRepos(repo.metadata.slug)
                _state.update {
                    it.copy(
                        selectedDiffSummary = null,
                        statusMessage = "Yerel Commit kaydedildi [${commit.commitSha}]: ${commit.message}"
                    )
                }
            }.onFailure { err ->
                _state.update { it.copy(errorMessage = "Commit hatası: ${err.message}") }
            }
        }
    }

    fun pushFileToGitHub(relativePath: String, commitMessage: String) {
        val repo = _state.value.activeRepo ?: return
        val pat = _state.value.personalAccessToken.trim()
        if (pat.isBlank()) {
            _state.update { it.copy(errorMessage = "GitHub Push için Personal Access Token (PAT) gereklidir.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isBusy = true, progressLabel = "GitHub'a push ediliyor...") }
            runCatching {
                gitService.pushFileToGitHub(
                    slug = repo.metadata.slug,
                    relativePath = relativePath,
                    commitMessage = commitMessage.ifBlank { "Update $relativePath via Apk Çeviricisi Plus" },
                    githubPatToken = pat
                )
            }.onSuccess { msg ->
                _state.update { it.copy(isBusy = false, progressLabel = "", statusMessage = msg) }
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, progressLabel = "", errorMessage = err.message) }
            }
        }
    }

    fun readRepoXmlFile(relativePath: String): String {
        val repo = _state.value.activeRepo ?: return ""
        val file = File(repo.workingDir, relativePath)
        return if (file.exists() && file.isFile) file.readText(Charsets.UTF_8) else ""
    }

    fun deleteActiveRepo(slug: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gitService.deleteRepository(slug)
            refreshRepos(null)
            _state.update { it.copy(statusMessage = "Yerel Git kopyası silindi.") }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                GitViewModel(app)
            }
        }
    }
}
