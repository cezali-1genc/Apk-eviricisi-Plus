package com.example.apktranslatorpro.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.apktranslatorpro.core.security.KeystoreSecretStorage
import com.example.apktranslatorpro.core.text.TranslationProtectionConfig
import com.example.apktranslatorpro.domain.model.LanguageResourceScopeConfig
import com.example.apktranslatorpro.domain.model.MultiProviderApiConfig
import com.example.apktranslatorpro.domain.model.OnlineApiConfig
import com.example.apktranslatorpro.domain.model.OnlineProviderPreset
import com.example.apktranslatorpro.domain.model.ProviderEndpointConfig
import com.example.apktranslatorpro.domain.model.TranslationMode
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "apk_translator_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

enum class AppUiLanguage(val code: String, val label: String) {
    TR("tr", "Türkçe"),
    EN("en", "English"),
    DE("de", "Deutsch")
}

data class AppUserSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appUiLanguage: AppUiLanguage = AppUiLanguage.TR,
    val isGuestTrialActive: Boolean = false,
    val guestTranslationsUsed: Int = 0,
    val translationMode: TranslationMode = TranslationMode.AUTO,
    val onlineApiConfig: OnlineApiConfig = OnlineApiConfig(),
    val multiProviderConfig: MultiProviderApiConfig = MultiProviderApiConfig(),
    val protectionConfig: TranslationProtectionConfig = TranslationProtectionConfig(),
    val resourceScopeConfig: LanguageResourceScopeConfig = LanguageResourceScopeConfig(),
    val outputTreeUri: String? = null,
    val outputTreeLabel: String = "Uygulama İçi Güvenli Klasör (outputs/)",
    val selectedApkUri: String? = null,
    val batchApkUris: List<String> = emptyList()
) {
    val guestTranslationsRemaining: Int
        get() = (MAX_GUEST_TRIAL_TRANSLATIONS - guestTranslationsUsed).coerceAtLeast(0)

    val isGuestTrialLimitReached: Boolean
        get() = guestTranslationsUsed >= MAX_GUEST_TRIAL_TRANSLATIONS

    companion object {
        const val MAX_GUEST_TRIAL_TRANSLATIONS = 2
    }
}

