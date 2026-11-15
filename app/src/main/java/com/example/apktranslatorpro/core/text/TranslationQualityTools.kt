package com.example.apktranslatorpro.core.text

import com.example.apktranslatorpro.domain.model.ResourceCategory
import com.example.apktranslatorpro.domain.model.ResourceEntryItem

data class TranslationQualityReport(
    val totalEntries: Int,
    val translatedCount: Int,
    val missingCount: Int,
    val duplicateSourceCount: Int,
    val lockedCount: Int,
    val averageQualityScore: Int,
    val categoryBreakdown: Map<ResourceCategory, Int>,
    val warnings: List<String>
)

/**
 * Provides translation quality scoring, category detection (menu/error/button/notification/settings/system),
 * duplicate & missing translation checks, spell/punctuation fixes, custom glossary replacement, and JSON/CSV exports.
 */
object TranslationQualityTools {

    fun classifyCategory(key: String, text: String): ResourceCategory {
        val k = key.lowercase()
        val t = text.lowercase()
        return when {
            k.contains("err") || k.contains("fail") || k.contains("invalid") || t.contains("hata") || t.contains("error") ->
                ResourceCategory.ERROR
            k.contains("btn") || k.contains("button") || k.contains("ok") || k.contains("cancel") || k.contains("save") || k.contains("submit") ->
                ResourceCategory.BUTTON
            k.contains("menu") || k.contains("nav") || k.contains("tab") ->
                ResourceCategory.MENU
            k.contains("notif") || k.contains("channel") || k.contains("alert") ->
                ResourceCategory.NOTIFICATION
            k.contains("pref") || k.contains("setting") || k.contains("config") ->
                ResourceCategory.SETTINGS
            k.contains("sys") || k.contains("perm") || k.contains("dialog") || k.contains("status") ->
                ResourceCategory.SYSTEM
            else -> ResourceCategory.GENERAL
        }
    }

    fun scoreTranslation(item: ResourceEntryItem): Int {
        if (item.translatedText.isBlank()) return 0
        var score = 100
        val orig = item.originalText
        val tr = item.translatedText

        // Check placeholders match
        val phRegex = Regex("""%(?:\d+\$)?[sdifcx]|\{\d+\}""")
        val origPh = phRegex.findAll(orig).map { it.value }.toList()
        val trPh = phRegex.findAll(tr).map { it.value }.toList()
        if (origPh.size != trPh.size) score -= 25

        // Check extreme length deviation for UI buttons/menus
        if (orig.length in 3..40 && tr.length > orig.length * 3) {
            score -= 15
        }
        // Check double spaces or stray punctuation spacing
        if (tr.contains("  ")) score -= 5
        if (orig.endsWith(".") && !tr.trimEnd().endsWith(".") && orig.length > 20) score -= 5

        return score.coerceIn(10, 100)
    }

    fun autoFixFormatting(text: String): String {
        return text
            .replace(Regex(""" {2,}"""), " ")
            .replace(" ,", ",")
            .replace(" .", ".")
            .replace(" !", "!")
            .replace(" ?", "?")
            .trim()
    }

    fun applyCustomGlossary(text: String, glossary: Map<String, String>): String {
        var result = text
        glossary.forEach { (sourceTerm, preferredTarget) ->
            if (sourceTerm.isNotBlank() && preferredTarget.isNotBlank()) {
                result = result.replace(
                    Regex("\\b${Regex.escape(sourceTerm.trim())}\\b", RegexOption.IGNORE_CASE),
                    preferredTarget.trim()
                )
            }
        }
        return result
    }

    fun suggestAlternatives(item: ResourceEntryItem): List<String> {
        val base = item.translatedText.ifBlank { item.originalText }.trim()
        if (base.isEmpty()) return emptyList()
        val fixed = autoFixFormatting(base)
        val concise = if (fixed.length > 28) fixed.substringBefore(" — ").substringBefore(" (") else fixed
        return listOfNotNull(
            fixed.takeIf { it != base },
            fixed.replaceFirstChar { it.uppercaseChar() },
            concise.takeIf { it.isNotBlank() && it != fixed }
        ).distinct()
    }

    fun buildReport(items: List<ResourceEntryItem>): TranslationQualityReport {
        val translated = items.filter { it.translatedText.isNotBlank() }
        val missing = items.count { it.isTranslatable && it.translatedText.isBlank() }
        val locked = items.count { it.isLocked }
        val duplicateGroups = items.groupBy { it.originalText.trim().lowercase() }
            .count { it.key.isNotBlank() && it.value.size > 1 }
        val avg = if (translated.isEmpty()) 0 else translated.map { scoreTranslation(it) }.average().toInt()
        val breakdown = items.groupingBy { classifyCategory(it.key, it.originalText) }.eachCount()

        val warnings = mutableListOf<String>()
        if (missing > 0) warnings.add("$missing adet çevrilmemiş kaynak bulunuyor.")
        if (duplicateGroups > 0) warnings.add("$duplicateGroups grup yinelenen kaynak metni tespit edildi.")
        val lowQuality = translated.count { scoreTranslation(it) < 75 }
        if (lowQuality > 0) warnings.add("$lowQuality çeviride placeholder/biçim uyarısı var.")

        return TranslationQualityReport(
            totalEntries = items.size,
            translatedCount = translated.size,
            missingCount = missing,
            duplicateSourceCount = duplicateGroups,
            lockedCount = locked,
            averageQualityScore = avg,
            categoryBreakdown = breakdown,
            warnings = warnings
        )
    }

    fun exportAsJson(packageName: String, items: List<ResourceEntryItem>): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("""  "packageName": "${escapeJson(packageName)}",""")
        sb.appendLine("""  "count": ${items.size},""")
        sb.appendLine("""  "entries": [""")
        items.forEachIndexed { idx, item ->
            val comma = if (idx < items.lastIndex) "," else ""
            sb.appendLine(
                """    {"key": "${escapeJson(item.key)}", "kind": "${item.kind.tagName}", "locale": "${item.localeQualifier}", "original": "${escapeJson(item.originalText)}", "translated": "${escapeJson(item.translatedText)}", "locked": ${item.isLocked}}$comma"""
            )
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    fun exportAsCsv(items: List<ResourceEntryItem>): String {
        val sb = StringBuilder()
        sb.appendLine("key,kind,locale,locked,original,translated")
        items.forEach { item ->
            sb.appendLine(
                listOf(
                    csvCell(item.key),
                    csvCell(item.kind.tagName),
                    csvCell(item.localeQualifier),
                    item.isLocked.toString(),
                    csvCell(item.originalText),
                    csvCell(item.translatedText)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    private fun csvCell(s: String): String =
        "\"${s.replace("\"", "\"\"")}\""
}
