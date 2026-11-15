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
    CHINESE_TRADITIONAL("zh-TW", "Çince (Geleneksel)", "繁體中文", "zh", "TW", TranslateLanguage.CHINESE, false),
    PORTUGUESE("pt", "Portekizce", "Português", "pt", "", TranslateLanguage.PORTUGUESE, false),
    PERSIAN("fa", "Farsça", "فارسی", "fa", "", TranslateLanguage.PERSIAN, true),
    AZERBAIJANI("az", "Azerice", "Azərbaycan", "az", "", null, false),
    DUTCH("nl", "Hollandaca", "Nederlands", "nl", "", TranslateLanguage.DUTCH, false),
    POLISH("pl", "Lehçe", "Polski", "pl", "", TranslateLanguage.POLISH, false),
    UKRAINIAN("uk", "Ukraynaca", "Українська", "uk", "", TranslateLanguage.UKRAINIAN, false),
    HINDI("hi", "Hintçe", "हिन्दी", "hi", "", TranslateLanguage.HINDI, false),
    HEBREW("he", "İbranice", "עברית", "iw", "", TranslateLanguage.HEBREW, true),
    INDONESIAN("id", "Endonezce", "Bahasa Indonesia", "in", "", TranslateLanguage.INDONESIAN, false),
    AFRIKAANS("af", "Afrikaanca", "Afrikaans", "af", "", TranslateLanguage.AFRIKAANS, false),
    ALBANIAN("sq", "Arnavutça", "Shqip", "sq", "", TranslateLanguage.ALBANIAN, false),
    AMHARIC("am", "Amharca", "አማርኛ", "am", "", null, false),
    ARMENIAN("hy", "Ermenice", "Հայերեն", "hy", "", null, false),
    ASSAMESE("as", "Assamca", "অসমীয়া", "as", "", null, false),
    BASQUE("eu", "Baskça", "Euskara", "eu", "", null, false),
    BELARUSIAN("be", "Belarusça", "Беларуская", "be", "", TranslateLanguage.BELARUSIAN, false),
    BENGALI("bn", "Bengalce", "বাংলা", "bn", "", TranslateLanguage.BENGALI, false),
    BOSNIAN("bs", "Boşnakça", "Bosanski", "bs", "", null, false),
    BULGARIAN("bg", "Bulgarca", "Български", "bg", "", TranslateLanguage.BULGARIAN, false),
    BURMESE("my", "Birmanca", "မြန်မာ", "my", "", null, false),
    CATALAN("ca", "Katalanca", "Català", "ca", "", TranslateLanguage.CATALAN, false),
    CEBUANO("ceb", "Sebuanca", "Cebuano", "ceb", "", null, false),
    CROATIAN("hr", "Hırvatça", "Hrvatski", "hr", "", TranslateLanguage.CROATIAN, false),
    CZECH("cs", "Çekçe", "Čeština", "cs", "", TranslateLanguage.CZECH, false),
    DANISH("da", "Danca", "Dansk", "da", "", TranslateLanguage.DANISH, false),
    ESPERANTO("eo", "Esperanto", "Esperanto", "eo", "", TranslateLanguage.ESPERANTO, false),
    ESTONIAN("et", "Estonca", "Eesti", "et", "", TranslateLanguage.ESTONIAN, false),
    FILIPINO("fil", "Filipince", "Filipino", "fil", "", TranslateLanguage.TAGALOG, false),
    FINNISH("fi", "Fince", "Suomi", "fi", "", TranslateLanguage.FINNISH, false),
    GALICIAN("gl", "Galiçyaca", "Galego", "gl", "", TranslateLanguage.GALICIAN, false),
    GEORGIAN("ka", "Gürcüce", "ქართული", "ka", "", TranslateLanguage.GEORGIAN, false),
    GREEK("el", "Yunanca", "Ελληνικά", "el", "", TranslateLanguage.GREEK, false),
    GUJARATI("gu", "Gucaratça", "ગુજરાતી", "gu", "", TranslateLanguage.GUJARATI, false),
    HAITIAN_CREOLE("ht", "Haiti Kreolü", "Kreyòl", "ht", "", TranslateLanguage.HAITIAN_CREOLE, false),
    HAUSA("ha", "Hausaca", "Hausa", "ha", "", null, false),
    HAWAIIAN("haw", "Hawaiice", "ʻŌlelo Hawaiʻi", "haw", "", null, false),
    HMONG("hmn", "Hmongca", "Hmoob", "hmn", "", null, false),
    HUNGARIAN("hu", "Macarca", "Magyar", "hu", "", TranslateLanguage.HUNGARIAN, false),
    ICELANDIC("is", "İzlandaca", "Íslenska", "is", "", TranslateLanguage.ICELANDIC, false),
    IGBO("ig", "İgboca", "Igbo", "ig", "", null, false),
    IRISH("ga", "İrlandaca", "Gaeilge", "ga", "", TranslateLanguage.IRISH, false),
    JAVANESE("jv", "Cavaca", "Basa Jawa", "jv", "", null, false),
    KANNADA("kn", "Kannadaca", "ಕನ್ನಡ", "kn", "", TranslateLanguage.KANNADA, false),
    KAZAKH("kk", "Kazakça", "Қазақша", "kk", "", null, false),
    KHMER("km", "Kmerce", "ខ្មែរ", "km", "", null, false),
    KINYARWANDA("rw", "Kinyarvanda", "Kinyarwanda", "rw", "", null, false),
    KURDISH("ku", "Kürtçe", "Kurdî", "ku", "", null, false),
    KYRGYZ("ky", "Kırgızca", "Кыргызча", "ky", "", null, false),
    LAO("lo", "Laoca", "ລາວ", "lo", "", null, false),
    LATIN("la", "Latince", "Latina", "la", "", null, false),
    LATVIAN("lv", "Letonca", "Latviešu", "lv", "", TranslateLanguage.LATVIAN, false),
    LITHUANIAN("lt", "Litvanca", "Lietuvių", "lt", "", TranslateLanguage.LITHUANIAN, false),
    LUXEMBOURGISH("lb", "Lüksemburgca", "Lëtzebuergesch", "lb", "", null, false),
    MACEDONIAN("mk", "Makedonca", "Македонски", "mk", "", TranslateLanguage.MACEDONIAN, false),
    MALAGASY("mg", "Malgaşça", "Malagasy", "mg", "", null, false),
    MALAY("ms", "Malayca", "Bahasa Melayu", "ms", "", TranslateLanguage.MALAY, false),
    MALAYALAM("ml", "Malayalamca", "മലയാളം", "ml", "", null, false),
    MALTESE("mt", "Maltaca", "Malti", "mt", "", TranslateLanguage.MALTESE, false),
    MAORI("mi", "Maorice", "Māori", "mi", "", null, false),
    MARATHI("mr", "Marathice", "मराठी", "mr", "", TranslateLanguage.MARATHI, false),
    MONGOLIAN("mn", "Moğolca", "Монгол", "mn", "", null, false),
    NEPALI("ne", "Nepalce", "नेपाली", "ne", "", null, false),
    NORWEGIAN("no", "Norveççe", "Norsk", "nb", "", TranslateLanguage.NORWEGIAN, false),
    ODIA("or", "Oriyaca", "ଓଡ଼ିଆ", "or", "", null, false),
    PASHTO("ps", "Peştuca", "پښتو", "ps", "", null, true),
    PUNJABI("pa", "Pencapça", "ਪੰਜਾਬੀ", "pa", "", null, false),
    ROMANIAN("ro", "Rumence", "Română", "ro", "", TranslateLanguage.ROMANIAN, false),
    SAMOAN("sm", "Samoaca", "Gagana Samoa", "sm", "", null, false),
    SCOTS_GAELIC("gd", "İskoç Galcesi", "Gàidhlig", "gd", "", null, false),
    SERBIAN("sr", "Sırpça", "Српски", "sr", "", null, false),
    SESOTHO("st", "Sothoca", "Sesotho", "st", "", null, false),
    SHONA("sn", "Şonaca", "chiShona", "sn", "", null, false),
    SINDHI("sd", "Sindhi", "سنڌي", "sd", "", null, true),
    SINHALA("si", "Seylanca", "සිංහල", "si", "", null, false),
    SLOVAK("sk", "Slovakça", "Slovenčina", "sk", "", TranslateLanguage.SLOVAK, false),
    SLOVENIAN("sl", "Slovence", "Slovenščina", "sl", "", TranslateLanguage.SLOVENIAN, false),
    SOMALI("so", "Somalice", "Soomaali", "so", "", null, false),
    SUNDANESE("su", "Sundaca", "Basa Sunda", "su", "", null, false),
    SWAHILI("sw", "Svahilice", "Kiswahili", "sw", "", TranslateLanguage.SWAHILI, false),
    SWEDISH("sv", "İsveççe", "Svenska", "sv", "", TranslateLanguage.SWEDISH, false),
    TAJIK("tg", "Tacikçe", "Тоҷикӣ", "tg", "", null, false),
    TAMIL("ta", "Tamilce", "தமிழ்", "ta", "", TranslateLanguage.TAMIL, false),
    TATAR("tt", "Tatarca", "Татарча", "tt", "", null, false),
    TELUGU("te", "Teluguca", "తెలుగు", "te", "", TranslateLanguage.TELUGU, false),
    THAI("th", "Tayca", "ไทย", "th", "", TranslateLanguage.THAI, false),
    TURKMEN("tk", "Türkmence", "Türkmençe", "tk", "", null, false),
    URDU("ur", "Urduca", "اردو", "ur", "", TranslateLanguage.URDU, true),
    UYGHUR("ug", "Uygurca", "ئۇيغۇرچە", "ug", "", null, true),
    UZBEK("uz", "Özbekçe", "Oʻzbekcha", "uz", "", null, false),
    VIETNAMESE("vi", "Vietnamca", "Tiếng Việt", "vi", "", TranslateLanguage.VIETNAMESE, false),
    WELSH("cy", "Galce", "Cymraeg", "cy", "", TranslateLanguage.WELSH, false),
    XHOSA("xh", "Xhosaca", "isiXhosa", "xh", "", null, false),
    YIDDISH("yi", "Yidişçe", "ייִדיש", "yi", "", null, true),
    YORUBA("yo", "Yorubaca", "Yorùbá", "yo", "", null, false),
    ZULU("zu", "Zuluca", "isiZulu", "zu", "", null, false),
    MALAY_BRUNEI("ms-BN", "Brunei Malaycası", "Melayu", "ms", "BN", TranslateLanguage.MALAY, false),
    LATIN_AMERICA_ES("es-419", "İspanyolca (Latin Amerika)", "Español (LA)", "es", "", TranslateLanguage.SPANISH, false),
    BRAZILIAN_PT("pt-BR", "Portekizce (Brezilya)", "Português (BR)", "pt", "BR", TranslateLanguage.PORTUGUESE, false);

    val resourceFolderSuffix: String
        get() = when {
            this == AUTO -> ""
            androidRegion.isNotBlank() -> "$androidLang-r$androidRegion"
            else -> androidLang
        }

    companion object {
        val targetLanguages: List<LanguageOption> = entries.filter { it != AUTO }
        val mlKitSupportedLanguages: List<LanguageOption> = entries.filter { it.mlKitCode != null }
        val totalSupportedCount: Int = entries.count { it != AUTO }

        fun fromCode(code: String?): LanguageOption {
            if (code.isNullOrBlank()) return AUTO
            val normalized = code.lowercase().trim()
            return entries.firstOrNull {
                it.code.lowercase() == normalized ||
                    it.androidLang.lowercase() == normalized ||
                    (normalized == "zh-cn" && it == CHINESE_SIMPLIFIED) ||
                    (normalized == "he" && it == HEBREW) ||
                    (normalized == "id" && it == INDONESIAN)
            } ?: ENGLISH
        }
    }
}
