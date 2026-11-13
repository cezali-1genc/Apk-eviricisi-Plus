package com.example.apktranslatorpro.core.apk

import com.example.apktranslatorpro.core.text.PlaceholderProtector
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.ResourceKind
import java.nio.charset.StandardCharsets

sealed interface ArscPackageChild {
    fun toByteArray(): ByteArray
}

data class RawPackageChild(
    val chunkType: Int,
    val rawBytes: ByteArray
) : ArscPackageChild {
    override fun toByteArray(): ByteArray = rawBytes
}

sealed interface ArscEntry {
    val flags: Int
    val keyIndex: Int

    data class Simple(
        override val flags: Int,
        override val keyIndex: Int,
        val valueSize: Int,
        val res0: Int,
        val dataType: Int,
        var data: Int
    ) : ArscEntry

    data class Complex(
        override val flags: Int,
        override val keyIndex: Int,
        val parentIdent: Int,
        val items: MutableList<MapItem>
    ) : ArscEntry

    data class MapItem(
        val nameIdent: Int,
        val valueSize: Int,
        val res0: Int,
        val dataType: Int,
        var data: Int
    )
}

data class ParsedTypeChunk(
    val typeId: Int,
    val flags: Int,
    val res0: Int,
    val logicalEntryCount: Int,
    val configBytes: ByteArray,
    val entriesByIndex: MutableMap<Int, ArscEntry>
) : ArscPackageChild {

    val localeTag: String
        get() = extractLocaleTag(configBytes)

    override fun toByteArray(): ByteArray {
        // Always emit clean standard non-compact, 32-bit offset table chunk for maximum Android 8-15 compatibility
        val headerSize = 20 + configBytes.size
        val offsetsPos = headerSize
        val entriesStart = headerSize + logicalEntryCount * 4

        val entriesWriter = LittleEndianWriter(logicalEntryCount * 16 + 64)
        val offsets = IntArray(logicalEntryCount) { NO_ENTRY }

        for (idx in 0 until logicalEntryCount) {
            val entry = entriesByIndex[idx] ?: continue
            offsets[idx] = entriesWriter.size
            when (entry) {
                is ArscEntry.Simple -> {
                    val cleanFlags = entry.flags and FLAG_COMPACT.inv()
                    entriesWriter.writeUInt16(8) // ResTable_entry size
                    entriesWriter.writeUInt16(cleanFlags)
                    entriesWriter.writeInt32(entry.keyIndex)
                    // Res_value
                    entriesWriter.writeUInt16(8)
                    entriesWriter.writeUInt8(0)
                    entriesWriter.writeUInt8(entry.dataType)
                    entriesWriter.writeInt32(entry.data)
                }
                is ArscEntry.Complex -> {
                    val cleanFlags = (entry.flags or FLAG_COMPLEX) and FLAG_COMPACT.inv()
                    entriesWriter.writeUInt16(16) // ResTable_map_entry size
                    entriesWriter.writeUInt16(cleanFlags)
                    entriesWriter.writeInt32(entry.keyIndex)
                    entriesWriter.writeInt32(entry.parentIdent)
                    entriesWriter.writeInt32(entry.items.size)
                    for (item in entry.items) {
                        entriesWriter.writeInt32(item.nameIdent)
                        entriesWriter.writeUInt16(8)
                        entriesWriter.writeUInt8(0)
                        entriesWriter.writeUInt8(item.dataType)
                        entriesWriter.writeInt32(item.data)
                    }
                }
            }
        }

        val writer = LittleEndianWriter(entriesStart + entriesWriter.size + 16)
        writer.writeUInt16(CHUNK_TYPE_TABLE_TYPE)
        writer.writeUInt16(headerSize)
        writer.writeInt32(0) // total chunk size patched below
        writer.writeUInt8(typeId)
        writer.writeUInt8(0) // standard non-sparse, non-offset16 flags
        writer.writeUInt16(0)
        writer.writeInt32(logicalEntryCount)
        writer.writeInt32(entriesStart)
        writer.writeBytes(configBytes)

        for (offset in offsets) {
            writer.writeInt32(offset)
        }
        writer.writeBytes(entriesWriter.toByteArray())
        writer.padTo4Bytes()

        val result = writer.toByteArray()
        result.patchInt32(4, result.size)
        return result
    }

    fun deepCopyWithLocale(language: LanguageOption): ParsedTypeChunk {
        val newConfig = configBytes.copyOf()
        writeLocaleToConfig(newConfig, language)
        val copiedEntries = LinkedHashMap<Int, ArscEntry>(entriesByIndex.size)
        for ((idx, entry) in entriesByIndex) {
            copiedEntries[idx] = when (entry) {
                is ArscEntry.Simple -> entry.copy()
                is ArscEntry.Complex -> entry.copy(
                    items = entry.items.map { it.copy() }.toMutableList()
                )
            }
        }
        return ParsedTypeChunk(
            typeId = typeId,
            flags = 0,
            res0 = 0,
            logicalEntryCount = logicalEntryCount,
            configBytes = newConfig,
            entriesByIndex = copiedEntries
        )
    }

    companion object {
        const val CHUNK_TYPE_TABLE_TYPE = 0x0201
        const val NO_ENTRY = -1
        const val FLAG_SPARSE = 0x01
        const val FLAG_OFFSET16 = 0x02
        const val FLAG_COMPLEX = 0x0001
        const val FLAG_COMPACT = 0x0008
        const val TYPE_STRING = 0x03

        fun extractLocaleTag(configBytes: ByteArray): String {
            if (configBytes.size < 12) return "default"
            val lang0 = configBytes[8].toInt() and 0xFF
            val lang1 = configBytes[9].toInt() and 0xFF
            val reg0 = configBytes[10].toInt() and 0xFF
            val reg1 = configBytes[11].toInt() and 0xFF
            if (lang0 == 0 && lang1 == 0) return "default"
            val lang = unpackTwoCharOrIso(lang0, lang1, 'a')
            val reg = if (reg0 == 0 && reg1 == 0) "" else unpackTwoCharOrIso(reg0, reg1, '0')
            return if (reg.isNotEmpty()) "$lang-r$reg" else lang
        }

        private fun unpackTwoCharOrIso(b0: Int, b1: Int, base: Char): String {
            return if ((b0 and 0x80) != 0) {
                val c1 = ((b1 and 0x1F) + base.code).toChar()
                val c2 = ((((b0 and 0x03) shl 3) or ((b1 and 0xE0) ushr 5)) + base.code).toChar()
                val c3 = (((b0 and 0x7C) ushr 2) + base.code).toChar()
                "$c1$c2$c3"
            } else {
                String(byteArrayOf(b0.toByte(), b1.toByte()), StandardCharsets.US_ASCII).trim('\u0000')
            }
        }

        fun writeLocaleToConfig(configBytes: ByteArray, language: LanguageOption) {
            if (configBytes.size < 12) return
            val langChars = language.androidLang.padEnd(2, '\u0000').take(2)
            configBytes[8] = if (language.androidLang.isEmpty()) 0 else langChars[0].code.toByte()
            configBytes[9] = if (language.androidLang.length < 2) 0 else langChars[1].code.toByte()
            val regChars = language.androidRegion.padEnd(2, '\u0000').take(2)
            configBytes[10] = if (language.androidRegion.isEmpty()) 0 else regChars[0].code.toByte()
            configBytes[11] = if (language.androidRegion.length < 2) 0 else regChars[1].code.toByte()
            // Clear localeScript (bytes 36..39) & localeVariant (bytes 40..47) if present
            if (configBytes.size >= 40) {
                for (i in 36 until 40) configBytes[i] = 0
            }
            if (configBytes.size >= 48) {
                for (i in 40 until 48) configBytes[i] = 0
            }
        }

        fun parse(chunkBytes: ByteArray): ParsedTypeChunk {
            val reader = LittleEndianReader(chunkBytes)
            val chunkType = reader.readUInt16()
            require(chunkType == CHUNK_TYPE_TABLE_TYPE) { "Beklenen ResTable_type chunk değil" }
            val headerSize = reader.readUInt16()
            reader.readInt32() // total chunk size
            val typeId = reader.readUInt8()
            val flags = reader.readUInt8()
            val res0 = reader.readUInt16()
            val entryCount = reader.readInt32()
            val entriesStart = reader.readInt32()

            val configSize = reader.readInt32()
            reader.position = 20
            val actualConfigSize = configSize.coerceAtLeast(28).coerceAtMost(headerSize - 20)
            val configBytes = reader.readBytes(actualConfigSize)

            reader.position = headerSize
            val entriesByIndex = LinkedHashMap<Int, ArscEntry>()
            val isSparse = (flags and FLAG_SPARSE) != 0
            val isOffset16 = (flags and FLAG_OFFSET16) != 0

            var maxLogicalIndex = entryCount - 1
            val indexOffsetPairs = mutableListOf<Pair<Int, Int>>()

            if (isSparse) {
                for (i in 0 until entryCount) {
                    val idx = reader.readUInt16()
                    val off16 = reader.readUInt16()
                    if (idx > maxLogicalIndex) maxLogicalIndex = idx
                    if (off16 != 0xFFFF) {
                        indexOffsetPairs.add(idx to (off16 * 4))
                    }
                }
            } else if (isOffset16) {
                for (idx in 0 until entryCount) {
                    val off16 = reader.readUInt16()
                    if (off16 != 0xFFFF) {
                        indexOffsetPairs.add(idx to (off16 * 4))
                    }
                }
            } else {
                for (idx in 0 until entryCount) {
                    val off32 = reader.readInt32()
                    if (off32 != NO_ENTRY) {
                        indexOffsetPairs.add(idx to off32)
                    }
                }
            }

            for ((idx, relOffset) in indexOffsetPairs) {
                val absPos = entriesStart + relOffset
                if (absPos + 8 > chunkBytes.size) continue
                reader.position = absPos
                val firstWord = reader.readUInt16()
                val entryFlags = reader.readUInt16()
                if ((entryFlags and FLAG_COMPACT) != 0) {
                    val keyIdx = firstWord
                    val dataType = (entryFlags ushr 8) and 0xFF
                    val data = reader.readInt32()
                    entriesByIndex[idx] = ArscEntry.Simple(
                        flags = entryFlags,
                        keyIndex = keyIdx,
                        valueSize = 8,
                        res0 = 0,
                        dataType = dataType,
                        data = data
                    )
                } else {
                    val keyIdx = reader.readInt32()
                    if ((entryFlags and FLAG_COMPLEX) != 0) {
                        val parentIdent = reader.readInt32()
                        val mapCount = reader.readInt32()
                        val items = ArrayList<ArscEntry.MapItem>(mapCount.coerceAtMost(4096))
                        for (m in 0 until mapCount) {
                            if (reader.remaining < 12) break
                            val nameIdent = reader.readInt32()
                            val vSize = reader.readUInt16()
                            val vRes0 = reader.readUInt8()
                            val vType = reader.readUInt8()
                            val vData = reader.readInt32()
                            items.add(ArscEntry.MapItem(nameIdent, vSize, vRes0, vType, vData))
                        }
                        entriesByIndex[idx] = ArscEntry.Complex(entryFlags, keyIdx, parentIdent, items)
                    } else {
                        val vSize = reader.readUInt16()
                        val vRes0 = reader.readUInt8()
                        val vType = reader.readUInt8()
                        val vData = reader.readInt32()
                        entriesByIndex[idx] = ArscEntry.Simple(entryFlags, keyIdx, vSize, vRes0, vType, vData)
                    }
                }
            }

            return ParsedTypeChunk(
                typeId = typeId,
                flags = flags,
                res0 = res0,
                logicalEntryCount = maxLogicalIndex + 1,
                configBytes = configBytes,
                entriesByIndex = entriesByIndex
            )
        }
    }
}

