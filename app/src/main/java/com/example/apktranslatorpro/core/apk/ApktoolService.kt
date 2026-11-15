package com.example.apktranslatorpro.core.apk

import android.content.Context
import android.net.Uri
import com.example.apktranslatorpro.core.security.ApkSecurityUtils
import com.example.apktranslatorpro.core.security.SecurityGuards
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class DecompiledWorkspace(
    val workDir: File,
    val originalsDir: File,
    val apkCopyFile: File,
    val sha256Hex: String,
    val manifestFile: File,
    val arscFile: File,
    val resXmlFiles: List<File>,
    val smaliFiles: List<File>,
    val dexSummaries: List<DexSummary>,
    val allEntries: List<String>,
    val binaryManifestFile: File? = null
)

data class BulkKeyRenameResult(
    val renamedResourceCount: Int,
    val updatedLayoutReferences: Int,
    val modifiedFiles: List<String>
)

/**
 * Wraps APK decompression and Apktool-compatible resource/XML/Smali workspace extraction
 * into an isolated temporary application cache directory, never touching the original APK.
 */
class ApktoolService(
    private val context: Context,
    private val packager: ApkPackager = ApkPackager()
) {

    fun decompileFromUri(
        uri: Uri,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): DecompiledWorkspace {
        onProgress(0.08f, "SAF dosya varlığı ve SHA-256 doğrulanıyor...")
        val verification = ApkSecurityUtils.verifySafApkExistsReadOnly(context, uri)

        val rootCache = File(context.cacheDir, "apktool_work/${verification.sha256Hex.take(12)}")
        if (rootCache.exists()) rootCache.deleteRecursively()
        rootCache.mkdirs()

        val apkCopy = File(rootCache, "base_readonly.apk")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw SecurityException("APK giriş akışı açılamadı.")

        inputStream.use { input ->
            FileOutputStream(apkCopy).use { out ->
                input.copyTo(out, 16 * 1024)
            }
        }

        val copiedSha = ApkSecurityUtils.calculateSha256(apkCopy)
        if (!copiedSha.equals(verification.sha256Hex, ignoreCase = true)) {
            throw SecurityException("APK kopya SHA-256 doğrulaması başarısız.")
        }

        return decompileApkFile(apkCopy, rootCache, copiedSha, onProgress)
    }

    fun decompileApkFile(
        apkFile: File,
        workDir: File,
        sha256Hex: String = ApkSecurityUtils.calculateSha256(apkFile),
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): DecompiledWorkspace {
        if (!apkFile.exists() || apkFile.length() < 22L) {
            throw IllegalArgumentException("Geçersiz veya boş APK dosyası.")
        }

        // Verify ZIP local file header magic ('P','K',0x03,0x04)
        val header = ByteArray(4)
        FileInputStream(apkFile).use { it.read(header) }
        if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
            throw IllegalArgumentException("Seçilen dosya geçerli bir APK/ZIP arşivi değil.")
        }

        onProgress(0.25f, "APK arşivi ve resources.arsc çözümleniyor...")
        val analyzed = packager.analyzeApk(apkFile)

        val decodedDir = File(workDir, "decompiled").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val allEntries = mutableListOf<String>()
        val resXmlFiles = mutableListOf<File>()
        val smaliFiles = mutableListOf<File>()
        val dexSummaries = mutableListOf<DexSummary>()

        ZipFile(apkFile).use { zip ->
            val entries = zip.entries().toList()
            val total = entries.size.coerceAtLeast(1)

            entries.forEachIndexed { idx, entry ->
                val safeName = SecurityGuards.validateZipEntryPath(entry.name, decodedDir)
                allEntries.add(safeName)
                if (entry.isDirectory) return@forEachIndexed

                when {
                    safeName == "AndroidManifest.xml" -> {
                        val rawManifestBytes = zip.getInputStream(entry).use { it.readBytes() }
                        val axmlOutFile = File(decodedDir, "AndroidManifest.axml")
                        axmlOutFile.parentFile?.mkdirs()
                        axmlOutFile.writeBytes(rawManifestBytes)

                        val outFile = File(decodedDir, "AndroidManifest.xml")
                        outFile.parentFile?.mkdirs()
                        val fullXml = runCatching { AxmlParser.decodeToXmlString(rawManifestBytes) }
                            .getOrElse { analyzed.manifestInfo.xmlPreview }
                        outFile.writeText(fullXml, Charsets.UTF_8)
                    }
                    safeName == "resources.arsc" -> {
                        val arscOut = File(decodedDir, "resources.arsc")
                        zip.getInputStream(entry).use { inp ->
                            FileOutputStream(arscOut).use { out -> inp.copyTo(out, 16 * 1024) }
                        }
                    }
                    safeName.startsWith("res/") && safeName.endsWith(".xml", ignoreCase = true) -> {
                        val outFile = File(decodedDir, safeName)
                        outFile.parentFile?.mkdirs()
                        val rawBytes = zip.getInputStream(entry).use { it.readBytes() }
                        val decodedXml = runCatching { AxmlParser.parseManifest(rawBytes).xmlPreview }
                            .getOrElse { String(rawBytes, Charsets.UTF_8) }
                        outFile.writeText(decodedXml, Charsets.UTF_8)
                        resXmlFiles.add(outFile)
                    }
                    safeName.matches(Regex("^classes\\d*\\.dex$", RegexOption.IGNORE_CASE)) -> {
                        val dexOut = File(decodedDir, safeName)
                        val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                        dexOut.writeBytes(dexBytes)
                        runCatching { DexSmaliInspector.parseDex(safeName, dexBytes, maxClasses = 50) }
                            .onSuccess { summary ->
                                dexSummaries.add(summary)
                                val smaliRoot = File(decodedDir, "smali/${safeName.removeSuffix(".dex")}")
                                smaliRoot.mkdirs()
                                summary.classes.forEach { cls ->
                                    val smaliFile = File(smaliRoot, "${cls.simpleName.replace('.', '_')}.smali")
                                    smaliFile.writeText(cls.smaliSource, Charsets.UTF_8)
                                    smaliFiles.add(smaliFile)
                                }
                            }
                    }
                }

                if (idx % 20 == 0 || idx == total - 1) {
                    val frac = 0.25f + (idx.toFloat() / total.toFloat()) * 0.55f
                    onProgress(frac, "Çıkarılıyor: $safeName")
                }
            }
        }

        // Write decoded values/strings.xml + locale XMLs into cache working tree
        onProgress(0.88f, "values/strings.xml ve dil dosyaları hazırlanıyor...")
        val defaultStringsFile = File(decodedDir, "res/values/strings.xml").apply {
            parentFile?.mkdirs()
            writeText(ValuesXmlCodec.generateStringsXml(analyzed.extractedResources, useTranslated = false), Charsets.UTF_8)
        }
        resXmlFiles.add(0, defaultStringsFile)

        for (locale in analyzed.arscTable.extractLocales().filter { it == "tr" || it == "en" || it == "de" }) {
            val locItems = analyzed.arscTable.extractTranslatableResources(preferredSourceLocale = locale)
            if (locItems.isNotEmpty()) {
                val locFile = File(decodedDir, "res/values-$locale/strings.xml").apply {
                    parentFile?.mkdirs()
                    writeText(ValuesXmlCodec.generateStringsXml(locItems, useTranslated = false), Charsets.UTF_8)
                }
                resXmlFiles.add(locFile)
            }
        }

        val originalsDir = File(workDir, "originals").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val manifestInDecoded = File(decodedDir, "AndroidManifest.xml")
        if (manifestInDecoded.exists()) {
            File(originalsDir, "AndroidManifest.xml").apply {
                parentFile?.mkdirs()
                writeText(manifestInDecoded.readText(Charsets.UTF_8), Charsets.UTF_8)
            }
        }
        resXmlFiles.forEach { xmlFile ->
            val rel = runCatching { xmlFile.relativeTo(decodedDir).path }.getOrNull()
            if (!rel.isNullOrBlank() && xmlFile.exists()) {
                File(originalsDir, rel).apply {
                    parentFile?.mkdirs()
                    writeText(xmlFile.readText(Charsets.UTF_8), Charsets.UTF_8)
                }
            }
        }

        onProgress(1f, "Apktool çalışma alanı hazır (${resXmlFiles.size} XML, ${smaliFiles.size} Smali)")

        return DecompiledWorkspace(
            workDir = decodedDir,
            originalsDir = originalsDir,
            apkCopyFile = apkFile,
            sha256Hex = sha256Hex,
            manifestFile = File(decodedDir, "AndroidManifest.xml"),
            arscFile = File(decodedDir, "resources.arsc"),
            resXmlFiles = resXmlFiles.distinctBy { it.absolutePath }.sortedBy { it.relativeTo(decodedDir).path },
            smaliFiles = smaliFiles.sortedBy { it.name },
            dexSummaries = dexSummaries,
            allEntries = allEntries.sorted(),
            binaryManifestFile = File(decodedDir, "AndroidManifest.axml").takeIf { it.exists() }
        )
    }

    fun readOriginalSnapshot(workspace: DecompiledWorkspace, workingFile: File): String {
        val rel = runCatching { workingFile.relativeTo(workspace.workDir).path }.getOrNull() ?: return ""
        val snapshot = File(workspace.originalsDir, rel)
        return if (snapshot.exists() && snapshot.isFile) snapshot.readText(Charsets.UTF_8) else ""
    }

    fun compareWorkingFileWithOriginal(workspace: DecompiledWorkspace, workingFile: File): XmlDiffSummary {
        val original = readOriginalSnapshot(workspace, workingFile)
        val modified = readWorkingFile(workingFile)
        return XmlDiffEngine.computeSideBySideDiff(original, modified)
    }

    fun readWorkingFile(file: File): String =
        if (file.exists() && file.isFile) file.readText(Charsets.UTF_8) else ""

    fun saveWorkingFile(file: File, newContent: String) {
        file.parentFile?.mkdirs()
        file.writeText(newContent, Charsets.UTF_8)
    }

    /**
     * Performs bulk key renaming in values/strings*.xml files and automatically updates
     * all `@string/<key>` references across decoded res/layout/ and res/ XML files.
     */
    fun bulkRenameStringKeys(
        workDir: File,
        oldPrefixOrKey: String,
        newPrefixOrKey: String,
        prefixMode: Boolean = true
    ): BulkKeyRenameResult {
        val oldVal = oldPrefixOrKey.trim()
        val newVal = newPrefixOrKey.trim()
        require(oldVal.isNotEmpty() && newVal.isNotEmpty()) {
            "Eski ve yeni anahtar adları boş olamaz."
        }
        require(Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(newVal)) {
            "Yeni anahtar yalnızca harf, rakam ve alt çizgi (_) içerebilir."
        }

        val resDir = File(workDir, "res")
        if (!resDir.exists()) return BulkKeyRenameResult(0, 0, emptyList())

        val renamedPairs = linkedMapOf<String, String>()
        val modifiedFiles = linkedSetOf<String>()

        val valuesXmls = resDir.walkTopDown()
            .filter { it.isFile && it.parentFile?.name?.startsWith("values") == true && it.extension == "xml" }
            .toList()

        val nameAttrRegex = Regex("""name\s*=\s*"([A-Za-z0-9_]+)"""")
        for (xmlFile in valuesXmls) {
            val content = xmlFile.readText(Charsets.UTF_8)
            var fileChanged = false
            val updated = nameAttrRegex.replace(content) { match ->
                val currentKey = match.groupValues[1]
                val nextKey = when {
                    prefixMode && currentKey.startsWith(oldVal) -> newVal + currentKey.removePrefix(oldVal)
                    !prefixMode && currentKey == oldVal -> newVal
                    else -> null
                }
                if (nextKey != null && nextKey != currentKey) {
                    renamedPairs[currentKey] = nextKey
                    fileChanged = true
                    """name="$nextKey""""
                } else {
                    match.value
                }
            }
            if (fileChanged) {
                xmlFile.writeText(updated, Charsets.UTF_8)
                modifiedFiles.add(xmlFile.relativeTo(workDir).path)
            }
        }

        var layoutRefUpdates = 0
        val nonValuesXmls = resDir.walkTopDown()
            .filter { it.isFile && !(it.parentFile?.name?.startsWith("values") == true) && it.extension == "xml" }
            .toList()

        if (renamedPairs.isNotEmpty()) {
            val refRegex = Regex("""@string/([A-Za-z0-9_]+)""")
            for (layoutFile in nonValuesXmls) {
                val content = layoutFile.readText(Charsets.UTF_8)
                var changed = false
                val updated = refRegex.replace(content) { match ->
                    val targetKey = match.groupValues[1]
                    val mapped = renamedPairs[targetKey]
                    if (mapped != null) {
                        layoutRefUpdates++
                        changed = true
                        "@string/$mapped"
                    } else {
                        match.value
                    }
                }
                if (changed) {
                    layoutFile.writeText(updated, Charsets.UTF_8)
                    modifiedFiles.add(layoutFile.relativeTo(workDir).path)
                }
            }
        }

        return BulkKeyRenameResult(
            renamedResourceCount = renamedPairs.size,
            updatedLayoutReferences = layoutRefUpdates,
            modifiedFiles = modifiedFiles.toList()
        )
    }

    fun applyStringsXmlToResources(
        xmlContent: String,
        currentResources: List<ResourceEntryItem>
    ): List<ResourceEntryItem> {
        val parsedItems = ValuesXmlCodec.parseStringsXml(xmlContent)
        if (parsedItems.isEmpty()) return currentResources
        val byUniqueKey = parsedItems.associateBy { it.uniqueKey }
        val byKey = parsedItems.associateBy { it.key }
        return currentResources.map { item ->
            val updated = (byUniqueKey[item.uniqueKey] ?: byKey[item.key])?.originalText
            if (updated != null && updated != item.translatedText) {
                item.copy(
                    translatedText = updated,
                    origin = com.example.apktranslatorpro.domain.model.TranslationOrigin.MANUAL_EDIT
                )
            } else {
                item
            }
        }
    }

    fun exportWorkspaceAsZip(workDir: File, targetOut: OutputStream): Int {
        if (!workDir.exists()) return 0
        var count = 0
        ZipOutputStream(targetOut).use { zos ->
            workDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(workDir).path.replace('\\', '/')
                zos.putNextEntry(ZipEntry(rel))
                FileInputStream(file).use { it.copyTo(zos, 16 * 1024) }
                zos.closeEntry()
                count++
            }
        }
        return count
    }
}
