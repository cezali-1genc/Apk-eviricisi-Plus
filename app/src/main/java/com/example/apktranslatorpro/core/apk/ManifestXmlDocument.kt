package com.example.apktranslatorpro.core.apk

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.zip.ZipFile

data class ManifestActivityItem(
    val name: String,
    val exported: Boolean?,
    val isAlias: Boolean = false
)

data class EditableManifestDocument(
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val minSdk: String,
    val targetSdk: String,
    val appLabel: String,
    val isDebuggable: Boolean,
    val allowBackup: Boolean,
    val permissions: List<String>,
    val activities: List<ManifestActivityItem>,
    val rawXml: String,
    val parsedFromBinaryAxml: Boolean = false
)

/**
 * Parses and edits decoded AndroidManifest.xml documents produced via AXMLPrinter2 binary decoding,
 * preserving XML namespaces, nested intent-filters, and formatting during standalone XML export.
 */
object ManifestXmlDocument {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun parseFromAxmlBytes(axmlBytes: ByteArray): EditableManifestDocument {
        val xml = AxmlParser.decodeToXmlString(axmlBytes)
        return parseFromXmlText(xml, parsedFromBinaryAxml = true)
    }

    fun loadFromWorkspace(workspace: DecompiledWorkspace): EditableManifestDocument {
        if (workspace.manifestFile.exists() && workspace.manifestFile.length() > 0L) {
            val text = workspace.manifestFile.readText(Charsets.UTF_8)
            if (text.isNotBlank()) {
                return parseFromXmlText(
                    xmlText = text,
                    parsedFromBinaryAxml = workspace.binaryManifestFile?.exists() == true
                )
            }
        }
        return loadFromBinaryAxml(workspace)
            ?: parseFromXmlText(defaultSkeletonXml(), parsedFromBinaryAxml = false)
    }

    fun loadFromBinaryAxml(workspace: DecompiledWorkspace): EditableManifestDocument? {
        val binFile = workspace.binaryManifestFile
        if (binFile != null && binFile.exists() && binFile.length() > 8L) {
            return runCatching { parseFromAxmlBytes(binFile.readBytes()) }.getOrNull()
        }
        if (workspace.apkCopyFile.exists()) {
            val fromZip = runCatching {
                ZipFile(workspace.apkCopyFile).use { zip ->
                    val entry = zip.getEntry("AndroidManifest.xml") ?: return@use null
                    zip.getInputStream(entry).use { it.readBytes() }
                }
            }.getOrNull()
            if (fromZip != null && fromZip.size > 8) {
                return runCatching { parseFromAxmlBytes(fromZip) }.getOrNull()
            }
        }
        return null
    }

