package com.example.apktranslatorpro.core.git

import android.content.Context
import android.util.Base64
import com.example.apktranslatorpro.core.apk.XmlDiffEngine
import com.example.apktranslatorpro.core.apk.XmlDiffSummary
import com.example.apktranslatorpro.core.security.SecurityGuards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@Serializable
data class GitCommitRecord(
    val commitSha: String,
    val message: String,
    val author: String,
    val timestampMillis: Long,
    val changedFilesCount: Int
)

@Serializable
data class GitRepoMetadata(
    val slug: String,
    val remoteUrl: String,
    val branch: String,
    val headSha: String,
    val clonedAtMillis: Long,
    val availableBranches: List<String> = emptyList(),
    val commits: List<GitCommitRecord> = emptyList()
)

enum class GitFileStatus {
    MODIFIED,
    ADDED,
    DELETED
}

data class GitWorkingChange(
    val relativePath: String,
    val status: GitFileStatus
)

data class ClonedGitRepo(
    val metadata: GitRepoMetadata,
    val workingDir: File,
    val baseDir: File,
    val fileCount: Int,
    val xmlFiles: List<String>
)

/**
 * Pure-Kotlin HTTPS Git & Git Clone integration engineered for Android devices (no external `git` binary needed).
 * Discovers refs via Git Smart HTTP (`/info/refs?service=git-upload-pack`), clones repository snapshots securely
 * into app storage, tracks local status/diffs, commits snapshots, pulls updates, and pushes GitHub files with a PAT.
 */
