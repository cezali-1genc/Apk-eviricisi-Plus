package com.example.apktranslatorpro.core.apk

import java.nio.charset.StandardCharsets

data class StringPoolSpan(
    val nameIndex: Int,
    val firstChar: Int,
    val lastChar: Int
)

class ResStringPool(
    val strings: MutableList<String>,
    val styles: MutableList<List<StringPoolSpan>>,
    var flags: Int
) {
    val isUtf8: Boolean
        get() = (flags and UTF8_FLAG) != 0

    fun getOrNull(index: Int): String? = strings.getOrNull(index)

    fun appendString(value: String): Int {
        val idx = strings.size
        strings.add(value)
        // Appended strings are not guaranteed to remain lexicographically sorted
        flags = flags and SORTED_FLAG.inv()
        return idx
    }

    fun toByteArray(): ByteArray {
        val writer = LittleEndianWriter(strings.size * 32 + 64)
        val stringCount = strings.size
        val styleCount = styles.size

        // Chunk header (28 bytes) - size patched at end
        writer.writeUInt16(CHUNK_TYPE_STRING_POOL)
        writer.writeUInt16(HEADER_SIZE)
        writer.writeInt32(0) // placeholder for total size
        writer.writeInt32(stringCount)
        writer.writeInt32(styleCount)
        writer.writeInt32(flags)
        writer.writeInt32(0) // placeholder for stringsStart
        writer.writeInt32(0) // placeholder for stylesStart

        // Reserve offset tables
        val stringOffsetsPos = writer.size
        writer.writeZeroes(stringCount * 4)
        val styleOffsetsPos = writer.size
        writer.writeZeroes(styleCount * 4)

        val encodedStrings = LittleEndianWriter(strings.size * 24)
        val stringOffsets = IntArray(stringCount)

        if (isUtf8) {
            for (i in 0 until stringCount) {
                stringOffsets[i] = encodedStrings.size
                val s = strings[i]
                val utf8 = s.toByteArray(StandardCharsets.UTF_8)
                writeUtf8Length(encodedStrings, s.length)
                writeUtf8Length(encodedStrings, utf8.size)
                encodedStrings.writeBytes(utf8)
                encodedStrings.writeUInt8(0)
            }
        } else {
            for (i in 0 until stringCount) {
                stringOffsets[i] = encodedStrings.size
                val s = strings[i]
                writeUtf16Length(encodedStrings, s.length)
                for (ch in s) {
                    encodedStrings.writeUInt16(ch.code)
                }
                encodedStrings.writeUInt16(0)
            }
        }

        val stringsStart = if (stringCount > 0) writer.size else 0
        writer.writeBytes(encodedStrings.toByteArray())
        writer.padTo4Bytes()

        val styleOffsets = IntArray(styleCount)
        val encodedStyles = LittleEndianWriter(styleCount * 16)
        if (styleCount > 0) {
            for (i in 0 until styleCount) {
                styleOffsets[i] = encodedStyles.size
                for (span in styles[i]) {
                    encodedStyles.writeInt32(span.nameIndex)
                    encodedStyles.writeInt32(span.firstChar)
                    encodedStyles.writeInt32(span.lastChar)
                }
                encodedStyles.writeInt32(SPAN_END)
            }
            // AOSP ResStringPool terminates style block with two extra END markers
            encodedStyles.writeInt32(SPAN_END)
            encodedStyles.writeInt32(SPAN_END)
        }

        val stylesStart = if (styleCount > 0) writer.size else 0
        if (styleCount > 0) {
            writer.writeBytes(encodedStyles.toByteArray())
            writer.padTo4Bytes()
        }

        val bytes = writer.toByteArray()
        bytes.patchInt32(4, bytes.size)
        bytes.patchInt32(20, stringsStart)
        bytes.patchInt32(24, stylesStart)

        for (i in 0 until stringCount) {
            bytes.patchInt32(stringOffsetsPos + i * 4, stringOffsets[i])
        }
        for (i in 0 until styleCount) {
            bytes.patchInt32(styleOffsetsPos + i * 4, styleOffsets[i])
        }

        return bytes
    }

    companion object {
        const val CHUNK_TYPE_STRING_POOL = 0x0001
        const val HEADER_SIZE = 28
        const val SORTED_FLAG = 1 shl 0
        const val UTF8_FLAG = 1 shl 8
        const val SPAN_END = -1

        fun parse(chunkBytes: ByteArray): ResStringPool {
            val reader = LittleEndianReader(chunkBytes)
            val type = reader.readUInt16()
            require(type == CHUNK_TYPE_STRING_POOL) { "Beklenen StringPool chunk değil: 0x${type.toString(16)}" }
            val headerSize = reader.readUInt16()
            val totalSize = reader.readInt32()
            val stringCount = reader.readInt32()
            val styleCount = reader.readInt32()
            val flags = reader.readInt32()
            val stringsStart = reader.readInt32()
            val stylesStart = reader.readInt32()

            reader.position = headerSize
            val stringOffsets = IntArray(stringCount) { reader.readInt32() }
            val styleOffsets = IntArray(styleCount) { reader.readInt32() }

            val isUtf8 = (flags and UTF8_FLAG) != 0
            val strings = ArrayList<String>(stringCount)
            for (i in 0 until stringCount) {
                val offset = stringsStart + stringOffsets[i]
                reader.position = offset
                if (isUtf8) {
                    readUtf8Length(reader) // utf16 character length
                    val byteLen = readUtf8Length(reader)
                    val strBytes = reader.readBytes(byteLen)
                    strings.add(String(strBytes, StandardCharsets.UTF_8))
                } else {
                    val charLen = readUtf16Length(reader)
                    val chars = CharArray(charLen)
                    for (c in 0 until charLen) {
                        chars[c] = reader.readUInt16().toChar()
                    }
                    strings.add(String(chars))
                }
            }

            val styles = ArrayList<List<StringPoolSpan>>(styleCount)
            if (styleCount > 0 && stylesStart > 0 && stylesStart < totalSize) {
                for (i in 0 until styleCount) {
                    val offset = stylesStart + styleOffsets[i]
                    if (offset >= totalSize) {
                        styles.add(emptyList())
                        continue
                    }
                    reader.position = offset
                    val spans = mutableListOf<StringPoolSpan>()
                    while (reader.remaining >= 4) {
                        val nameIdx = reader.readInt32()
                        if (nameIdx == SPAN_END) break
                        if (reader.remaining < 8) break
                        val firstChar = reader.readInt32()
                        val lastChar = reader.readInt32()
                        spans.add(StringPoolSpan(nameIdx, firstChar, lastChar))
                    }
                    styles.add(spans)
                }
            }

            return ResStringPool(strings, styles, flags)
        }

        private fun readUtf8Length(reader: LittleEndianReader): Int {
            val first = reader.readUInt8()
            return if ((first and 0x80) != 0) {
                val second = reader.readUInt8()
                ((first and 0x7F) shl 8) or second
            } else {
                first
            }
        }

        private fun readUtf16Length(reader: LittleEndianReader): Int {
            val first = reader.readUInt16()
            return if ((first and 0x8000) != 0) {
                val second = reader.readUInt16()
                ((first and 0x7FFF) shl 16) or second
            } else {
                first
            }
        }

        private fun writeUtf8Length(writer: LittleEndianWriter, length: Int) {
            if (length >= 0x80) {
                writer.writeUInt8(((length ushr 8) and 0x7F) or 0x80)
                writer.writeUInt8(length and 0xFF)
            } else {
                writer.writeUInt8(length)
            }
        }

        private fun writeUtf16Length(writer: LittleEndianWriter, length: Int) {
            if (length >= 0x8000) {
                writer.writeUInt16(((length ushr 16) and 0x7FFF) or 0x8000)
                writer.writeUInt16(length and 0xFFFF)
            } else {
                writer.writeUInt16(length)
            }
        }
    }
}
