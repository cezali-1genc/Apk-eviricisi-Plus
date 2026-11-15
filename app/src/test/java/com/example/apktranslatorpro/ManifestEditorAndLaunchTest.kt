package com.example.apktranslatorpro

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.apktranslatorpro.core.apk.ApktoolService
import com.example.apktranslatorpro.core.apk.AxmlParser
import com.example.apktranslatorpro.core.apk.DecompiledWorkspace
import com.example.apktranslatorpro.core.apk.LittleEndianWriter
import com.example.apktranslatorpro.core.apk.ManifestXmlDocument
import com.example.apktranslatorpro.core.apk.ResStringPool
import com.example.apktranslatorpro.ui.screens.ManifestEditorScreen
import com.example.ui.theme.ApkTranslatorProTheme
import com.example.ui.theme.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class ManifestEditorAndLaunchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `axml binary decoder parses package permissions activities and edits standalone xml cleanly`() {
        val axmlBytes = buildSyntheticBinaryManifest()
        val decodedXml = AxmlParser.decodeToXmlString(axmlBytes)
        assertTrue(decodedXml.contains("com.demo.sampleapp"))
        assertTrue(decodedXml.contains("android.permission.INTERNET"))
        assertTrue(decodedXml.contains("com.demo.sampleapp.MainActivity"))

        var doc = ManifestXmlDocument.parseFromAxmlBytes(axmlBytes)
        assertTrue(doc.parsedFromBinaryAxml)
        assertEquals("com.demo.sampleapp", doc.packageName)
        assertEquals("1.2.0", doc.versionName)
        assertEquals("12", doc.versionCode)
        assertEquals(listOf("android.permission.INTERNET", "android.permission.VIBRATE"), doc.permissions)
        assertEquals(2, doc.activities.size)
        assertEquals("com.demo.sampleapp.MainActivity", doc.activities[0].name)
        assertEquals(true, doc.activities[0].exported)
        assertEquals("com.demo.sampleapp.SettingsActivity", doc.activities[1].name)
        assertEquals(false, doc.activities[1].exported)

        // Modify package name
        doc = ManifestXmlDocument.updatePackageName(doc, "com.demo.renamed")
        assertEquals("com.demo.renamed", doc.packageName)
        assertTrue(doc.rawXml.contains("""package="com.demo.renamed""""))

        // Add, update, and remove permissions
        doc = ManifestXmlDocument.addPermission(doc, "android.permission.CAMERA")
        assertTrue(doc.permissions.contains("android.permission.CAMERA"))
        doc = ManifestXmlDocument.updatePermission(doc, 1, "android.permission.ACCESS_NETWORK_STATE")
        assertTrue(doc.permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(doc.permissions.contains("android.permission.VIBRATE"))
        doc = ManifestXmlDocument.removePermission(doc, 0)
        assertFalse(doc.permissions.contains("android.permission.INTERNET"))

        // Modify, add, and remove activities
        doc = ManifestXmlDocument.updateActivity(doc, 1, "com.demo.renamed.PrefsActivity", true)
        assertEquals("com.demo.renamed.PrefsActivity", doc.activities[1].name)
        assertEquals(true, doc.activities[1].exported)
        doc = ManifestXmlDocument.addActivity(doc, "com.demo.renamed.AboutActivity", false)
        assertEquals(3, doc.activities.size)
        doc = ManifestXmlDocument.removeActivity(doc, 0)
        assertEquals(2, doc.activities.size)

        // Ensure standalone XML re-parses cleanly with all mutations intact
        val reparsed = ManifestXmlDocument.parseFromXmlText(doc.rawXml, parsedFromBinaryAxml = true)
        assertEquals("com.demo.renamed", reparsed.packageName)
        assertEquals(
            listOf("android.permission.ACCESS_NETWORK_STATE", "android.permission.CAMERA"),
            reparsed.permissions
        )
        assertEquals(
            listOf("com.demo.renamed.PrefsActivity", "com.demo.renamed.AboutActivity"),
            reparsed.activities.map { it.name }
        )
    }

    @Test
    fun `apktool decompile preserves binary AndroidManifest axml and decoded xml`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apkFile = File(context.cacheDir, "synthetic_manifest_test.apk")
        val workDir = File(context.cacheDir, "synthetic_manifest_work").apply { deleteRecursively() }
        try {
            val arscBytes = ArscAndApkExtractionTest.buildSyntheticArscTable().toByteArray()
            val axmlBytes = buildSyntheticBinaryManifest()
            ZipOutputStream(ByteArrayOutputStream().also { baos ->
                ZipOutputStream(baos).use { zos ->
                    zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                    zos.write(axmlBytes)
                    zos.closeEntry()
                    zos.putNextEntry(ZipEntry("resources.arsc"))
                    zos.write(arscBytes)
                    zos.closeEntry()
                }
                apkFile.writeBytes(baos.toByteArray())
            }).close()

            val service = ApktoolService(context)
            val workspace = service.decompileApkFile(apkFile, workDir)

            assertNotNull(workspace.binaryManifestFile)
            assertTrue(workspace.binaryManifestFile!!.exists())
            assertTrue(workspace.manifestFile.exists())

            val loaded = ManifestXmlDocument.loadFromWorkspace(workspace)
            assertEquals("com.demo.sampleapp", loaded.packageName)
            assertTrue(loaded.permissions.contains("android.permission.INTERNET"))
        } finally {
            apkFile.delete()
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `ManifestEditorScreen displays AXML fields edits package permissions activities and shows Export Manifest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "manifest_ui_test").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val decodedDir = File(workDir, "decompiled").apply { mkdirs() }
            val originalsDir = File(workDir, "originals").apply { mkdirs() }
            val axmlBytes = buildSyntheticBinaryManifest()
            val axmlFile = File(decodedDir, "AndroidManifest.axml").apply { writeBytes(axmlBytes) }
            val manifestFile = File(decodedDir, "AndroidManifest.xml").apply {
                writeText(AxmlParser.decodeToXmlString(axmlBytes), Charsets.UTF_8)
            }
            val workspace = DecompiledWorkspace(
                workDir = decodedDir,
                originalsDir = originalsDir,
                apkCopyFile = File(workDir, "base.apk"),
                sha256Hex = "abc123",
                manifestFile = manifestFile,
                arscFile = File(decodedDir, "resources.arsc"),
                resXmlFiles = emptyList(),
                smaliFiles = emptyList(),
                dexSummaries = emptyList(),
                allEntries = listOf("AndroidManifest.xml"),
                binaryManifestFile = axmlFile
            )

            var savedText: String? = null
            var exportedPair: Pair<Uri, String>? = null

            composeTestRule.setContent {
                ApkTranslatorProTheme(themeMode = AppThemeMode.DARK) {
                    ManifestEditorScreen(
                        workspace = workspace,
                        hasApkSelected = true,
                        isBusy = false,
                        onDecompileApkClick = {},
                        onSaveManifestToWorkspace = { file, content ->
                            file.writeText(content, Charsets.UTF_8)
                            savedText = content
                        },
                        onExportManifestToUri = { uri, content ->
                            exportedPair = uri to content
                        },
                        onBackClick = {}
                    )
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("manifest_editor_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("export_manifest_button").assertIsDisplayed()
            composeTestRule.onNodeWithText("Export Manifest").assertIsDisplayed()

            // Edit package name
            composeTestRule.onNodeWithTag("manifest_package_input")
                .performTextReplacement("com.demo.edited")
            composeTestRule.onNodeWithTag("manifest_apply_package_button").performClick()
            composeTestRule.waitForIdle()

            // Add a permission
            composeTestRule.onNodeWithTag("manifest_add_permission_input")
                .performTextInput("android.permission.CAMERA")
            composeTestRule.onNodeWithTag("manifest_add_permission_button").performClick()
            composeTestRule.waitForIdle()

            // Save to cache and verify file output reflects changes
            composeTestRule.onNodeWithTag("save_manifest_button").performClick()
            composeTestRule.waitForIdle()

            assertNotNull(savedText)
            assertTrue(savedText!!.contains("""package="com.demo.edited""""))
            assertTrue(savedText!!.contains("android.permission.CAMERA"))
            assertTrue(savedText!!.contains("com.demo.sampleapp.MainActivity"))
            assertEquals(null, exportedPair)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun buildSyntheticBinaryManifest(): ByteArray {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val poolStrings = mutableListOf(
            "android",                                 // 0
            androidNs,                                 // 1
            "manifest",                                // 2
            "package",                                 // 3
            "versionCode",                             // 4
            "versionName",                             // 5
            "uses-sdk",                                // 6
            "minSdkVersion",                           // 7
            "targetSdkVersion",                        // 8
            "uses-permission",                         // 9
            "name",                                    // 10
            "application",                             // 11
            "allowBackup",                             // 12
            "activity",                                // 13
            "exported",                                // 14
            "com.demo.sampleapp",                      // 15
            "1.2.0",                                   // 16
            "android.permission.INTERNET",             // 17
            "android.permission.VIBRATE",              // 18
            "com.demo.sampleapp.MainActivity",         // 19
            "com.demo.sampleapp.SettingsActivity"      // 20
        )
        val pool = ResStringPool(
            strings = poolStrings,
            styles = mutableListOf(),
            flags = ResStringPool.UTF8_FLAG
        )
        val poolBytes = pool.toByteArray()

        val body = LittleEndianWriter(1024)

        // Start namespace: android
        writeStartNamespace(body, prefixIdx = 0, uriIdx = 1)

        // <manifest package="com.demo.sampleapp" android:versionCode="12" android:versionName="1.2.0">
        writeStartTag(
            body,
            nameIdx = 2,
            attrs = listOf(
                Attr(-1, 3, 15, 0x03, 15),   // package
                Attr(1, 4, -1, 0x10, 12),    // android:versionCode
                Attr(1, 5, 16, 0x03, 16)     // android:versionName
            )
        )

        // <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36" />
        writeStartTag(
            body,
            nameIdx = 6,
            attrs = listOf(
                Attr(1, 7, -1, 0x10, 24),
                Attr(1, 8, -1, 0x10, 36)
            )
        )
        writeEndTag(body, nameIdx = 6)

        // <uses-permission android:name="android.permission.INTERNET" />
        writeStartTag(body, nameIdx = 9, attrs = listOf(Attr(1, 10, 17, 0x03, 17)))
        writeEndTag(body, nameIdx = 9)

        // <uses-permission android:name="android.permission.VIBRATE" />
        writeStartTag(body, nameIdx = 9, attrs = listOf(Attr(1, 10, 18, 0x03, 18)))
        writeEndTag(body, nameIdx = 9)

        // <application android:allowBackup="true">
        writeStartTag(body, nameIdx = 11, attrs = listOf(Attr(1, 12, -1, 0x12, -1)))

        // <activity android:name="com.demo.sampleapp.MainActivity" android:exported="true" />
        writeStartTag(
            body,
            nameIdx = 13,
            attrs = listOf(
                Attr(1, 10, 19, 0x03, 19),
                Attr(1, 14, -1, 0x12, -1)
            )
        )
        writeEndTag(body, nameIdx = 13)

        // <activity android:name="com.demo.sampleapp.SettingsActivity" android:exported="false" />
        writeStartTag(
            body,
            nameIdx = 13,
            attrs = listOf(
                Attr(1, 10, 20, 0x03, 20),
                Attr(1, 14, -1, 0x12, 0)
            )
        )
        writeEndTag(body, nameIdx = 13)

        writeEndTag(body, nameIdx = 11) // </application>
        writeEndTag(body, nameIdx = 2)  // </manifest>
        writeEndNamespace(body, prefixIdx = 0, uriIdx = 1)

        val bodyBytes = body.toByteArray()
        val totalSize = 8 + poolBytes.size + bodyBytes.size
        val root = LittleEndianWriter(totalSize)
        root.writeUInt16(0x0003) // CHUNK_AXML_FILE
        root.writeUInt16(8)
        root.writeInt32(totalSize)
        root.writeBytes(poolBytes)
        root.writeBytes(bodyBytes)
        return root.toByteArray()
    }

    private data class Attr(
        val nsIdx: Int,
        val nameIdx: Int,
        val rawValueIdx: Int,
        val valueType: Int,
        val valueData: Int
    )

    private fun writeStartNamespace(out: LittleEndianWriter, prefixIdx: Int, uriIdx: Int) {
        out.writeUInt16(0x0100) // CHUNK_START_NAMESPACE
        out.writeUInt16(16)
        out.writeInt32(24)
        out.writeInt32(1) // line
        out.writeInt32(-1) // comment
        out.writeInt32(prefixIdx)
        out.writeInt32(uriIdx)
    }

    private fun writeEndNamespace(out: LittleEndianWriter, prefixIdx: Int, uriIdx: Int) {
        out.writeUInt16(0x0101) // CHUNK_END_NAMESPACE
        out.writeUInt16(16)
        out.writeInt32(24)
        out.writeInt32(1)
        out.writeInt32(-1)
        out.writeInt32(prefixIdx)
        out.writeInt32(uriIdx)
    }

    private fun writeStartTag(out: LittleEndianWriter, nameIdx: Int, attrs: List<Attr>) {
        val chunkSize = 36 + attrs.size * 20
        out.writeUInt16(0x0102) // CHUNK_START_TAG
        out.writeUInt16(16)
        out.writeInt32(chunkSize)
        out.writeInt32(1) // line
        out.writeInt32(-1) // comment
        out.writeInt32(-1) // ns
        out.writeInt32(nameIdx)
        out.writeUInt16(0x0014) // attrStart
        out.writeUInt16(0x0014) // attrSize
        out.writeUInt16(attrs.size)
        out.writeUInt16(0) // idIndex
        out.writeUInt16(0) // classIndex
        out.writeUInt16(0) // styleIndex
        for (a in attrs) {
            out.writeInt32(a.nsIdx)
            out.writeInt32(a.nameIdx)
            out.writeInt32(a.rawValueIdx)
            out.writeUInt16(8) // valueSize
            out.writeUInt8(0) // res0
            out.writeUInt8(a.valueType)
            out.writeInt32(a.valueData)
        }
    }

    private fun writeEndTag(out: LittleEndianWriter, nameIdx: Int) {
        out.writeUInt16(0x0103) // CHUNK_END_TAG
        out.writeUInt16(16)
        out.writeInt32(24)
        out.writeInt32(1)
        out.writeInt32(-1)
        out.writeInt32(-1) // ns
        out.writeInt32(nameIdx)
    }
}
