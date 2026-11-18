package com.example.apktranslatorpro.domain.model

import com.example.apktranslatorpro.core.apk.ApkFullInspectionReport

enum class ResourceKind(val tagName: String, val labelTr: String) {
    STRING("string", "Metin"),
    STRING_ARRAY("string-array", "Dizi"),
    PLURALS("plurals", "Çoğul")
}

enum class ResourceCategory(val labelTr: String) {
    GENERAL("Genel"),
    MENU("Menü"),
    ERROR("Hata"),
    BUTTON("Buton"),
    NOTIFICATION("Bildirim"),
    SETTINGS("Ayarlar"),
    SYSTEM("Sistem")
}

enum class TranslationOrigin(val labelTr: String) {
    PENDING("Bekliyor"),
    CACHE("Önbellek"),
    MLKIT_OFFLINE("Çevrimdışı ML Kit"),
    ONLINE_API("Çevrimiçi API"),
    GEMINI_AI("Gemini AI"),
    MANUAL_EDIT("Manuel Düzenleme"),
    PRESERVED("Korundu")
}

enum class TranslationMode(val labelTr: String, val descriptionTr: String) {
    AUTO("Otomatik", "Ağ ve API varsa çevrimiçi çevirir, kesilirse ML Kit çevrimdışıya geçer"),
    OFFLINE_ONLY("Çevrimdışı", "Yalnızca cihaz içi ML Kit dil paketlerini kullanır (veri göndermez)"),
    ONLINE_ONLY("Çevrimiçi", "Yapılandırılmış gerçek çeviri API uç noktasını kullanır"),
    GEMINI_AI("Gemini AI", "Firebase AI Logic (gemini-3.8-flash) ile bağlama duyarlı akıllı çeviri")
}

enum class OnlineProviderPreset(
    val displayName: String,
    val defaultUrl: String,
    val requiresKey: Boolean,
    val descriptionTr: String
) {
    LIBRE_TRANSLATE(
        displayName = "LibreTranslate",
        defaultUrl = "https://libretranslate.com/translate",
        requiresKey = false,
        descriptionTr = "Açık kaynak LibreTranslate / uyumlu sunucu (/translate)"
    ),
    GOOGLE_CLOUD_V2(
        displayName = "Google Cloud Translate v2",
        defaultUrl = "https://translation.googleapis.com/language/translate/v2",
        requiresKey = true,
        descriptionTr = "Google Cloud Translation Basic v2 REST API"
    ),
    MICROSOFT_TRANSLATOR(
        displayName = "Microsoft Translator",
        defaultUrl = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0",
        requiresKey = true,
        descriptionTr = "Azure Cognitive Services Microsoft Translator Text API v3"
    ),
    DEEPL(
        displayName = "DeepL API",
        defaultUrl = "https://api-free.deepl.com/v2/translate",
        requiresKey = true,
        descriptionTr = "DeepL v2 REST API (Free / Pro)"
    ),
    CUSTOM_JSON(
        displayName = "Özel REST API",
        defaultUrl = "",
        requiresKey = false,
        descriptionTr = "Bearer veya X-API-Key başlıklı özel JSON {text, source, target} uç noktası"
    )
}

enum class PipelineStage(val stepIndex: Int, val labelTr: String) {
    IDLE(0, "Hazır"),
    VERIFYING_INPUT(1, "Girdi SHA-256 ve güvenlik kontrolü"),
    ANALYZING_APK(2, "APK yapısı ve Manifest analizi"),
    EXTRACTING_RESOURCES(3, "resources.arsc ve XML kaynakları çıkarılıyor"),
    TRANSLATING(4, "Metinler çevriliyor"),
    REBUILDING_RESOURCES(5, "resources.arsc yeniden derleniyor"),
    PACKAGING_APK(6, "APK yeniden paketleniyor (4-byte hizalı)"),
    SIGNING_APK(7, "APK v1/v2/v3 anahtarla imzalanıyor"),
    VERIFYING_OUTPUT(8, "İmza ve bütünlük doğrulanıyor"),
    SAVING_OUTPUT(9, "Çıktı telefona kaydediliyor"),
    COMPLETED(10, "Tamamlandı"),
    ERROR(-1, "Hata oluştu")
}

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val stage: PipelineStage,
    val message: String
)

data class ResourceEntryItem(
    val id: Int,
    val kind: ResourceKind,
    val key: String,
    val subKey: String = "",
    val localeQualifier: String = "default",
    val originalText: String,
    val translatedText: String = "",
    val previousTranslatedText: String = "",
    val isLocked: Boolean = false,
    val protectedTokens: List<String> = emptyList(),
    val isTranslatable: Boolean = true,
    val origin: TranslationOrigin = TranslationOrigin.PENDING,
    val hasStyleSpans: Boolean = false
) {
    val uniqueKey: String
        get() = "${kind.name}:$id:$key:$subKey:$localeQualifier"
}

data class ApkMetadata(
    val fileName: String,
    val uriString: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val supportsRtl: Boolean,
    val detectedSourceLanguage: LanguageOption,
    val detectedConfidence: Float,
    val availableLocales: List<String>,
    val totalStringCount: Int,
    val totalArrayItemCount: Int,
    val totalPluralItemCount: Int,
    val hasExistingSignature: Boolean,
    val securityWarnings: List<String>,
    val manifestXmlPreview: String,
    val binaryXmlFiles: List<String> = emptyList(),
    val fullInspection: ApkFullInspectionReport? = null
)

