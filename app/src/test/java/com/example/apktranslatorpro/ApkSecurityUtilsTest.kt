package com.example.apktranslatorpro

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.apktranslatorpro.core.apk.ApkToolkitInspector
import com.example.apktranslatorpro.core.apk.ArscTable
import com.example.apktranslatorpro.core.security.ApkSecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ApkSecurityUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `calculateSha256 computes consistent lowercase hex digest without modifying file`() {
        val sampleFile = tempFolder.newFile("sample.apk")
        val payload = "ApkCeviricisiPlus-SHA256-TestPayload".toByteArray()
        sampleFile.writeBytes(payload)
        val beforeModified = sampleFile.lastModified()

        val utils = ApkSecurityUtils()
        val fileHash = utils.calculateSha256(sampleFile)
        val bytesHash = utils.calculateSha256(payload)
        val streamHash = utils.calculateSha256(ByteArrayInputStream(payload))

        assertEquals(64, fileHash.length)
        assertTrue(fileHash.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(bytesHash, fileHash)
        assertEquals(streamHash, fileHash)
        assertEquals(beforeModified, sampleFile.lastModified())
    }

    @Test
    fun `verifySafApkExistsReadOnly validates local file uri and detects tampering`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apkFile = tempFolder.newFile("saf_sample.apk")
        apkFile.writeBytes(ByteArray(256) { 0x42 })

        val uri = Uri.fromFile(apkFile)
        val utils = ApkSecurityUtils()
        val initialCheck = utils.verifySafApkExistsReadOnly(context, uri)

        assertTrue(initialCheck.exists)
        assertTrue(initialCheck.isReadable)
        assertEquals(256L, initialCheck.sizeBytes)
        assertEquals(64, initialCheck.sha256Hex.length)

        val unchanged = utils.verifyApkIntegrity(context, uri, initialCheck.sha256Hex)
        assertTrue(unchanged.isUnchanged)

        apkFile.appendBytes(byteArrayOf(0x01))
        val tampered = utils.verifyApkIntegrity(context, uri, initialCheck.sha256Hex)
        assertFalse(tampered.isUnchanged)
    }

    @Test
    fun `apk toolkit inspector reports dex abis res assets and zipalign stored arsc`() {
        val apkFile = tempFolder.newFile("toolkit_probe.apk")
        val arscBytes = ArscAndApkExtractionTest.buildSyntheticArscTable().toByteArray()
        FileOutputStream(apkFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val crc = CRC32().apply { update(arscBytes) }.value
                zos.putNextEntry(ZipEntry("resources.arsc").apply {
                    method = ZipEntry.STORED
                    size = arscBytes.size.toLong()
                    compressedSize = arscBytes.size.toLong()
                    this.crc = crc
                })
                zos.write(arscBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(byteArrayOf(1, 2, 3, 4))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("lib/arm64-v8a/libnative.so"))
                zos.write(byteArrayOf(5, 6, 7))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("assets/config.json"))
                zos.write("{\"ok\":true}".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("res/layout/activity_main.xml"))
                zos.write(byteArrayOf(8, 9))
                zos.closeEntry()
            }
        }

        val shaHex = ApkSecurityUtils.calculateSha256(apkFile)
        val manifestInfo = com.example.apktranslatorpro.core.apk.ManifestInfo(
            packageName = "com.example.probe",
            versionName = "1.0",
            versionCode = 1L,
            minSdkVersion = 26,
            targetSdkVersion = 34,
            appLabelRaw = "Probe App",
            appLabelResId = null,
            supportsRtl = true,
            usesPermissions = listOf("android.permission.CAMERA", "android.permission.INTERNET"),
            xmlPreview = "<manifest/>",
            activities = listOf("com.example.MainActivity"),
            isDebuggable = true
        )

        val report = ApkToolkitInspector.inspectFullApk(apkFile, manifestInfo, shaHex)
        assertTrue(report.resourcesArscStored)
        assertTrue(report.isDebuggable)
        assertEquals(listOf("classes.dex"), report.dexFiles)
        assertEquals(listOf("libnative.so"), report.nativeAbis["arm64-v8a"])
        assertEquals(1, report.assetCount)
        assertEquals(1, report.resEntryCount)
        assertEquals(listOf("android.permission.CAMERA"), report.dangerousPermissions)
        assertEquals(shaHex, report.sha256Hex)
        assertEquals(64, report.sha256Hex.length)
        assertEquals(40, report.sha1Hex.length)
        assertEquals(32, report.md5Hex.length)
    }
}