    fun parseFromXmlText(
        xmlText: String,
        parsedFromBinaryAxml: Boolean = false
    ): EditableManifestDocument {
        val normalized = if (xmlText.isBlank()) defaultSkeletonXml() else xmlText
        var packageName = ""
        var versionName = ""
        var versionCode = ""
        var minSdk = ""
        var targetSdk = ""
        var appLabel = ""
        var isDebuggable = false
        var allowBackup = true
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<ManifestActivityItem>()

        val parsed = runCatching {
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(normalized))

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "manifest" -> {
                            packageName = attr(parser, null, "package")
                                ?: attrAny(parser, "package")
                                ?: packageName
                            versionName = attr(parser, ANDROID_NS, "versionName")
                                ?: attrAny(parser, "versionName")
                                ?: versionName
                            versionCode = attr(parser, ANDROID_NS, "versionCode")
                                ?: attrAny(parser, "versionCode")
                                ?: versionCode
                        }
                        "uses-sdk" -> {
                            minSdk = attr(parser, ANDROID_NS, "minSdkVersion")
                                ?: attrAny(parser, "minSdkVersion")
                                ?: minSdk
                            targetSdk = attr(parser, ANDROID_NS, "targetSdkVersion")
                                ?: attrAny(parser, "targetSdkVersion")
                                ?: targetSdk
                        }
                        "application" -> {
                            appLabel = attr(parser, ANDROID_NS, "label")
                                ?: attrAny(parser, "label")
                                ?: appLabel
                            (attr(parser, ANDROID_NS, "debuggable") ?: attrAny(parser, "debuggable"))
                                ?.let { isDebuggable = it.equals("true", ignoreCase = true) }
                            (attr(parser, ANDROID_NS, "allowBackup") ?: attrAny(parser, "allowBackup"))
                                ?.let { allowBackup = !it.equals("false", ignoreCase = true) }
                        }
                        "uses-permission", "uses-permission-sdk-23" -> {
                            val permName = attr(parser, ANDROID_NS, "name") ?: attrAny(parser, "name")
                            if (!permName.isNullOrBlank()) {
                                permissions.add(permName)
                            }
                        }
                        "activity", "activity-alias" -> {
                            val actName = attr(parser, ANDROID_NS, "name") ?: attrAny(parser, "name")
                            val exportedStr = attr(parser, ANDROID_NS, "exported") ?: attrAny(parser, "exported")
                            val exported = when {
                                exportedStr.equals("true", ignoreCase = true) -> true
                                exportedStr.equals("false", ignoreCase = true) -> false
                                else -> null
                            }
                            if (!actName.isNullOrBlank()) {
                                activities.add(
                                    ManifestActivityItem(
                                        name = actName,
                                        exported = exported,
                                        isAlias = parser.name == "activity-alias"
                                    )
                                )
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }

        if (parsed.isFailure && packageName.isBlank()) {
            // Fallback regex scan if the user temporarily has malformed XML in raw mode
            packageName = Regex("""\bpackage\s*=\s*"([^"]*)"""")
                .find(normalized)?.groupValues?.getOrNull(1).orEmpty()
        }

        return EditableManifestDocument(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            appLabel = appLabel,
            isDebuggable = isDebuggable,
            allowBackup = allowBackup,
            permissions = permissions,
            activities = activities,
            rawXml = normalized,
            parsedFromBinaryAxml = parsedFromBinaryAxml
        )
    }

    fun updatePackageName(
        doc: EditableManifestDocument,
        newPackageName: String
    ): EditableManifestDocument {
        val clean = newPackageName.trim()
        if (clean.isEmpty()) return doc
        val span = findTagSpans(doc.rawXml, listOf("manifest")).firstOrNull()
            ?: return parseFromXmlText(doc.rawXml, doc.parsedFromBinaryAxml)
        val updatedOpenTag = upsertAttributeInStartTag(
            startTagText = doc.rawXml.substring(span.openStart, span.openEnd),
            attrName = "package",
            attrValue = clean
        )
        val updatedXml = doc.rawXml.replaceRange(span.openStart, span.openEnd, updatedOpenTag)
        return parseFromXmlText(updatedXml, doc.parsedFromBinaryAxml)
    }

    fun addPermission(
        doc: EditableManifestDocument,
        permissionName: String
    ): EditableManifestDocument {
        val clean = permissionName.trim()
        if (clean.isEmpty()) return doc
        if (doc.permissions.any { it.equals(clean, ignoreCase = false) }) return doc

        val escaped = escapeXmlAttr(clean)
        val newTag = "  <uses-permission android:name=\"$escaped\" />\n"

        val appSpan = findTagSpans(doc.rawXml, listOf("application")).firstOrNull()
        val manifestCloseIdx = doc.rawXml.lastIndexOf("</manifest>")
        val insertPos = when {
            appSpan != null -> appSpan.openStart
            manifestCloseIdx >= 0 -> manifestCloseIdx
            else -> doc.rawXml.length
        }

        val updatedXml = StringBuilder(doc.rawXml).insert(insertPos, newTag).toString()
        return parseFromXmlText(updatedXml, doc.parsedFromBinaryAxml)
    }

    fun updatePermission(
        doc: EditableManifestDocument,
        index: Int,
        newPermissionName: String
    ): EditableManifestDocument {
        val clean = newPermissionName.trim()
        if (clean.isEmpty()) return doc
        val spans = findTagSpans(doc.rawXml, listOf("uses-permission", "uses-permission-sdk-23"))
        val target = spans.getOrNull(index) ?: return doc

        val updatedOpenTag = upsertAttributeInStartTag(
            startTagText = doc.rawXml.substring(target.openStart, target.openEnd),
            attrName = "android:name",
            attrValue = clean
        )
        val updatedXml = doc.rawXml.replaceRange(target.openStart, target.openEnd, updatedOpenTag)
        return parseFromXmlText(updatedXml, doc.parsedFromBinaryAxml)
    }

    fun removePermission(
        doc: EditableManifestDocument,
        index: Int
    ): EditableManifestDocument {
        val spans = findTagSpans(doc.rawXml, listOf("uses-permission", "uses-permission-sdk-23"))
        val target = spans.getOrNull(index) ?: return doc
        val updatedXml = removeElementSpan(doc.rawXml, target)
        return parseFromXmlText(updatedXml, doc.parsedFromBinaryAxml)
    }

    fun addActivity(
        doc: EditableManifestDocument,
        activityName: String,
        exported: Boolean
    ): EditableManifestDocument {
        val clean = activityName.trim()
        if (clean.isEmpty()) return doc
        if (doc.activities.any { it.name == clean }) return doc

        val escaped = escapeXmlAttr(clean)
        val newTag = "    <activity\n        android:name=\"$escaped\"\n        android:exported=\"$exported\" />\n"

        val appCloseIdx = doc.rawXml.lastIndexOf("</application>")
        val manifestCloseIdx = doc.rawXml.lastIndexOf("</manifest>")
        val updatedXml = when {
            appCloseIdx >= 0 -> StringBuilder(doc.rawXml).insert(appCloseIdx, newTag).toString()
            manifestCloseIdx >= 0 -> StringBuilder(doc.rawXml).insert(
                manifestCloseIdx,
                "  <application>\n$newTag  </application>\n"
            ).toString()
            else -> doc.rawXml + "\n" + newTag
        }
        return parseFromXmlText(updatedXml, doc.parsedFromBinaryAxml)
    }

    fun updateActivity(
        doc: EditableManifestDocument,
        index: Int,
        newName: String,
        newExported: Boolean?
    ): EditableManifestDocument {
        val clean = newName.trim()
        if (clean.isEmpty()) return doc
        val spans = findTagSpans(doc.rawXml, listOf("activity", "activity-alias"))
        val target = spans.getOrNull(index) ?: return doc

        var openTag = doc.rawXml.substring(target.openStart, target.openEnd)
        openTag = upsertAttributeInStartTag(openTag, "android:name", clean)
        openTag = if (newExported != null) {
            upsertAttributeInStartTag(openTag, "android:exported", newExported.toString())
        } else {
            removeAttributeFromStartTag(openTag, "android:exported")
        }

        val updatedXml = doc.rawXml.replaceRange(target.openStart, target.openEnd, openTag)
        return parseFromXmlText(updatedXml, doc.parsedFromBinaryAxml)
    }

    fun removeActivity(
        doc: EditableManifestDocument,
        index: Int
    ): EditableManifestDocument {
        val spans = findTagSpans(doc.rawXml, listOf("activity", "activity-alias"))
        val target = spans.getOrNull(index) ?: return doc
        val updatedXml = removeElementSpan(doc.rawXml, target)
        return parseFromXmlText(updatedXml, doc.parsedFromBinaryAxml)
    }

    private fun attr(parser: XmlPullParser, namespace: String?, name: String): String? {
        return parser.getAttributeValue(namespace, name)
    }

    private fun attrAny(parser: XmlPullParser, localOrPrefixedName: String): String? {
        for (i in 0 until parser.attributeCount) {
            val rawName = parser.getAttributeName(i)
            if (rawName == localOrPrefixedName || rawName.substringAfter(':') == localOrPrefixedName) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private data class TagSpan(
        val tagName: String,
        val openStart: Int,
        val openEnd: Int,
        val fullEnd: Int
    )

    private fun findTagSpans(xml: String, targetNames: List<String>): List<TagSpan> {
        val result = mutableListOf<TagSpan>()
        val targetSet = targetNames.toSet()
        var i = 0
        val n = xml.length

        while (i < n) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break

            if (xml.startsWith("<!--", lt)) {
                val commentEnd = xml.indexOf("-->", lt + 4)
                i = if (commentEnd >= 0) commentEnd + 3 else n
                continue
            }
            if (xml.startsWith("<?", lt)) {
                val piEnd = xml.indexOf("?>", lt + 2)
                i = if (piEnd >= 0) piEnd + 2 else n
                continue
            }
            if (xml.startsWith("</", lt) || xml.startsWith("<!", lt)) {
                val close = findTagClosingBracket(xml, lt + 2)
                i = if (close >= 0) close + 1 else n
                continue
            }

            var nameEnd = lt + 1
            while (nameEnd < n && !xml[nameEnd].isWhitespace() && xml[nameEnd] != '>' && xml[nameEnd] != '/') {
                nameEnd++
            }
            val tagName = xml.substring(lt + 1, nameEnd)
            val openClose = findTagClosingBracket(xml, nameEnd)
            if (openClose < 0) break

            val isSelfClosing = openClose > lt && xml[openClose - 1] == '/'
            val openEnd = openClose + 1

            if (tagName in targetSet) {
                val fullEnd = if (isSelfClosing) {
                    openEnd
                } else {
                    findMatchingEndTag(xml, openEnd, tagName) ?: openEnd
                }
                result.add(TagSpan(tagName, lt, openEnd, fullEnd))
                i = if (tagName == "manifest") openEnd else fullEnd
            } else {
                i = openEnd
            }
        }
        return result
    }

    private fun findTagClosingBracket(xml: String, start: Int): Int {
        var idx = start
        var quoteChar: Char? = null
        while (idx < xml.length) {
            val ch = xml[idx]
            if (quoteChar != null) {
                if (ch == quoteChar) quoteChar = null
            } else {
                when (ch) {
                    '"', '\'' -> quoteChar = ch
                    '>' -> return idx
                }
            }
            idx++
        }
        return -1
    }

    private fun findMatchingEndTag(xml: String, fromIndex: Int, tagName: String): Int? {
        var depth = 1
        var idx = fromIndex
        val n = xml.length
        while (idx < n) {
            val lt = xml.indexOf('<', idx)
            if (lt < 0) return null
            if (xml.startsWith("<!--", lt)) {
                val end = xml.indexOf("-->", lt + 4)
                idx = if (end >= 0) end + 3 else n
                continue
            }
            if (xml.startsWith("</$tagName", lt)) {
                val afterName = lt + 2 + tagName.length
                if (afterName < n && (xml[afterName] == '>' || xml[afterName].isWhitespace())) {
                    val endBracket = xml.indexOf('>', afterName)
                    if (endBracket >= 0) {
                        depth--
                        if (depth == 0) return endBracket + 1
                        idx = endBracket + 1
                        continue
                    }
                }
            } else if (xml.startsWith("<$tagName", lt)) {
                val afterName = lt + 1 + tagName.length
                if (afterName < n && (xml[afterName].isWhitespace() || xml[afterName] == '>' || xml[afterName] == '/')) {
                    val endBracket = findTagClosingBracket(xml, afterName)
                    if (endBracket >= 0) {
                        if (xml[endBracket - 1] != '/') depth++
                        idx = endBracket + 1
                        continue
                    }
                }
            }
            idx = lt + 1
        }
        return null
    }

    private fun removeElementSpan(xml: String, span: TagSpan): String {
        var start = span.openStart
        var end = span.fullEnd
        // Trim leading spaces on the same line + one trailing newline for clean formatting
        while (start > 0 && (xml[start - 1] == ' ' || xml[start - 1] == '\t')) {
            start--
        }
        if (end < xml.length && xml[end] == '\r') end++
        if (end < xml.length && xml[end] == '\n') end++
        return xml.removeRange(start, end)
    }

    private fun upsertAttributeInStartTag(
        startTagText: String,
        attrName: String,
        attrValue: String
    ): String {
        val escaped = escapeXmlAttr(attrValue)
        val localName = attrName.substringAfter(':')
        val attrRegex = if (attrName.contains(':')) {
            Regex("""(\s+)${Regex.escape(attrName)}\s*=\s*("[^"]*"|'[^']*')""")
        } else {
            Regex("""(\s+)${Regex.escape(localName)}\s*=\s*("[^"]*"|'[^']*')""")
        }
        val match = attrRegex.find(startTagText)
        if (match != null) {
            val indentPrefix = match.groupValues[1]
            return startTagText.replaceRange(match.range, "$indentPrefix$attrName=\"$escaped\"")
        }

        val closeTokenIdx = if (startTagText.endsWith("/>")) {
            startTagText.length - 2
        } else {
            startTagText.length - 1
        }
        val prefix = startTagText.substring(0, closeTokenIdx).trimEnd()
        val suffix = startTagText.substring(closeTokenIdx)
        val separator = if (prefix.contains('\n')) "\n    " else " "
        return "$prefix$separator$attrName=\"$escaped\"$suffix"
    }

    private fun removeAttributeFromStartTag(
        startTagText: String,
        attrName: String
    ): String {
        val attrRegex = Regex("""\s+${Regex.escape(attrName)}\s*=\s*("[^"]*"|'[^']*')""")
        return attrRegex.replace(startTagText, "")
    }

    private fun escapeXmlAttr(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun defaultSkeletonXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.example.app"
            android:versionCode="1"
            android:versionName="1.0">
          <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="35" />
          <application android:allowBackup="true">
          </application>
        </manifest>
    """.trimIndent()
}
