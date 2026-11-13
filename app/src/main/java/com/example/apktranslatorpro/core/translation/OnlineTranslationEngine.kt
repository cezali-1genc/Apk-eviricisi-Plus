package com.example.apktranslatorpro.core.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.OnlineApiConfig
import com.example.apktranslatorpro.domain.model.OnlineProviderPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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

    fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

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
        if (endpoint.isEmpty() || endpoint.toHttpUrlOrNull() == null) {
            throw IllegalArgumentException("Geçerli bir çeviri API adresi (URL) yapılandırılmadı.")
        }

        when (config.preset) {
            OnlineProviderPreset.LIBRE_TRANSLATE -> callLibreTranslate(endpoint, text, sourceLang, targetLang, config.apiKey)
            OnlineProviderPreset.GOOGLE_CLOUD_V2 -> callGoogleCloudV2(endpoint, text, sourceLang, targetLang, config.apiKey)
            OnlineProviderPreset.DEEPL -> callDeepL(endpoint, text, sourceLang, targetLang, config.apiKey)
            OnlineProviderPreset.CUSTOM_JSON -> callCustomJson(endpoint, text, sourceLang, targetLang, config)
        }
    }

    private fun callLibreTranslate(
        endpoint: String,
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
            if (apiKey.isNotBlank()) put("api_key", apiKey)
        }
        val req = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val bodyStr = executeRequest(req)
        val json = JSONObject(bodyStr)
        if (json.has("error")) {
            throw IOException("LibreTranslate hatası: ${json.optString("error")}")
        }
        return json.optString("translatedText").takeIf { it.isNotEmpty() }
            ?: throw IOException("LibreTranslate yanıtında 'translatedText' bulunamadı.")
    }

    private fun callGoogleCloudV2(
        endpoint: String,
        text: String,
        sourceLang: LanguageOption,
        targetLang: LanguageOption,
        apiKey: String
    ): String {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Google Cloud Translate v2 için API anahtarı gerekli.")
        }
        val url = endpoint.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("key", apiKey)
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
        val json = JSONObject(bodyStr)
        val translations = json.getJSONObject("data").getJSONArray("translations")
        return translations.getJSONObject(0).getString("translatedText")
    }

    private fun callDeepL(
        endpoint: String,
        text: String,
        sourceLang: LanguageOption,
        targetLang: LanguageOption,
        apiKey: String
    ): String {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("DeepL API için yetkilendirme anahtarı gerekli.")
        }
        val payload = JSONObject().apply {
            put("text", JSONArray().put(text))
            if (sourceLang != LanguageOption.AUTO) {
                put("source_lang", sourceLang.code.uppercase())
            }
            put("target_lang", targetLang.code.uppercase())
        }
        val req = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "DeepL-Auth-Key $apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val bodyStr = executeRequest(req)
        val json = JSONObject(bodyStr)
        val translations = json.getJSONArray("translations")
        return translations.getJSONObject(0).getString("text")
    }

    private fun callCustomJson(
        endpoint: String,
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
            .url(endpoint)
            .post(payload.toString().toRequestBody(jsonMediaType))

        if (config.apiKey.isNotBlank()) {
            val headerName = config.customHeaderName.ifBlank { "Authorization" }
            val headerVal = if (headerName.equals("Authorization", true) && !config.apiKey.startsWith("Bearer ", true)) {
                "Bearer ${config.apiKey}"
            } else {
                config.apiKey
            }
            reqBuilder.addHeader(headerName, headerVal)
        }

        val bodyStr = executeRequest(reqBuilder.build())
        val json = JSONObject(bodyStr)
        return json.optString("translatedText")
            .ifEmpty { json.optString("translation") }
            .ifEmpty { json.optString("text") }
            .ifEmpty { json.optString("result") }
            .ifEmpty { throw IOException("Özel API yanıtında çeviri alanı bulunamadı.") }
    }

    private fun executeRequest(request: Request): String {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${bodyStr.take(240)}")
            }
            return bodyStr
        }
    }
}
