package com.example.apktranslatorpro.core.apk

import com.example.apktranslatorpro.core.security.SecurityGuards
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class AnalyzedApkBundle(
    val manifestInfo: ManifestInfo,
    val arscTable: ArscTable,
    val extractedResources: List<ResourceEntryItem>,
    val hasExistingSignature: Boolean,
    val securityWarnings: List<String>,
    val entryCount: Int,
    val binaryXmlFiles: List<String>
)

private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        bytesWritten++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        bytesWritten += len.toLong()
    }
}

class ApkPackager {

    companion object {
        private const val BUFFER_SIZE = 16 * 1024
        private val SIGNATURE_META_INF_REGEX = Regex("""^META-INF/(MANIFEST\.MF|.*\.(SF|RSA|DSA|EC))$""", RegexOption.IGNORE_CASE)
    }

    fun analyzeApk(apkFile: File): AnalyzedApkBundle {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            throw IllegalArgumentException("APK dosyası bulunamadı veya boş.")
        }

        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            var entryCount = 0
            var totalUncompressed = 0L
            var manifestBytes: ByteArray? = null
            var arscBytes: ByteArray? = null
            var hasSignature = false
            var hasDex = false
            var hasIntegrityHints = false
            val warnings = mutableListOf<String>()
            val binaryXmls = mutableListOf<String>()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                val safeName = SecurityGuards.validateZipEntryPath(entry.name)
                val uncompressed = entry.size.coerceAtLeast(0L)
                totalUncompressed += uncompressed
                SecurityGuards.validateZipBombLimits(
                    entryCount = entryCount,
                    entryName = safeName,
                    entryUncompressedBytes = uncompressed,
                    entryCompressedBytes = entry.compressedSize.coerceAtLeast(0L),
                    runningTotalUncompressedBytes = totalUncompressed
                )

