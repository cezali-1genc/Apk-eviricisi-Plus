package com.example.apktranslatorpro

import com.example.apktranslatorpro.core.apk.ValuesXmlCodec
import com.example.apktranslatorpro.core.text.PlaceholderProtector
import com.example.apktranslatorpro.domain.model.ResourceEntryItem
import com.example.apktranslatorpro.domain.model.ResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ValuesXmlAndPlaceholderTest {

    @Test
    fun `placeholder protector preserves printf html xliff and newline tokens`() {
        val source = "Welcome <b>%1\$s</b>!\\nYou have <xliff:g id=\"cnt\">%2\$d</xliff:g> items & {percent}%."
        val protected = PlaceholderProtector.protect(source)

        assertTrue(protected.tokens.contains("<b>"))
        assertTrue(protected.tokens.contains("%1\$s"))
        assertTrue(protected.tokens.contains("</b>"))
        assertTrue(protected.tokens.contains("\\n"))
        assertTrue(protected.tokens.contains("%2\$d"))
        assertTrue(protected.tokens.contains("{percent}"))

        // Simulate translator adding extra spaces inside markers
        val simulatedTranslation = protected.maskedText
            .replace("Welcome", "Hoş geldin")
            .replace("You have", "Sahipsiniz")
            .replace("items", "öğeye")
            .replace("⟦0⟧", "⟦ 0 ⟧")

        val restored = PlaceholderProtector.restore(simulatedTranslation, protected.tokens)
        assertTrue(PlaceholderProtector.verifyPlaceholdersPreserved(source, restored))
        assertEquals(
            "Hoş geldin <b>%1\$s</b>!\\nSahipsiniz <xliff:g id=\"cnt\">%2\$d</xliff:g> öğeye & {percent}%.",
            restored
        )
    }

    @Test
    fun `repair missing printf placeholder falls back safely to prevent runtime format crash`() {
        val original = "Download %1\$s (%2\$d MB)"
        val brokenTranslation = "İndirildi (%2\$d MB)"
        assertFalse(PlaceholderProtector.verifyPlaceholdersPreserved(original, brokenTranslation))
        val repaired = PlaceholderProtector.repairMissingPlaceholders(original, brokenTranslation)
        assertEquals(original, repaired)
    }

    @Test
    fun `values xml generation and parsing roundtrip preserves strings arrays plurals and escaping`() {
        val items = listOf(
            ResourceEntryItem(
                id = 1,
                kind = ResourceKind.STRING,
                key = "terms_link",
                originalText = "Read & Accept <b>Terms</b>",
                translatedText = "Şartları <b>Oku</b> & Onayla"
            ),
            ResourceEntryItem(
                id = 2,
                kind = ResourceKind.STRING_ARRAY,
                key = "modes",
                subKey = "[0]",
                originalText = "Offline",
                translatedText = "Çevrimdışı"
            ),
            ResourceEntryItem(
                id = 2,
                kind = ResourceKind.STRING_ARRAY,
                key = "modes",
                subKey = "[1]",
                originalText = "Online",
                translatedText = "Çevrimiçi"
            ),
            ResourceEntryItem(
                id = 3,
                kind = ResourceKind.PLURALS,
                key = "files_count",
                subKey = "one",
                originalText = "1 file",
                translatedText = "1 dosya"
            ),
            ResourceEntryItem(
                id = 3,
                kind = ResourceKind.PLURALS,
                key = "files_count",
                subKey = "other",
                originalText = "%d files",
                translatedText = "%d dosya"
            )
        )

        val xml = ValuesXmlCodec.generateStringsXml(items, useTranslated = true)
        assertTrue(xml.contains("<resources"))
        assertTrue(xml.contains("Şartları <b>Oku</b> &amp; Onayla"))
        assertTrue(xml.contains("<string-array name=\"modes\">"))
        assertTrue(xml.contains("<item quantity=\"one\">1 dosya</item>"))

        val reparsed = ValuesXmlCodec.parseStringsXml(xml, "tr")
        assertEquals(5, reparsed.size)
        assertEquals("Şartları <b>Oku</b> & Onayla", reparsed[0].originalText)
        assertEquals("Çevrimiçi", reparsed[2].originalText)
        assertEquals("%d dosya", reparsed[4].originalText)
    }

    @Test
    fun `translation protection config respects toggles and excluded technical terms`() {
        val sample = "Connect <b>%1\$s</b> via Bluetooth to Cezalı 1Genç API"

        // All protections enabled + custom technical term
        val fullConfig = com.example.apktranslatorpro.core.text.TranslationProtectionConfig(
            preservePlaceholders = true,
            preserveMarkupTags = true,
            excludeTechnicalTerms = true,
            excludedTerms = listOf("Bluetooth", "API", "Cezalı 1Genç")
        )
        val fullProtected = PlaceholderProtector.protect(sample, fullConfig)
        assertTrue(fullProtected.tokens.contains("<b>"))
        assertTrue(fullProtected.tokens.contains("%1\$s"))
        assertTrue(fullProtected.tokens.contains("Bluetooth"))
        assertTrue(fullProtected.tokens.contains("Cezalı 1Genç"))
        assertTrue(fullProtected.tokens.contains("API"))

        val restored = PlaceholderProtector.restore(
            fullProtected.maskedText.replace("Connect", "Bağlan"),
            fullProtected.tokens
        )
        assertEquals("Bağlan <b>%1\$s</b> via Bluetooth to Cezalı 1Genç API", restored)

        // Whole-text exclusion check
        assertTrue(PlaceholderProtector.isWholeTextExcluded("Cezalı 1Genç", fullConfig))
        assertFalse(PlaceholderProtector.isWholeTextExcluded("Hello World", fullConfig))

        // Toggles disabled: nothing masked when all 3 switches are off
        val disabledConfig = com.example.apktranslatorpro.core.text.TranslationProtectionConfig(
            preservePlaceholders = false,
            preserveMarkupTags = false,
            excludeTechnicalTerms = false,
            excludedTerms = listOf("Bluetooth")
        )
        val unprotected = PlaceholderProtector.protect(sample, disabledConfig)
        assertTrue(unprotected.tokens.isEmpty())
        assertEquals(sample, unprotected.maskedText)
    }
}