data class ParsedArscPackage(
    val headerSize: Int,
    val packageId: Int,
    val packageName: String,
    val typeIdOffset: Int,
    val lastPublicType: Int,
    val lastPublicKey: Int,
    val extraHeaderBytes: ByteArray,
    val typeStrings: ResStringPool,
    val keyStrings: ResStringPool,
    val children: MutableList<ArscPackageChild>
) {
    fun resolveTypeName(typeId: Int): String {
        val index = typeId - 1 - typeIdOffset
        return typeStrings.getOrNull(index) ?: typeStrings.getOrNull(typeId - 1) ?: ""
    }

    fun toByteArray(): ByteArray {
        val typePoolBytes = typeStrings.toByteArray()
        val keyPoolBytes = keyStrings.toByteArray()
        val bodyWriter = LittleEndianWriter(typePoolBytes.size + keyPoolBytes.size + 4096)
        val typeStringsOffset = headerSize
        bodyWriter.writeBytes(typePoolBytes)
        val keyStringsOffset = headerSize + bodyWriter.size
        bodyWriter.writeBytes(keyPoolBytes)

        for (child in children) {
            bodyWriter.writeBytes(child.toByteArray())
        }

        val headerWriter = LittleEndianWriter(headerSize)
        headerWriter.writeUInt16(CHUNK_TYPE_PACKAGE)
        headerWriter.writeUInt16(headerSize)
        headerWriter.writeInt32(headerSize + bodyWriter.size)
        headerWriter.writeInt32(packageId)
        val nameChars = CharArray(128)
        for (i in packageName.indices.take(127)) {
            nameChars[i] = packageName[i]
        }
        for (c in nameChars) {
            headerWriter.writeUInt16(c.code)
        }
        headerWriter.writeInt32(typeStringsOffset)
        headerWriter.writeInt32(lastPublicType)
        headerWriter.writeInt32(keyStringsOffset)
        headerWriter.writeInt32(lastPublicKey)
        if (headerSize >= 288) {
            headerWriter.writeInt32(typeIdOffset)
        }
        if (extraHeaderBytes.isNotEmpty()) {
            headerWriter.writeBytes(extraHeaderBytes)
        }

        val total = LittleEndianWriter(headerSize + bodyWriter.size)
        total.writeBytes(headerWriter.toByteArray())
        total.writeBytes(bodyWriter.toByteArray())
        return total.toByteArray()
    }

    companion object {
        const val CHUNK_TYPE_PACKAGE = 0x0200
    }
}

