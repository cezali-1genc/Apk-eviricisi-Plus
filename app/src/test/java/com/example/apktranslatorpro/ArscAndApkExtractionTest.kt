package com.example.apktranslatorpro

import com.example.apktranslatorpro.core.apk.ArscEntry
import com.example.apktranslatorpro.core.apk.ArscTable
import com.example.apktranslatorpro.core.apk.LittleEndianWriter
import com.example.apktranslatorpro.core.apk.ParsedArscPackage
import com.example.apktranslatorpro.core.apk.ParsedTypeChunk
import com.example.apktranslatorpro.core.apk.RawPackageChild
import com.example.apktranslatorpro.core.apk.ResStringPool
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.ResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArscAndApkExtractionTest {

    @Test
    fun `parse and rebuild resources arsc preserves default locale and adds target translations`() {
        val table = buildSyntheticArscTable()
        val rawBytes = table.toByteArray()

        val parsed = ArscTable.parse(rawBytes)
        val extracted = parsed.extractTranslatableResources()

        assertEquals(6, extracted.size)
        assertEquals("Hello %1\$s", extracted.first { it.key == "greeting" }.originalText)
        assertEquals("Item A", extracted.first { it.key == "options" && it.subKey == "[0]" }.originalText)
        assertEquals("1 item", extracted.first { it.key == "count_label" && it.subKey == "one" }.originalText)

        val translated = extracted.map { item ->
            val trText = when (item.key) {
                "app_name" -> "Uygulama"
                "greeting" -> "Merhaba %1\$s"
                "options" -> if (item.subKey == "[0]") "Öğe A" else "Öğe B"
                "count_label" -> if (item.subKey == "one") "1 öğe" else "%d öğe"
                else -> item.originalText
            }
            item.copy(translatedText = trText)
        }

        val appliedCount = parsed.applyTranslationsForTargetLocale(LanguageOption.TURKISH, translated)
        assertEquals(6, appliedCount)

        val rebuiltBytes = parsed.toByteArray()
        val reparsed = ArscTable.parse(rebuiltBytes)

        val locales = reparsed.extractLocales()
        assertTrue(locales.contains("default"))
        assertTrue(locales.contains("tr"))

        // Original default locale must remain untouched
        val defaultItems = reparsed.extractTranslatableResources("default")
        assertEquals("Hello %1\$s", defaultItems.first { it.key == "greeting" }.originalText)

        // New Turkish locale must resolve translated strings, arrays, and plurals
        val turkishItems = reparsed.extractTranslatableResources("tr")
        assertEquals("Merhaba %1\$s", turkishItems.first { it.key == "greeting" }.originalText)
        assertEquals("Öğe B", turkishItems.first { it.key == "options" && it.subKey == "[1]" }.originalText)
        assertEquals("%d öğe", turkishItems.first { it.key == "count_label" && it.subKey == "other" }.originalText)
    }

    @Test
    fun `utf16 string pool roundtrip preserves unicode and emoji`() {
        val pool = ResStringPool(
            strings = mutableListOf("Settings", "Türkçe Ayarlar 🚀", "日本語"),
            styles = mutableListOf(),
            flags = 0 // UTF-16LE
        )
        pool.appendString("العربية")
        val bytes = pool.toByteArray()
        val reparsed = ResStringPool.parse(bytes)
        assertEquals(listOf("Settings", "Türkçe Ayarlar 🚀", "日本語", "العربية"), reparsed.strings)
    }

    @Test
    fun `strict stringsOnly scope translates only strings in default tr en without touching arrays plurals`() {
        val table = buildSyntheticArscTable()
        val parsed = ArscTable.parse(table.toByteArray())

        val stringsOnly = parsed.extractTranslatableResources(stringsOnly = true)
        assertEquals(2, stringsOnly.size)
        assertTrue(stringsOnly.all { it.kind == ResourceKind.STRING })

        val translated = stringsOnly.map {
            when (it.key) {
                "app_name" -> it.copy(translatedText = "Örnek Uygulama")
                "greeting" -> it.copy(translatedText = "Selam %1\$s")
                else -> it
            }
        }

        val applied = parsed.applyTranslationsForScopes(
            targetLanguage = LanguageOption.TURKISH,
            translatedItems = translated,
            stringsOnly = true,
            translateDefaultStrings = true,
            translateValuesTr = true,
            translateValuesEn = true
        )
        assertEquals(4, applied) // 2 strings in default + 2 strings in tr

        val reparsed = ArscTable.parse(parsed.toByteArray())
        val defaultItems = reparsed.extractTranslatableResources("default")
        assertEquals("Örnek Uygulama", defaultItems.first { it.key == "app_name" }.originalText)
        assertEquals("Selam %1\$s", defaultItems.first { it.key == "greeting" }.originalText)
        // Arrays & plurals remained completely untouched!
        assertEquals("Item A", defaultItems.first { it.key == "options" && it.subKey == "[0]" }.originalText)
        assertEquals("1 item", defaultItems.first { it.key == "count_label" && it.subKey == "one" }.originalText)

        val trItems = reparsed.extractTranslatableResources("tr")
        assertEquals("Örnek Uygulama", trItems.first { it.key == "app_name" }.originalText)
        assertEquals("Selam %1\$s", trItems.first { it.key == "greeting" }.originalText)
    }

    companion object {
        fun buildSyntheticArscTable(): ArscTable {
            val globalPool = ResStringPool(
                strings = mutableListOf(
                    "Demo App",
                    "Hello %1\$s",
                    "Item A",
                    "Item B",
                    "1 item",
                    "%d items"
                ),
                styles = mutableListOf(),
                flags = ResStringPool.UTF8_FLAG
            )

            val typeStrings = ResStringPool(
                strings = mutableListOf("string", "array", "plurals"),
                styles = mutableListOf(),
                flags = ResStringPool.UTF8_FLAG
            )

            val keyStrings = ResStringPool(
                strings = mutableListOf("app_name", "greeting", "options", "count_label"),
                styles = mutableListOf(),
                flags = ResStringPool.UTF8_FLAG
            )

            val configBytes = ByteArray(48).apply {
                this[0] = 48 // config size = 48
            }

            val stringChunk = ParsedTypeChunk(
                typeId = 1,
                flags = 0,
                res0 = 0,
                logicalEntryCount = 2,
                configBytes = configBytes.copyOf(),
                entriesByIndex = linkedMapOf(
                    0 to ArscEntry.Simple(0, 0, 8, 0, ParsedTypeChunk.TYPE_STRING, 0),
                    1 to ArscEntry.Simple(0, 1, 8, 0, ParsedTypeChunk.TYPE_STRING, 1)
                )
            )

            val arrayChunk = ParsedTypeChunk(
                typeId = 2,
                flags = 0,
                res0 = 0,
                logicalEntryCount = 1,
                configBytes = configBytes.copyOf(),
                entriesByIndex = linkedMapOf(
                    0 to ArscEntry.Complex(
                        flags = ParsedTypeChunk.FLAG_COMPLEX,
                        keyIndex = 2,
                        parentIdent = 0,
                        items = mutableListOf(
                            ArscEntry.MapItem(0x02000000, 8, 0, ParsedTypeChunk.TYPE_STRING, 2),
                            ArscEntry.MapItem(0x02000001, 8, 0, ParsedTypeChunk.TYPE_STRING, 3)
                        )
                    )
                )
            )

            val pluralsChunk = ParsedTypeChunk(
                typeId = 3,
                flags = 0,
                res0 = 0,
                logicalEntryCount = 1,
                configBytes = configBytes.copyOf(),
                entriesByIndex = linkedMapOf(
                    0 to ArscEntry.Complex(
                        flags = ParsedTypeChunk.FLAG_COMPLEX,
                        keyIndex = 3,
                        parentIdent = 0,
                        items = mutableListOf(
                            ArscEntry.MapItem(0x01000006, 8, 0, ParsedTypeChunk.TYPE_STRING, 4), // one
                            ArscEntry.MapItem(0x01000004, 8, 0, ParsedTypeChunk.TYPE_STRING, 5)  // other
                        )
                    )
                )
            )

            val typeSpec1 = buildTypeSpecChunk(1, 2)
            val typeSpec2 = buildTypeSpecChunk(2, 1)
            val typeSpec3 = buildTypeSpecChunk(3, 1)

            val pkg = ParsedArscPackage(
                headerSize = 288,
                packageId = 0x7F,
                packageName = "com.example.demo",
                typeIdOffset = 0,
                lastPublicType = 3,
                lastPublicKey = 4,
                extraHeaderBytes = ByteArray(0),
                typeStrings = typeStrings,
                keyStrings = keyStrings,
                children = mutableListOf(
                    RawPackageChild(0x0202, typeSpec1),
                    stringChunk,
                    RawPackageChild(0x0202, typeSpec2),
                    arrayChunk,
                    RawPackageChild(0x0202, typeSpec3),
                    pluralsChunk
                )
            )

            return ArscTable(globalPool, mutableListOf(pkg), mutableListOf())
        }

        private fun buildTypeSpecChunk(typeId: Int, entryCount: Int): ByteArray {
            val size = 16 + entryCount * 4
            val w = LittleEndianWriter(size)
            w.writeUInt16(0x0202)
            w.writeUInt16(16)
            w.writeInt32(size)
            w.writeUInt8(typeId)
            w.writeUInt8(0)
            w.writeUInt16(0)
            w.writeInt32(entryCount)
            repeat(entryCount) { w.writeInt32(0) }
            return w.toByteArray()
        }
    }
}
