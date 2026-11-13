package com.example.apktranslatorpro.core.apk

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LittleEndianReader(private val data: ByteArray) {
    var position: Int = 0

    val size: Int get() = data.size
    val remaining: Int get() = data.size - position

    fun readUInt8(): Int {
        val v = data[position].toInt() and 0xFF
        position += 1
        return v
    }

    fun readUInt16(): Int {
        val v = (data[position].toInt() and 0xFF) or
            ((data[position + 1].toInt() and 0xFF) shl 8)
        position += 2
        return v
    }

    fun readInt32(): Int {
        val v = (data[position].toInt() and 0xFF) or
            ((data[position + 1].toInt() and 0xFF) shl 8) or
            ((data[position + 2].toInt() and 0xFF) shl 16) or
            ((data[position + 3].toInt() and 0xFF) shl 24)
        position += 4
        return v
    }

    fun readUInt32(): Long = readInt32().toLong() and 0xFFFFFFFFL

    fun readBytes(length: Int): ByteArray {
        val out = data.copyOfRange(position, position + length)
        position += length
        return out
    }

    fun slice(start: Int, length: Int): ByteArray = data.copyOfRange(start, start + length)

    fun readUtf16Fixed(charCount: Int): String {
        val bytes = readBytes(charCount * 2)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sb = StringBuilder()
        for (i in 0 until charCount) {
            val ch = bb.short.toInt() and 0xFFFF
            if (ch == 0) break
            sb.append(ch.toChar())
        }
        return sb.toString()
    }
}

class LittleEndianWriter(initialCapacity: Int = 1024) {
    private val out = ByteArrayOutputStream(initialCapacity)

    val size: Int get() = out.size()

    fun writeUInt8(value: Int) {
        out.write(value and 0xFF)
    }

    fun writeUInt16(value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    fun writeInt32(value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    fun writeBytes(bytes: ByteArray) {
        out.write(bytes)
    }

    fun writeZeroes(count: Int) {
        repeat(count) { out.write(0) }
    }

    fun padTo4Bytes() {
        val rem = out.size() % 4
        if (rem != 0) {
            writeZeroes(4 - rem)
        }
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

fun ByteArray.patchInt32(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
}