class ArscTable(
    val globalStringPool: ResStringPool,
    val packages: MutableList<ParsedArscPackage>,
    val trailingChunks: MutableList<ByteArray>
) {
    fun extractLocales(): List<String> {
        val locales = linkedSetOf<String>()
        for (pkg in packages) {
            for (child in pkg.children) {
                if (child is ParsedTypeChunk) {
                    locales.add(child.localeTag)
                }
            }
        }
        return locales.toList()
    }

    fun extractTranslatableResources(
        preferredSourceLocale: String? = null,
        stringsOnly: Boolean = false
    ): List<ResourceEntryItem> {
        val result = mutableListOf<ResourceEntryItem>()
        for (pkg in packages) {
            val typeChunks = pkg.children.filterIsInstance<ParsedTypeChunk>()
            val groupedByTypeId = typeChunks.groupBy { it.typeId }

            for ((typeId, chunks) in groupedByTypeId) {
                val typeName = pkg.resolveTypeName(typeId)
                val kind = when (typeName) {
                    "string" -> ResourceKind.STRING
                    "array" -> ResourceKind.STRING_ARRAY
                    "plurals" -> ResourceKind.PLURALS
                    else -> continue
                }
                if (stringsOnly && kind != ResourceKind.STRING) continue

                // Prefer explicit source locale if requested, else default ("default")
                val chosenChunk = if (!preferredSourceLocale.isNullOrBlank() && preferredSourceLocale != "auto") {
                    chunks.filter { it.localeTag == preferredSourceLocale }.maxByOrNull { it.entriesByIndex.size }
                        ?: chunks.filter { it.localeTag == "default" }.maxByOrNull { it.entriesByIndex.size }
                        ?: chunks.maxByOrNull { it.entriesByIndex.size }
                } else {
                    chunks.filter { it.localeTag == "default" }.maxByOrNull { it.entriesByIndex.size }
                        ?: chunks.maxByOrNull { it.entriesByIndex.size }
                } ?: continue

                for ((entryIdx, entry) in chosenChunk.entriesByIndex) {
                    val resId = (pkg.packageId shl 24) or (typeId shl 16) or entryIdx
                    val keyName = pkg.keyStrings.getOrNull(entry.keyIndex) ?: "res_${resId.toString(16)}"

                    when (entry) {
                        is ArscEntry.Simple -> {
                            if (entry.dataType == ParsedTypeChunk.TYPE_STRING) {
                                val text = globalStringPool.getOrNull(entry.data) ?: continue
                                val hasSpans = entry.data < globalStringPool.styles.size &&
                                    globalStringPool.styles[entry.data].isNotEmpty()
                                val nonTranslatable = PlaceholderProtector.isLikelyNonTranslatable(keyName, text)
                                result.add(
                                    ResourceEntryItem(
                                        id = resId,
                                        kind = kind,
                                        key = keyName,
                                        localeQualifier = chosenChunk.localeTag,
                                        originalText = text,
                                        translatedText = if (nonTranslatable) text else "",
                                        protectedTokens = PlaceholderProtector.extractTokens(text),
                                        isTranslatable = !nonTranslatable,
                                        hasStyleSpans = hasSpans
                                    )
                                )
                            }
                        }
                        is ArscEntry.Complex -> {
                            entry.items.forEachIndexed { itemPos, mapItem ->
                                if (mapItem.dataType == ParsedTypeChunk.TYPE_STRING) {
                                    val text = globalStringPool.getOrNull(mapItem.data) ?: return@forEachIndexed
                                    val subKey = if (kind == ResourceKind.PLURALS) {
                                        pluralQuantityName(mapItem.nameIdent)
                                    } else {
                                        "[$itemPos]"
                                    }
                                    val nonTranslatable = PlaceholderProtector.isLikelyNonTranslatable(keyName, text)
                                    result.add(
                                        ResourceEntryItem(
                                            id = resId,
                                            kind = kind,
                                            key = keyName,
                                            subKey = subKey,
                                            localeQualifier = chosenChunk.localeTag,
                                            originalText = text,
                                            translatedText = if (nonTranslatable) text else "",
                                            protectedTokens = PlaceholderProtector.extractTokens(text),
                                            isTranslatable = !nonTranslatable
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    fun applyTranslationsForTargetLocale(
        targetLanguage: LanguageOption,
        translatedItems: List<ResourceEntryItem>,
        preferredSourceLocale: String? = null
    ): Int {
        if (targetLanguage == LanguageOption.AUTO) return 0
        val byUnique = translatedItems.associateBy { it.uniqueKey }
        val byIdAndSubKey = translatedItems.associateBy { "${it.id}:${it.subKey}" }
        val targetTag = targetLanguage.resourceFolderSuffix
        var appliedCount = 0

        for (pkg in packages) {
            val existingTypeChunks = pkg.children.filterIsInstance<ParsedTypeChunk>()
            val groupedByTypeId = existingTypeChunks.groupBy { it.typeId }

            for ((typeId, chunks) in groupedByTypeId) {
                val typeName = pkg.resolveTypeName(typeId)
                val kind = when (typeName) {
                    "string" -> ResourceKind.STRING
                    "array" -> ResourceKind.STRING_ARRAY
                    "plurals" -> ResourceKind.PLURALS
                    else -> continue
                }

                val sourceChunk = if (!preferredSourceLocale.isNullOrBlank() && preferredSourceLocale != "auto") {
                    chunks.filter { it.localeTag == preferredSourceLocale }.maxByOrNull { it.entriesByIndex.size }
                        ?: chunks.filter { it.localeTag == "default" }.maxByOrNull { it.entriesByIndex.size }
                        ?: chunks.maxByOrNull { it.entriesByIndex.size }
                } else {
                    chunks.filter { it.localeTag == "default" }.maxByOrNull { it.entriesByIndex.size }
                        ?: chunks.maxByOrNull { it.entriesByIndex.size }
                } ?: continue

                val existingTargetChunk = chunks.firstOrNull { it.localeTag == targetTag }
                val targetChunk = existingTargetChunk ?: sourceChunk.deepCopyWithLocale(targetLanguage)
                if (existingTargetChunk != null) {
                    for ((idx, srcEntry) in sourceChunk.entriesByIndex) {
                        if (!targetChunk.entriesByIndex.containsKey(idx)) {
                            targetChunk.entriesByIndex[idx] = when (srcEntry) {
                                is ArscEntry.Simple -> srcEntry.copy()
                                is ArscEntry.Complex -> srcEntry.copy(
                                    items = srcEntry.items.map { it.copy() }.toMutableList()
                                )
                            }
                        }
                    }
                }

                for ((entryIdx, entry) in targetChunk.entriesByIndex) {
                    val resId = (pkg.packageId shl 24) or (typeId shl 16) or entryIdx
                    val keyName = pkg.keyStrings.getOrNull(entry.keyIndex) ?: continue

                    when (entry) {
                        is ArscEntry.Simple -> {
                            if (entry.dataType == ParsedTypeChunk.TYPE_STRING) {
                                val match = byUnique["${kind.name}:$resId:$keyName::${sourceChunk.localeTag}"]
                                    ?: byIdAndSubKey["$resId:"]
                                val translated = match?.translatedText?.takeIf { it.isNotEmpty() }
                                if (translated != null) {
                                    entry.data = globalStringPool.appendString(translated)
                                    appliedCount++
                                }
                            }
                        }
                        is ArscEntry.Complex -> {
                            entry.items.forEachIndexed { itemPos, mapItem ->
                                if (mapItem.dataType == ParsedTypeChunk.TYPE_STRING) {
                                    val subKey = if (kind == ResourceKind.PLURALS) {
                                        pluralQuantityName(mapItem.nameIdent)
                                    } else {
                                        "[$itemPos]"
                                    }
                                    val match = byUnique["${kind.name}:$resId:$keyName:$subKey:${sourceChunk.localeTag}"]
                                        ?: byIdAndSubKey["$resId:$subKey"]
                                    val translated = match?.translatedText?.takeIf { it.isNotEmpty() }
                                    if (translated != null) {
                                        mapItem.data = globalStringPool.appendString(translated)
                                        appliedCount++
                                    }
                                }
                            }
                        }
                    }
                }

                if (existingTargetChunk == null) {
                    val insertPos = pkg.children.indexOfLast { it is ParsedTypeChunk && it.typeId == typeId }
                    if (insertPos >= 0) {
                        pkg.children.add(insertPos + 1, targetChunk)
                    } else {
                        pkg.children.add(targetChunk)
                    }
                }
            }
        }
        return appliedCount
    }

    fun applyTranslationsForScopes(
        targetLanguage: LanguageOption,
        translatedItems: List<ResourceEntryItem>,
        stringsOnly: Boolean,
        translateDefaultStrings: Boolean,
        translateValuesTr: Boolean,
        translateValuesEn: Boolean,
        preferredSourceLocale: String? = null
    ): Int {
        if (targetLanguage == LanguageOption.AUTO) return 0
        val byUnique = translatedItems.associateBy { it.uniqueKey }
        val byIdAndSubKey = translatedItems.associateBy { "${it.id}:${it.subKey}" }
        var appliedCount = 0

        for (pkg in packages) {
            val existingTypeChunks = pkg.children.filterIsInstance<ParsedTypeChunk>()
            val groupedByTypeId = existingTypeChunks.groupBy { it.typeId }

            for ((typeId, chunks) in groupedByTypeId) {
                val typeName = pkg.resolveTypeName(typeId)
                val kind = when (typeName) {
                    "string" -> ResourceKind.STRING
                    "array" -> ResourceKind.STRING_ARRAY
                    "plurals" -> ResourceKind.PLURALS
                    else -> continue
                }
                if (stringsOnly && kind != ResourceKind.STRING) continue

                val sourceChunk = if (!preferredSourceLocale.isNullOrBlank() && preferredSourceLocale != "auto") {
                    chunks.filter { it.localeTag == preferredSourceLocale }.maxByOrNull { it.entriesByIndex.size }
                        ?: chunks.filter { it.localeTag == "default" }.maxByOrNull { it.entriesByIndex.size }
                        ?: chunks.maxByOrNull { it.entriesByIndex.size }
                } else {
                    chunks.filter { it.localeTag == "default" }.maxByOrNull { it.entriesByIndex.size }
                        ?: chunks.maxByOrNull { it.entriesByIndex.size }
                } ?: continue

                val sourceLocaleTag = sourceChunk.localeTag

                // Determine exact target chunks to update based on strict user scope selection
                val targets = linkedMapOf<String, Pair<ParsedTypeChunk, Boolean>>()

                fun enqueueLocale(tag: String, langOption: LanguageOption, createIfMissing: Boolean) {
                    val existing = chunks.firstOrNull { it.localeTag == tag }
                    if (existing != null) {
                        targets[tag] = existing to false
                    } else if (createIfMissing) {
                        targets[tag] = sourceChunk.deepCopyWithLocale(langOption) to true
                    }
                }

                if (translateDefaultStrings) {
                    enqueueLocale("default", LanguageOption.AUTO, createIfMissing = false)
                }
                if (translateValuesTr) {
                    enqueueLocale("tr", LanguageOption.TURKISH, createIfMissing = (targetLanguage == LanguageOption.TURKISH))
                }
                if (translateValuesEn) {
                    enqueueLocale("en", LanguageOption.ENGLISH, createIfMissing = (targetLanguage == LanguageOption.ENGLISH))
                }
                if (targetLanguage != LanguageOption.TURKISH && targetLanguage != LanguageOption.ENGLISH) {
                    enqueueLocale(targetLanguage.resourceFolderSuffix, targetLanguage, createIfMissing = true)
                } else if (targets.isEmpty()) {
                    enqueueLocale(targetLanguage.resourceFolderSuffix, targetLanguage, createIfMissing = true)
                }

                // Backfill missing entries in existing target chunks BEFORE mutating any shared source entries
                for ((_, pair) in targets) {
                    val (targetChunk, isNew) = pair
                    if (!isNew) {
                        for ((idx, srcEntry) in sourceChunk.entriesByIndex) {
                            if (!targetChunk.entriesByIndex.containsKey(idx)) {
                                targetChunk.entriesByIndex[idx] = when (srcEntry) {
                                    is ArscEntry.Simple -> srcEntry.copy()
                                    is ArscEntry.Complex -> srcEntry.copy(
                                        items = srcEntry.items.map { it.copy() }.toMutableList()
                                    )
                                }
                            }
                        }
                    }
                }

                // Apply translated strings to every selected target scope
                for ((_, pair) in targets) {
                    val (targetChunk, isNew) = pair
                    for ((entryIdx, entry) in targetChunk.entriesByIndex) {
                        val resId = (pkg.packageId shl 24) or (typeId shl 16) or entryIdx
                        val keyName = pkg.keyStrings.getOrNull(entry.keyIndex) ?: continue

                        when (entry) {
                            is ArscEntry.Simple -> {
                                if (entry.dataType == ParsedTypeChunk.TYPE_STRING) {
                                    val match = byUnique["${kind.name}:$resId:$keyName::$sourceLocaleTag"]
                                        ?: byIdAndSubKey["$resId:"]
                                    val translated = match?.translatedText?.takeIf { it.isNotEmpty() }
                                    if (translated != null) {
                                        entry.data = globalStringPool.appendString(translated)
                                        appliedCount++
                                    }
                                }
                            }
                            is ArscEntry.Complex -> {
                                entry.items.forEachIndexed { itemPos, mapItem ->
                                    if (mapItem.dataType == ParsedTypeChunk.TYPE_STRING) {
                                        val subKey = if (kind == ResourceKind.PLURALS) {
                                            pluralQuantityName(mapItem.nameIdent)
                                        } else {
                                            "[$itemPos]"
                                        }
                                        val match = byUnique["${kind.name}:$resId:$keyName:$subKey:$sourceLocaleTag"]
                                            ?: byIdAndSubKey["$resId:$subKey"]
                                        val translated = match?.translatedText?.takeIf { it.isNotEmpty() }
                                        if (translated != null) {
                                            mapItem.data = globalStringPool.appendString(translated)
                                            appliedCount++
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isNew) {
                        val insertPos = pkg.children.indexOfLast { it is ParsedTypeChunk && it.typeId == typeId }
                        if (insertPos >= 0) {
                            pkg.children.add(insertPos + 1, targetChunk)
                        } else {
                            pkg.children.add(targetChunk)
                        }
                    }
                }
            }
        }
        return appliedCount
    }

    fun toByteArray(): ByteArray {
        val poolBytes = globalStringPool.toByteArray()
        val pkgByteArrays = packages.map { it.toByteArray() }
        val totalSize = 12 + poolBytes.size +
            pkgByteArrays.sumOf { it.size } +
            trailingChunks.sumOf { it.size }

        val writer = LittleEndianWriter(totalSize)
        writer.writeUInt16(CHUNK_TYPE_TABLE)
        writer.writeUInt16(12)
        writer.writeInt32(totalSize)
        writer.writeInt32(packages.size)
        writer.writeBytes(poolBytes)
        for (pkgBytes in pkgByteArrays) {
            writer.writeBytes(pkgBytes)
        }
        for (extra in trailingChunks) {
            writer.writeBytes(extra)
        }
        return writer.toByteArray()
    }

    companion object {
        const val CHUNK_TYPE_TABLE = 0x0002

        fun pluralQuantityName(attrId: Int): String = when (attrId) {
            0x01000004 -> "other"
            0x01000005 -> "zero"
            0x01000006 -> "one"
            0x01000007 -> "two"
            0x01000008 -> "few"
            0x01000009 -> "many"
            else -> "other"
        }

        fun parse(bytes: ByteArray): ArscTable {
            require(bytes.size >= 12) { "resources.arsc dosyası çok kısa veya bozuk." }
            val reader = LittleEndianReader(bytes)
            val tableType = reader.readUInt16()
            require(tableType == CHUNK_TYPE_TABLE) { "Geçersiz resources.arsc başlığı: 0x${tableType.toString(16)}" }
            val headerSize = reader.readUInt16()
            val totalSize = reader.readInt32()
            val packageCount = reader.readInt32()

            reader.position = headerSize
            var globalPool: ResStringPool? = null
            val packages = mutableListOf<ParsedArscPackage>()
            val trailing = mutableListOf<ByteArray>()

            while (reader.position + 8 <= bytes.size && reader.position < totalSize) {
                val chunkStart = reader.position
                val chunkType = reader.readUInt16()
                reader.readUInt16() // chunk header size
                val chunkSize = reader.readInt32()
                if (chunkSize < 8 || chunkStart + chunkSize > bytes.size) {
                    throw IllegalArgumentException("Bozuk ARSC chunk boyutu: $chunkSize (konum $chunkStart)")
                }
                val chunkBytes = reader.slice(chunkStart, chunkSize)
                reader.position = chunkStart + chunkSize

                when (chunkType) {
                    ResStringPool.CHUNK_TYPE_STRING_POOL -> {
                        if (globalPool == null) {
                            globalPool = ResStringPool.parse(chunkBytes)
                        } else {
                            trailing.add(chunkBytes)
                        }
                    }
                    ParsedArscPackage.CHUNK_TYPE_PACKAGE -> {
                        packages.add(parsePackage(chunkBytes))
                    }
                    else -> trailing.add(chunkBytes)
                }
            }

            val finalPool = globalPool
                ?: throw IllegalArgumentException("resources.arsc içinde global string havuzu bulunamadı.")
            if (packages.isEmpty() || packageCount == 0) {
                throw IllegalArgumentException("resources.arsc içinde geçerli kaynak paketi bulunamadı.")
            }
            return ArscTable(finalPool, packages, trailing)
        }

        private fun parsePackage(pkgBytes: ByteArray): ParsedArscPackage {
            val reader = LittleEndianReader(pkgBytes)
            reader.readUInt16() // type
            val headerSize = reader.readUInt16()
            val totalSize = reader.readInt32()
            val pkgId = reader.readInt32()
            val pkgName = reader.readUtf16Fixed(128)
            val typeStringsOff = reader.readInt32()
            val lastPublicType = reader.readInt32()
            val keyStringsOff = reader.readInt32()
            val lastPublicKey = reader.readInt32()
            val typeIdOffset = if (headerSize >= 288 && reader.position + 4 <= headerSize) {
                reader.readInt32()
            } else {
                0
            }
            val extraHeaderBytes = if (reader.position < headerSize) {
                reader.readBytes(headerSize - reader.position)
            } else {
                ByteArray(0)
            }

            // Parse typeStrings and keyStrings pools
            reader.position = typeStringsOff
            reader.readUInt16()
            reader.readUInt16()
            val typeStringsSize = reader.readInt32()
            val typeStrings = ResStringPool.parse(reader.slice(typeStringsOff, typeStringsSize))

            reader.position = keyStringsOff
            reader.readUInt16()
            reader.readUInt16()
            val keyStringsSize = reader.readInt32()
            val keyStrings = ResStringPool.parse(reader.slice(keyStringsOff, keyStringsSize))

            val translatableTypeIds = mutableSetOf<Int>()
            val children = mutableListOf<ArscPackageChild>()

            var cursor = maxOf(typeStringsOff + typeStringsSize, keyStringsOff + keyStringsSize)
            while (cursor + 8 <= pkgBytes.size && cursor < totalSize) {
                reader.position = cursor
                val childType = reader.readUInt16()
                reader.readUInt16()
                val childSize = reader.readInt32()
                if (childSize < 8 || cursor + childSize > pkgBytes.size) break
                val childBytes = reader.slice(cursor, childSize)
                cursor += childSize

                if (childType == ParsedTypeChunk.CHUNK_TYPE_TABLE_TYPE) {
                    val chunkTypeId = childBytes[8].toInt() and 0xFF
                    val typeNameIdx = chunkTypeId - 1 - typeIdOffset
                    val typeName = typeStrings.getOrNull(typeNameIdx)
                        ?: typeStrings.getOrNull(chunkTypeId - 1)
                        ?: ""
                    if (typeName == "string" || typeName == "array" || typeName == "plurals") {
                        translatableTypeIds.add(chunkTypeId)
                        children.add(ParsedTypeChunk.parse(childBytes))
                    } else {
                        children.add(RawPackageChild(childType, childBytes))
                    }
                } else {
                    children.add(RawPackageChild(childType, childBytes))
                }
            }

            return ParsedArscPackage(
                headerSize = headerSize,
                packageId = pkgId,
                packageName = pkgName,
                typeIdOffset = typeIdOffset,
                lastPublicType = lastPublicType,
                lastPublicKey = lastPublicKey,
                extraHeaderBytes = extraHeaderBytes,
                typeStrings = typeStrings,
                keyStrings = keyStrings,
                children = children
            )
        }
    }
}
