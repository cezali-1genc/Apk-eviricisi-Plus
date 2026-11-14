package com.example.apktranslatorpro.core.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DexSummary(
    val dexName: String,
    val version: String,
    val fileSize: Int,
    val stringCount: Int,
    val typeCount: Int,
    val methodCount: Int,
    val classCount: Int,
    val stringsSample: List<String>,
    val classes: List<DexClassModel>
)

data class DexClassModel(
    val descriptor: String,
    val simpleName: String,
    val superDescriptor: String,
    val smaliSource: String,
    val javaSource: String
)

/**
 * Pure-Kotlin binary Dalvik DEX inspector that decodes real DEX headers, MUTF-8 strings,
 * type/proto/method tables, class definitions, and Dalvik instructions into Smali and Java views.
 */
object DexSmaliInspector {

    fun parseDex(dexName: String, bytes: ByteArray, maxClasses: Int = 80): DexSummary {
        if (bytes.size < 112) {
            throw IllegalArgumentException("$dexName geçerli bir DEX dosyası değil (boyut < 112 bayt).")
        }
        if (bytes[0] != 'd'.code.toByte() || bytes[1] != 'e'.code.toByte() || bytes[2] != 'x'.code.toByte() || bytes[3] != '\n'.code.toByte()) {
            throw IllegalArgumentException("$dexName geçersiz DEX sihirli başlığına sahip.")
        }
        val version = String(bytes, 4, 3, Charsets.US_ASCII)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val fileSize = buf.getInt(32)
        val stringIdsSize = buf.getInt(56).coerceAtLeast(0)
        val stringIdsOff = buf.getInt(60)
        val typeIdsSize = buf.getInt(64).coerceAtLeast(0)
        val typeIdsOff = buf.getInt(68)
        val protoIdsSize = buf.getInt(72).coerceAtLeast(0)
        val protoIdsOff = buf.getInt(76)
        val fieldIdsSize = buf.getInt(80).coerceAtLeast(0)
        val fieldIdsOff = buf.getInt(84)
        val methodIdsSize = buf.getInt(88).coerceAtLeast(0)
        val methodIdsOff = buf.getInt(92)
        val classDefsSize = buf.getInt(96).coerceAtLeast(0)
        val classDefsOff = buf.getInt(100)

        val stringCache = HashMap<Int, String>(minOf(stringIdsSize, 4096))
        fun getString(idx: Int): String {
            if (idx < 0 || idx >= stringIdsSize) return ""
            return stringCache.getOrPut(idx) {
                val entryOff = stringIdsOff + idx * 4
                if (entryOff + 4 > bytes.size) return@getOrPut ""
                val dataOff = buf.getInt(entryOff)
                readMutf8String(bytes, dataOff)
            }
        }

        fun getType(typeIdx: Int): String {
            if (typeIdx < 0 || typeIdx >= typeIdsSize) return "Ljava/lang/Object;"
            val off = typeIdsOff + typeIdx * 4
            if (off + 4 > bytes.size) return "Ljava/lang/Object;"
            val descIdx = buf.getInt(off)
            return getString(descIdx)
        }

        fun getMethodSignature(methodIdx: Int): String {
            if (methodIdx < 0 || methodIdx >= methodIdsSize) return "unknown()->unknown()V"
            val off = methodIdsOff + methodIdx * 8
            if (off + 8 > bytes.size) return "unknown()->unknown()V"
            val classIdx = buf.getShort(off).toInt() and 0xFFFF
            val protoIdx = buf.getShort(off + 2).toInt() and 0xFFFF
            val nameIdx = buf.getInt(off + 4)
            val classDesc = getType(classIdx)
            val methodName = getString(nameIdx)
            val retType = if (protoIdx in 0 until protoIdsSize && protoIdsOff + protoIdx * 12 + 8 <= bytes.size) {
                val retIdx = buf.getInt(protoIdsOff + protoIdx * 12 + 4)
                getType(retIdx)
            } else {
                "V"
            }
            return "$classDesc->$methodName()$retType"
        }

        fun getFieldSignature(fieldIdx: Int): String {
            if (fieldIdx < 0 || fieldIdx >= fieldIdsSize) return "unknown:I"
            val off = fieldIdsOff + fieldIdx * 8
            if (off + 8 > bytes.size) return "unknown:I"
            val classIdx = buf.getShort(off).toInt() and 0xFFFF
            val typeIdx = buf.getShort(off + 2).toInt() and 0xFFFF
            val nameIdx = buf.getInt(off + 4)
            return "${getType(classIdx)}->${getString(nameIdx)}:${getType(typeIdx)}"
        }

        val sampleStrings = (0 until minOf(stringIdsSize, 400))
            .mapNotNull { i -> getString(i).takeIf { it.isNotBlank() && it.length <= 160 } }

        val classes = mutableListOf<DexClassModel>()
        val limitClasses = minOf(classDefsSize, maxClasses)
        for (c in 0 until limitClasses) {
            val defOff = classDefsOff + c * 32
            if (defOff + 32 > bytes.size) break
            val classIdx = buf.getInt(defOff)
            val accessFlags = buf.getInt(defOff + 4)
            val superIdx = buf.getInt(defOff + 8)
            val classDataOff = buf.getInt(defOff + 24)

            val descriptor = getType(classIdx)
            val superDesc = if (superIdx >= 0) getType(superIdx) else "Ljava/lang/Object;"
            val simpleName = descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')

            val smali = StringBuilder()
            val java = StringBuilder()

            val pkgName = simpleName.substringBeforeLast('.', "")
            val shortClass = simpleName.substringAfterLast('.')
            if (pkgName.isNotEmpty()) {
                java.append("package ").append(pkgName).append(";\n\n")
            }
            val accessText = decodeAccessFlags(accessFlags)
            smali.append(".class ").append(accessText).append(' ').append(descriptor).append('\n')
            smali.append(".super ").append(superDesc).append('\n')
            smali.append(".source \"").append(shortClass).append(".java\"\n\n")

            java.append(accessText).append(" class ").append(shortClass)
            if (superDesc != "Ljava/lang/Object;") {
                java.append(" extends ").append(dexTypeToJava(superDesc))
            }
            java.append(" {\n")

            if (classDataOff > 0 && classDataOff < bytes.size) {
                val cursor = IntArray(1) { classDataOff }
                val staticFieldsCount = readUleb128(bytes, cursor)
                val instanceFieldsCount = readUleb128(bytes, cursor)
                val directMethodsCount = readUleb128(bytes, cursor)
                val virtualMethodsCount = readUleb128(bytes, cursor)

                var prevFieldIdx = 0
                repeat(staticFieldsCount + instanceFieldsCount) { idx ->
                    val diff = readUleb128(bytes, cursor)
                    val fAccess = readUleb128(bytes, cursor)
                    prevFieldIdx += diff
                    val fSig = getFieldSignature(prevFieldIdx).substringAfter("->")
                    val fName = fSig.substringBefore(':')
                    val fType = fSig.substringAfter(':')
                    val isStatic = idx < staticFieldsCount
                    val mod = decodeAccessFlags(fAccess) + if (isStatic) " static" else ""
                    smali.append(".field ").append(mod.trim()).append(' ').append(fName).append(':').append(fType).append('\n')
                    java.append("    ").append(mod.trim()).append(' ').append(dexTypeToJava(fType)).append(' ').append(fName).append(";\n")
                }

                if (staticFieldsCount + instanceFieldsCount > 0) {
                    smali.append('\n')
                    java.append('\n')
                }

                fun readMethodSection(count: Int, isDirect: Boolean) {
                    var prevMethodIdx = 0
                    repeat(count) {
                        val diff = readUleb128(bytes, cursor)
                        val mAccess = readUleb128(bytes, cursor)
                        val codeOff = readUleb128(bytes, cursor)
                        prevMethodIdx += diff
                        val mSig = getMethodSignature(prevMethodIdx).substringAfter("->")
                        val mMod = decodeAccessFlags(mAccess).trim()
                        smali.append(".method ").append(if (mMod.isNotEmpty()) "$mMod " else "").append(mSig).append('\n')

                        val mName = mSig.substringBefore('(')
                        val retJava = dexTypeToJava(mSig.substringAfterLast(')'))
                        java.append("    ").append(if (mMod.isNotEmpty()) "$mMod " else "")
                            .append(retJava).append(' ').append(mName).append("() {\n")

                        if (codeOff > 0 && codeOff + 16 <= bytes.size) {
                            val regs = buf.getShort(codeOff).toInt() and 0xFFFF
                            val insnsSize = buf.getInt(codeOff + 12).coerceAtLeast(0).coerceAtMost(256)
                            smali.append("    .registers ").append(regs).append('\n')
                            val codeLines = disassembleBytecode(
                                bytes = bytes,
                                buf = buf,
                                insnStart = codeOff + 16,
                                insnUnits = insnsSize,
                                getString = ::getString,
                                getMethod = ::getMethodSignature,
                                getField = ::getFieldSignature
                            )
                            for (line in codeLines) {
                                smali.append("    ").append(line.smali).append('\n')
                                if (line.javaHint != null) {
                                    java.append("        ").append(line.javaHint).append('\n')
                                }
                            }
                        } else {
                            smali.append("    # abstract or native method\n")
                            java.append("        // abstract/native\n")
                        }
                        smali.append(".end method\n\n")
                        java.append("    }\n\n")
                    }
                }

                readMethodSection(directMethodsCount, isDirect = true)
                readMethodSection(virtualMethodsCount, isDirect = false)
            }

            java.append("}\n")
            classes.add(
                DexClassModel(
                    descriptor = descriptor,
                    simpleName = simpleName,
                    superDescriptor = superDesc,
                    smaliSource = smali.toString(),
                    javaSource = java.toString()
                )
            )
        }

        return DexSummary(
            dexName = dexName,
            version = version,
            fileSize = fileSize,
            stringCount = stringIdsSize,
            typeCount = typeIdsSize,
            methodCount = methodIdsSize,
            classCount = classDefsSize,
            stringsSample = sampleStrings,
            classes = classes
        )
    }

