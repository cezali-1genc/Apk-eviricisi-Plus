package com.example.apktranslatorpro.core.apk

import com.example.apktranslatorpro.core.text.PlaceholderProtector
import com.example.apktranslatorpro.core.text.TranslationProtectionConfig
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.TranslationOrigin

data class RecoveryActionDetail(
    val key: String,
    val reason: String,
    val restoredText: String
)

data class RecoveryValidationReport(
    val totalChecked: Int,
    val autoRecoveredCount: Int,
    val turkishCharEntriesCount: Int,
    val longTextWarningCount: Int,
    val arscHeaderHealthy: Boolean,
    val repairedEntries: List<RecoveryActionDetail>,
    val warnings: List<String>
) {
    val isHealthy: Boolean
        get() = arscHeaderHealthy
}

/**
 * Post-translation & pre-ARSC-rebuild safety validator.
 * Detects and automatically rolls back crash-inducing translation outputs (broken `%s`/`%d`/`%1$s` format
 * specifiers -> `IllegalFormatException`, unbalanced HTML/XML tags -> `InflateException`, empty required
 * labels, or corrupted ARSC header) so unstable APKs are never emitted.
 */
object ApkRecoveryValidator {

    private val FORMAT_SPECIFIER_REGEX = Regex("""%(?:\d+\$)?[-#+ 0,(]*(?:\d+)?(?:\.\d+)?[a-zA-Z%]""")
    private val HTML_TAG_REGEX = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)\b[^>]*?(/?)>""")
    private val TURKISH_CHAR_REGEX = Regex("""[çğıİöşüÇĞÖŞÜ]""")

    fun validateAndAutoRecover(
        items: List<ResourceEntryItem>,
        protectionConfig: TranslationProtectionConfig = TranslationProtectionConfig(),
        arscBytes: ByteArray? = null
    ): Pair<List<ResourceEntryItem>, RecoveryValidationReport> {
        val repaired = mutableListOf<RecoveryActionDetail>()
        val warnings = mutableListOf<String>()
        var turkishCharEntries = 0
        var longTextWarnings = 0

        val validatedItems = items.map { item ->
            val candidate = item.translatedText
            if (candidate.isBlank() && item.originalText.isNotBlank() && item.origin != TranslationOrigin.PENDING) {
                repaired.add(
                    RecoveryActionDetail(
                        key = item.key,
                        reason = "Boş kalan zorunlu metin orijinaliyle geri yüklendi",
                        restoredText = item.originalText
                    )
                )
                return@map item.copy(
                    translatedText = item.originalText,
                    origin = TranslationOrigin.PRESERVED
                )
            }

            if (candidate.isBlank()) {
                return@map item
            }

            if (TURKISH_CHAR_REGEX.containsMatchIn(candidate)) {
                turkishCharEntries++
            }

            if (item.originalText.length in 3..45 && candidate.length > (item.originalText.length * 2.5) && candidate.length > 36) {
                longTextWarnings++
            }

            // 1. Validate printf/String.format specifiers parity (excludes literal %%)
            val origFormatSpecs = extractFormatSpecifiers(item.originalText)
            var currentText = candidate
            var currentFormatSpecs = extractFormatSpecifiers(currentText)

            if (origFormatSpecs != currentFormatSpecs) {
                val repairedText = PlaceholderProtector.repairMissingPlaceholders(
                    original = item.originalText,
                    translated = currentText,
                    config = protectionConfig
                )
                currentText = repairedText
                currentFormatSpecs = extractFormatSpecifiers(currentText)

                if (origFormatSpecs != currentFormatSpecs) {
                    repaired.add(
                        RecoveryActionDetail(
                            key = item.key,
                            reason = "Format parametresi uyuşmazlığı (${origFormatSpecs.joinToString()} != ${currentFormatSpecs.joinToString()}) — çökme önlendi",
                            restoredText = item.originalText
                        )
                    )
                    return@map item.copy(
                        translatedText = item.originalText,
                        origin = TranslationOrigin.PRESERVED
                    )
                }
            }

            // 2. Validate HTML/XML markup balance to prevent InflateException / SAX failure
            if (!hasBalancedMarkupTags(item.originalText, currentText)) {
                repaired.add(
                    RecoveryActionDetail(
                        key = item.key,
                        reason = "Bozuk XML/HTML etiketi tespit edildi — orijinal metne geri dönüldü",
                        restoredText = item.originalText
                    )
                )
                return@map item.copy(
                    translatedText = item.originalText,
                    origin = TranslationOrigin.PRESERVED
                )
            }

            // 3. Guard against stray unescaped XML ampersand or raw angle bracket inside plain strings
            if (hasBrokenXmlAngleBrackets(currentText)) {
                val sanitized = currentText.replace("<", "&lt;").replace(">", "&gt;")
                if (hasBrokenXmlAngleBrackets(sanitized)) {
                    repaired.add(
                        RecoveryActionDetail(
                            key = item.key,
                            reason = "Geçersiz XML karakter dizisi tespit edildi",
                            restoredText = item.originalText
                        )
                    )
                    return@map item.copy(
                        translatedText = item.originalText,
                        origin = TranslationOrigin.PRESERVED
                    )
                } else {
                    currentText = sanitized
                }
            }

            if (currentText != item.translatedText) {
                item.copy(translatedText = currentText)
            } else {
                item
            }
        }

        val arscHealthy = arscBytes == null || verifyArscTableHeader(arscBytes)
        if (!arscHealthy) {
            warnings.add("KRİTİK: Yeniden derlenen resources.arsc başlığı (RES_TABLE_TYPE 0x0002) geçersiz!")
        }
        if (repaired.isNotEmpty()) {
            warnings.add("${repaired.size} hatalı/riskli çeviri otomatik kurtarıldı (çökme koruması).")
        }
        if (longTextWarnings > 0) {
            warnings.add("$longTextWarnings metin orijinalinden belirgin şekilde uzun (buton taşma riski).")
        }

        return validatedItems to RecoveryValidationReport(
            totalChecked = items.size,
            autoRecoveredCount = repaired.size,
            turkishCharEntriesCount = turkishCharEntries,
            longTextWarningCount = longTextWarnings,
            arscHeaderHealthy = arscHealthy,
            repairedEntries = repaired,
            warnings = warnings
        )
    }

    fun verifyArscTableHeader(arscBytes: ByteArray): Boolean {
        if (arscBytes.size < 12) return false
        val type = (arscBytes[0].toInt() and 0xFF) or ((arscBytes[1].toInt() and 0xFF) shl 8)
        val headerSize = (arscBytes[2].toInt() and 0xFF) or ((arscBytes[3].toInt() and 0xFF) shl 8)
        val totalSize = (arscBytes[4].toInt() and 0xFF) or
            ((arscBytes[5].toInt() and 0xFF) shl 8) or
            ((arscBytes[6].toInt() and 0xFF) shl 16) or
            ((arscBytes[7].toInt() and 0xFF) shl 24)
        return type == 0x0002 && headerSize >= 12 && totalSize == arscBytes.size
    }

    fun containsTurkishCharacters(text: String): Boolean =
        TURKISH_CHAR_REGEX.containsMatchIn(text)

    fun isOverlyLongTranslation(originalText: String, translatedText: String): Boolean =
        originalText.length in 3..45 &&
            translatedText.length > (originalText.length * 2.5) &&
            translatedText.length > 36

    private fun extractFormatSpecifiers(text: String): List<String> =
        FORMAT_SPECIFIER_REGEX.findAll(text)
            .map { it.value }
            .filter { it != "%%" }
            .toList()

    private fun hasBalancedMarkupTags(original: String, translated: String): Boolean {
        val origTags = extractTagSignature(original)
        if (origTags.isEmpty()) {
            // If original had no HTML tags, translated shouldn't introduce unclosed tags
            return extractUnbalancedStack(translated).isEmpty()
        }
        val trTags = extractTagSignature(translated)
        if (extractUnbalancedStack(translated).isNotEmpty()) return false
        return origTags == trTags
    }

    private fun extractTagSignature(text: String): List<String> =
        HTML_TAG_REGEX.findAll(text).map { match ->
            val closing = match.groupValues[1]
            val name = match.groupValues[2].lowercase()
            val selfClose = match.groupValues[3]
            "$closing$name$selfClose"
        }.toList()

    private fun extractUnbalancedStack(text: String): List<String> {
        val stack = ArrayDeque<String>()
        HTML_TAG_REGEX.findAll(text).forEach { match ->
            val isClosing = match.groupValues[1] == "/"
            val name = match.groupValues[2].lowercase()
            val isSelfClosing = match.groupValues[3] == "/"
            if (isSelfClosing || name == "br" || name == "hr") return@forEach
            if (!isClosing) {
                stack.addLast(name)
            } else {
                if (stack.isEmpty() || stack.last() != name) {
                    stack.addLast("/$name")
                } else {
                    stack.removeLast()
                }
            }
        }
        return stack.toList()
    }

    private fun hasBrokenXmlAngleBrackets(text: String): Boolean {
        val withoutKnownTags = HTML_TAG_REGEX.replace(text, "")
        return withoutKnownTags.contains('<') || withoutKnownTags.contains('>')
    }
}