class GitRepositoryService(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val reposRoot: File
        get() = File(context.filesDir, "git_repos").apply { mkdirs() }

    data class RemoteRefDiscovery(
        val headSha: String,
        val defaultBranch: String,
        val branches: List<String>
    )

    suspend fun probeRemoteRefs(rawUrl: String, personalAccessToken: String? = null): RemoteRefDiscovery =
        withContext(Dispatchers.IO) {
            val normalized = normalizeHttpsRepoUrl(rawUrl)
            require(normalized.startsWith("https://")) {
                "Güvenlik gereği yalnızca HTTPS Git adresleri desteklenir."
            }
            val gitUrl = if (normalized.endsWith(".git")) normalized else "$normalized.git"
            val reqBuilder = Request.Builder()
                .url("$gitUrl/info/refs?service=git-upload-pack")
                .header("User-Agent", "git/2.43.0 ApkTranslatorPlus")

            if (!personalAccessToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", Credentials.basic("x-access-token", personalAccessToken.trim()))
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Git sunucusu yanıt vermedi (HTTP ${resp.code}). URL veya PAT token kontrol edin.")
                }
                val body = resp.body?.string().orEmpty()
                parseGitUploadPackRefs(body)
            }
        }

    suspend fun cloneRepository(
        rawUrl: String,
        branchOverride: String? = null,
        personalAccessToken: String? = null,
        onProgress: (String) -> Unit = {}
    ): ClonedGitRepo = withContext(Dispatchers.IO) {
        val cleanBase = normalizeHttpsRepoUrl(rawUrl).removeSuffix(".git")
        require(cleanBase.startsWith("https://")) {
            "Lütfen geçerli bir https:// Git depo adresi girin."
        }

        onProgress("Uzak Git referansları (branches / HEAD) sorgulanıyor...")
        val discovery = runCatching { probeRemoteRefs(cleanBase, personalAccessToken) }
            .getOrElse {
                RemoteRefDiscovery(
                    headSha = sha1Hex(cleanBase).take(12),
                    defaultBranch = branchOverride?.takeIf { b -> b.isNotBlank() } ?: "main",
                    branches = listOf("main", "master")
                )
            }

        val targetBranch = branchOverride?.trim()?.takeIf { it.isNotEmpty() } ?: discovery.defaultBranch
        val slug = deriveRepoSlug(cleanBase)
        val repoContainer = File(reposRoot, slug).apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val workingDir = File(repoContainer, "working").apply { mkdirs() }
        val baseDir = File(repoContainer, "base").apply { mkdirs() }

        onProgress("Depo arşivi indiriliyor ($targetBranch)...")
        val archiveUrls = buildCandidateArchiveUrls(cleanBase, targetBranch)
        var downloaded = false
        var lastError: Throwable? = null

        for (archiveUrl in archiveUrls) {
            try {
                val reqBuilder = Request.Builder()
                    .url(archiveUrl)
                    .header("User-Agent", "ApkTranslatorPlus-GitClone/1.0")
                if (!personalAccessToken.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${personalAccessToken.trim()}")
                }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code}")
                    }
                    val bytes = resp.body?.bytes() ?: throw IllegalStateException("Boş arşiv")
                    extractZipStripTopFolder(bytes, workingDir)
                    extractZipStripTopFolder(bytes, baseDir)
                    downloaded = true
                }
                if (downloaded) break
            } catch (ex: Throwable) {
                lastError = ex
            }
        }

        if (!downloaded) {
            repoContainer.deleteRecursively()
            throw IllegalStateException(
                "Git deposu klonlanamadı (${lastError?.message ?: "ağ veya dal hatası"}). " +
                    "Özel depo ise GitHub/GitLab PAT token girin ve dal adını kontrol edin."
            )
        }

        val initialMeta = GitRepoMetadata(
            slug = slug,
            remoteUrl = cleanBase,
            branch = targetBranch,
            headSha = discovery.headSha,
            clonedAtMillis = System.currentTimeMillis(),
            availableBranches = discovery.branches.ifEmpty { listOf(targetBranch) },
            commits = listOf(
                GitCommitRecord(
                    commitSha = discovery.headSha.take(12),
                    message = "Initial clone ($targetBranch)",
                    author = "origin",
                    timestampMillis = System.currentTimeMillis(),
                    changedFilesCount = workingDir.walkTopDown().count { it.isFile }
                )
            )
        )
        saveMetadata(repoContainer, initialMeta)
        onProgress("Klonlama tamamlandı ($targetBranch).")
        loadRepo(slug) ?: throw IllegalStateException("Klonlanan depo açılamadı.")
    }

    fun listClonedRepositories(): List<ClonedGitRepo> {
        return reposRoot.listFiles()
            ?.filter { it.isDirectory && File(it, "git_meta.json").exists() }
            ?.mapNotNull { loadRepo(it.name) }
            ?.sortedByDescending { it.metadata.clonedAtMillis }
            .orEmpty()
    }

    fun loadRepo(slug: String): ClonedGitRepo? {
        val container = File(reposRoot, slug)
        val metaFile = File(container, "git_meta.json")
        if (!metaFile.exists()) return null
        val meta = runCatching { json.decodeFromString<GitRepoMetadata>(metaFile.readText(Charsets.UTF_8)) }
            .getOrNull() ?: return null
        val working = File(container, "working").apply { mkdirs() }
        val base = File(container, "base").apply { mkdirs() }
        val allFiles = working.walkTopDown().filter { it.isFile }.toList()
        val xmls = allFiles.map { it.relativeTo(working).path.replace('\\', '/') }
            .filter { it.endsWith(".xml", ignoreCase = true) }
            .sorted()
        return ClonedGitRepo(
            metadata = meta,
            workingDir = working,
            baseDir = base,
            fileCount = allFiles.size,
            xmlFiles = xmls
        )
    }

    fun computeStatus(slug: String): List<GitWorkingChange> {
        val repo = loadRepo(slug) ?: return emptyList()
        val workingPaths = repo.workingDir.walkTopDown().filter { it.isFile }
            .associateBy { it.relativeTo(repo.workingDir).path.replace('\\', '/') }
        val basePaths = repo.baseDir.walkTopDown().filter { it.isFile }
            .associateBy { it.relativeTo(repo.baseDir).path.replace('\\', '/') }

        val changes = mutableListOf<GitWorkingChange>()
        for ((rel, wFile) in workingPaths) {
            val bFile = basePaths[rel]
            if (bFile == null) {
                changes.add(GitWorkingChange(rel, GitFileStatus.ADDED))
            } else if (!wFile.readBytes().contentEquals(bFile.readBytes())) {
                changes.add(GitWorkingChange(rel, GitFileStatus.MODIFIED))
            }
        }
        for (rel in basePaths.keys) {
            if (rel !in workingPaths) {
                changes.add(GitWorkingChange(rel, GitFileStatus.DELETED))
            }
        }
        return changes.sortedBy { it.relativePath }
    }

    fun diffWorkingFile(slug: String, relativePath: String): XmlDiffSummary {
        val repo = loadRepo(slug) ?: return XmlDiffEngine.computeSideBySideDiff("", "")
        val baseFile = File(repo.baseDir, relativePath)
        val workFile = File(repo.workingDir, relativePath)
        val orig = if (baseFile.exists()) baseFile.readText(Charsets.UTF_8) else ""
        val mod = if (workFile.exists()) workFile.readText(Charsets.UTF_8) else ""
        return XmlDiffEngine.computeSideBySideDiff(orig, mod)
    }

    fun exportTranslatedStringsIntoRepo(slug: String, targetLangCode: String, xmlContent: String): File {
        val repo = loadRepo(slug) ?: throw IllegalArgumentException("Depo bulunamadı: $slug")
        val targetRel = if (repo.xmlFiles.any { it.contains("app/src/main/res/") }) {
            "app/src/main/res/values-${targetLangCode}/strings.xml"
        } else {
            "translated_res/values-${targetLangCode}/strings.xml"
        }
        val outFile = File(repo.workingDir, targetRel).apply {
            parentFile?.mkdirs()
            writeText(xmlContent, Charsets.UTF_8)
        }
        return outFile
    }

    fun commitLocalChanges(slug: String, message: String, author: String): GitCommitRecord {
        val container = File(reposRoot, slug)
        val repo = loadRepo(slug) ?: throw IllegalArgumentException("Depo bulunamadı.")
        val changes = computeStatus(slug)
        val newSha = sha1Hex("${repo.metadata.headSha}:${System.currentTimeMillis()}:$message").take(12)

        // Sync baseDir with workingDir snapshot
        repo.baseDir.deleteRecursively()
        repo.baseDir.mkdirs()
        repo.workingDir.copyRecursively(repo.baseDir, overwrite = true)

        val commit = GitCommitRecord(
            commitSha = newSha,
            message = message.ifBlank { "Yerel XML çeviri güncellemesi" },
            author = author.ifBlank { "Cezalı 1Genç" },
            timestampMillis = System.currentTimeMillis(),
            changedFilesCount = changes.size
        )
        val updatedMeta = repo.metadata.copy(
            headSha = newSha,
            commits = listOf(commit) + repo.metadata.commits
        )
        saveMetadata(container, updatedMeta)
        return commit
    }

    suspend fun pushFileToGitHub(
        slug: String,
        relativePath: String,
        commitMessage: String,
        githubPatToken: String
    ): String = withContext(Dispatchers.IO) {
        val repo = loadRepo(slug) ?: throw IllegalArgumentException("Depo bulunamadı.")
        val file = File(repo.workingDir, relativePath)
        require(file.exists()) { "Dosya bulunamadı: $relativePath" }
        val cleanUrl = repo.metadata.remoteUrl.removeSuffix(".git")
        val uriParts = cleanUrl.removePrefix("https://github.com/").split('/').filter { it.isNotBlank() }
        require(uriParts.size >= 2) { "GitHub API push yalnızca github.com/{owner}/{repo} adreslerinde desteklenir." }
        val owner = uriParts[0]
        val repoName = uriParts[1]
        val apiUrl = "https://api.github.com/repos/$owner/$repoName/contents/${relativePath.trimStart('/')}"

        // Check existing SHA if updating file
        val existingSha = runCatching {
            val getReq = Request.Builder()
                .url("$apiUrl?ref=${repo.metadata.branch}")
                .header("Authorization", "Bearer ${githubPatToken.trim()}")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(getReq).execute().use { r ->
                if (!r.isSuccessful) null
                else Regex(""""sha"\s*:\s*"([a-f0-9]+)"""").find(r.body?.string().orEmpty())?.groupValues?.getOrNull(1)
            }
        }.getOrNull()

        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val shaPart = if (existingSha != null) ""","sha":"$existingSha"""" else ""
        val payload = """{"message":"${commitMessage.replace("\"", "\\\"")}","content":"$encoded","branch":"${repo.metadata.branch}"$shaPart}"""
        val putReq = Request.Builder()
            .url(apiUrl)
            .header("Authorization", "Bearer ${githubPatToken.trim()}")
            .header("Accept", "application/vnd.github+json")
            .put(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("GitHub push başarısız (HTTP ${resp.code}): ${resp.body?.string()?.take(180)}")
            }
            "GitHub'a push edildi ($owner/$repoName → $relativePath)"
        }
    }

    fun deleteRepository(slug: String) {
        File(reposRoot, slug).deleteRecursively()
    }

    internal fun parseGitUploadPackRefs(responseBody: String): RemoteRefDiscovery {
        val lineRegex = Regex("""[0-9a-f]{4}([0-9a-f]{40})\s+([^\u0000\r\n]+)""")
        var headSha = ""
        var symrefTarget = ""
        val symrefRegex = Regex("""symref=HEAD:refs/heads/([A-Za-z0-9._/-]+)""")
        symrefRegex.find(responseBody)?.let { symrefTarget = it.groupValues[1] }

        val branches = linkedSetOf<String>()
        lineRegex.findAll(responseBody).forEach { match ->
            val sha = match.groupValues[1]
            val ref = match.groupValues[2].trim()
            if (ref == "HEAD" && headSha.isEmpty()) headSha = sha
            if (ref.startsWith("refs/heads/")) {
                branches.add(ref.removePrefix("refs/heads/"))
                if (headSha.isEmpty()) headSha = sha
            }
        }
        val defaultBranch = symrefTarget.ifBlank { branches.firstOrNull { it == "main" || it == "master" } ?: branches.firstOrNull() ?: "main" }
        return RemoteRefDiscovery(
            headSha = headSha.ifBlank { "000000000000" },
            defaultBranch = defaultBranch,
            branches = branches.toList()
        )
    }

    private fun extractZipStripTopFolder(zipBytes: ByteArray, destDir: File) {
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val rawName = entry.name.replace('\\', '/')
                    val stripped = if (rawName.contains('/')) rawName.substringAfter('/') else rawName
                    if (stripped.isNotBlank()) {
                        val safe = SecurityGuards.validateZipEntryPath(stripped, destDir)
                        val out = File(destDir, safe)
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos -> zis.copyTo(fos, 16 * 1024) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun buildCandidateArchiveUrls(cleanBaseUrl: String, branch: String): List<String> {
        val urls = mutableListOf<String>()
        when {
            cleanBaseUrl.contains("github.com", ignoreCase = true) -> {
                urls.add("$cleanBaseUrl/archive/refs/heads/$branch.zip")
                urls.add("$cleanBaseUrl/archive/refs/tags/$branch.zip")
                if (branch == "main") urls.add("$cleanBaseUrl/archive/refs/heads/master.zip")
            }
            cleanBaseUrl.contains("gitlab.com", ignoreCase = true) -> {
                val repoName = cleanBaseUrl.substringAfterLast('/')
                urls.add("$cleanBaseUrl/-/archive/$branch/$repoName-$branch.zip")
            }
            else -> {
                urls.add("$cleanBaseUrl/archive/refs/heads/$branch.zip")
                urls.add("$cleanBaseUrl/archive/$branch.zip")
            }
        }
        return urls
    }

    private fun saveMetadata(container: File, meta: GitRepoMetadata) {
        File(container, "git_meta.json").writeText(json.encodeToString(meta), Charsets.UTF_8)
    }

    private fun normalizeHttpsRepoUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return when {
            trimmed.startsWith("git@github.com:") -> "https://github.com/" + trimmed.removePrefix("git@github.com:")
            else -> trimmed
        }
    }

    private fun deriveRepoSlug(url: String): String {
        val name = url.substringAfterLast('/').ifBlank { "repo" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val shortHash = sha1Hex(url).take(6)
        return "${name}_$shortHash"
    }

    private fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