                if (safeName.matches(SIGNATURE_META_INF_REGEX) && !safeName.equals("META-INF/MANIFEST.MF", true)) {
                    hasSignature = true
                }
                if (safeName.endsWith(".dex", ignoreCase = true)) {
                    hasDex = true
                }
                if (safeName == "AndroidManifest.xml" || (safeName.startsWith("res/") && safeName.endsWith(".xml", ignoreCase = true))) {
                    binaryXmls.add(safeName)
                }
                when (safeName) {
                    "AndroidManifest.xml" -> manifestBytes = readZipEntryBytes(zip, entry)
                    "resources.arsc" -> arscBytes = readZipEntryBytes(zip, entry)
                }
                if (safeName.contains("com/android/vending/licensing", true) ||
                    safeName.contains("play/core/integrity", true)
                ) {
                    hasIntegrityHints = true
                }
            }

            if (manifestBytes == null) {
                throw IllegalArgumentException("Geçersiz APK: AndroidManifest.xml bulunamadı.")
            }
            if (arscBytes == null) {
                throw IllegalArgumentException("Bu APK içinde resources.arsc bulunamadı (yalnızca split config veya desteklenmeyen paket olabilir).")
            }
            if (!hasDex) {
                warnings.add("APK içinde classes.dex bulunamadı (kaynak/config split APK olabilir).")
            }

            val manifestInfo = AxmlParser.parseManifest(manifestBytes)
            val arscTable = ArscTable.parse(arscBytes)
            val extracted = arscTable.extractTranslatableResources()

            if (manifestInfo.usesPermissions.any { it.contains("CHECK_LICENSE", ignoreCase = true) } || hasIntegrityHints) {
                warnings.add(
                    "Bu uygulamada Google Play Lisans veya bütünlük kontrolü tespit edildi. Yeniden imzalama sonrası bazı lisanslı özellikler çalışmayabilir."
                )
            }
            if (hasSignature) {
                warnings.add(
                    "APK yeni yerel anahtarla imzalanacaktır; orijinal geliştirici imzası korunamaz. Aynı paket adına sahip kurulu uygulama varsa önce kaldırmanız gerekebilir."
                )
            }

            return AnalyzedApkBundle(
                manifestInfo = manifestInfo,
                arscTable = arscTable,
                extractedResources = extracted,
                hasExistingSignature = hasSignature,
                securityWarnings = warnings,
                entryCount = entryCount,
                binaryXmlFiles = binaryXmls.sorted()
            )
        }
    }

    fun decodeBinaryXmlEntry(apkFile: File, entryPath: String): String {
        val safePath = SecurityGuards.validateZipEntryPath(entryPath)
        ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry(safePath)
                ?: throw IllegalArgumentException("APK içinde '$safePath' bulunamadı.")
            val bytes = readZipEntryBytes(zip, entry)
            return AxmlParser.parseManifest(bytes).xmlPreview
        }
    }

    fun repackageApkWithUpdatedArsc(
        sourceApk: File,
        unsignedOutputApk: File,
        updatedArscBytes: ByteArray,
        onProgress: (Int) -> Unit = {}
    ) {
        unsignedOutputApk.parentFile?.mkdirs()
        if (unsignedOutputApk.exists()) unsignedOutputApk.delete()

        ZipFile(sourceApk).use { zip ->
            val allEntries = zip.entries().toList()
            val totalEntries = allEntries.size.coerceAtLeast(1)

            FileOutputStream(unsignedOutputApk).use { fos ->
                val countingOut = CountingOutputStream(fos)
                BufferedOutputStream(countingOut, BUFFER_SIZE).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var processed = 0
                        var runningTotalUncompressed = 0L

                        for (entry in allEntries) {
                            processed++
                            val name = SecurityGuards.validateZipEntryPath(entry.name)
                            if (name.matches(SIGNATURE_META_INF_REGEX)) {
                                continue
                            }

                            if (name == "resources.arsc") {
                                zos.flush()
                                bos.flush()
                                writeAlignedStoredEntry(
                                    zos = zos,
                                    name = "resources.arsc",
                                    data = updatedArscBytes,
                                    alignment = 4,
                                    exactCurrentOffset = countingOut.bytesWritten
                                )
                            } else if (entry.isDirectory) {
                                val dirEntry = ZipEntry(name).apply {
                                    time = entry.time
                                }
                                zos.putNextEntry(dirEntry)
                                zos.closeEntry()
                            } else {
                                val uncompressed = entry.size.coerceAtLeast(0L)
                                runningTotalUncompressed += uncompressed
                                SecurityGuards.validateZipBombLimits(
                                    entryCount = processed,
                                    entryName = name,
                                    entryUncompressedBytes = uncompressed,
                                    entryCompressedBytes = entry.compressedSize.coerceAtLeast(0L),
                                    runningTotalUncompressedBytes = runningTotalUncompressed
                                )

                                val isStoredSo = entry.method == ZipEntry.STORED && name.endsWith(".so", ignoreCase = true)
                                val isStoredAny = entry.method == ZipEntry.STORED && entry.crc != -1L && entry.size >= 0L

                                if (isStoredSo || isStoredAny) {
                                    zos.flush()
                                    bos.flush()
                                    val alignment = if (isStoredSo) 4096 else 4
                                    val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
                                    val headerBase = countingOut.bytesWritten + 30L + nameBytes.size
                                    val padLen = ((alignment - (headerBase % alignment)) % alignment).toInt()
                                    val newEntry = ZipEntry(name).apply {
                                        method = ZipEntry.STORED
                                        size = entry.size
                                        compressedSize = entry.size
                                        crc = entry.crc
                                        time = entry.time
                                        if (padLen > 0) extra = ByteArray(padLen)
                                    }
                                    zos.putNextEntry(newEntry)
                                    zip.getInputStream(entry).use { input ->
                                        var r: Int
                                        while (input.read(buffer).also { r = it } != -1) {
                                            zos.write(buffer, 0, r)
                                        }
                                    }
                                    zos.closeEntry()
                                } else {
                                    val newEntry = ZipEntry(name).apply {
                                        method = ZipEntry.DEFLATED
                                        time = entry.time
                                    }
                                    zos.putNextEntry(newEntry)
                                    zip.getInputStream(entry).use { input ->
                                        var r: Int
                                        while (input.read(buffer).also { r = it } != -1) {
                                            zos.write(buffer, 0, r)
                                        }
                                    }
                                    zos.closeEntry()
                                }
                            }

                            if (processed % 25 == 0 || processed == totalEntries) {
                                onProgress(((processed * 100) / totalEntries).coerceIn(0, 100))
                            }
                        }
                    }
                }
            }
        }
    }

    fun resolveAppLabel(manifestInfo: ManifestInfo, arscTable: ArscTable): String {
        val resId = manifestInfo.appLabelResId
        if (resId != null) {
            val extracted = arscTable.extractTranslatableResources()
            val match = extracted.firstOrNull { it.id == resId }
            if (match != null && match.originalText.isNotBlank()) {
                return match.originalText
            }
        }
        return manifestInfo.appLabelRaw.ifBlank { manifestInfo.packageName }
    }

    private fun writeAlignedStoredEntry(
        zos: ZipOutputStream,
        name: String,
        data: ByteArray,
        alignment: Int,
        exactCurrentOffset: Long
    ) {
        val crc = CRC32().apply { update(data) }.value
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val headerBase = exactCurrentOffset + 30L + nameBytes.size
        val padLen = ((alignment - (headerBase % alignment)) % alignment).toInt()
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            this.crc = crc
            if (padLen > 0) extra = ByteArray(padLen)
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun readZipEntryBytes(zip: ZipFile, entry: ZipEntry): ByteArray {
        zip.getInputStream(entry).use { input ->
            val out = ByteArrayOutputStream(entry.size.toInt().coerceIn(256, 4 * 1024 * 1024))
            val buf = ByteArray(BUFFER_SIZE)
            var r: Int
            while (input.read(buf).also { r = it } != -1) {
                out.write(buf, 0, r)
            }
            return out.toByteArray()
        }
    }
}