class SettingsDataStore(
    private val context: Context,
    private val secretStorage: KeystoreSecretStorage = KeystoreSecretStorage()
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_UI_LANGUAGE = stringPreferencesKey("app_ui_language")
        val GUEST_TRIAL_ACTIVE = booleanPreferencesKey("guest_trial_active")
        val GUEST_TRANSLATIONS_USED = intPreferencesKey("guest_translations_used")
        val TRANSLATION_MODE = stringPreferencesKey("translation_mode")
        val ONLINE_PRESET = stringPreferencesKey("online_preset")
        val ENCRYPTED_API_URL = stringPreferencesKey("encrypted_api_url")
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        val CUSTOM_HEADER = stringPreferencesKey("custom_header")
        val MULTI_PRIMARY_PRESET = stringPreferencesKey("multi_primary_preset")
        val MULTI_AUTO_FAILOVER = booleanPreferencesKey("multi_auto_failover")
        val OUTPUT_TREE_URI = stringPreferencesKey("output_tree_uri")
        val OUTPUT_TREE_LABEL = stringPreferencesKey("output_tree_label")
        val PRESERVE_PLACEHOLDERS = booleanPreferencesKey("preserve_placeholders")
        val PRESERVE_MARKUP_TAGS = booleanPreferencesKey("preserve_markup_tags")
        val EXCLUDE_TECH_TERMS = booleanPreferencesKey("exclude_tech_terms")
        val EXCLUDED_TERMS_RAW = stringPreferencesKey("excluded_terms_raw")
        val SELECTED_APK_URI = stringPreferencesKey("selected_apk_uri")
        val BATCH_APK_URIS_RAW = stringPreferencesKey("batch_apk_uris_raw")
        val STRICT_STRINGS_ONLY = booleanPreferencesKey("strict_strings_only")
        val TRANSLATE_DEFAULT_STRINGS = booleanPreferencesKey("translate_default_strings")
        val TRANSLATE_VALUES_TR = booleanPreferencesKey("translate_values_tr")
        val TRANSLATE_VALUES_EN = booleanPreferencesKey("translate_values_en")

        fun providerUrlKey(preset: OnlineProviderPreset) = stringPreferencesKey("provider_${preset.name}_url")
        fun providerApiKey(preset: OnlineProviderPreset) = stringPreferencesKey("provider_${preset.name}_key")
        fun providerExtraKey(preset: OnlineProviderPreset) = stringPreferencesKey("provider_${preset.name}_extra")
        fun providerEnabledKey(preset: OnlineProviderPreset) = booleanPreferencesKey("provider_${preset.name}_enabled")
    }

    val settingsFlow: Flow<AppUserSettings> = context.appSettingsDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs ->
            val theme = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.SYSTEM
            val uiLang = prefs[Keys.APP_UI_LANGUAGE]?.let { runCatching { AppUiLanguage.valueOf(it) }.getOrNull() }
                ?: AppUiLanguage.TR
            val isGuestActive = prefs[Keys.GUEST_TRIAL_ACTIVE] ?: false
            val guestUsed = prefs[Keys.GUEST_TRANSLATIONS_USED] ?: 0
            val mode = prefs[Keys.TRANSLATION_MODE]?.let { runCatching { TranslationMode.valueOf(it) }.getOrNull() }
                ?: TranslationMode.AUTO

            val legacyPreset = prefs[Keys.ONLINE_PRESET]?.let { runCatching { OnlineProviderPreset.valueOf(it) }.getOrNull() }
                ?: OnlineProviderPreset.LIBRE_TRANSLATE
            val legacyUrl = prefs[Keys.ENCRYPTED_API_URL]?.let { runCatching { secretStorage.decrypt(it) }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
                ?: legacyPreset.defaultUrl
            val legacyKey = prefs[Keys.ENCRYPTED_API_KEY]?.let { runCatching { secretStorage.decrypt(it) }.getOrNull() } ?: ""
            val legacyHeader = prefs[Keys.CUSTOM_HEADER] ?: "Authorization"

            val primaryPreset = prefs[Keys.MULTI_PRIMARY_PRESET]
                ?.let { runCatching { OnlineProviderPreset.valueOf(it) }.getOrNull() }
                ?: legacyPreset
            val autoFailover = prefs[Keys.MULTI_AUTO_FAILOVER] ?: true

            val providersMap = OnlineProviderPreset.entries.associateWith { preset ->
                val storedUrl = prefs[Keys.providerUrlKey(preset)]
                    ?.let { runCatching { secretStorage.decrypt(it) }.getOrNull() }
                val storedKey = prefs[Keys.providerApiKey(preset)]
                    ?.let { runCatching { secretStorage.decrypt(it) }.getOrNull() }
                val storedExtra = prefs[Keys.providerExtraKey(preset)]
                    ?.let { runCatching { secretStorage.decrypt(it) }.getOrNull() }
                val storedEnabled = prefs[Keys.providerEnabledKey(preset)]

                val resolvedUrl = when {
                    !storedUrl.isNullOrBlank() -> storedUrl
                    preset == legacyPreset && legacyUrl.isNotBlank() -> legacyUrl
                    else -> preset.defaultUrl
                }
                val resolvedKey = when {
                    storedKey != null -> storedKey
                    preset == legacyPreset -> legacyKey
                    else -> ""
                }
                val resolvedExtra = when {
                    storedExtra != null -> storedExtra
                    preset == OnlineProviderPreset.CUSTOM_JSON -> legacyHeader
                    else -> ""
                }
                val resolvedEnabled = storedEnabled ?: (preset == primaryPreset || preset == OnlineProviderPreset.LIBRE_TRANSLATE)

                ProviderEndpointConfig(
                    preset = preset,
                    endpointUrl = resolvedUrl,
                    apiKey = resolvedKey,
                    regionOrHeader = resolvedExtra,
                    isEnabled = resolvedEnabled
                )
            }

            val multiConfig = MultiProviderApiConfig(
                primaryPreset = primaryPreset,
                autoFailoverEnabled = autoFailover,
                providers = providersMap
            )
            val activePrimaryOnline = multiConfig.primaryOnlineApiConfig()

            val preservePlaceholders = prefs[Keys.PRESERVE_PLACEHOLDERS] ?: true
            val preserveMarkup = prefs[Keys.PRESERVE_MARKUP_TAGS] ?: true
            val excludeTech = prefs[Keys.EXCLUDE_TECH_TERMS] ?: true
            val terms = prefs[Keys.EXCLUDED_TERMS_RAW]?.let { raw ->
                raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            } ?: TranslationProtectionConfig.DEFAULT_TECHNICAL_TERMS

            val scopeConfig = LanguageResourceScopeConfig(
                strictStringsOnly = prefs[Keys.STRICT_STRINGS_ONLY] ?: true,
                translateDefaultStrings = prefs[Keys.TRANSLATE_DEFAULT_STRINGS] ?: true,
                translateValuesTr = prefs[Keys.TRANSLATE_VALUES_TR] ?: true,
                translateValuesEn = prefs[Keys.TRANSLATE_VALUES_EN] ?: true
            )

            val batchUris = prefs[Keys.BATCH_APK_URIS_RAW]
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?: emptyList()

            AppUserSettings(
                themeMode = theme,
                appUiLanguage = uiLang,
                isGuestTrialActive = isGuestActive,
                guestTranslationsUsed = guestUsed,
                translationMode = mode,
                onlineApiConfig = activePrimaryOnline,
                multiProviderConfig = multiConfig,
                protectionConfig = TranslationProtectionConfig(
                    preservePlaceholders = preservePlaceholders,
                    preserveMarkupTags = preserveMarkup,
                    excludeTechnicalTerms = excludeTech,
                    excludedTerms = terms
                ),
                resourceScopeConfig = scopeConfig,
                outputTreeUri = prefs[Keys.OUTPUT_TREE_URI],
                outputTreeLabel = prefs[Keys.OUTPUT_TREE_LABEL] ?: "Uygulama İçi Güvenli Klasör (outputs/)",
                selectedApkUri = prefs[Keys.SELECTED_APK_URI],
                batchApkUris = batchUris
            )
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.appSettingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAppUiLanguage(language: AppUiLanguage) {
        context.appSettingsDataStore.edit { it[Keys.APP_UI_LANGUAGE] = language.name }
    }

    suspend fun setGuestTrialActive(active: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GUEST_TRIAL_ACTIVE] = active }
    }

    suspend fun incrementGuestTranslationsUsed() {
        context.appSettingsDataStore.edit { prefs ->
            val current = prefs[Keys.GUEST_TRANSLATIONS_USED] ?: 0
            prefs[Keys.GUEST_TRANSLATIONS_USED] = current + 1
        }
    }

    suspend fun setTranslationMode(mode: TranslationMode) {
        context.appSettingsDataStore.edit { it[Keys.TRANSLATION_MODE] = mode.name }
    }

    suspend fun saveOnlineApiConfig(config: OnlineApiConfig) {
        val cleanUrl = config.endpointUrl.trim().ifBlank { config.preset.defaultUrl }
        val cleanKey = config.apiKey.trim()
        val cleanHeader = config.customHeaderName.trim().ifBlank { "Authorization" }
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.ONLINE_PRESET] = config.preset.name
            prefs[Keys.MULTI_PRIMARY_PRESET] = config.preset.name
            prefs[Keys.ENCRYPTED_API_URL] = secretStorage.encrypt(cleanUrl)
            prefs[Keys.ENCRYPTED_API_KEY] = secretStorage.encrypt(cleanKey)
            prefs[Keys.CUSTOM_HEADER] = cleanHeader

            prefs[Keys.providerUrlKey(config.preset)] = secretStorage.encrypt(cleanUrl)
            prefs[Keys.providerApiKey(config.preset)] = secretStorage.encrypt(cleanKey)
            prefs[Keys.providerExtraKey(config.preset)] = secretStorage.encrypt(cleanHeader)
            prefs[Keys.providerEnabledKey(config.preset)] = true
        }
    }

    suspend fun saveMultiProviderConfig(config: MultiProviderApiConfig) {
        val primaryCfg = config.resolveConfig(config.primaryPreset)
        val cleanPrimaryUrl = primaryCfg.endpointUrl.trim().ifBlank { config.primaryPreset.defaultUrl }
        val cleanPrimaryKey = primaryCfg.apiKey.trim()
        val cleanPrimaryExtra = primaryCfg.regionOrHeader.trim()

        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.MULTI_PRIMARY_PRESET] = config.primaryPreset.name
            prefs[Keys.MULTI_AUTO_FAILOVER] = config.autoFailoverEnabled

            // Keep legacy single-provider fields aligned with active primary provider
            prefs[Keys.ONLINE_PRESET] = config.primaryPreset.name
            prefs[Keys.ENCRYPTED_API_URL] = secretStorage.encrypt(cleanPrimaryUrl)
            prefs[Keys.ENCRYPTED_API_KEY] = secretStorage.encrypt(cleanPrimaryKey)
            prefs[Keys.CUSTOM_HEADER] = cleanPrimaryExtra.ifBlank { "Authorization" }

            OnlineProviderPreset.entries.forEach { preset ->
                val item = config.resolveConfig(preset)
                prefs[Keys.providerUrlKey(preset)] = secretStorage.encrypt(item.endpointUrl.trim().ifBlank { preset.defaultUrl })
                prefs[Keys.providerApiKey(preset)] = secretStorage.encrypt(item.apiKey.trim())
                prefs[Keys.providerExtraKey(preset)] = secretStorage.encrypt(item.regionOrHeader.trim())
                prefs[Keys.providerEnabledKey(preset)] = item.isEnabled
            }
        }
    }

    suspend fun saveProtectionConfig(config: TranslationProtectionConfig) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.PRESERVE_PLACEHOLDERS] = config.preservePlaceholders
            prefs[Keys.PRESERVE_MARKUP_TAGS] = config.preserveMarkupTags
            prefs[Keys.EXCLUDE_TECH_TERMS] = config.excludeTechnicalTerms
            prefs[Keys.EXCLUDED_TERMS_RAW] = config.excludedTerms
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .joinToString("\n")
        }
    }

    suspend fun setOutputDirectory(uriString: String?, displayLabel: String) {
        context.appSettingsDataStore.edit { prefs ->
            if (uriString == null) {
                prefs.remove(Keys.OUTPUT_TREE_URI)
            } else {
                prefs[Keys.OUTPUT_TREE_URI] = uriString
            }
            prefs[Keys.OUTPUT_TREE_LABEL] = displayLabel
        }
    }

    suspend fun setSelectedApkUri(uriString: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (uriString.isNullOrBlank()) {
                prefs.remove(Keys.SELECTED_APK_URI)
            } else {
                prefs[Keys.SELECTED_APK_URI] = uriString
            }
        }
    }

    suspend fun setBatchApkUris(uris: List<String>) {
        context.appSettingsDataStore.edit { prefs ->
            val cleaned = uris.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleaned.isEmpty()) {
                prefs.remove(Keys.BATCH_APK_URIS_RAW)
            } else {
                prefs[Keys.BATCH_APK_URIS_RAW] = cleaned.joinToString("\n")
            }
        }
    }

    suspend fun saveResourceScopeConfig(config: LanguageResourceScopeConfig) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.STRICT_STRINGS_ONLY] = config.strictStringsOnly
            prefs[Keys.TRANSLATE_DEFAULT_STRINGS] = config.translateDefaultStrings
            prefs[Keys.TRANSLATE_VALUES_TR] = config.translateValuesTr
            prefs[Keys.TRANSLATE_VALUES_EN] = config.translateValuesEn
        }
    }
}
