package com.example.apktranslatorpro.core.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.OnlineApiConfig
import com.example.apktranslatorpro.domain.model.OnlineProviderPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OnlineTranslationEngine(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun isNetworkConnected(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        hasInternet && (hasTransport || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }.getOrDefault(false)

    suspend fun testConnection(config: OnlineApiConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val out = translateText(
                text = "Settings",
                sourceLang = LanguageOption.ENGLISH,
                targetLang = LanguageOption.TURKISH,
                config = config
            )
            val elapsed = System.currentTimeMillis() - start
            "Bağlantı başarılı (${elapsed}ms): 'Settings' -> '$out'"
        }
    }

    suspend fun translateText(
        text: String,
        sourceLang: LanguageOption,
        targetLang: LanguageOption,
        config: OnlineApiConfig
    ): String = withContext(Dispatchers.IO) {
        if (!isNetworkConnected()) {
            throw IOException("Aktif internet bağlantısı yok.")
        }
        val endpoint = config.endpointUrl.trim()
        val parsedUrl = endpoint.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Geçerli bir çeviri API adresi (URL) yapılandırılmadı.")

        when (config.preset) {
            OnlineProviderPreset.LIBRE_TRANSLATE ->
                callLibreTranslate(normalizeEndpointUrl(parsedUrl, "translate"), text, sourceLang, targetLang, config.apiKey)
            OnlineProviderPreset.GOOGLE_CLOUD_V2 ->
                callGoogleCloudV2(parsedUrl, text, sourceLang, targetLang, config.apiKey)
            OnlineProviderPreset.DEEPL ->
                callDeepL(normalizeEndpointUrl(parsedUrl, "v2/translate"), text, sourceLang, targetLang, config.apiKey)
            OnlineProviderPreset.CUSTOM_JSON ->
                callCustomJson(parsedUrl, text, sourceLang, targetLang, config)
        }
    }

    private fun callLibreTranslate(
        url: HttpUrl,
        text: String,
        sourceLang: LanguageOption,
        targetLang: LanguageOption,
        apiKey: String
    ): String {
        val payload = JSONObject().apply {
            put("q", text)
            put("source", if (sourceLang == LanguageOption.AUTO) "auto" else sourceLang.code)
            put("target", targetLang.code)
            put("format", "text")
            if (apiKey.isNotBlank()) put("api_key", apiKey.trim())
        }
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val bodyStr = executeRequest(req)
        val json = parseJsonObject(bodyStr, "LibreTranslate")
        val errorMsg = json.optString("error")
        if (errorMsg.isNotBlank()) {
            throw IOException("LibreTranslate hatası: $errorMsg")
        }
        return json.optString("translatedText").takeIf { it.isNotEmpty() }
            ?: throw IOException("LibreTranslate yanıtında 'translatedText' bulunamadı.")
    }

    private fun callGoogleCloudV2(
        endpointUrl: HttpUrl,
        text: String,
        sourceLang: LanguageOption,
        targetLang: LanguageOption,
        apiKey: String
    ): String {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            throw IllegalArgumentException("Google Cloud Translate v2 için API anahtarı gerekli.")
        }
        val url = endpointUrl.newBuilder()
            .setQueryParameter("key", trimmedKey)
            .build()

        val payload = JSONObject().apply {
            put("q", JSONArray().put(text))
            if (sourceLang != LanguageOption.AUTO) put("source", sourceLang.code)
            put("target", targetLang.code)
            put("format", "text")
        }
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val bodyStr = executeRequest(req)
        val json = parseJsonObject(bodyStr, "Google Cloud Translate v2")
        val errorObj = json.optJSONObject("error")
        if (errorObj != null) {
            val message = errorObj.optString("message").ifBlank { errorObj.toString() }
            throw IOException("Google Cloud Translate hatası: $message")
        }
        val translations = json.optJSONObject("data")?.optJSONArray("translations")
        val translated = translations?.optJSONObject(0)?.optString("translatedText").orEmpty()
        if (translated.isEmpty()) {
            throw IOException("Google Cloud Translate yanıtında çeviri verisi bulunamadı.")
        }
        return translated
    }

    private fun callDeepL(
        url: HttpUrl,
        text: String,
        sourceLang: LanguageOption,
        targetLang: LanguageOption,
        apiKey: String
    ): String {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            throw IllegalArgumentException("DeepL API için yetkilendirme anahtarı gerekli.")
        }
        val payload = JSONObject().apply {
            put("text", JSONArray().put(text))
            if (sourceLang != LanguageOption.AUTO) {
                put("source_lang", mapDeepLSourceCode(sourceLang))
            }
            put("target_lang", mapDeepLTargetCode(targetLang))
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "DeepL-Auth-Key $trimmedKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val bodyStr = executeRequest(req)
        val json = parseJsonObject(bodyStr, "DeepL")
        val message = json.optString("message")
        val translations = json.optJSONArray("translations")
        if (translations == null || translations.length() == 0) {
            val detail = message.ifBlank { "Boş translations dizisi" }
            throw IOException("DeepL yanıt hatası: $detail")
        }
        val translated = translations.optJSONObject(0)?.optString("text").orEmpty()
        if (translated.isEmpty()) {
            throw IOException("DeepL çeviri metni boş döndü.")
        }
        return translated
    }

    private fun callCustomJson(
        url: HttpUrl,
        text: String,
        sourceLang: LanguageOption,
        targetLang: LanguageOption,
        config: OnlineApiConfig
    ): String {
        val payload = JSONObject().apply {
            put("text", text)
            put("q", text)
            put("source", if (sourceLang == LanguageOption.AUTO) "auto" else sourceLang.code)
            put("target", targetLang.code)
        }
        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))

        val trimmedKey = config.apiKey.trim()
        if (trimmedKey.isNotBlank()) {
            val headerName = config.customHeaderName.trim().ifBlank { "Authorization" }
            val headerVal = if (headerName.equals("Authorization", true) && !trimmedKey.startsWith("Bearer ", true)) {
                "Bearer $trimmedKey"
            } else {
                trimmedKey
            }
            reqBuilder.addHeader(headerName, headerVal)
        }

        val bodyStr = executeRequest(reqBuilder.build())
        val json = parseJsonObject(bodyStr, "Özel API")
        val errMsg = json.optString("error")
        if (errMsg.isNotBlank()) {
            throw IOException("Özel API hatası: $errMsg")
        }
        return json.optString("translatedText")
            .ifEmpty { json.optString("translation") }
            .ifEmpty { json.optString("text") }
            .ifEmpty { json.optString("result") }
            .ifEmpty { throw IOException("Özel API yanıtında çeviri alanı bulunamadı.") }
    }

    private fun parseJsonObject(bodyStr: String, providerName: String): JSONObject {
        val trimmed = bodyStr.trim()
        if (trimmed.isEmpty()) {
            throw IOException("$providerName sunucusundan boş yanıt geldi.")
        }
        return try {
            JSONObject(trimmed)
        } catch (ex: JSONException) {
            throw IOException("$providerName geçersiz JSON yanıtı döndürdü: ${trimmed.take(120)}", ex)
        }
    }

    private fun executeRequest(request: Request): String {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedError = runCatching {
                    val obj = JSONObject(bodyStr)
                    obj.optString("message")
                        .ifBlank { obj.optString("error") }
                        .ifBlank { obj.optJSONObject("error")?.optString("message").orEmpty() }
                }.getOrDefault("")
                val detail = parsedError.ifBlank { bodyStr.take(240) }
                throw IOException("HTTP ${response.code}: $detail")
            }
            return bodyStr
        }
    }

    companion object {
        internal fun normalizeEndpointUrl(url: HttpUrl, defaultPath: String): HttpUrl {
            val cleanSegs = url.pathSegments.filter { it.isNotBlank() }
            if (cleanSegs.isNotEmpty()) return url
            return url.newBuilder()
                .encodedPath("/${defaultPath.trimStart('/')}")
                .build()
        }

        internal fun mapDeepLSourceCode(lang: LanguageOption): String = when (lang) {
            LanguageOption.CHINESE_SIMPLIFIED, LanguageOption.CHINESE_TRADITIONAL -> "ZH"
            LanguageOption.NORWEGIAN -> "NB"
            LanguageOption.BRAZILIAN_PT -> "PT"
            LanguageOption.LATIN_AMERICA_ES -> "ES"
            LanguageOption.MALAY_BRUNEI -> "MS"
            else -> lang.code.substringBefore('-').uppercase()
        }

        internal fun mapDeepLTargetCode(lang: LanguageOption): String = when (lang) {
            LanguageOption.CHINESE_SIMPLIFIED -> "ZH-HANS"
            LanguageOption.CHINESE_TRADITIONAL -> "ZH-HANT"
            LanguageOption.NORWEGIAN -> "NB"
            LanguageOption.BRAZILIAN_PT -> "PT-BR"
            LanguageOption.PORTUGUESE -> "PT-PT"
            LanguageOption.LATIN_AMERICA_ES -> "ES"
            LanguageOption.MALAY_BRUNEI -> "MS"
            else -> lang.code.uppercase()
        }
    }
}