data class OfflineModelState(
    val language: LanguageOption,
    val isDownloaded: Boolean,
    val isDownloading: Boolean = false,
    val statusMessage: String = ""
)

data class OutputApkResult(
    val workspaceFilePath: String,
    val savedUriString: String?,
    val savedDisplayLocation: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val outputSha256: String,
    val originalSha256Before: String,
    val originalSha256After: String,
    val originalIntegrityOk: Boolean,
    val signatureVerified: Boolean,
    val v1Verified: Boolean,
    val v2Verified: Boolean,
    val v3Verified: Boolean,
    val signerCertSha256: String,
    val translatedCount: Int,
    val cachedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val warnings: List<String>
)

data class OnlineApiConfig(
    val preset: OnlineProviderPreset = OnlineProviderPreset.LIBRE_TRANSLATE,
    val endpointUrl: String = OnlineProviderPreset.LIBRE_TRANSLATE.defaultUrl,
    val apiKey: String = "",
    val customHeaderName: String = "Authorization"
)

data class ProviderEndpointConfig(
    val preset: OnlineProviderPreset,
    val endpointUrl: String = preset.defaultUrl,
    val apiKey: String = "",
    val regionOrHeader: String = if (preset == OnlineProviderPreset.CUSTOM_JSON) "Authorization" else "",
    val isEnabled: Boolean = preset == OnlineProviderPreset.LIBRE_TRANSLATE
) {
    fun toOnlineApiConfig(): OnlineApiConfig = OnlineApiConfig(
        preset = preset,
        endpointUrl = endpointUrl.ifBlank { preset.defaultUrl },
        apiKey = apiKey,
        customHeaderName = regionOrHeader.ifBlank {
            if (preset == OnlineProviderPreset.CUSTOM_JSON) "Authorization" else ""
        }
    )

    fun isConfigured(): Boolean {
        val urlOk = endpointUrl.trim().isNotBlank() || preset.defaultUrl.isNotBlank()
        val keyOk = !preset.requiresKey || apiKey.trim().isNotBlank()
        return isEnabled && urlOk && keyOk
    }
}

data class MultiProviderApiConfig(
    val primaryPreset: OnlineProviderPreset = OnlineProviderPreset.LIBRE_TRANSLATE,
    val autoFailoverEnabled: Boolean = true,
    val providers: Map<OnlineProviderPreset, ProviderEndpointConfig> = defaultProviders()
) {
    fun resolveConfig(preset: OnlineProviderPreset): ProviderEndpointConfig =
        providers[preset] ?: ProviderEndpointConfig(preset = preset)

    fun primaryOnlineApiConfig(): OnlineApiConfig =
        resolveConfig(primaryPreset).toOnlineApiConfig()

    fun orderedFailoverCandidates(): List<ProviderEndpointConfig> {
        val fallbackOrder = listOf(
            OnlineProviderPreset.DEEPL,
            OnlineProviderPreset.GOOGLE_CLOUD_V2,
            OnlineProviderPreset.MICROSOFT_TRANSLATOR,
            OnlineProviderPreset.LIBRE_TRANSLATE,
            OnlineProviderPreset.CUSTOM_JSON
        )
        val ordered = mutableListOf<ProviderEndpointConfig>()
        val primary = resolveConfig(primaryPreset)
        if (primary.isEnabled) {
            ordered.add(primary)
        }
        if (autoFailoverEnabled) {
            for (preset in fallbackOrder) {
                if (preset == primaryPreset) continue
                val cfg = resolveConfig(preset)
                if (cfg.isEnabled && cfg.isConfigured()) {
                    ordered.add(cfg)
                }
            }
        }
        return ordered
    }

    companion object {
        fun defaultProviders(): Map<OnlineProviderPreset, ProviderEndpointConfig> =
            OnlineProviderPreset.entries.associateWith { preset ->
                ProviderEndpointConfig(
                    preset = preset,
                    endpointUrl = preset.defaultUrl,
                    apiKey = "",
                    regionOrHeader = if (preset == OnlineProviderPreset.CUSTOM_JSON) "Authorization" else "",
                    isEnabled = preset == OnlineProviderPreset.LIBRE_TRANSLATE
                )
            }
    }
}

data class ProviderRuntimeStatus(
    val preset: OnlineProviderPreset,
    val isQuotaExceeded: Boolean = false,
    val isTemporarilyDisabled: Boolean = false,
    val lastHttpCode: Int? = null,
    val lastErrorMessageTr: String = "",
    val lastSuccessMillis: Long? = null,
    val disabledUntilMillis: Long = 0L
)

data class BatchRunSummary(
    val modeLabel: String,
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val generatedFiles: List<String> = emptyList(),
    val failedItems: List<String> = emptyList()
)

data class LanguageResourceScopeConfig(
    val strictStringsOnly: Boolean = true,
    val translateDefaultStrings: Boolean = true,
    val translateValuesTr: Boolean = true,
    val translateValuesEn: Boolean = true
) {
    fun describeScopes(): String = buildList {
        if (translateDefaultStrings) add("res/values/strings.xml (default)")
        if (translateValuesTr) add("res/values-tr/strings.xml")
        if (translateValuesEn) add("res/values-en/strings.xml")
    }.ifEmpty { listOf("Hedef dil (values-xx)") }.joinToString(", ")
}
