package com.example.apktranslatorpro.core.text

data class ProtectedText(
    val maskedText: String,
    val tokens: List<String>
)

data class TranslationProtectionConfig(
    val preservePlaceholders: Boolean = true,
    val preserveMarkupTags: Boolean = true,
    val excludeTechnicalTerms: Boolean = true,
    val excludedTerms: List<String> = DEFAULT_TECHNICAL_TERMS
) {
    companion object {
        val DEFAULT_TECHNICAL_TERMS = listOf(
            "APK", "Android", "SDK", "API", "JSON", "XML", "HTTP", "HTTPS",
            "URL", "Wi-Fi", "Bluetooth", "USB", "GPS", "NFC", "QR", "SHA-256",
            "PDF", "MP3", "MP4", "PNG", "SVG", "PIN", "OTP", "ID"
        )
    }
}

object PlaceholderProtector {

    private val markupPatternSource =
        """</?[a-zA-Z][a-zA-Z0-9:_-]*(?:\s+[^<>]*)?/?>"""

    private val placeholderPatternSource = listOf(
        // Android / Java printf-style format specifiers (%1$s, %2$.2f, %s, %d, %%)
        """%(?:[1-9]\d*\$)?[-+# 0,(]*(?:\d+)?(?:\.\d+)?[a-zA-Z%]""",
        // Double curly / dollar / single curly template placeholders
        """\{\{[^{}]+\}\}""",
        """\$\{[^{}]+\}""",
        """\{[a-zA-Z0-9_.-]+\}""",
        // URLs
        """https?://[^\s<>"']+""",
        // Android resource references
        """@[a-zA-Z0-9_]+/[a-zA-Z0-9_.]+""",
        """\?[a-zA-Z0-9_]+/[a-zA-Z0-9_.]+""",
        // Escaped sequences
        """\\[ntr'"\\]"""
    ).joinToString("|")

    private val combinedPattern = Regex("$markupPatternSource|$placeholderPatternSource")
    private val placeholderOnlyPattern = Regex(placeholderPatternSource)
    private val markupOnlyPattern = Regex(markupPatternSource)

    private val urlOnlyPattern = Regex("""^https?://\S+$""")
    private val packageOnlyPattern = Regex("""^[a-z][a-z0-9_]*(\.[a-z0-9_]+){2,}$""")
    private val pureNumberOrSymbols = Regex("""^[\d\s.,:/+\-_%#()\[\]{}@!?*=]+$""")

    fun protect(
        input: String,
        config: TranslationProtectionConfig = TranslationProtectionConfig()
    ): ProtectedText {
        if (input.isEmpty()) return ProtectedText(input, emptyList())

        val patterns = mutableListOf<String>()
        if (config.preserveMarkupTags) {
            patterns.add(markupPatternSource)
        }
        if (config.preservePlaceholders) {
            patterns.add(placeholderPatternSource)
        }
        if (config.excludeTechnicalTerms && config.excludedTerms.isNotEmpty()) {
            val sortedTerms = config.excludedTerms
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .sortedByDescending { it.length }
            if (sortedTerms.isNotEmpty()) {
                val termAlternation = sortedTerms.joinToString("|") { term ->
                    val escaped = Regex.escape(term)
                    // Match whole words/tokens when alphanumeric at boundaries
                    val startBoundary = if (term.first().isLetterOrDigit()) """(?<![\p{L}\p{N}_])""" else ""
                    val endBoundary = if (term.last().isLetterOrDigit()) """(?![\p{L}\p{N}_])""" else ""
                    "$startBoundary$escaped$endBoundary"
                }
                patterns.add("(?i:$termAlternation)")
            }
        }

        if (patterns.isEmpty()) {
            return ProtectedText(input, emptyList())
        }

        val activeRegex = Regex(patterns.joinToString("|"))
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var lastIndex = 0
        for (match in activeRegex.findAll(input)) {
            if (match.range.first > lastIndex) {
                sb.append(input, lastIndex, match.range.first)
            }
            val tokenIndex = tokens.size
            tokens.add(match.value)
            sb.append("⟦").append(tokenIndex).append("⟧")
            lastIndex = match.range.last + 1
        }
        if (lastIndex < input.length) {
            sb.append(input, lastIndex, input.length)
        }
        return ProtectedText(sb.toString(), tokens)
    }

    fun restore(translatedMasked: String, tokens: List<String>): String {
        if (tokens.isEmpty()) return translatedMasked
        var result = translatedMasked
        tokens.forEachIndexed { index, originalToken ->
            val flexibleMarker = Regex("""⟦\s*$index\s*⟧""")
            if (flexibleMarker.containsMatchIn(result)) {
                result = flexibleMarker.replace(result) { originalToken }
            }
        }
        return result
    }

    fun extractTokens(
        input: String,
        config: TranslationProtectionConfig = TranslationProtectionConfig()
    ): List<String> {
        val tokens = mutableListOf<String>()
        if (config.preserveMarkupTags && config.preservePlaceholders) {
            tokens.addAll(combinedPattern.findAll(input).map { it.value })
        } else {
            if (config.preserveMarkupTags) {
                tokens.addAll(markupOnlyPattern.findAll(input).map { it.value })
            }
            if (config.preservePlaceholders) {
                tokens.addAll(placeholderOnlyPattern.findAll(input).map { it.value })
            }
        }
        return tokens
    }

    fun verifyPlaceholdersPreserved(
        original: String,
        translated: String,
        config: TranslationProtectionConfig = TranslationProtectionConfig()
    ): Boolean {
        val expected = extractTokens(original, config)
        if (expected.isEmpty()) return true
        val actual = extractTokens(translated, config)
        if (expected.size != actual.size) return false
        val expectedCounts = expected.groupingBy { it }.eachCount()
        val actualCounts = actual.groupingBy { it }.eachCount()
        return expectedCounts == actualCounts
    }

    fun repairMissingPlaceholders(
        original: String,
        translated: String,
        config: TranslationProtectionConfig = TranslationProtectionConfig()
    ): String {
        val expected = extractTokens(original, config)
        if (expected.isEmpty()) return translated
        val actual = extractTokens(translated, config).toMutableList()
        val missing = mutableListOf<String>()
        for (token in expected) {
            val idx = actual.indexOf(token)
            if (idx >= 0) {
                actual.removeAt(idx)
            } else {
                missing.add(token)
            }
        }
        if (missing.isEmpty()) return translated
        val hasPrintfLost = missing.any { it.startsWith("%") && it != "%%" }
        return if (hasPrintfLost) {
            original
        } else {
            "$translated ${missing.joinToString(" ")}".trim()
        }
    }

    fun isWholeTextExcluded(
        text: String,
        config: TranslationProtectionConfig
    ): Boolean {
        if (!config.excludeTechnicalTerms) return false
        val trimmed = text.trim()
        return config.excludedTerms.any { it.trim().equals(trimmed, ignoreCase = true) }
    }

    fun isLikelyNonTranslatable(key: String, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (urlOnlyPattern.matches(trimmed)) return true
        if (packageOnlyPattern.matches(trimmed)) return true
        if (pureNumberOrSymbols.matches(trimmed)) return true
        val lowerKey = key.lowercase()
        if (lowerKey.startsWith("abc_") && trimmed.length <= 2) return true
        if (lowerKey.contains("provider_authority") || lowerKey.contains("file_provider")) return true
        val stripped = combinedPattern.replace(trimmed, "").trim()
        if (stripped.isEmpty()) return true
        return false
    }
}