    private data class DecodedInsn(val smali: String, val javaHint: String?)

    private fun disassembleBytecode(
        bytes: ByteArray,
        buf: ByteBuffer,
        insnStart: Int,
        insnUnits: Int,
        getString: (Int) -> String,
        getMethod: (Int) -> String,
        getField: (Int) -> String
    ): List<DecodedInsn> {
        val out = mutableListOf<DecodedInsn>()
        var unit = 0
        while (unit < insnUnits && insnStart + (unit + 1) * 2 <= bytes.size) {
            val pos = insnStart + unit * 2
            val codeUnit = buf.getShort(pos).toInt() and 0xFFFF
            val opcode = codeUnit and 0xFF
            val vA = (codeUnit ushr 8) and 0x0F
            val vB = (codeUnit ushr 12) and 0x0F
            val aa = (codeUnit ushr 8) and 0xFF

            when (opcode) {
                0x00 -> {
                    out.add(DecodedInsn("nop", null))
                    unit += 1
                }
                0x0e -> {
                    out.add(DecodedInsn("return-void", "return;"))
                    unit += 1
                }
                0x0f, 0x10, 0x11 -> {
                    out.add(DecodedInsn("return-object v$aa", "return v$aa;"))
                    unit += 1
                }
                0x0a, 0x0b, 0x0c -> {
                    out.add(DecodedInsn("move-result-object v$aa", null))
                    unit += 1
                }
                0x12 -> {
                    out.add(DecodedInsn("const/4 v$vA, #int $vB", "int v$vA = $vB;"))
                    unit += 1
                }
                0x1a -> {
                    val strIdx = if (pos + 4 <= bytes.size) buf.getShort(pos + 2).toInt() and 0xFFFF else 0
                    val s = getString(strIdx).replace("\n", "\\n").replace("\"", "\\\"").take(90)
                    out.add(DecodedInsn("const-string v$aa, \"$s\"", "String v$aa = \"$s\";"))
                    unit += 2
                }
                0x1b -> {
                    val strIdx = if (pos + 6 <= bytes.size) buf.getInt(pos + 2) else 0
                    val s = getString(strIdx).replace("\n", "\\n").replace("\"", "\\\"").take(90)
                    out.add(DecodedInsn("const-string/jumbo v$aa, \"$s\"", "String v$aa = \"$s\";"))
                    unit += 3
                }
                0x28 -> {
                    val off = aa.toByte().toInt()
                    out.add(DecodedInsn(":goto_${unit + off}\n    goto :goto_${unit + off}", "// jump +$off"))
                    unit += 1
                }
                in 0x32..0x3d -> {
                    val off = if (pos + 4 <= bytes.size) buf.getShort(pos + 2).toInt() else 0
                    out.add(DecodedInsn("if-cond v$vA, v$vB, :cond_${unit + off}", "if (cond) { /* :cond_${unit + off} */ }"))
                    unit += 2
                }
                in 0x52..0x6d -> {
                    val fldIdx = if (pos + 4 <= bytes.size) buf.getShort(pos + 2).toInt() and 0xFFFF else 0
                    val sig = getField(fldIdx)
                    val opName = if (opcode >= 0x60) "sget/sput" else "iget/iput"
                    out.add(DecodedInsn("$opName v$vA, $sig", "// field: $sig"))
                    unit += 2
                }
                in 0x6e..0x72 -> {
                    val mIdx = if (pos + 4 <= bytes.size) buf.getShort(pos + 2).toInt() and 0xFFFF else 0
                    val argRegs = if (pos + 6 <= bytes.size) buf.getShort(pos + 4).toInt() and 0xFFFF else 0
                    val r0 = argRegs and 0x0F
                    val mSig = getMethod(mIdx)
                    val opName = when (opcode) {
                        0x6e -> "invoke-virtual"
                        0x6f -> "invoke-super"
                        0x70 -> "invoke-direct"
                        0x71 -> "invoke-static"
                        else -> "invoke-interface"
                    }
                    out.add(DecodedInsn("$opName {v$r0}, $mSig", "${mSig.substringAfterLast('/').replace("->", ".")};"))
                    unit += 3
                }
                else -> {
                    out.add(DecodedInsn("op_0x${opcode.toString(16).padStart(2, '0')} v$aa", null))
                    unit += 1
                }
            }
        }
        return out
    }

