package com.example.apktranslatorpro.core.apk

import android.content.Context
import com.example.apktranslatorpro.core.security.ApkSecurityUtils
import com.example.apktranslatorpro.core.security.SecurityGuards
import com.example.apktranslatorpro.core.signing.ApkSignerEngine
import com.example.apktranslatorpro.core.signing.SigningVerificationReport
import com.example.apktranslatorpro.core.text.PlaceholderProtector
import com.example.apktranslatorpro.core.text.TranslationProtectionConfig
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.TranslationOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class ApkExtractionResult(
    val extractedDir: File,
    val extractedEntryCount: Int,
    val entryNames: List<String>,
    val manifestBytes: ByteArray,
    val arscBytes: ByteArray,
    val arscTable: ArscTable,
    val manifestInfo: ManifestInfo,
    val resources: List<ResourceEntryItem>
)

data class ApkProcessOutput(
    val signedApk: File,
    val signerIdentity: ApkSignerEngine.SignerIdentity,
    val verifyReport: SigningVerificationReport,
    val appliedResourceCount: Int,
    val recoveryReport: RecoveryValidationReport,
    val outputSha256: String
)

/**
 * Core Coroutine-driven APK processing service.
 *
 * Handles streaming extraction using [ZipInputStream], decoding `resources.arsc` and
 * `AndroidManifest.xml`, performing placeholder-safe string localization while strictly preserving
 * resource identifiers (`id`, `kind`, `key`, `subKey`, `localeQualifier`), rebuilding `resources.arsc`,
 * repackaging with 4-byte alignment, and signing/verifying via APK Signature Scheme v1/v2/v3.
 */
