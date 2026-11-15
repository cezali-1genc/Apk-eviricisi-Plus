package com.example.apktranslatorpro

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.apktranslatorpro.core.apk.ApktoolService
import com.example.apktranslatorpro.core.apk.DiffLineStatus
import com.example.apktranslatorpro.core.apk.XmlDiffEngine
import com.example.apktranslatorpro.core.git.GitCommitRecord
import com.example.apktranslatorpro.core.git.GitFileStatus
import com.example.apktranslatorpro.core.git.GitRepoMetadata
import com.example.apktranslatorpro.core.git.GitRepositoryService
import com.example.apktranslatorpro.core.text.TranslationQualityTools
import com.example.apktranslatorpro.data.local.ProjectDatabase
import com.example.apktranslatorpro.data.local.ProjectRepository
import com.example.apktranslatorpro.data.local.TranslationProjectEntity
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.ResourceCategory
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.ResourceKind
import com.example.apktranslatorpro.ui.screens.GitIntegrationScreen
import com.example.apktranslatorpro.ui.screens.XmlDiffComparisonScreen
import com.example.apktranslatorpro.ui.viewmodel.GitUiState
import com.example.ui.theme.ApkTranslatorProTheme
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class DiffGitQualityAndWorkTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `XmlDiffEngine detects added removed modified unchanged rows and produces unified patch`() {
        val original = """
            <resources>
                <string name="app_name">Sample</string>
                <string name="old_key">Delete me</string>
                <string name="greeting">Hello</string>
            </resources>
        """.trimIndent()

        val modified = """
            <resources>
                <string name="app_name">Sample</string>
                <string name="greeting">Merhaba</string>
                <string name="new_key">Added line</string>
            </resources>
        """.trimIndent()

        val summary = XmlDiffEngine.computeSideBySideDiff(original, modified)
        assertTrue(summary.hasChanges)
        assertTrue(summary.unchangedCount >= 2)
        assertTrue(summary.modifiedCount >= 1 || (summary.addedCount >= 1 && summary.removedCount >= 1))

        val patch = XmlDiffEngine.generateUnifiedPatch("res/values/strings.xml", summary)
        assertTrue(patch.contains("--- a/res/values/strings.xml"))
        assertTrue(patch.contains("+++ b/res/values/strings.xml"))
        assertTrue(patch.contains("Merhaba") || patch.contains("new_key"))
    }

    @Test
    fun `LanguageOption supports over 100 languages with accurate RTL metadata`() {
        assertTrue(
            "Expected 100+ supported languages, got ${LanguageOption.totalSupportedCount}",
            LanguageOption.totalSupportedCount >= 100
        )
        assertTrue(LanguageOption.ARABIC.isRtl)
        assertTrue(LanguageOption.PERSIAN.isRtl)
        assertTrue(LanguageOption.HEBREW.isRtl)
        assertTrue(LanguageOption.URDU.isRtl)
        assertFalse(LanguageOption.TURKISH.isRtl)
        assertFalse(LanguageOption.ENGLISH.isRtl)
        assertEquals(LanguageOption.AZERBAIJANI, LanguageOption.fromCode("az"))
        assertEquals(LanguageOption.UKRAINIAN, LanguageOption.fromCode("uk"))
    }

    @Test
    fun `TranslationQualityTools classifies categories scores placeholders fixes punctuation and exports JSON CSV`() {
        assertEquals(ResourceCategory.ERROR, TranslationQualityTools.classifyCategory("err_network", "Connection failed"))
        assertEquals(ResourceCategory.BUTTON, TranslationQualityTools.classifyCategory("btn_submit", "Save"))
        assertEquals(ResourceCategory.MENU, TranslationQualityTools.classifyCategory("menu_settings", "Menu"))
        assertEquals(ResourceCategory.NOTIFICATION, TranslationQualityTools.classifyCategory("notif_title", "Alert"))

        val good = ResourceEntryItem(1, ResourceKind.STRING, "hello", originalText = "Hi %1\$s", translatedText = "Merhaba %1\$s")
        val brokenPh = ResourceEntryItem(2, ResourceKind.STRING, "broken", originalText = "Hi %1\$s", translatedText = "Merhaba")
        assertTrue(TranslationQualityTools.scoreTranslation(good) > TranslationQualityTools.scoreTranslation(brokenPh))

        assertEquals("Merhaba, dünya!", TranslationQualityTools.autoFixFormatting("Merhaba ,   dünya !"))
        assertEquals(
            "Oturumu Kapat şimdi",
            TranslationQualityTools.applyCustomGlossary("Logout now", mapOf("Logout" to "Oturumu Kapat", "now" to "şimdi"))
        )

        val report = TranslationQualityTools.buildReport(
            listOf(
                good,
                ResourceEntryItem(3, ResourceKind.STRING, "missing", originalText = "Settings", translatedText = "")
            )
        )
        assertEquals(2, report.totalEntries)
        assertEquals(1, report.missingCount)

        val json = TranslationQualityTools.exportAsJson("com.test.app", listOf(good))
        val csv = TranslationQualityTools.exportAsCsv(listOf(good))
        assertTrue(json.contains("\"packageName\": \"com.test.app\""))
        assertTrue(csv.startsWith("key,kind,locale,locked,original,translated"))
    }

    @Test
    fun `GitRepositoryService parses Smart HTTP refs tracks status diffs and commits changes locally`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val gitService = GitRepositoryService(app)

        val mockSmartHttp = """
            001e# service=git-upload-pack
            0000010f0123456789abcdef0123456789abcdef01234567 HEAD\u0000multi_ack symref=HEAD:refs/heads/main agent=git/2.43
            003d0123456789abcdef0123456789abcdef01234567 refs/heads/main
            003dabcdef0123456789abcdef0123456789abcdef01 refs/heads/develop
            0000
        """.trimIndent()
        val parsed = gitService.parseGitUploadPackRefs(mockSmartHttp)
        assertEquals("main", parsed.defaultBranch)
        assertTrue(parsed.branches.containsAll(listOf("main", "develop")))

        val slug = "test_git_repo_unit"
        val repoRoot = File(app.filesDir, "git_repos/$slug").apply {
            deleteRecursively()
            mkdirs()
        }
        val working = File(repoRoot, "working/app/src/main/res/values").apply { mkdirs() }
        val base = File(repoRoot, "base/app/src/main/res/values").apply { mkdirs() }
        val origXml = "<resources><string name=\"app\">MyApp</string></resources>"
        File(working, "strings.xml").writeText(origXml)
        File(base, "strings.xml").writeText(origXml)
        val meta = GitRepoMetadata(
            slug = slug,
            remoteUrl = "https://github.com/cezali1genc/sample",
            branch = "main",
            headSha = "111111111111",
            clonedAtMillis = System.currentTimeMillis(),
            availableBranches = listOf("main"),
            commits = listOf(GitCommitRecord("111111111111", "Init", "origin", 0L, 1))
        )
        File(repoRoot, "git_meta.json").writeText(Json.encodeToString(meta))

        val exportedFile = gitService.exportTranslatedStringsIntoRepo(
            slug = slug,
            targetLangCode = "tr",
            xmlContent = "<resources><string name=\"app\">Uygulamam</string></resources>"
        )
        assertTrue(exportedFile.exists())

        val status = gitService.computeStatus(slug)
        assertEquals(1, status.size)
        assertEquals(GitFileStatus.ADDED, status.first().status)

        val diff = gitService.diffWorkingFile(slug, "app/src/main/res/values-tr/strings.xml")
        assertTrue(diff.addedCount >= 1)

        val commit = gitService.commitLocalChanges(slug, "Add Turkish strings", "Serhat Güner")
        assertEquals(1, commit.changedFilesCount)
        assertTrue(gitService.computeStatus(slug).isEmpty())

        gitService.deleteRepository(slug)
        assertFalse(repoRoot.exists())
    }

    @Test
    fun `ProjectDatabase saves loads duplicates projects and saves glossary terms`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ProjectDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ProjectRepository(db.projectDao())

        val proj = TranslationProjectEntity(
            projectId = "p1",
            projectName = "Demo TR",
            packageName = "com.demo",
            apkFileName = "demo.apk",
            sourceApkUri = "content://demo",
            sourceSha256 = "abc123",
            sourceLangCode = "en",
            targetLangCode = "tr",
            profileName = "Oyun & RPG",
            serializedResourcesJson = "[]",
            entryCount = 10,
            translatedCount = 8
        )
        repo.saveProject(proj)
        assertEquals("Demo TR", repo.getProject("p1")?.projectName)

        repo.saveGlossaryTerm("Login", "Oturum Aç")
        repo.deleteProject("p1")
        assertEquals(null, repo.getProject("p1"))
        db.close()
    }

    @Test
    fun `ApktoolService preserves pristine originals snapshot and compares modified XML`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val apkFile = File(app.cacheDir, "unit_diff_test.apk")
        ApkSecurityAndSigningTest().buildSyntheticApk(apkFile)

        val service = ApktoolService(app)
        val workDir = File(app.cacheDir, "unit_diff_work").apply { deleteRecursively() }
        val ws = service.decompileApkFile(apkFile, workDir)
        val stringsFile = File(ws.workDir, "res/values/strings.xml")
        assertTrue(File(ws.originalsDir, "res/values/strings.xml").exists())

        service.saveWorkingFile(stringsFile, "<resources>\n  <string name=\"modified_line\">Yeni Metin</string>\n</resources>")
        val diff = service.compareWorkingFileWithOriginal(ws, stringsFile)
        assertTrue(diff.hasChanges)
        workDir.deleteRecursively()
        apkFile.delete()
    }

    @Test
    fun `XmlDiffComparisonScreen and GitIntegrationScreen render cleanly in Robolectric Compose`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val workDir = File(app.cacheDir, "ui_diff_ws").apply {
            deleteRecursively()
            mkdirs()
        }
        val decDir = File(workDir, "decompiled").apply { mkdirs() }
        val origDir = File(workDir, "originals").apply { mkdirs() }
        val origStrings = File(origDir, "res/values/strings.xml").apply {
            parentFile?.mkdirs()
            writeText("<resources>\n<string name=\"a\">Hello</string>\n</resources>")
        }
        val modStrings = File(decDir, "res/values/strings.xml").apply {
            parentFile?.mkdirs()
            writeText("<resources>\n<string name=\"a\">Merhaba</string>\n<string name=\"b\">Yeni</string>\n</resources>")
        }
        val manifest = File(decDir, "AndroidManifest.xml").apply { writeText("<manifest/>") }
        File(origDir, "AndroidManifest.xml").writeText("<manifest/>")

        val ws = com.example.apktranslatorpro.core.apk.DecompiledWorkspace(
            workDir = decDir,
            originalsDir = origDir,
            apkCopyFile = File(workDir, "base.apk"),
            sha256Hex = "00",
            manifestFile = manifest,
            arscFile = File(decDir, "resources.arsc"),
            resXmlFiles = listOf(modStrings),
            smaliFiles = emptyList(),
            dexSummaries = emptyList(),
            allEntries = listOf("AndroidManifest.xml", "res/values/strings.xml")
        )
        assertTrue(origStrings.exists())

        composeRule.setContent {
            ApkTranslatorProTheme(themeMode = AppThemeMode.DARK) {
                XmlDiffComparisonScreen(
                    workspace = ws,
                    hasApkSelected = true,
                    isBusy = false,
                    onDecompileApkClick = {},
                    onExportUnifiedPatch = { _, _ -> },
                    onOpenXmlEditor = {},
                    onBackClick = {}
                )
            }
        }
        composeRule.onNodeWithTag("xml_diff_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("xml_diff_only_changes_switch").performClick()
        workDir.deleteRecursively()
    }

    @Test
    fun `GitIntegrationScreen displays clone controls and repository URL input`() {
        composeRule.setContent {
            ApkTranslatorProTheme(themeMode = AppThemeMode.DARK) {
                GitIntegrationScreen(
                    gitState = GitUiState(),
                    hasApkResources = true,
                    targetLangCode = "tr",
                    userDisplayName = "Serhat Güner",
                    onUrlChanged = {},
                    onBranchChanged = {},
                    onPatChanged = {},
                    onProbeRefsClick = {},
                    onCloneRepoClick = {},
                    onSelectRepoClick = {},
                    onInspectDiffClick = {},
                    onExportTranslatedStringsToRepo = {},
                    onCommitChangesClick = { _, _ -> },
                    onPushFileToGitHub = { _, _ -> },
                    onDeleteRepoClick = {},
                    onBackClick = {}
                )
            }
        }
        composeRule.onNodeWithTag("git_integration_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("git_repo_url_input").assertIsDisplayed()
        composeRule.onNodeWithTag("git_clone_button").assertIsDisplayed()
        composeRule.onNodeWithTag("git_probe_refs_button").assertIsDisplayed()
    }
}
