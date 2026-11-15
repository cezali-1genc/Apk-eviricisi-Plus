package com.example.apktranslatorpro

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.example.apktranslatorpro.core.apk.ApkIconExtractor
import com.example.apktranslatorpro.core.apk.ApkPackager
import com.example.apktranslatorpro.core.translation.OnlineTranslationEngine
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.OnlineApiConfig
import com.example.apktranslatorpro.domain.model.OnlineProviderPreset
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineTranslationAndMemoryTest {

    private fun configureConnectedWifi(context: Context, validated: Boolean = false) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = ShadowNetworkCapabilities.newInstance()
        val shadowCaps = Shadows.shadowOf(caps)
        shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) {
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        Shadows.shadowOf(cm).setNetworkCapabilities(network, caps)
    }

    @Test
    fun `connectivity accepts active wifi internet even without NET_CAPABILITY_VALIDATED`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureConnectedWifi(context, validated = false)
        val engine = OnlineTranslationEngine(context)
        assertTrue(engine.isNetworkConnected())
    }

    @Test
    fun `DeepL normalizes root url, maps language codes, and parses translation`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureConnectedWifi(context)

        var capturedUrl = ""
        var capturedAuth = ""
        var capturedPayload: JSONObject? = null

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedUrl = req.url.toString()
                capturedAuth = req.header("Authorization").orEmpty()
                val buf = Buffer()
                req.body?.writeTo(buf)
                capturedPayload = JSONObject(buf.readUtf8())
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"translations":[{"text":"設定"}]}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            })
            .build()

        val engine = OnlineTranslationEngine(context, client)
        val result = engine.translateText(
            text = "Settings",
            sourceLang = LanguageOption.NORWEGIAN,
            targetLang = LanguageOption.CHINESE_TRADITIONAL,
            config = OnlineApiConfig(
                preset = OnlineProviderPreset.DEEPL,
                endpointUrl = "https://api-free.deepl.com",
                apiKey = "test-deepl-key"
            )
        )

        assertEquals("設定", result)
        assertEquals("https://api-free.deepl.com/v2/translate", capturedUrl)
        assertEquals("DeepL-Auth-Key test-deepl-key", capturedAuth)
        assertEquals("NB", capturedPayload?.optString("source_lang"))
        assertEquals("ZH-HANT", capturedPayload?.optString("target_lang"))
    }

    @Test
    fun `LibreTranslate appends translate path and handles non-JSON error cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureConnectedWifi(context)

        var capturedUrl = ""
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedUrl = req.url.toString()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("<html>502 Bad Gateway</html>".toResponseBody("text/html".toMediaType()))
                    .build()
            })
            .build()

        val engine = OnlineTranslationEngine(context, client)
        val outcome = runCatching {
            engine.translateText(
                text = "Hello",
                sourceLang = LanguageOption.ENGLISH,
                targetLang = LanguageOption.TURKISH,
                config = OnlineApiConfig(
                    preset = OnlineProviderPreset.LIBRE_TRANSLATE,
                    endpointUrl = "https://libre.example.local",
                    apiKey = ""
                )
            )
        }

        assertEquals("https://libre.example.local/translate", capturedUrl)
        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull() is IOException)
        assertTrue(outcome.exceptionOrNull()?.message?.contains("JSON") == true)
    }

    @Test
    fun `Google Cloud v2 parses error body and success translations accurately`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureConnectedWifi(context)

        var capturedUrl = ""
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedUrl = req.url.toString()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"data":{"translations":[{"translatedText":"Ayarlar"}]}}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            })
            .build()

        val engine = OnlineTranslationEngine(context, client)
        val out = engine.translateText(
            text = "Settings",
            sourceLang = LanguageOption.ENGLISH,
            targetLang = LanguageOption.TURKISH,
            config = OnlineApiConfig(
                preset = OnlineProviderPreset.GOOGLE_CLOUD_V2,
                endpointUrl = "https://translation.googleapis.com/language/translate/v2",
                apiKey = "gc-key-123"
            )
        )

        assertEquals("Ayarlar", out)
        assertTrue(capturedUrl.contains("key=gc-key-123"))
    }

    @Test
    fun `ApkIconExtractor downsamples large raster icons safely below memory cap`() {
        val largeBitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val pngBytes = ByteArrayOutputStream().use { baos ->
            largeBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
        largeBitmap.recycle()

        assertEquals(4, ApkIconExtractor.calculateInSampleSize(1024, 1024, 256, 256))
        val sampled = ApkIconExtractor.decodeSampledBitmap(pngBytes, maxDimensionPx = 256)
        assertNotNull(sampled)
        assertTrue((sampled?.width ?: Int.MAX_VALUE) <= 256)
        assertTrue((sampled?.height ?: Int.MAX_VALUE) <= 256)
        sampled?.recycle()
    }

    @Test
    fun `ApkPackager cleans up partial unsigned APK when source repackaging fails`() {
        val tempDir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "packager_cleanup_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val badZip = File(tempDir, "traversal.apk")
        ZipOutputStream(badZip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("bad".toByteArray())
            zos.closeEntry()
        }
        val outApk = File(tempDir, "partial_unsigned.apk")
        val packager = ApkPackager()

        val failure = runCatching {
            packager.repackageApkWithUpdatedArsc(badZip, outApk, ByteArray(16))
        }

        assertTrue(failure.isFailure)
        assertFalse(outApk.exists())
        tempDir.deleteRecursively()
    }
}