    private fun decodeAccessFlags(flags: Int): String = buildList {
        if ((flags and 0x0001) != 0) add("public")
        if ((flags and 0x0002) != 0) add("private")
        if ((flags and 0x0004) != 0) add("protected")
        if ((flags and 0x0008) != 0) add("static")
        if ((flags and 0x0010) != 0) add("final")
        if ((flags and 0x0200) != 0) add("interface")
        if ((flags and 0x0400) != 0) add("abstract")
    }.joinToString(" ")

    private fun dexTypeToJava(desc: String): String = when {
        desc == "V" -> "void"
        desc == "Z" -> "boolean"
        desc == "B" -> "byte"
        desc == "S" -> "short"
        desc == "C" -> "char"
        desc == "I" -> "int"
        desc == "J" -> "long"
        desc == "F" -> "float"
        desc == "D" -> "double"
        desc.startsWith("[") -> "${dexTypeToJava(desc.substring(1))}[]"
        desc.startsWith("L") && desc.endsWith(";") -> desc.substring(1, desc.length - 1).substringAfterLast('/')
        else -> desc
    }

    private fun readUleb128(bytes: ByteArray, cursor: IntArray): Int {
        var result = 0
        var shift = 0
        while (cursor[0] < bytes.size) {
            val b = bytes[cursor[0]++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    private fun readMutf8String(bytes: ByteArray, offset: Int): String {
        if (offset < 0 || offset >= bytes.size) return ""
        val cursor = IntArray(1) { offset }
        readUleb128(bytes, cursor) // utf16 length
        val start = cursor[0]
        var end = start
        while (end < bytes.size && bytes[end].toInt() != 0 && end - start < 4096) {
            end++
        }
        return runCatching { String(bytes, start, end - start, Charsets.UTF_8) }.getOrDefault("")
    }
}
