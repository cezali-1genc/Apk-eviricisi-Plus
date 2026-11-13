package com.example.apktranslatorpro.core.apk

import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.ResourceKind
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object ValuesXmlCodec {

    fun generateStringsXml(
        items: List<ResourceEntryItem>,
        useTranslated: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n")

        val strings = items.filter { it.kind == ResourceKind.STRING }
        for (item in strings) {
            val text = pickText(item, useTranslated)
            val attr = if (!item.isTranslatable) " translatable=\"false\"" else ""
            sb.append("    <string name=\"")
                .append(escapeAttr(item.key))
                .append("\"")
                .append(attr)
                .append(">")
                .append(escapeXmlTextPreservingInlineTags(text))
                .append("</string>\n")
        }

        val arrays = items.filter { it.kind == ResourceKind.STRING_ARRAY }.groupBy { it.key }
        for ((key, arrayItems) in arrays) {
            sb.append("    <string-array name=\"").append(escapeAttr(key)).append("\">\n")
            for (item in arrayItems) {
                val text = pickText(item, useTranslated)
                sb.append("        <item>")
                    .append(escapeXmlTextPreservingInlineTags(text))
                    .append("</item>\n")
            }
            sb.append("    </string-array>\n")
        }

        val plurals = items.filter { it.kind == ResourceKind.PLURALS }.groupBy { it.key }
        for ((key, pluralItems) in plurals) {
            sb.append("    <plurals name=\"").append(escapeAttr(key)).append("\">\n")
            for (item in pluralItems) {
                val qty = item.subKey.ifBlank { "other" }
                val text = pickText(item, useTranslated)
                sb.append("        <item quantity=\"").append(escapeAttr(qty)).append("\">")
                    .append(escapeXmlTextPreservingInlineTags(text))
                    .append("</item>\n")
            }
            sb.append("    </plurals>\n")
        }

        sb.append("</resources>\n")
        return sb.toString()
    }

    fun parseStringsXml(xmlContent: String, localeQualifier: String = "default"): List<ResourceEntryItem> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))

        val items = mutableListOf<ResourceEntryItem>()
        var currentTag: String? = null
        var currentName = ""
        var currentTranslatable = true
        var currentQuantity = ""
        var arrayIndex = 0
        var insideArray = false
        var insidePlurals = false
        val textBuffer = StringBuilder()
        var syntheticId = 0x7F0E0000

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    when (tag) {
                        "string" -> {
                            currentTag = "string"
                            currentName = parser.getAttributeValue(null, "name") ?: ""
                            currentTranslatable = parser.getAttributeValue(null, "translatable") != "false"
                            textBuffer.clear()
                        }
                        "string-array" -> {
                            insideArray = true
                            currentName = parser.getAttributeValue(null, "name") ?: ""
                            currentTranslatable = parser.getAttributeValue(null, "translatable") != "false"
                            arrayIndex = 0
                        }
                        "plurals" -> {
                            insidePlurals = true
                            currentName = parser.getAttributeValue(null, "name") ?: ""
                            currentTranslatable = parser.getAttributeValue(null, "translatable") != "false"
                        }
                        "item" -> if (insideArray || insidePlurals) {
                            currentTag = "item"
                            currentQuantity = parser.getAttributeValue(null, "quantity") ?: "other"
                            textBuffer.clear()
                        }
                        else -> if (currentTag != null) {
                            // Preserve inline HTML/XLIFF start tag
                            textBuffer.append("<").append(tag)
                            for (a in 0 until parser.attributeCount) {
                                textBuffer.append(" ")
                                    .append(parser.getAttributeName(a))
                                    .append("=\"")
                                    .append(parser.getAttributeValue(a))
                                    .append("\"")
                            }
                            textBuffer.append(">")
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (currentTag != null) {
                        textBuffer.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    when (tag) {
                        "string" -> {
                            if (currentName.isNotBlank()) {
                                val text = unescapeAndroidString(textBuffer.toString())
                                items.add(
                                    ResourceEntryItem(
                                        id = syntheticId++,
                                        kind = ResourceKind.STRING,
                                        key = currentName,
                                        localeQualifier = localeQualifier,
                                        originalText = text,
                                        isTranslatable = currentTranslatable
                                    )
                                )
                            }
                            currentTag = null
                        }
                        "item" -> if (insideArray || insidePlurals) {
                            val text = unescapeAndroidString(textBuffer.toString())
                            if (insideArray) {
                                items.add(
                                    ResourceEntryItem(
                                        id = syntheticId++,
                                        kind = ResourceKind.STRING_ARRAY,
                                        key = currentName,
                                        subKey = "[$arrayIndex]",
                                        localeQualifier = localeQualifier,
                                        originalText = text,
                                        isTranslatable = currentTranslatable
                                    )
                                )
                                arrayIndex++
                            } else {
                                items.add(
                                    ResourceEntryItem(
                                        id = syntheticId++,
                                        kind = ResourceKind.PLURALS,
                                        key = currentName,
                                        subKey = currentQuantity,
                                        localeQualifier = localeQualifier,
                                        originalText = text,
                                        isTranslatable = currentTranslatable
                                    )
                                )
                            }
                            currentTag = null
                        }
                        "string-array" -> insideArray = false
                        "plurals" -> insidePlurals = false
                        else -> if (currentTag != null) {
                            textBuffer.append("</").append(tag).append(">")
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return items
    }

    private fun pickText(item: ResourceEntryItem, useTranslated: Boolean): String {
        return if (useTranslated && item.translatedText.isNotBlank()) {
            item.translatedText
        } else {
            item.originalText
        }
    }

    private fun escapeAttr(v: String): String {
        return v.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun escapeXmlTextPreservingInlineTags(input: String): String {
        // Escape bare ampersands that are not already XML entities, and keep valid inline HTML/xliff tags intact
        val inlineTagRegex = Regex("""</?(?:b|i|u|br|font|a|strong|em|small|big|sub|sup|xliff:g)(?:\s+[^<>]*)?/?>""")
        val sb = StringBuilder()
        var last = 0
        for (match in inlineTagRegex.findAll(input)) {
            if (match.range.first > last) {
                sb.append(escapePlainSegment(input.substring(last, match.range.first)))
            }
            sb.append(match.value)
            last = match.range.last + 1
        }
        if (last < input.length) {
            sb.append(escapePlainSegment(input.substring(last)))
        }
        return sb.toString()
    }

    private fun escapePlainSegment(segment: String): String {
        return segment
            .replace(Regex("""&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[0-9a-fA-F]+;)"""), "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun unescapeAndroidString(raw: String): String {
        return raw
            .replace("\\'", "'")
            .replace("\\\"", "\"")
    }
}
