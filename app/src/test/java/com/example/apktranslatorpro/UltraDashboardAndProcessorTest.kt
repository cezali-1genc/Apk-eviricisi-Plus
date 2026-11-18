package com.example.apktranslatorpro

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.apktranslatorpro.core.apk.ApkProcessor
import com.example.apktranslatorpro.core.apk.ApkRecoveryValidator
import com.example.apktranslatorpro.core.environment.BinaryAvailabilityStatus
import com.example.apktranslatorpro.core.environment.LinuxDistributionConfig
import com.example.apktranslatorpro.core.environment.PackageManagerHealth
import com.example.apktranslatorpro.core.environment.PackageServiceSnapshot
import com.example.apktranslatorpro.core.environment.PermissionFixer
import com.example.apktranslatorpro.core.environment.TerminalSessionInfo
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.ResourceKind
import com.example.apktranslatorpro.domain.model.TranslationOrigin
import com.example.apktranslatorpro.ui.AuthenticatedAppShell
import com.example.apktranslatorpro.ui.components.ApkEditorDashboard
import com.example.apktranslatorpro.ui.viewmodel.LinuxEnvUiState
import com.example.apktranslatorpro.ui.viewmodel.MainViewModel
import com.example.ui.theme.ApkTranslatorProTheme
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class UltraDashboardAndProcessorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `PermissionFixer recursively sets executable bits on proot bash zsh and configures internal ProcessBuilder env`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempRoot = File(context.cacheDir, "perm_fixer_test_root").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val fixer = PermissionFixer(context, internalRootOverride = tempRoot)
            fixer.ensureInternalDirectories()

            val prootFile = File(fixer.prootDir, "proot").apply {
                writeText("#!/bin/sh\necho proot")
                setExecutable(false, false)
            }
            val bashFile = File(fixer.usrBinDir, "bash").apply {
                writeText("#!/bin/sh\necho bash")
                setExecutable(false, false)
            }
            val zshFile = File(fixer.binDir, "zsh").apply {
                writeText("#!/bin/sh\necho zsh")
                setExecutable(false, false)
            }

            assertFalse(prootFile.canExecute())
            assertFalse(bashFile.canExecute())
            assertFalse(zshFile.canExecute())

            val report = fixer.verifyAndFixExecutables()

            assertTrue(prootFile.canExecute())
            assertTrue(bashFile.canExecute())
            assertTrue(zshFile.canExecute())
            assertTrue(report.allCoreBinariesExecutable)
            assertEquals(BinaryAvailabilityStatus.PERMISSION_FIXED, report.coreBinaries["proot"]?.status)
            assertEquals(BinaryAvailabilityStatus.PERMISSION_FIXED, report.coreBinaries["bash"]?.status)
            assertEquals(BinaryAvailabilityStatus.PERMISSION_FIXED, report.coreBinaries["zsh"]?.status)

            val pb = fixer.createConfiguredProcessBuilder(listOf("bash", "-c", "echo ok"))
            val env = pb.environment()
            assertTrue(env["HOME"]!!.startsWith(tempRoot.absolutePath))
            assertTrue(env["PREFIX"]!!.startsWith(tempRoot.absolutePath))
            assertTrue(env["PATH"]!!.contains(fixer.usrBinDir.absolutePath))
            assertTrue(env["PATH"]!!.contains(fixer.prootDir.absolutePath))
            assertEquals(zshFile.absolutePath, env["SHELL"])
            assertEquals(zshFile.absolutePath.replace("/bin/zsh", "/bin/bash").let { bashFile.absolutePath }, pb.command()[0])
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `ApkProcessor extracts via ZipInputStream reads resources arsc preserves identifiers and signs v1 v2 v3`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "apk_processor_unit_test").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val sourceApk = File(tempDir, "input.apk")
            ApkSecurityAndSigningTest().buildSyntheticApk(sourceApk)

            val processor = ApkProcessor(context)
            val extractDir = File(tempDir, "extracted")
            val extracted = processor.extractApkWithZipInputStream(sourceApk, extractDir)

            assertTrue(extracted.entryNames.contains("AndroidManifest.xml"))
            assertTrue(extracted.entryNames.contains("resources.arsc"))
            assertTrue(extracted.resources.isNotEmpty())
            assertTrue(File(extractDir, "resources.arsc").exists())

            val (reReadTable, reReadEntries) = processor.readResourcesArsc(File(extractDir, "resources.arsc"))
            assertEquals(extracted.resources.size, reReadEntries.size)

            val (localized, recoveryReport) = processor.localizeEntriesPlaceholderSafe(
                entries = reReadEntries
            ) { masked, _ ->
                if (masked.contains("Hello")) "Merhaba $masked" else "Çevrildi: $masked"
            }

            assertTrue(recoveryReport.isHealthy)
            assertEquals(reReadEntries.map { it.id }, localized.map { it.id })
            assertEquals(reReadEntries.map { it.key }, localized.map { it.key })

            val signedOut = File(tempDir, "signed_out.apk")
            val processOutput = processor.rebuildAndSignPreparedApk(
                sourceApk = sourceApk,
                arscTable = reReadTable,
                localizedEntries = localized,
                targetLanguage = LanguageOption.TURKISH,
                minSdkVersion = 26,
                signedOutputApk = signedOut
            )

            assertTrue(processOutput.signedApk.exists())
            assertTrue(processOutput.verifyReport.isVerified)
            assertTrue(processOutput.outputSha256.isNotBlank())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `ApkRecoveryValidator detects broken format specifiers and unbalanced tags and recovers safely`() {
        val sampleItems = listOf(
            ResourceEntryItem(
                id = 0x7f010001,
                kind = ResourceKind.STRING,
                key = "welcome_user",
                originalText = "Welcome %1\$s, you have %2\$d messages!",
                translatedText = "Hoş geldin %1\$s, mesaj sayın geçersiz!", // missing %2$d
                origin = TranslationOrigin.ONLINE_API
            ),
            ResourceEntryItem(
                id = 0x7f010002,
                kind = ResourceKind.STRING,
                key = "broken_html",
                originalText = "Click <b>Here</b>",
                translatedText = "Tıklayın <b>Buraya", // unclosed <b>
                origin = TranslationOrigin.ONLINE_API
            ),
            ResourceEntryItem(
                id = 0x7f010003,
                kind = ResourceKind.STRING,
                key = "good_tr",
                originalText = "Settings",
                translatedText = "Ayarlar ve Gelişmiş Çözümler",
                origin = TranslationOrigin.ONLINE_API
            )
        )

        val (recovered, report) = ApkRecoveryValidator.validateAndAutoRecover(sampleItems)
        assertTrue(report.autoRecoveredCount >= 1)
        assertEquals("Tıklayın <b>Buraya".let { "Click <b>Here</b>" }, recovered[1].translatedText)
        assertEquals("Ayarlar ve Gelişmiş Çözümler", recovered[2].translatedText)
        assertTrue(report.turkishCharEntriesCount >= 1)
    }

    @Test
    fun `ApkEditorDashboard displays distro switches terminal stop button package status and permissions`() {
        val sampleState = LinuxEnvUiState(
            distributions = listOf(
                LinuxDistributionConfig(
                    id = "ubuntu-noble",
                    displayName = "Ubuntu 24.04 LTS",
                    releaseLabel = "PRoot ARM64",
                    architecture = "aarch64",
                    rootfsRelativePath = "ubuntu-noble",
                    defaultShell = "/bin/bash",
                    prootFlags = "-0",
                    packageManagerName = "apt",
                    isEnabled = true,
                    isRootfsPresentOnDisk = true,
                    diskSizeBytes = 4096
                )
            ),
            activeSessions = listOf(
                TerminalSessionInfo(
                    id = "sess123",
                    title = "APK PRoot Build",
                    commandPreview = "apt update",
                    shellPath = "/bin/bash",
                    startedAtMillis = 1000L,
                    isRunning = true,
                    outputLines = listOf("Hit:1 ports.ubuntu.com")
                )
            ),
            serviceSnapshot = PackageServiceSnapshot(
                health = PackageManagerHealth.READY,
                prootReady = true,
                bashReady = true,
                zshReady = true,
                detectedPackageManagers = listOf("apt"),
                detailMessageTr = "Hazır"
            )
        )

        var toggled: Pair<String, Boolean>? = null
        var stoppedSessionId: String? = null
        var permFixClicked = false

        composeTestRule.setContent {
            ApkTranslatorProTheme(themeMode = AppThemeMode.DARK) {
                ApkEditorDashboard(
                    envState = sampleState,
                    hasSafOutputFolder = false,
                    outputFolderLabel = "Varsayılan",
                    onToggleDistro = { id, enabled -> toggled = id to enabled },
                    onUpdateDistroConfig = { _, _, _ -> },
                    onRunPermissionFixer = { permFixClicked = true },
                    onStartTerminalSession = { _, _, _ -> },
                    onStopTerminalSession = { id -> stoppedSessionId = id },
                    onClearFinishedSessions = {},
                    onRequestSafOutputFolder = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("package_management_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fix_permissions_button").performClick()
        assertTrue(permFixClicked)

        composeTestRule.onNodeWithTag("apk_editor_dashboard_list")
            .performScrollToNode(hasTestTag("distro_switch_ubuntu-noble"))
        composeTestRule.onNodeWithTag("distro_switch_ubuntu-noble").performScrollTo().performClick()
        assertEquals("ubuntu-noble" to false, toggled)

        composeTestRule.onNodeWithTag("apk_editor_dashboard_list")
            .performScrollToNode(hasTestTag("stop_session_sess123"))
        composeTestRule.onNodeWithTag("stop_session_sess123").performScrollTo().performClick()
        assertEquals("sess123", stoppedSessionId)

        composeTestRule.onNodeWithTag("apk_editor_dashboard_list")
            .performScrollToNode(hasTestTag("phone_owner_permissions_card"))
        composeTestRule.onNodeWithTag("phone_owner_permissions_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("request_notification_permission_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "tr-w412dp-h915dp")
    fun `BottomNavigationBar displays all six required section labels including Calistir`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)

        composeTestRule.setContent {
            ApkTranslatorProTheme(themeMode = AppThemeMode.DARK) {
                AuthenticatedAppShell(
                    viewModel = vm,
                    cloudViewModel = null,
                    isGuestMode = true,
                    userDisplayName = "CEZALI GENÇ (SG)",
                    userEmail = null
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Çalıştır").assertIsDisplayed()
        composeTestRule.onNodeWithText("Araçlar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Önizleme").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kurallar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Modeller").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ayarlar").assertIsDisplayed()
    }
}
