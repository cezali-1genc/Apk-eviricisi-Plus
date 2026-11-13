package com.example.apktranslatorpro.core.apk

data class ManifestInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val appLabelRaw: String,
    val appLabelResId: Int?,
    val supportsRtl: Boolean,
    val usesPermissions: List<String>,
    val xmlPreview: String,
    val activities: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val usesFeatures: List<String> = emptyList(),
    val isDebuggable: Boolean = false,
    val allowBackup: Boolean = true
)

object AxmlParser {

    private const val CHUNK_AXML_FILE = 0x0003
    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_RESOURCE_MAP = 0x0180
    private const val CHUNK_START_NAMESPACE = 0x0100
    private const val CHUNK_END_NAMESPACE = 0x0101
    private const val CHUNK_START_TAG = 0x0102
    private const val CHUNK_END_TAG = 0x0103
    private const val CHUNK_CDATA = 0x0104

    private const val TYPE_STRING = 0x03
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12

    fun parseManifest(bytes: ByteArray): ManifestInfo {
        if (bytes.size < 8) {
            throw IllegalArgumentException("AndroidManifest.xml ikili dosyası geçersiz veya boş.")
        }
        val reader = LittleEndianReader(bytes)
        val fileType = reader.readUInt16()
        if (fileType != CHUNK_AXML_FILE) {
            throw IllegalArgumentException("AndroidManifest.xml geçerli AXML formatında değil (0x${fileType.toString(16)})")
        }
        val headerSize = reader.readUInt16()
        val totalSize = reader.readInt32()
        reader.position = headerSize

        var stringPool: ResStringPool? = null
        val nsPrefixes = mutableMapOf<String, String>()
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

        var indent = 0
        var packageName = ""
        var versionName = ""
        var versionCode = 0L
        var minSdk = 0
        var targetSdk = 0
        var appLabelRaw = ""
        var appLabelResId: Int? = null
        var supportsRtl = false
        var isDebuggable = false
        var allowBackup = true
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<String>()
        val services = mutableListOf<String>()
        val receivers = mutableListOf<String>()
        val providers = mutableListOf<String>()
        val features = mutableListOf<String>()

        while (reader.position + 8 <= bytes.size && reader.position < totalSize) {
            val chunkStart = reader.position
            val chunkType = reader.readUInt16()
            val chunkHeaderSize = reader.readUInt16()
            val chunkSize = reader.readInt32()
            if (chunkSize < 8 || chunkStart + chunkSize > bytes.size) break

            when (chunkType) {
                CHUNK_STRING_POOL -> {
                    stringPool = ResStringPool.parse(reader.slice(chunkStart, chunkSize))
                }
                CHUNK_RESOURCE_MAP -> {
                    // Resource ID mapping array; skipped during textual preview
                }
                CHUNK_START_NAMESPACE -> {
                    reader.position = chunkStart + chunkHeaderSize
                    val prefixIdx = reader.readInt32()
                    val uriIdx = reader.readInt32()
                    val prefix = stringPool?.getOrNull(prefixIdx) ?: "ns"
                    val uri = stringPool?.getOrNull(uriIdx) ?: ""
                    if (uri.isNotEmpty()) nsPrefixes[uri] = prefix
                }
                CHUNK_END_NAMESPACE -> {
                    // No-op for preview
                }
                CHUNK_START_TAG -> {
                    reader.position = chunkStart + chunkHeaderSize
                    reader.readInt32() // nsIdx
                    val nameIdx = reader.readInt32()
                    reader.readUInt16() // attrStart
                    reader.readUInt16() // attrSize
                    val attrCount = reader.readUInt16()
                    reader.readUInt16() // idIndex
                    reader.readUInt16() // classIndex
                    reader.readUInt16() // styleIndex

                    val tagName = stringPool?.getOrNull(nameIdx) ?: "tag"
                    val indentStr = "  ".repeat(indent)
                    xmlBuilder.append(indentStr).append("<").append(tagName)
                    if (indent == 0 && nsPrefixes.isNotEmpty()) {
                        for ((uri, prefix) in nsPrefixes) {
                            xmlBuilder.append(" xmlns:").append(prefix).append("=\"").append(uri).append("\"")
                        }
                    }

                    for (i in 0 until attrCount) {
                        if (reader.remaining < 20) break
                        val attrNsIdx = reader.readInt32()
                        val attrNameIdx = reader.readInt32()
                        val rawValueIdx = reader.readInt32()
                        reader.readUInt16() // valueSize
                        reader.readUInt8() // res0
                        val valueType = reader.readUInt8()
                        val valueData = reader.readInt32()

                        val attrNsUri = if (attrNsIdx >= 0) stringPool?.getOrNull(attrNsIdx) else null
                        val attrPrefix = attrNsUri?.let { nsPrefixes[it] }
                        val attrName = stringPool?.getOrNull(attrNameIdx) ?: "attr_$i"
                        val fullAttrName = if (!attrPrefix.isNullOrEmpty()) "$attrPrefix:$attrName" else attrName

                        val attrValueStr = when (valueType) {
                            TYPE_STRING -> stringPool?.getOrNull(valueData)
                                ?: (if (rawValueIdx >= 0) stringPool?.getOrNull(rawValueIdx) else null)
                                ?: ""
                            TYPE_REFERENCE -> "@0x${valueData.toUInt().toString(16)}"
                            TYPE_INT_DEC -> valueData.toString()
                            TYPE_INT_HEX -> "0x${valueData.toUInt().toString(16)}"
                            TYPE_INT_BOOLEAN -> (valueData != 0).toString()
                            else -> if (rawValueIdx >= 0) stringPool?.getOrNull(rawValueIdx) ?: valueData.toString() else valueData.toString()
                        }

                        xmlBuilder.append("\n").append(indentStr).append("    ")
                            .append(fullAttrName).append("=\"").append(escapeXmlAttr(attrValueStr)).append("\"")

                        when (tagName) {
                            "manifest" -> when (attrName) {
                                "package" -> packageName = attrValueStr
                                "versionName" -> versionName = attrValueStr
                                "versionCode" -> versionCode = valueData.toLong() and 0xFFFFFFFFL
                            }
                            "uses-sdk" -> when (attrName) {
                                "minSdkVersion" -> minSdk = valueData
                                "targetSdkVersion" -> targetSdk = valueData
                            }
                            "application" -> when (attrName) {
                                "label" -> {
                                    if (valueType == TYPE_REFERENCE) {
                                        appLabelResId = valueData
                                    } else {
                                        appLabelRaw = attrValueStr
                                    }
                                }
                                "supportsRtl" -> supportsRtl = valueData != 0
                                "debuggable" -> isDebuggable = valueData != 0
                                "allowBackup" -> allowBackup = valueData != 0
                            }
                            "uses-permission", "uses-permission-sdk-23" -> {
                                if (attrName == "name" && attrValueStr.isNotBlank()) {
                                    permissions.add(attrValueStr)
                                }
                            }
                            "uses-feature" -> {
                                if (attrName == "name" && attrValueStr.isNotBlank()) {
                                    features.add(attrValueStr)
                                }
                            }
                            "activity", "activity-alias" -> {
                                if (attrName == "name" && attrValueStr.isNotBlank()) {
                                    activities.add(attrValueStr)
                                }
                            }
                            "service" -> {
                                if (attrName == "name" && attrValueStr.isNotBlank()) {
                                    services.add(attrValueStr)
                                }
                            }
                            "receiver" -> {
                                if (attrName == "name" && attrValueStr.isNotBlank()) {
                                    receivers.add(attrValueStr)
                                }
                            }
                            "provider" -> {
                                if (attrName == "name" && attrValueStr.isNotBlank()) {
                                    providers.add(attrValueStr)
                                }
                            }
                        }
                    }
                    xmlBuilder.append(">\n")
                    indent++
                }
                CHUNK_END_TAG -> {
                    indent = (indent - 1).coerceAtLeast(0)
                    reader.position = chunkStart + chunkHeaderSize
                    reader.readInt32() // nsIdx
                    val nameIdx = reader.readInt32()
                    val tagName = stringPool?.getOrNull(nameIdx) ?: "tag"
                    xmlBuilder.append("  ".repeat(indent)).append("</").append(tagName).append(">\n")
                }
                CHUNK_CDATA -> {
                    reader.position = chunkStart + chunkHeaderSize
                    val dataIdx = reader.readInt32()
                    val cdata = stringPool?.getOrNull(dataIdx) ?: ""
                    if (cdata.isNotBlank()) {
                        xmlBuilder.append("  ".repeat(indent)).append(cdata).append("\n")
                    }
                }
            }

            reader.position = chunkStart + chunkSize
        }

        return ManifestInfo(
            packageName = packageName.ifBlank { "bilinmeyen.paket" },
            versionName = versionName.ifBlank { "1.0" },
            versionCode = versionCode,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            appLabelRaw = appLabelRaw,
            appLabelResId = appLabelResId,
            supportsRtl = supportsRtl,
            usesPermissions = permissions.distinct(),
            xmlPreview = xmlBuilder.toString().take(24_000),
            activities = activities.distinct(),
            services = services.distinct(),
            receivers = receivers.distinct(),
            providers = providers.distinct(),
            usesFeatures = features.distinct(),
            isDebuggable = isDebuggable,
            allowBackup = allowBackup
        )
    }

    private fun escapeXmlAttr(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
