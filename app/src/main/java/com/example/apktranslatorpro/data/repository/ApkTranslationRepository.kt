package com.example.apktranslatorpro.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.apktranslatorpro.core.apk.ApkPackager
import com.example.apktranslatorpro.core.apk.ApkToolkitInspector
import com.example.apktranslatorpro.core.apk.ArscTable
import com.example.apktranslatorpro.core.security.ApkIntegrityHelper
import com.example.apktranslatorpro.core.security.ApkIntegrityReport
import com.example.apktranslatorpro.core.security.ApkSecurityUtils
import com.example.apktranslatorpro.core.security.SafApkVerificationResult
import com.example.apktranslatorpro.core.security.SecurityGuards
import com.example.apktranslatorpro.core.signing.ApkSignerEngine
import com.example.apktranslatorpro.core.text.PlaceholderProtector
import com.example.apktranslatorpro.core.text.ProtectedText
import com.example.apktranslatorpro.core.text.TranslationProtectionConfig
import com.example.apktranslatorpro.core.translation.GeminiTranslationEngine
import com.example.apktranslatorpro.core.translation.MlKitTranslationEngine
import com.example.apktranslatorpro.core.translation.OnlineTranslationEngine
import com.example.apktranslatorpro.data.local.JobHistoryDao
import com.example.apktranslatorpro.data.local.JobHistoryEntity
import com.example.apktranslatorpro.data.local.TranslationCacheDao
import com.example.apktranslatorpro.data.local.TranslationCacheEntity
import com.example.apktranslatorpro.domain.model.ApkMetadata
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.LanguageResourceScopeConfig
import com.example.apktranslatorpro.domain.model.LogLevel
import com.example.apktranslatorpro.domain.model.OnlineApiConfig
import com.example.apktranslatorpro.domain.model.OutputApkResult
import com.example.apktranslatorpro.domain.model.PipelineStage
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.ResourceKind
import com.example.apktranslatorpro.domain.model.TranslationMode
import com.example.apktranslatorpro.domain.model.TranslationOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class InspectedApkSession(
    val metadata: ApkMetadata,
    val arscTable: ArscTable,
    val resources: List<ResourceEntryItem>,
    val workingInputApk: File
)

