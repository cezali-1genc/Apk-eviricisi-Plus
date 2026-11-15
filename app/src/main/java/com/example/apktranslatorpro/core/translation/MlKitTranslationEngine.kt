package com.example.apktranslatorpro.core.translation

import android.content.Context
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.OfflineModelState
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class MlKitTranslationEngine(private val context: Context) {

    private fun getModelManagerOrNull(): RemoteModelManager? = runCatching {
        MlKitContext.initializeIfNeeded(context)
        RemoteModelManager.getInstance()
    }.getOrNull()

    suspend fun detectLanguage(samples: List<String>): Pair<LanguageOption, Float> {
        val cleanSamples = samples
            .map { it.replace(Regex("""<[^>]+>|%\d*\$?[sdif]|\\n"""), " ").trim() }
            .filter { it.length >= 4 }
            .take(25)

        if (cleanSamples.isEmpty()) {
            return LanguageOption.ENGLISH to 0.5f
        }

        val combined = cleanSamples.joinToString(". ").take(1200)
        return try {
            runCatching { MlKitContext.initializeIfNeeded(context) }
            val identifier = LanguageIdentification.getClient(
                LanguageIdentificationOptions.Builder()
                    .setConfidenceThreshold(0.35f)
                    .build()
            )
            val possible = identifier.identifyPossibleLanguages(combined).await()
            identifier.close()
            val top = possible.firstOrNull { it.languageTag != "und" }
            if (top != null) {
                LanguageOption.fromCode(top.languageTag) to top.confidence
            } else {
                heuristicDetect(combined)
            }
        } catch (_: Throwable) {
            heuristicDetect(combined)
        }
    }

    suspend fun listOfflineModelStates(): List<OfflineModelState> {
        val downloadedCodes = try {
            val manager = getModelManagerOrNull()
            manager?.getDownloadedModels(TranslateRemoteModel::class.java)
                ?.await()
                ?.map { it.language }
                ?.toSet()
                ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }

        return LanguageOption.mlKitSupportedLanguages.map { lang ->
            val code = lang.mlKitCode
            OfflineModelState(
                language = lang,
                isDownloaded = code != null && code in downloadedCodes
            )
        }
    }

    suspend fun isModelDownloaded(language: LanguageOption): Boolean {
        val code = language.mlKitCode ?: return false
        val manager = getModelManagerOrNull() ?: return false
        val model = TranslateRemoteModel.Builder(code).build()
        return runCatching { manager.isModelDownloaded(model).await() }.getOrDefault(false)
    }

    suspend fun downloadModel(language: LanguageOption) {
        val code = language.mlKitCode
            ?: throw IllegalArgumentException("${language.displayNameTr} ML Kit çevrimdışı modelini desteklemiyor.")
        val manager = getModelManagerOrNull()
            ?: throw IllegalStateException("ML Kit model yöneticisi bu cihazda başlatılamadı.")
        val model = TranslateRemoteModel.Builder(code).build()
        val conditions = DownloadConditions.Builder().build()
        manager.download(model, conditions).await()
    }

    suspend fun deleteModel(language: LanguageOption) {
        val code = language.mlKitCode ?: return
        val manager = getModelManagerOrNull() ?: return
        val model = TranslateRemoteModel.Builder(code).build()
        manager.deleteDownloadedModel(model).await()
    }

    fun createTranslator(sourceLang: LanguageOption, targetLang: LanguageOption): Translator {
        runCatching { MlKitContext.initializeIfNeeded(context) }
        val srcCode = sourceLang.mlKitCode
            ?: throw IllegalArgumentException("Kaynak dil (${sourceLang.displayNameTr}) ML Kit çevrimdışı modda desteklenmiyor.")
        val tgtCode = targetLang.mlKitCode
            ?: throw IllegalArgumentException("Hedef dil (${targetLang.displayNameTr}) ML Kit çevrimdışı modda desteklenmiyor.")

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(srcCode)
            .setTargetLanguage(tgtCode)
            .build()
        return Translation.getClient(options)
    }

    suspend fun ensureTranslatorReady(translator: Translator) {
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()
    }

    suspend fun translateWithClient(translator: Translator, text: String): String {
        return translator.translate(text).await()
    }

    private fun heuristicDetect(text: String): Pair<LanguageOption, Float> {
        val lower = text.lowercase()
        return when {
            lower.any { it in 'ğ'..'ş' || it == 'ı' || it == 'İ' || it == 'ç' || it == 'ö' || it == 'ü' } &&
                (lower.contains(" ve ") || lower.contains(" için ") || lower.contains("ayar")) ->
                LanguageOption.TURKISH to 0.72f
            lower.any { it in '\u0600'..'\u06FF' } -> LanguageOption.ARABIC to 0.85f
            lower.any { it in '\u0400'..'\u04FF' } -> LanguageOption.RUSSIAN to 0.85f
            lower.any { it in '\u3040'..'\u30FF' } -> LanguageOption.JAPANESE to 0.85f
            lower.any { it in '\uAC00'..'\uD7AF' } -> LanguageOption.KOREAN to 0.85f
            lower.any { it in '\u4E00'..'\u9FFF' } -> LanguageOption.CHINESE_SIMPLIFIED to 0.80f
            else -> LanguageOption.ENGLISH to 0.60f
        }
    }
}