class ApkProcessor(
    private val context: Context,
    private val packager: ApkPackager = ApkPackager(),
    private val signerEngine: ApkSignerEngine = ApkSignerEngine(context)
) {
    companion object {
        private const val BUFFER_SIZE = 16 * 1024
    }

    /**
     * Streams and extracts an APK using [ZipInputStream] in [Dispatchers.IO], enforcing ZIP-slip
     * and ZIP-bomb security guards, then parses `AndroidManifest.xml` and `resources.arsc`.
     */
    suspend fun extractApkWithZipInputStream(
        sourceApk: File,
        destinationDir: File,
        onProgress: (extractedCount: Int, entryName: String) -> Unit = { _, _ -> }
    ): ApkExtractionResult = withContext(Dispatchers.IO) {
        if (!sourceApk.exists() || sourceApk.length() == 0L) {
            throw IllegalArgumentException("İşlenecek APK dosyası bulunamadı veya boş.")
        }

        if (destinationDir.exists()) {
            destinationDir.deleteRecursively()
        }
        destinationDir.mkdirs()

        val canonicalRoot = destinationDir.canonicalFile
        var manifestBytes: ByteArray? = null
        var arscBytes: ByteArray? = null
        val entryNames = mutableListOf<String>()
        var entryCount = 0
        var runningTotalUncompressed = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        ZipInputStream(BufferedInputStream(FileInputStream(sourceApk), BUFFER_SIZE)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                currentCoroutineContext().ensureActive()
                entryCount++
                val safeName = SecurityGuards.validateZipEntryPath(entry.name)
                entryNames.add(safeName)

                val outFile = File(canonicalRoot, safeName).canonicalFile
                if (!outFile.path.startsWith(canonicalRoot.path + File.separator) &&
                    outFile.path != canonicalRoot.path
                ) {
                    throw SecurityException("ZIP path traversal engellendi: $safeName")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    var entryBytesRead = 0L
                    val captureStream = if (safeName == "resources.arsc" || safeName == "AndroidManifest.xml") {
                        ByteArrayOutputStream()
                    } else null

                    FileOutputStream(outFile).use { fos ->
                        BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                entryBytesRead += read.toLong()
                                runningTotalUncompressed += read.toLong()
                                SecurityGuards.validateZipBombLimits(
                                    entryCount = entryCount,
                                    entryName = safeName,
                                    entryUncompressedBytes = entryBytesRead,
                                    entryCompressedBytes = entry.compressedSize.coerceAtLeast(0L),
                                    runningTotalUncompressedBytes = runningTotalUncompressed
                                )
                                bos.write(buffer, 0, read)
                                captureStream?.write(buffer, 0, read)
                            }
                        }
                    }

                    when (safeName) {
                        "AndroidManifest.xml" -> manifestBytes = captureStream?.toByteArray()
                        "resources.arsc" -> arscBytes = captureStream?.toByteArray()
                    }
                }

                if (entryCount % 15 == 0 || safeName == "resources.arsc" || safeName == "AndroidManifest.xml") {
                    onProgress(entryCount, safeName)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val resolvedManifest = manifestBytes
            ?: throw IllegalStateException("APK geçersiz: AndroidManifest.xml bulunamadı.")
        val resolvedArsc = arscBytes
            ?: throw IllegalStateException("APK geçersiz: resources.arsc bulunamadı.")

        val manifestInfo = AxmlParser.parseManifest(resolvedManifest)
        val arscTable = ArscTable.parse(resolvedArsc)
        val resources = arscTable.extractTranslatableResources()

        ApkExtractionResult(
            extractedDir = destinationDir,
            extractedEntryCount = entryCount,
            entryNames = entryNames,
            manifestBytes = resolvedManifest,
            arscBytes = resolvedArsc,
            arscTable = arscTable,
            manifestInfo = manifestInfo,
            resources = resources
        )
    }

    /**
     * Reads and decodes `resources.arsc` directly from an already extracted workspace directory
     * or falls back to streaming extraction if missing.
     */
    suspend fun readResourcesArsc(arscFile: File): Pair<ArscTable, List<ResourceEntryItem>> =
        withContext(Dispatchers.IO) {
            if (!arscFile.exists()) {
                throw IllegalArgumentException("resources.arsc dosyası bulunamadı: ${arscFile.absolutePath}")
            }
            val bytes = arscFile.readBytes()
            val table = ArscTable.parse(bytes)
            table to table.extractTranslatableResources()
        }

    /**
     * Modifies localized resource entries in parallel-safe coroutines while strictly preserving
     * resource identifiers (`id`, `key`, `kind`, `subKey`, `localeQualifier`), printf placeholders,
     * and HTML/XML tags, followed by [ApkRecoveryValidator] auto-recovery.
     */
    suspend fun localizeEntriesPlaceholderSafe(
        entries: List<ResourceEntryItem>,
        protectionConfig: TranslationProtectionConfig = TranslationProtectionConfig(),
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        translator: suspend (maskedText: String, item: ResourceEntryItem) -> String
    ): Pair<List<ResourceEntryItem>, RecoveryValidationReport> = withContext(Dispatchers.Default) {
        val total = entries.size.coerceAtLeast(1)
        val updated = entries.mapIndexed { idx, item ->
            currentCoroutineContext().ensureActive()

            if (!item.isTranslatable ||
                item.isLocked ||
                PlaceholderProtector.isWholeTextExcluded(item.originalText, protectionConfig)
            ) {
                onProgress(idx + 1, total)
                return@mapIndexed item.copy(
                    translatedText = item.translatedText.ifBlank { item.originalText },
                    origin = if (item.translatedText.isNotBlank()) item.origin else TranslationOrigin.PRESERVED
                )
            }

            val protected = PlaceholderProtector.protect(item.originalText, protectionConfig)
            val rawTranslation = translator(protected.maskedText, item)
            val restored = PlaceholderProtector.restore(rawTranslation, protected.tokens)
            val safeText = if (!protectionConfig.preservePlaceholders && !protectionConfig.preserveMarkupTags) {
                restored
            } else if (PlaceholderProtector.verifyPlaceholdersPreserved(item.originalText, restored, protectionConfig)) {
                restored
            } else {
                PlaceholderProtector.repairMissingPlaceholders(item.originalText, restored, protectionConfig)
            }

            onProgress(idx + 1, total)
            // Copy preserves original id, kind, key, subKey, localeQualifier untouched!
            item.copy(
                translatedText = safeText,
                protectedTokens = protected.tokens,
                origin = if (item.origin == TranslationOrigin.PENDING) TranslationOrigin.ONLINE_API else item.origin
            )
        }

        ApkRecoveryValidator.validateAndAutoRecover(
            items = updated,
            protectionConfig = protectionConfig
        )
    }

    /**
     * Applies identifier-preserving localizations to [ArscTable], validates binary integrity,
     * repackages with 4-byte alignment, and prepares the v1/v2/v3 signed APK output.
     */
    suspend fun rebuildAndSignPreparedApk(
        sourceApk: File,
        arscTable: ArscTable,
        localizedEntries: List<ResourceEntryItem>,
        targetLanguage: LanguageOption,
        minSdkVersion: Int,
        signedOutputApk: File,
        stringsOnly: Boolean = true,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ApkProcessOutput = withContext(Dispatchers.IO) {
        onProgress(0.2f, "resources.arsc güncelleniyor...")
        val (safeEntries, recoveryReport) = ApkRecoveryValidator.validateAndAutoRecover(localizedEntries)
        val applied = arscTable.applyTranslationsForScopes(
            targetLanguage = targetLanguage,
            translatedItems = safeEntries,
            stringsOnly = stringsOnly,
            translateDefaultStrings = true,
            translateValuesTr = true,
            translateValuesEn = true,
            preferredSourceLocale = null
        )
        val updatedArsc = arscTable.toByteArray()
        if (!ApkRecoveryValidator.verifyArscTableHeader(updatedArsc)) {
            throw IllegalStateException("Güncellenen resources.arsc başlığı doğrulamayı geçemedi.")
        }

        onProgress(0.55f, "APK 4-byte hizalı olarak paketleniyor...")
        val tempDir = File(context.cacheDir, "apk_processor_build").apply { mkdirs() }
        val unsignedApk = File(tempDir, "${signedOutputApk.nameWithoutExtension}_unsigned.apk")

        try {
            packager.repackageApkWithUpdatedArsc(
                sourceApk = sourceApk,
                unsignedOutputApk = unsignedApk,
                updatedArscBytes = updatedArsc
            )

            onProgress(0.8f, "APK v1/v2/v3 sertifika şemalarıyla imzalanıyor...")
            signedOutputApk.parentFile?.mkdirs()
            val signerIdentity = signerEngine.signApk(
                unsignedApk = unsignedApk,
                signedApk = signedOutputApk,
                minSdkVersion = minSdkVersion.coerceAtLeast(24)
            )
            val verifyReport = signerEngine.verifyApk(signedOutputApk, minSdkVersion.coerceAtLeast(24))
            if (!verifyReport.isVerified) {
                throw SecurityException("Çıktı APK v1/v2/v3 imza doğrulaması başarısız: ${verifyReport.errors.joinToString()}")
            }
            val sha256 = ApkSecurityUtils.calculateSha256(signedOutputApk)
            onProgress(1.0f, "APK v1/v2/v3 imzalandı ve doğrulandı.")

            ApkProcessOutput(
                signedApk = signedOutputApk,
                signerIdentity = signerIdentity,
                verifyReport = verifyReport,
                appliedResourceCount = applied,
                recoveryReport = recoveryReport,
                outputSha256 = sha256
            )
        } finally {
            runCatching { unsignedApk.delete() }
        }
    }
}
