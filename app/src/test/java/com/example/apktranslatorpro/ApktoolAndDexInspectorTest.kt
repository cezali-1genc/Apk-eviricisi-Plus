package com.example.apktranslatorpro

import android.content.Context
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import com.example.apktranslatorpro.core.apk.ApktoolService
import com.example.apktranslatorpro.core.apk.DexSmaliInspector
import com.example.apktranslatorpro.ui.components.SmaliSyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApktoolAndDexInspectorTest {

    @Test
    fun `apktool decompile rejects invalid non-zip file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = ApktoolService(context)
        val badFile = File(context.cacheDir, "invalid_not_apk.apk").apply {
            writeText("NOT_A_ZIP_OR_APK_FILE")
        }
        val workDir = File(context.cacheDir, "bad_work")
        var thrown = false
        try {
            service.decompileApkFile(badFile, workDir)
        } catch (ex: IllegalArgumentException) {
            thrown = true
            assertTrue(ex.message?.contains("APK", ignoreCase = true) == true)
        } finally {
            badFile.delete()
            workDir.deleteRecursively()
        }
        assertTrue(thrown)
    }

    @Test
    fun `apktool bulkRenameStringKeys updates strings xml and layout references automatically`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = ApktoolService(context)
        val workDir = File(context.cacheDir, "bulk_rename_test").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            File(workDir, "res/values").mkdirs()
            File(workDir, "res/values-tr").mkdirs()
            File(workDir, "res/layout").mkdirs()

            File(workDir, "res/values/strings.xml").writeText(
                """
                <resources>
                    <string name="btn_save">Save</string>
                    <string name="btn_cancel">Cancel</string>
                    <string name="title_home">Home</string>
                </resources>
                """.trimIndent(),
                Charsets.UTF_8
            )
            File(workDir, "res/values-tr/strings.xml").writeText(
                """
                <resources>
                    <string name="btn_save">Kaydet</string>
                    <string name="btn_cancel">İptal</string>
                </resources>
                """.trimIndent(),
                Charsets.UTF_8
            )
            val layoutFile = File(workDir, "res/layout/activity_main.xml").apply {
                writeText(
                    """
                    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                        <Button android:text="@string/btn_save" />
                        <Button android:text="@string/btn_cancel" />
                        <TextView android:text="@string/title_home" />
                    </LinearLayout>
                    """.trimIndent(),
                    Charsets.UTF_8
                )
            }

            val result = service.bulkRenameStringKeys(
                workDir = workDir,
                oldPrefixOrKey = "btn_",
                newPrefixOrKey = "action_",
                prefixMode = true
            )

            assertEquals(2, result.renamedResourceCount)
            assertEquals(2, result.updatedLayoutReferences)

            val updatedStrings = File(workDir, "res/values/strings.xml").readText()
            assertTrue(updatedStrings.contains("""name="action_save""""))
            assertTrue(updatedStrings.contains("""name="action_cancel""""))
            assertTrue(updatedStrings.contains("""name="title_home""""))

            val updatedLayout = layoutFile.readText()
            assertTrue(updatedLayout.contains("@string/action_save"))
            assertTrue(updatedLayout.contains("@string/action_cancel"))
            assertFalse(updatedLayout.contains("@string/btn_save"))

            // Export workspace as ZIP and verify entries exist
            val zipBytes = ByteArrayOutputStream()
            val exportedCount = service.exportWorkspaceAsZip(workDir, zipBytes)
            assertEquals(3, exportedCount)
            val entries = mutableListOf<String>()
            ZipInputStream(zipBytes.toByteArray().inputStream()).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    entries.add(e.name)
                    e = zis.nextEntry
                }
            }
            assertTrue(entries.contains("res/layout/activity_main.xml"))
            assertTrue(entries.contains("res/values/strings.xml"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `smali syntax highlighter distinguishes directives instructions registers labels strings and comments`() {
        val code = """
            .class public Lcom/example/Demo;
            .method public run()V
                :cond_start
                const-string v0, "Hello World"
                invoke-static {v0, p0}, Lcom/example/Log;->d(Ljava/lang/String;)V
                return-void # done
            .end method
        """.trimIndent()

        val annotated = SmaliSyntaxHighlighter.highlight(code, SmaliSyntaxHighlighter.DarkSmaliScheme)
        val styles = annotated.spanStyles

        val directiveColor = SmaliSyntaxHighlighter.DarkSmaliScheme.directiveColor
        val instructionColor = SmaliSyntaxHighlighter.DarkSmaliScheme.instructionColor
        val registerColor = SmaliSyntaxHighlighter.DarkSmaliScheme.registerColor
        val labelColor = SmaliSyntaxHighlighter.DarkSmaliScheme.labelColor
        val stringColor = SmaliSyntaxHighlighter.DarkSmaliScheme.stringLiteralColor
        val commentColor = SmaliSyntaxHighlighter.DarkSmaliScheme.commentColor

        assertTrue(styles.any { it.item.color == directiveColor && it.item.fontWeight == FontWeight.Bold })
        assertTrue(styles.any { it.item.color == instructionColor })
        assertTrue(styles.any { it.item.color == registerColor })
        assertTrue(styles.any { it.item.color == labelColor })
        assertTrue(styles.any { it.item.color == stringColor })
        assertTrue(styles.any { it.item.color == commentColor && it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `dex inspector parses minimal valid dex header`() {
        val bytes = ByteArray(112)
        "dex\n035\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(32, 112) // fileSize
        }
        val summary = DexSmaliInspector.parseDex("classes.dex", bytes)
        assertEquals("035", summary.version)
        assertEquals(112, summary.fileSize)
    }

    @Test
    fun `dex inspector rejects invalid magic bytes`() {
        val bad = ByteArray(128)
        var thrown = false
        try {
            DexSmaliInspector.parseDex("classes.dex", bad)
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
