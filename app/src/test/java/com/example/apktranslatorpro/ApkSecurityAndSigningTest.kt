package com.example.apktranslatorpro

import com.example.apktranslatorpro.core.apk.ApkPackager
import com.example.apktranslatorpro.core.apk.ArscTable
import com.example.apktranslatorpro.core.apk.LittleEndianWriter
import com.example.apktranslatorpro.core.apk.ResStringPool
import com.example.apktranslatorpro.core.security.ApkIntegrityHelper
import com.example.apktranslatorpro.core.security.SecurityGuards
import com.example.apktranslatorpro.core.signing.ApkSignerEngine
import com.example.apktranslatorpro.domain.model.LanguageOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkSecurityAndSigningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test(expected = SecurityException::class)
    fun `path traversal zip entry is rejected`() {
        SecurityGuards.validateZipEntryPath("../../system/framework/evil.jar", tempFolder.root)
    }

    @Test(expected = SecurityException::class)
    fun `zip bomb excessive compression ratio is rejected`() {
        SecurityGuards.validateZipBombLimits(
            entryCount = 1,
            entryName = "assets/bomb.bin",
            entryUncompressedBytes = 200L * 1024L * 1024L,
            entryCompressedBytes = 1024L,
            runningTotalUncompressedBytes = 200L * 1024L * 1024L
        )
    }

    @Test
    fun `repackage sign and verify apk produces valid v1 v2 v3 signatures and preserves original sha256`() {
        val originalApk = tempFolder.newFile("input_sample.apk")
        buildSyntheticApk(originalApk)
        val originalShaBefore = SecurityGuards.computeSha256(originalApk)

        val packager = ApkPackager()
        val analyzed = packager.analyzeApk(originalApk)
        assertEquals("com.example.synthetic", analyzed.manifestInfo.packageName)

        val translatedItems = analyzed.extractedResources.map {
            it.copy(translatedText = "${it.originalText} [TR]")
        }
        analyzed.arscTable.applyTranslationsForTargetLocale(LanguageOption.TURKISH, translatedItems)
        val newArsc = analyzed.arscTable.toByteArray()

        val unsignedApk = tempFolder.newFile("unsigned.apk")
        packager.repackageApkWithUpdatedArsc(originalApk, unsignedApk, newArsc)

        val keystoreFile = File(tempFolder.root, "test_signer.p12")
        val signer = ApkSignerEngine(keystoreFile)
        val signedApk = tempFolder.newFile("signed.apk")
        val identity = signer.signApk(unsignedApk, signedApk, minSdkVersion = 26)

        val report = signer.verifyApk(signedApk, minSdkVersion = 26)
        assertTrue("Expected APK signature verification to succeed: ${report.errors}", report.isVerified)
        assertTrue("Expected v2 signature to verify", report.v2Verified)
        assertTrue("Expected v3 signature to verify", report.v3Verified)
        assertEquals(identity.certSha256, report.signerCertSha256)

        // Verify resources.arsc in signed APK is stored UNCOMPRESSED (required on Android 11-14+)
        ZipFile(signedApk).use { zip ->
            val arscEntry = zip.getEntry("resources.arsc")
            assertEquals(ZipEntry.STORED, arscEntry.method)
            val reparsedArsc = ArscTable.parse(zip.getInputStream(arscEntry).readBytes())
            assertTrue(reparsedArsc.extractLocales().contains("tr"))
        }

        // Verify original APK file was never modified using ApkIntegrityHelper
        val integrityReport = ApkIntegrityHelper.verifyFileUnchanged(originalApk, originalShaBefore)
        assertTrue(integrityReport.isUnchanged)
        assertEquals(originalShaBefore, integrityReport.finalSha256)
        assertFalse(signedApk.length() == 0L)
    }

    @Test
    fun `apk integrity helper detects untouched and tampered files and decodes binary xml`() {
        val sampleApk = tempFolder.newFile("integrity_test.apk")
        buildSyntheticApk(sampleApk)

        val sha = ApkIntegrityHelper.calculateSha256(sampleApk)
        assertEquals(64, sha.length)
        assertTrue(ApkIntegrityHelper.verifyFileUnchanged(sampleApk, sha).isUnchanged)

        val packager = ApkPackager()
        val analyzed = packager.analyzeApk(sampleApk)
        assertTrue(analyzed.binaryXmlFiles.contains("AndroidManifest.xml"))

        val decodedManifest = packager.decodeBinaryXmlEntry(sampleApk, "AndroidManifest.xml")
        assertTrue(decodedManifest.contains("com.example.synthetic"))

        // Tamper file and verify integrity helper flags mismatch
        sampleApk.appendBytes(byteArrayOf(0x01))
        val afterTamper = ApkIntegrityHelper.verifyFileUnchanged(sampleApk, sha)
        assertFalse(afterTamper.isUnchanged)
    }

    @Test
    fun `partial locale merge fills missing entries without dropping existing ones`() {
        val arsc = ArscAndApkExtractionTest.buildSyntheticArscTable()
        val extracted = arsc.extractTranslatableResources()

        // First apply only the first string ("app_name") for Turkish
        val firstOnly = extracted.filter { it.key == "app_name" }
            .map { it.copy(translatedText = "İlk Uygulama") }
        arsc.applyTranslationsForTargetLocale(LanguageOption.TURKISH, firstOnly)

        val pass1Reparsed = ArscTable.parse(arsc.toByteArray())
        val pass1Turkish = pass1Reparsed.extractTranslatableResources("tr")
        assertTrue(pass1Turkish.any { it.key == "app_name" && it.originalText == "İlk Uygulama" })

        // Now apply the remaining strings/arrays/plurals in a second pass and confirm existing target chunk merges cleanly
        val secondPass = extracted.filter { it.key != "app_name" }
            .map { it.copy(translatedText = "${it.originalText} [TR2]") }
        pass1Reparsed.applyTranslationsForTargetLocale(LanguageOption.TURKISH, secondPass)

        val pass2Reparsed = ArscTable.parse(pass1Reparsed.toByteArray())
        val pass2Turkish = pass2Reparsed.extractTranslatableResources("tr")
        assertTrue(pass2Turkish.any { it.key == "app_name" && it.originalText == "İlk Uygulama" })
        assertTrue(pass2Turkish.any { it.key == "greeting" && it.originalText == "Hello %1\$s [TR2]" })
        assertTrue(pass2Turkish.any { it.key == "options" && it.originalText == "Item B [TR2]" })
    }

    private fun buildSyntheticApk(targetFile: File) {
        val arscBytes = ArscAndApkExtractionTest.buildSyntheticArscTable().toByteArray()
        val manifestBytes = buildMinimalBinaryManifest("com.example.synthetic")
        val dummyDex = ByteArray(128) { 0x64 }

        FileOutputStream(targetFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                writeStored(zos, "resources.arsc", arscBytes)
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(manifestBytes)
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(dummyDex)
                zos.closeEntry()
            }
        }
    }

    private fun writeStored(zos: ZipOutputStream, name: String, data: ByteArray) {
        val crc = CRC32().apply { update(data) }.value
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            this.crc = crc
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun buildMinimalBinaryManifest(packageName: String): ByteArray {
        val pool = ResStringPool(
            strings = mutableListOf(
                "http://schemas.android.com/apk/res/android",
                "android",
                "manifest",
                "package",
                packageName,
                "versionName",
                "1.0",
                "uses-sdk",
                "minSdkVersion",
                "targetSdkVersion",
                "application"
            ),
            styles = mutableListOf(),
            flags = ResStringPool.UTF8_FLAG
        ).toByteArray()

        // StartNamespace
        val startNs = LittleEndianWriter(24).apply {
            writeUInt16(0x0100); writeUInt16(16); writeInt32(24)
            writeInt32(1); writeInt32(-1)
            writeInt32(1); writeInt32(0)
        }.toByteArray()

        // StartTag <manifest package="com.example.synthetic" versionName="1.0">
        val manifestStart = LittleEndianWriter(80).apply {
            writeUInt16(0x0102); writeUInt16(16); writeInt32(36 + 2 * 20)
            writeInt32(1); writeInt32(-1)
            writeInt32(-1); writeInt32(2) // ns=-1, name="manifest"
            writeUInt16(20); writeUInt16(20); writeUInt16(2)
            writeUInt16(0); writeUInt16(0); writeUInt16(0)
            // Attr 1: package="com.example.synthetic"
            writeInt32(-1); writeInt32(3); writeInt32(4)
            writeUInt16(8); writeUInt8(0); writeUInt8(0x03); writeInt32(4)
            // Attr 2: versionName="1.0"
            writeInt32(0); writeInt32(5); writeInt32(6)
            writeUInt16(8); writeUInt8(0); writeUInt8(0x03); writeInt32(6)
        }.toByteArray()

        // StartTag <uses-sdk minSdkVersion=26 targetSdkVersion=34>
        val sdkStart = LittleEndianWriter(80).apply {
            writeUInt16(0x0102); writeUInt16(16); writeInt32(36 + 2 * 20)
            writeInt32(2); writeInt32(-1)
            writeInt32(-1); writeInt32(7)
            writeUInt16(20); writeUInt16(20); writeUInt16(2)
            writeUInt16(0); writeUInt16(0); writeUInt16(0)
            writeInt32(0); writeInt32(8); writeInt32(-1)
            writeUInt16(8); writeUInt8(0); writeUInt8(0x10); writeInt32(26)
            writeInt32(0); writeInt32(9); writeInt32(-1)
            writeUInt16(8); writeUInt8(0); writeUInt8(0x10); writeInt32(34)
        }.toByteArray()

        val sdkEnd = LittleEndianWriter(24).apply {
            writeUInt16(0x0103); writeUInt16(16); writeInt32(24)
            writeInt32(2); writeInt32(-1); writeInt32(-1); writeInt32(7)
        }.toByteArray()

        val manifestEnd = LittleEndianWriter(24).apply {
            writeUInt16(0x0103); writeUInt16(16); writeInt32(24)
            writeInt32(3); writeInt32(-1); writeInt32(-1); writeInt32(2)
        }.toByteArray()

        val endNs = LittleEndianWriter(24).apply {
            writeUInt16(0x0101); writeUInt16(16); writeInt32(24)
            writeInt32(3); writeInt32(-1); writeInt32(1); writeInt32(0)
        }.toByteArray()

        val bodySize = pool.size + startNs.size + manifestStart.size + sdkStart.size + sdkEnd.size + manifestEnd.size + endNs.size
        val total = 8 + bodySize
        return LittleEndianWriter(total).apply {
            writeUInt16(0x0003); writeUInt16(8); writeInt32(total)
            writeBytes(pool)
            writeBytes(startNs)
            writeBytes(manifestStart)
            writeBytes(sdkStart)
            writeBytes(sdkEnd)
            writeBytes(manifestEnd)
            writeBytes(endNs)
        }.toByteArray()
    }
}