class ApkTranslationRepository(
    private val context: Context,
    private val packager: ApkPackager,
    private val signerEngine: ApkSignerEngine,
    private val mlKitEngine: MlKitTranslationEngine,
    private val onlineEngine: OnlineTranslationEngine,
    private val geminiEngine: GeminiTranslationEngine,
    private val cacheDao: TranslationCacheDao,
    private val historyDao: JobHistoryDao
) {
    val cacheCountFlow: Flow<Int> = cacheDao.observeCacheCount()
    val recentJobsFlow: Flow<List<JobHistoryEntity>> = historyDao.observeRecentJobs()

    suspend fun clearCache() = cacheDao.clearAll()
    suspend fun clearHistory() = historyDao.clearHistory()

    suspend fun verifySafUriReadOnly(uri: Uri): SafApkVerificationResult =
        withContext(Dispatchers.IO) {
            ApkSecurityUtils.verifySafApkExistsReadOnly(context, uri)
        }

    suspend fun verifyOriginalIntegrity(uri: Uri, expectedSha256: String): ApkIntegrityReport =
        withContext(Dispatchers.IO) {
            ApkSecurityUtils.verifyApkIntegrity(context, uri, expectedSha256)
        }

    fun streamGeminiApkAnalysis(metadata: ApkMetadata): Flow<String> =
        geminiEngine.analyzeApkStream(metadata)

    suspend fun inspectApkFromUri(
        uri: Uri,
        onLog: (LogLevel, PipelineStage, String) -> Unit
    ): InspectedApkSession = withContext(Dispatchers.IO) {
        onLog(LogLevel.INFO, PipelineStage.VERIFYING_INPUT, "SAF üzerinden APK varlığı ve salt okunur SHA-256 bütünlüğü doğrulanıyor...")

        val safCheck = ApkSecurityUtils.verifySafApkExistsReadOnly(context, uri)
        val fileName = safCheck.displayName
        if (!fileName.endsWith(".apk", ignoreCase = true)) {
            onLog(LogLevel.WARN, PipelineStage.VERIFYING_INPUT, "Dosya uzantısı .apk değil ($fileName); yine de ZIP/APK yapısı doğrulanacak.")
        }

        val workspaceDir = File(context.cacheDir, "apk_workspace").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val workingInput = File(workspaceDir, "original_readonly_copy.apk")

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw SecurityException("Seçilen APK dosyası açılamadı.")

        inputStream.use { input ->
            FileOutputStream(workingInput).use { output ->
                val buffer = ByteArray(16 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    output.write(buffer, 0, read)
                }
            }
        }

        val copySha256 = ApkSecurityUtils.calculateSha256(workingInput)
        if (!copySha256.equals(safCheck.sha256Hex, ignoreCase = true)) {
            throw SecurityException("Girdi bütünlüğü doğrulaması başarısız! SHA-256 uyuşmazlığı.")
        }
        onLog(LogLevel.SUCCESS, PipelineStage.VERIFYING_INPUT, "Orijinal APK doğrulandı (değiştirilmedi) • SHA-256: ${safCheck.sha256Hex.take(16)}...")

        onLog(LogLevel.INFO, PipelineStage.ANALYZING_APK, "AndroidManifest.xml, ikili XML, sertifika ve resources.arsc çözümleniyor...")
        val analyzed = packager.analyzeApk(workingInput)
        val label = packager.resolveAppLabel(analyzed.manifestInfo, analyzed.arscTable)
        val locales = analyzed.arscTable.extractLocales()
        val fullInspection = ApkToolkitInspector.inspectFullApk(
            apkFile = workingInput,
            manifestInfo = analyzed.manifestInfo,
            sha256Hex = safCheck.sha256Hex
        )

        onLog(LogLevel.INFO, PipelineStage.EXTRACTING_RESOURCES, "Kaynak dili otomatik algılanıyor...")
        val sampleTexts = analyzed.extractedResources
            .asSequence()
            .filter { it.isTranslatable && it.originalText.length >= 4 }
            .map { it.originalText }
            .take(30)
            .toList()
        val (detectedLang, confidence) = mlKitEngine.detectLanguage(sampleTexts)
        onLog(
            LogLevel.SUCCESS,
            PipelineStage.EXTRACTING_RESOURCES,
            "Algılanan kaynak dil: ${detectedLang.displayNameTr} (%${(confidence * 100).toInt()} güven). ${analyzed.extractedResources.size} metin, ${analyzed.binaryXmlFiles.size} ikili XML, ${fullInspection.dexFiles.size} DEX."
        )

        val stringCount = analyzed.extractedResources.count { it.kind == ResourceKind.STRING }
        val arrayCount = analyzed.extractedResources.count { it.kind == ResourceKind.STRING_ARRAY }
        val pluralCount = analyzed.extractedResources.count { it.kind == ResourceKind.PLURALS }

        val metadata = ApkMetadata(
            fileName = fileName,
            uriString = uri.toString(),
            fileSizeBytes = workingInput.length(),
            sha256 = safCheck.sha256Hex,
            packageName = analyzed.manifestInfo.packageName,
            appLabel = label,
            versionName = analyzed.manifestInfo.versionName,
            versionCode = analyzed.manifestInfo.versionCode,
            minSdkVersion = analyzed.manifestInfo.minSdkVersion,
            targetSdkVersion = analyzed.manifestInfo.targetSdkVersion,
            supportsRtl = analyzed.manifestInfo.supportsRtl,
            detectedSourceLanguage = detectedLang,
            detectedConfidence = confidence,
            availableLocales = locales,
            totalStringCount = stringCount,
            totalArrayItemCount = arrayCount,
            totalPluralItemCount = pluralCount,
            hasExistingSignature = analyzed.hasExistingSignature,
            securityWarnings = analyzed.securityWarnings,
            manifestXmlPreview = analyzed.manifestInfo.xmlPreview,
            binaryXmlFiles = analyzed.binaryXmlFiles,
            fullInspection = fullInspection
        )

        InspectedApkSession(
            metadata = metadata,
            arscTable = analyzed.arscTable,
            resources = analyzed.extractedResources,
            workingInputApk = workingInput
        )
    }

    suspend fun decodeBinaryXmlEntry(session: InspectedApkSession, entryPath: String): String =
        withContext(Dispatchers.IO) {
            packager.decodeBinaryXmlEntry(session.workingInputApk, entryPath)
        }

    suspend fun translateResources(
        items: List<ResourceEntryItem>,
        sourceLanguage: LanguageOption,
        targetLanguage: LanguageOption,
        mode: TranslationMode,
        onlineConfig: OnlineApiConfig,
        protectionConfig: TranslationProtectionConfig,
        scopeConfig: LanguageResourceScopeConfig = LanguageResourceScopeConfig(),
        appName: String = "Android App",
        packageName: String = "com.example.app",
        onProgress: (processed: Int, total: Int) -> Unit,
        onLog: (LogLevel, PipelineStage, String) -> Unit
    ): List<ResourceEntryItem> = withContext(Dispatchers.IO) {
        if (sourceLanguage == targetLanguage) {
            throw IllegalArgumentException("Kaynak dil ile hedef dil aynı olamaz.")
        }

        val total = items.size.coerceAtLeast(1)
        val resultList = items.toMutableList()
        val rulesSignature = buildProtectionSignature(protectionConfig)

        onLog(
            LogLevel.INFO,
            PipelineStage.TRANSLATING,
            if (scopeConfig.strictStringsOnly) {
                "Sadece dil dosyaları modu aktif: Hedef (${scopeConfig.describeScopes()}) • Diğer APK dosya ve kaynaklarına dokunulmayacak."
            } else {
                "Tam kaynak modu: string, string-array ve plurals işlenecek."
            }
        )
        onLog(
            LogLevel.INFO,
            PipelineStage.TRANSLATING,
            "Koruma kuralları: Placeholder=${yesNoTr(protectionConfig.preservePlaceholders)}, Etiket=${yesNoTr(protectionConfig.preserveMarkupTags)}, Hariç Terim=${if (protectionConfig.excludeTechnicalTerms) protectionConfig.excludedTerms.size else 0}"
        )

        var cacheHits = 0
        var onlineCount = 0
        var offlineCount = 0
        var geminiCount = 0
        var excludedCount = 0

        if (mode == TranslationMode.GEMINI_AI) {
            if (!onlineEngine.isNetworkConnected()) {
                throw IllegalStateException("Gemini AI çevirisi için aktif internet bağlantısı gerekli.")
            }
            val pendingIndices = mutableListOf<Int>()
            val pendingProtected = mutableListOf<ProtectedText>()
            val pendingCacheKeys = mutableListOf<String>()

            for (index in resultList.indices) {
                currentCoroutineContext().ensureActive()
                val item = resultList[index]
                if (item.origin == TranslationOrigin.MANUAL_EDIT && item.translatedText.isNotBlank()) {
                    onProgress(index + 1, total)
                    continue
                }
                if ((scopeConfig.strictStringsOnly && item.kind != ResourceKind.STRING) ||
                    !item.isTranslatable ||
                    PlaceholderProtector.isWholeTextExcluded(item.originalText, protectionConfig)
                ) {
                    resultList[index] = item.copy(
                        translatedText = item.originalText,
                        origin = TranslationOrigin.PRESERVED
                    )
                    excludedCount++
                    onProgress(index + 1, total)
                    continue
                }
                val cacheKey = buildCacheKey(sourceLanguage.code, targetLanguage.code, rulesSignature, item.originalText)
                val cached = cacheDao.getByKey(cacheKey)
                if (cached != null && PlaceholderProtector.verifyPlaceholdersPreserved(item.originalText, cached.translatedText, protectionConfig)) {
                    resultList[index] = item.copy(
                        translatedText = cached.translatedText,
                        origin = TranslationOrigin.CACHE
                    )
                    cacheHits++
                    onProgress(index + 1, total)
                    continue
                }
                pendingIndices.add(index)
                pendingProtected.add(PlaceholderProtector.protect(item.originalText, protectionConfig))
                pendingCacheKeys.add(cacheKey)
            }

            val batchSize = 15
            var offset = 0
            while (offset < pendingIndices.size) {
                currentCoroutineContext().ensureActive()
                val end = (offset + batchSize).coerceAtMost(pendingIndices.size)
                val batchMasked = pendingProtected.subList(offset, end).map { it.maskedText }
                val batchTranslated = geminiEngine.translateBatch(
                    maskedTexts = batchMasked,
                    sourceLang = sourceLanguage,
                    targetLang = targetLanguage,
                    appName = appName,
                    packageName = packageName
                )
                for (localIdx in 0 until (end - offset)) {
                    val globalPos = offset + localIdx
                    val itemIdx = pendingIndices[globalPos]
                    val item = resultList[itemIdx]
                    val protected = pendingProtected[globalPos]
                    val restored = PlaceholderProtector.restore(batchTranslated[localIdx], protected.tokens)
                    val finalSafeText = if (!protectionConfig.preservePlaceholders && !protectionConfig.preserveMarkupTags) {
                        restored
                    } else if (PlaceholderProtector.verifyPlaceholdersPreserved(item.originalText, restored, protectionConfig)) {
                        restored
                    } else {
                        PlaceholderProtector.repairMissingPlaceholders(item.originalText, restored, protectionConfig)
                    }
                    cacheDao.upsert(
                        TranslationCacheEntity(
                            cacheKey = pendingCacheKeys[globalPos],
                            sourceLang = sourceLanguage.code,
                            targetLang = targetLanguage.code,
                            sourceText = item.originalText,
                            translatedText = finalSafeText,
                            engineTag = TranslationOrigin.GEMINI_AI.name
                        )
                    )
                    resultList[itemIdx] = item.copy(
                        translatedText = finalSafeText,
                        origin = TranslationOrigin.GEMINI_AI
                    )
                    geminiCount++
                }
                onProgress(((total - pendingIndices.size) + end).coerceAtMost(total), total)
                offset = end
            }

            onLog(
                LogLevel.SUCCESS,
                PipelineStage.TRANSLATING,
                "Gemini AI çevirisi tamamlandı: Gemini=$geminiCount, Önbellek=$cacheHits, Korunan=$excludedCount"
            )
            return@withContext resultList
        }

        var mlKitTranslator = if (mode == TranslationMode.OFFLINE_ONLY) {
            onLog(LogLevel.INFO, PipelineStage.TRANSLATING, "ML Kit çevrimdışı modeli hazırlanıyor (${sourceLanguage.code} -> ${targetLanguage.code})...")
            mlKitEngine.createTranslator(sourceLanguage, targetLanguage).also {
                mlKitEngine.ensureTranslatorReady(it)
            }
        } else null

        var autoSwitchedToOffline = false

        try {
            for (index in resultList.indices) {
                currentCoroutineContext().ensureActive()
                val item = resultList[index]

                if (item.origin == TranslationOrigin.MANUAL_EDIT && item.translatedText.isNotBlank()) {
                    onProgress(index + 1, total)
                    continue
                }
                if ((scopeConfig.strictStringsOnly && item.kind != ResourceKind.STRING) ||
                    !item.isTranslatable ||
                    PlaceholderProtector.isWholeTextExcluded(item.originalText, protectionConfig)
                ) {
                    resultList[index] = item.copy(
                        translatedText = item.originalText,
                        origin = TranslationOrigin.PRESERVED
                    )
                    excludedCount++
                    onProgress(index + 1, total)
                    continue
                }

                val cacheKey = buildCacheKey(sourceLanguage.code, targetLanguage.code, rulesSignature, item.originalText)
                val cached = cacheDao.getByKey(cacheKey)
                if (cached != null && PlaceholderProtector.verifyPlaceholdersPreserved(item.originalText, cached.translatedText, protectionConfig)) {
                    resultList[index] = item.copy(
                        translatedText = cached.translatedText,
                        origin = TranslationOrigin.CACHE
                    )
                    cacheHits++
                    onProgress(index + 1, total)
                    continue
                }

                val protected = PlaceholderProtector.protect(item.originalText, protectionConfig)
                var translatedRaw: String? = null
                var origin = TranslationOrigin.PENDING

                val shouldTryOnline = when (mode) {
                    TranslationMode.ONLINE_ONLY -> true
                    TranslationMode.OFFLINE_ONLY, TranslationMode.GEMINI_AI -> false
                    TranslationMode.AUTO -> !autoSwitchedToOffline && onlineEngine.isNetworkConnected() && onlineConfig.endpointUrl.isNotBlank()
                }

                if (shouldTryOnline) {
                    try {
                        translatedRaw = onlineEngine.translateText(
                            text = protected.maskedText,
                            sourceLang = sourceLanguage,
                            targetLang = targetLanguage,
                            config = onlineConfig
                        )
                        origin = TranslationOrigin.ONLINE_API
                        onlineCount++
                    } catch (ex: Exception) {
                        if (mode == TranslationMode.ONLINE_ONLY) {
                            throw ex
                        }
                        if (!autoSwitchedToOffline) {
                            autoSwitchedToOffline = true
                            onLog(
                                LogLevel.WARN,
                                PipelineStage.TRANSLATING,
                                "Çevrimiçi API kesildi (${ex.message}). Otomatik olarak ML Kit çevrimdışı moda geçiliyor..."
                            )
                        }
                    }
                } else if (mode == TranslationMode.AUTO && !autoSwitchedToOffline) {
                    autoSwitchedToOffline = true
                    onLog(
                        LogLevel.INFO,
                        PipelineStage.TRANSLATING,
                        "İnternet bağlantısı veya API ayarı yok; ML Kit çevrimdışı çeviri kullanılıyor."
                    )
                }

                if (translatedRaw == null) {
                    if (mlKitTranslator == null) {
                        onLog(LogLevel.INFO, PipelineStage.TRANSLATING, "ML Kit çevrimdışı dil paketi doğrulanıyor (${sourceLanguage.code} -> ${targetLanguage.code})...")
                        mlKitTranslator = mlKitEngine.createTranslator(sourceLanguage, targetLanguage).also {
                            mlKitEngine.ensureTranslatorReady(it)
                        }
                    }
                    translatedRaw = mlKitEngine.translateWithClient(mlKitTranslator, protected.maskedText)
                    origin = TranslationOrigin.MLKIT_OFFLINE
                    offlineCount++
                }

                val restored = PlaceholderProtector.restore(translatedRaw, protected.tokens)
                val finalSafeText = if (!protectionConfig.preservePlaceholders && !protectionConfig.preserveMarkupTags) {
                    restored
                } else if (PlaceholderProtector.verifyPlaceholdersPreserved(item.originalText, restored, protectionConfig)) {
                    restored
                } else {
                    PlaceholderProtector.repairMissingPlaceholders(item.originalText, restored, protectionConfig)
                }

                cacheDao.upsert(
                    TranslationCacheEntity(
                        cacheKey = cacheKey,
                        sourceLang = sourceLanguage.code,
                        targetLang = targetLanguage.code,
                        sourceText = item.originalText,
                        translatedText = finalSafeText,
                        engineTag = origin.name
                    )
                )

                resultList[index] = item.copy(
                    translatedText = finalSafeText,
                    origin = origin
                )
                onProgress(index + 1, total)
            }
        } finally {
            mlKitTranslator?.close()
        }

        onLog(
            LogLevel.SUCCESS,
            PipelineStage.TRANSLATING,
            "Çeviri tamamlandı: Çevrimiçi=$onlineCount, Çevrimdışı=$offlineCount, Önbellek=$cacheHits, Korunan=$excludedCount"
        )
        resultList
    }

    suspend fun rebuildSignAndVerifyApk(
        session: InspectedApkSession,
        translatedItems: List<ResourceEntryItem>,
        sourceLanguage: LanguageOption,
        targetLanguage: LanguageOption,
        outputTreeUriString: String?,
        scopeConfig: LanguageResourceScopeConfig = LanguageResourceScopeConfig(),
        onStage: (PipelineStage) -> Unit,
        onLog: (LogLevel, PipelineStage, String) -> Unit
    ): OutputApkResult = withContext(Dispatchers.IO) {
        onStage(PipelineStage.REBUILDING_RESOURCES)
        onLog(
            LogLevel.INFO,
            PipelineStage.REBUILDING_RESOURCES,
            "Sadece seçilen dil kapsamı güncelleniyor: ${scopeConfig.describeScopes()} (diğer APK dosyaları korunuyor)..."
        )

        val freshAnalyzed = packager.analyzeApk(session.workingInputApk)
        val arscTable = freshAnalyzed.arscTable
        val applied = arscTable.applyTranslationsForScopes(
            targetLanguage = targetLanguage,
            translatedItems = translatedItems,
            stringsOnly = scopeConfig.strictStringsOnly,
            translateDefaultStrings = scopeConfig.translateDefaultStrings,
            translateValuesTr = scopeConfig.translateValuesTr,
            translateValuesEn = scopeConfig.translateValuesEn,
            preferredSourceLocale = null
        )
        val rebuiltArscBytes = arscTable.toByteArray()
        onLog(LogLevel.SUCCESS, PipelineStage.REBUILDING_RESOURCES, "resources.arsc derlendi ($applied string güncellendi, diğer tüm dosyalar aynen korundu, ${rebuiltArscBytes.size / 1024} KB).")

        onStage(PipelineStage.PACKAGING_APK)
        val workspaceDir = session.workingInputApk.parentFile!!
        val unsignedApk = File(workspaceDir, "rebuilt_unsigned.apk")
        packager.repackageApkWithUpdatedArsc(
            sourceApk = session.workingInputApk,
            unsignedOutputApk = unsignedApk,
            updatedArscBytes = rebuiltArscBytes
        )
        onLog(LogLevel.SUCCESS, PipelineStage.PACKAGING_APK, "APK 4-byte hizalı olarak paketlendi.")

        onStage(PipelineStage.SIGNING_APK)
        val outputsDir = File(context.filesDir, "outputs").apply { mkdirs() }
        val baseName = session.metadata.fileName.removeSuffix(".apk").ifBlank { session.metadata.packageName }
        val outputFileName = "${baseName}_${targetLanguage.code}_signed.apk"
        val signedApk = File(outputsDir, outputFileName)

        val signerIdentity = signerEngine.signApk(
            unsignedApk = unsignedApk,
            signedApk = signedApk,
            minSdkVersion = session.metadata.minSdkVersion.coerceAtLeast(24)
        )
        onLog(
            LogLevel.SUCCESS,
            PipelineStage.SIGNING_APK,
            "APK yeni v1/v2/v3 anahtarla imzalandı (Sertifika SHA-256: ${signerIdentity.certSha256.take(16)}...)."
        )

        onStage(PipelineStage.VERIFYING_OUTPUT)
        val verifyReport = signerEngine.verifyApk(signedApk, session.metadata.minSdkVersion.coerceAtLeast(24))
        if (!verifyReport.isVerified) {
            throw SecurityException("Çıktı APK imza doğrulaması başarısız: ${verifyReport.errors.joinToString("; ")}")
        }
        val outputSha256 = ApkSecurityUtils.calculateSha256(signedApk)
        val originalUri = Uri.parse(session.metadata.uriString)
        val integrityReport = ApkSecurityUtils.verifyApkIntegrity(
            context = context,
            apkUri = originalUri,
            expectedSha256Hex = session.metadata.sha256
        )

        onLog(
            if (integrityReport.isUnchanged) LogLevel.SUCCESS else LogLevel.WARN,
            PipelineStage.VERIFYING_OUTPUT,
            "İmza doğrulandı (v1=${verifyReport.v1Verified}, v2=${verifyReport.v2Verified}, v3=${verifyReport.v3Verified}). Orijinal APK bütünlüğü: ${if (integrityReport.isUnchanged) "KORUNDU" else "DEĞİŞTİ!"}"
        )

        onStage(PipelineStage.SAVING_OUTPUT)
        var savedUriString: String? = null
        var savedDisplayLocation = "Uygulama içi güvenli depolama: outputs/$outputFileName"

        if (!outputTreeUriString.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(outputTreeUriString)
                val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (treeDoc != null && treeDoc.canWrite()) {
                    treeDoc.findFile(outputFileName)?.delete()
                    val created = treeDoc.createFile("application/vnd.android.package-archive", outputFileName)
                    if (created != null) {
                        context.contentResolver.openOutputStream(created.uri)?.use { out ->
                            FileInputStream(signedApk).use { input ->
                                input.copyTo(out, 16 * 1024)
                            }
                        }
                        savedUriString = created.uri.toString()
                        savedDisplayLocation = "Seçilen klasör: ${treeDoc.name ?: "SAF"}/$outputFileName"
                        onLog(LogLevel.SUCCESS, PipelineStage.SAVING_OUTPUT, "Çıktı APK seçilen klasöre kaydedildi: $savedDisplayLocation")
                    }
                }
            } catch (ex: Exception) {
                onLog(LogLevel.WARN, PipelineStage.SAVING_OUTPUT, "Seçilen klasöre yazılamadı (${ex.message}); APK uygulama içi güvenli alanda hazır.")
            }
        }

        val translatedCount = translatedItems.count {
            it.origin == TranslationOrigin.ONLINE_API ||
                it.origin == TranslationOrigin.MLKIT_OFFLINE ||
                it.origin == TranslationOrigin.GEMINI_AI ||
                it.origin == TranslationOrigin.MANUAL_EDIT
        }
        val cachedCount = translatedItems.count { it.origin == TranslationOrigin.CACHE }
        val skippedCount = translatedItems.count { it.origin == TranslationOrigin.PRESERVED }

        historyDao.insertJob(
            JobHistoryEntity(
                apkFileName = outputFileName,
                packageName = session.metadata.packageName,
                sourceLang = sourceLanguage.code,
                targetLang = targetLanguage.code,
                translatedCount = translatedCount + cachedCount,
                inputSha256 = session.metadata.sha256,
                outputSha256 = outputSha256,
                savedLocation = savedDisplayLocation
            )
        )

        unsignedApk.delete()

        val warnings = mutableListOf<String>().apply {
            addAll(session.metadata.securityWarnings)
            addAll(verifyReport.warnings)
        }

        onStage(PipelineStage.COMPLETED)
        OutputApkResult(
            workspaceFilePath = signedApk.absolutePath,
            savedUriString = savedUriString,
            savedDisplayLocation = savedDisplayLocation,
            fileName = outputFileName,
            fileSizeBytes = signedApk.length(),
            outputSha256 = outputSha256,
            originalSha256Before = integrityReport.initialSha256,
            originalSha256After = integrityReport.finalSha256,
            originalIntegrityOk = integrityReport.isUnchanged,
            signatureVerified = verifyReport.isVerified,
            v1Verified = verifyReport.v1Verified,
            v2Verified = verifyReport.v2Verified,
            v3Verified = verifyReport.v3Verified,
            signerCertSha256 = verifyReport.signerCertSha256,
            translatedCount = translatedCount,
            cachedCount = cachedCount,
            skippedCount = skippedCount,
            failedCount = 0,
            warnings = warnings.distinct()
        )
    }

    suspend fun saveFileToCustomDocumentUri(sourceFile: File, targetDocumentUri: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(targetDocumentUri)
            ?: throw IllegalStateException("Hedef dosya yazılamıyor.")
        out.use { output ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output, 16 * 1024)
            }
        }
    }

    private fun buildProtectionSignature(config: TranslationProtectionConfig): String {
        val raw = "${config.preservePlaceholders}|${config.preserveMarkupTags}|${config.excludeTechnicalTerms}|${config.excludedTerms.joinToString(",")}"
        return SecurityGuards.computeSha256(raw.toByteArray(Charsets.UTF_8)).take(12)
    }

    private fun buildCacheKey(src: String, tgt: String, rulesSig: String, text: String): String {
        val hash = SecurityGuards.computeSha256(text.toByteArray(Charsets.UTF_8)).take(20)
        return "$src|$tgt|$rulesSig|$hash"
    }

    private fun yesNoTr(v: Boolean) = if (v) "Açık" else "Kapalı"
}
