package com.example.apktranslatorpro.core.translation

import android.util.Log
import com.example.apktranslatorpro.domain.model.ApkMetadata
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "GeminiTranslationEngine"
private const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
private const val GEMINI_TIMEOUT_MS = 60_000L

class GeminiTranslationEngine {

    private val translationBatchSchema = Schema.obj(
        properties = mapOf(
            "translations" to Schema.array(
                items = Schema.string(description = "Translated string in the exact same order"),
                description = "List of translated UI strings matching the input array length and order"
            )
        )
    )

    private val analysisModel by lazy {
        Firebase.ai.generativeModel(modelName = DEFAULT_GEMINI_MODEL)
    }

    private val batchTranslationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationBatchSchema
            }
        )
    }

    suspend fun translateBatch(
        maskedTexts: List<String>,
        sourceLang: LanguageOption,
        targetLang: LanguageOption,
        appName: String,
        packageName: String
    ): List<String> {
        if (maskedTexts.isEmpty()) return emptyList()

        val payloadJson = JSONArray().apply {
            maskedTexts.forEach { put(it) }
        }

        val prompt = buildString {
            appendLine("You are an expert Android UI localization engineer.")
            appendLine("App Name: $appName ($packageName)")
            appendLine("Source Language: ${if (sourceLang == LanguageOption.AUTO) "Auto-detect" else sourceLang.displayNameTr} (${sourceLang.code})")
            appendLine("Target Language: ${targetLang.displayNameTr} (${targetLang.code})")
            appendLine("Rules:")
            appendLine("1. Translate each string naturally for Android mobile UI.")
            appendLine("2. CRITICAL: Preserve all placeholder tokens like __APK_PH_0__, __APK_PH_1__, %s, %1\$s, %d, \\n, and HTML/XML tags character-for-character without altering or removing them.")
            appendLine("3. Return a JSON object with a single 'translations' array containing EXACTLY ${maskedTexts.size} items in the exact same order.")
            appendLine("Input JSON array:")
            appendLine(payloadJson.toString())
        }

        try {
            val response = withTimeout(GEMINI_TIMEOUT_MS) {
                batchTranslationModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("Gemini boş yanıt döndürdü.")
            val root = JSONObject(rawJson)
            val arr = root.getJSONArray("translations")
            if (arr.length() != maskedTexts.size) {
                throw IllegalStateException(
                    "Gemini yanıt sayısı uyuşmuyor (beklenen=${maskedTexts.size}, gelen=${arr.length()})."
                )
            }
            return List(arr.length()) { idx -> arr.getString(idx) }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini batch translation timed out", e)
            throw IllegalStateException("Gemini çeviri isteği 60 saniye zaman aşımına uğradı.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini batch translation failed", e)
            throw IllegalStateException(
                "Gemini AI çeviri hatası: ${e.localizedMessage ?: e.message ?: "Bilinmeyen hata"}",
                e
            )
        }
    }

    fun analyzeApkStream(metadata: ApkMetadata): Flow<String> = flow {
        val full = metadata.fullInspection
        val prompt = buildString {
            appendLine("Sen kıdemli bir Android APK güvenlik ve yerelleştirme uzmanısın. Yanıtını Türkçe, net maddeler halinde ver.")
            appendLine("Aşağıdaki APK analiz verilerini incele ve geliştiriciye güvenlik, imza, mimari ve yerelleştirme tavsiyeleri sun:")
            appendLine("- Uygulama Adı: ${metadata.appLabel}")
            appendLine("- Paket Adı: ${metadata.packageName}")
            appendLine("- Sürüm: ${metadata.versionName} (code=${metadata.versionCode})")
            appendLine("- SDK: minSdk=${metadata.minSdkVersion}, targetSdk=${metadata.targetSdkVersion}, supportsRtl=${metadata.supportsRtl}")
            appendLine("- Kaynak Sayıları: ${metadata.totalStringCount} string, ${metadata.totalArrayItemCount} dizi, ${metadata.totalPluralItemCount} çoğul")
            appendLine("- Mevcut Diller: ${metadata.availableLocales.joinToString(", ").ifBlank { "Yalnızca varsayılan" }}")
            appendLine("- Algılanan Dil: ${metadata.detectedSourceLanguage.displayNameTr} (%${(metadata.detectedConfidence * 100).toInt()})")
            if (full != null) {
                appendLine("- Debuggable: ${full.isDebuggable}, AllowBackup: ${full.allowBackup}, resources.arsc STORED: ${full.resourcesArscStored}")
                appendLine("- İmza Durumu: verified=${full.isSignatureVerified} (v1=${full.v1Signed}, v2=${full.v2Signed}, v3=${full.v3Signed})")
                appendLine("- Native ABI'ler: ${full.nativeAbis.keys.joinToString(", ").ifBlank { "Yok (Saf DEX)" }}")
                appendLine("- DEX Sayısı: ${full.dexFiles.size}")
                appendLine("- Tehlikeli İzinler (${full.dangerousPermissions.size}): ${full.dangerousPermissions.joinToString(", ").ifBlank { "Yok" }}")
                appendLine("- Toplam İzinler (${full.usesPermissions.size}): ${full.usesPermissions.take(25).joinToString(", ")}")
            }
            if (metadata.securityWarnings.isNotEmpty()) {
                appendLine("- Mevcut Uyarılar: ${metadata.securityWarnings.joinToString(" | ")}")
            }
            appendLine("Lütfen 4 kısa başlık altında özetle: 1) Güvenlik & İzin Değerlendirmesi, 2) İmza & Play Lisans Uyumluluğu, 3) Mimari & Zipalign Durumu, 4) Çeviri & RTL Önerileri.")
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(GEMINI_TIMEOUT_MS) {
                analysisModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini analiz yanıtı boş döndü.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini APK analysis timed out", e)
            throw IllegalStateException("Gemini APK analizi 60 saniye zaman aşımına uğradı.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini APK analysis failed", e)
            throw IllegalStateException(
                "Gemini APK analizi başarısız: ${e.localizedMessage ?: e.message ?: "Bilinmeyen hata"}",
                e
            )
        }
    }
}
