package com.example.apktranslatorpro.domain.model

import com.google.mlkit.nl.translate.TranslateLanguage

enum class LanguageOption(
    val code: String,
    val displayNameTr: String,
    val nativeName: String,
    val androidLang: String,
    val androidRegion: String = "",
    val mlKitCode: String?,
    val isRtl: Boolean = false
) {
    AUTO("auto", "Otomatik Algıla", "Auto Detect", "", "", null, false),
    TURKISH("tr", "Türkçe", "Türkçe", "tr", "", TranslateLanguage.TURKISH, false),
    ENGLISH("en", "İngilizce", "English", "en", "", TranslateLanguage.ENGLISH, false),
    GERMAN("de", "Almanca", "Deutsch", "de", "", TranslateLanguage.GERMAN, false),
    FRENCH("fr", "Fransızca", "Français", "fr", "", TranslateLanguage.FRENCH, false),
    ARABIC("ar", "Arapça", "العربية", "ar", "", TranslateLanguage.ARABIC, true),
    RUSSIAN("ru", "Rusça", "Русский", "ru", "", TranslateLanguage.RUSSIAN, false),
    SPANISH("es", "İspanyolca", "Español", "es", "", TranslateLanguage.SPANISH, false),
    ITALIAN("it", "İtalyanca", "Italiano", "it", "", TranslateLanguage.ITALIAN, false),
    JAPANESE("ja", "Japonca", "日本語", "ja", "", TranslateLanguage.JAPANESE, false),
    KOREAN("ko", "Korece", "한국어", "ko", "", TranslateLanguage.KOREAN, false),
    CHINESE_SIMPLIFIED("zh", "Çince (Basitleştirilmiş)", "简体中文", "zh", "CN", TranslateLanguage.CHINESE, false),
    PORTUGUESE("pt", "Portekizce", "Português", "pt", "", TranslateLanguage.PORTUGUESE, false),
    HINDI("hi", "Hintçe", "हिन्दी", "hi", "", TranslateLanguage.HINDI, false),
    PERSIAN("fa", "Farsça", "فارسی", "fa", "", TranslateLanguage.PERSIAN, true),
    HEBREW("he", "İbranice", "עברית", "iw", "", TranslateLanguage.HEBREW, true),
    DUTCH("nl", "Felemenkçe", "Nederlands", "nl", "", TranslateLanguage.DUTCH, false),
    POLISH("pl", "Lehçe", "Polski", "pl", "", TranslateLanguage.POLISH, false),
    UKRAINIAN("uk", "Ukraynaca", "Українська", "uk", "", TranslateLanguage.UKRAINIAN, false),
    INDONESIAN("id", "Endonezce", "Bahasa Indonesia", "in", "", TranslateLanguage.INDONESIAN, false),
    AZERBAIJANI("az", "Azerbaycan Türkçesi", "Azərbaycan", "az", "", null, false);

    val resourceFolderSuffix: String
        get() = when {
            this == AUTO -> ""
            androidRegion.isNotBlank() -> "$androidLang-r$androidRegion"
            else -> androidLang
        }

    companion object {
        val targetLanguages: List<LanguageOption> = entries.filter { it != AUTO }
        val mlKitSupportedLanguages: List<LanguageOption> = entries.filter { it.mlKitCode != null }

        fun fromCode(code: String?): LanguageOption {
            if (code.isNullOrBlank()) return AUTO
            val normalized = code.lowercase().trim()
            return entries.firstOrNull {
                it.code == normalized ||
                    it.androidLang == normalized ||
                    (normalized == "zh-cn" && it == CHINESE_SIMPLIFIED) ||
                    (normalized == "he" && it == HEBREW) ||
                    (normalized == "id" && it == INDONESIAN)
            } ?: ENGLISH
        }
    }
}
