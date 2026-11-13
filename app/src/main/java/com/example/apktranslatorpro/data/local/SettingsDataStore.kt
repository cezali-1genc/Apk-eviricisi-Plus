package com.example.apktranslatorpro.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.apktranslatorpro.core.security.KeystoreSecretStorage
import com.example.apktranslatorpro.core.text.TranslationProtectionConfig
import com.example.apktranslatorpro.domain.model.LanguageResourceScopeConfig
import com.example.apktranslatorpro.domain.model.OnlineApiConfig
import com.example.apktranslatorpro.domain.model.OnlineProviderPreset
import com.example.apktranslatorpro.domain.model.TranslationMode
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "apk_translator_settings"
)

data class AppUserSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val translationMode: TranslationMode = TranslationMode.AUTO,
    val onlineApiConfig: OnlineApiConfig = OnlineApiConfig(),
    val protectionConfig: TranslationProtectionConfig = TranslationProtectionConfig(),
    val resourceScopeConfig: LanguageResourceScopeConfig = LanguageResourceScopeConfig(),
    val outputTreeUri: String? = null,
    val outputTreeLabel: String = "Uygulama İçi Güvenli Klasör (outputs/)",
    val selectedApkUri: String? = null
)

class SettingsDataStore(
    private val context: Context,
    private val secretStorage: KeystoreSecretStorage = KeystoreSecretStorage()
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TRANSLATION_MODE = stringPreferencesKey("translation_mode")
        val ONLINE_PRESET = stringPreferencesKey("online_preset")
        val ENCRYPTED_API_URL = stringPreferencesKey("encrypted_api_url")
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        val CUSTOM_HEADER = stringPreferencesKey("custom_header")
        val OUTPUT_TREE_URI = stringPreferencesKey("output_tree_uri")
        val OUTPUT_TREE_LABEL = stringPreferencesKey("output_tree_label")
        val PRESERVE_PLACEHOLDERS = booleanPreferencesKey("preserve_placeholders")
        val PRESERVE_MARKUP_TAGS = booleanPreferencesKey("preserve_markup_tags")
        val EXCLUDE_TECH_TERMS = booleanPreferencesKey("exclude_tech_terms")
        val EXCLUDED_TERMS_RAW = stringPreferencesKey("excluded_terms_raw")
        val SELECTED_APK_URI = stringPreferencesKey("selected_apk_uri")
        val STRICT_STRINGS_ONLY = booleanPreferencesKey("strict_strings_only")
        val TRANSLATE_DEFAULT_STRINGS = booleanPreferencesKey("translate_default_strings")
        val TRANSLATE_VALUES_TR = booleanPreferencesKey("translate_values_tr")
        val TRANSLATE_VALUES_EN = booleanPreferencesKey("translate_values_en")
    }

    val settingsFlow: Flow<AppUserSettings> = context.appSettingsDataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
            ?: AppThemeMode.SYSTEM
        val mode = prefs[Keys.TRANSLATION_MODE]?.let { runCatching { TranslationMode.valueOf(it) }.getOrNull() }
            ?: TranslationMode.AUTO
        val preset = prefs[Keys.ONLINE_PRESET]?.let { runCatching { OnlineProviderPreset.valueOf(it) }.getOrNull() }
            ?: OnlineProviderPreset.LIBRE_TRANSLATE
        val url = prefs[Keys.ENCRYPTED_API_URL]?.let { secretStorage.decrypt(it) }
            ?.takeIf { it.isNotBlank() }
            ?: preset.defaultUrl
        val key = prefs[Keys.ENCRYPTED_API_KEY]?.let { secretStorage.decrypt(it) } ?: ""
        val header = prefs[Keys.CUSTOM_HEADER] ?: "Authorization"

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

        AppUserSettings(
            themeMode = theme,
            translationMode = mode,
            onlineApiConfig = OnlineApiConfig(
                preset = preset,
                endpointUrl = url,
                apiKey = key,
                customHeaderName = header
            ),
            protectionConfig = TranslationProtectionConfig(
                preservePlaceholders = preservePlaceholders,
                preserveMarkupTags = preserveMarkup,
                excludeTechnicalTerms = excludeTech,
                excludedTerms = terms
            ),
            resourceScopeConfig = scopeConfig,
            outputTreeUri = prefs[Keys.OUTPUT_TREE_URI],
            outputTreeLabel = prefs[Keys.OUTPUT_TREE_LABEL] ?: "Uygulama İçi Güvenli Klasör (outputs/)",
            selectedApkUri = prefs[Keys.SELECTED_APK_URI]
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.appSettingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setTranslationMode(mode: TranslationMode) {
        context.appSettingsDataStore.edit { it[Keys.TRANSLATION_MODE] = mode.name }
    }

    suspend fun saveOnlineApiConfig(config: OnlineApiConfig) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.ONLINE_PRESET] = config.preset.name
            prefs[Keys.ENCRYPTED_API_URL] = secretStorage.encrypt(config.endpointUrl.trim())
            prefs[Keys.ENCRYPTED_API_KEY] = secretStorage.encrypt(config.apiKey.trim())
            prefs[Keys.CUSTOM_HEADER] = config.customHeaderName.trim().ifBlank { "Authorization" }
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

    suspend fun saveResourceScopeConfig(config: LanguageResourceScopeConfig) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.STRICT_STRINGS_ONLY] = config.strictStringsOnly
            prefs[Keys.TRANSLATE_DEFAULT_STRINGS] = config.translateDefaultStrings
            prefs[Keys.TRANSLATE_VALUES_TR] = config.translateValuesTr
            prefs[Keys.TRANSLATE_VALUES_EN] = config.translateValuesEn
        }
    }
}
