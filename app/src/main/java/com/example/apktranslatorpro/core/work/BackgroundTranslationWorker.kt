package com.example.apktranslatorpro.core.work

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.apktranslatorpro.ApkTranslatorApp
import com.example.apktranslatorpro.core.apk.ApkProcessor
import com.example.apktranslatorpro.domain.model.LanguageOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Jetpack WorkManager worker that executes end-to-end APK extraction via [ApkProcessor],
 * translation with identifier & placeholder preservation, resources.arsc rebuild,
 * v1/v2/v3 signing, and SHA-256 verification cleanly in background using Coroutines.
 */
class BackgroundTranslationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_APK_URI) ?: return Result.failure(
            workDataOf(KEY_STATUS to "Geçersiz APK URI")
        )
        val targetLangCode = inputData.getString(KEY_TARGET_LANG) ?: "tr"
        val app = applicationContext as? ApkTranslatorApp ?: return Result.failure()
        val processor = ApkProcessor(app, signerEngine = app.signerEngine)

        return try {
            setProgress(workDataOf(KEY_PROGRESS to 0.1f, KEY_STATUS to "Arka plan: APK analiz ediliyor..."))
            val session = app.repository.inspectApkFromUri(Uri.parse(uriString)) { _, _, msg ->
                setProgressAsync(workDataOf(KEY_PROGRESS to 0.22f, KEY_STATUS to msg))
            }

            val extractDir = File(app.cacheDir, "worker_extract")
            val extraction = processor.extractApkWithZipInputStream(
                sourceApk = session.workingInputApk,
                destinationDir = extractDir
            ) { count, entry ->
                setProgressAsync(workDataOf(KEY_PROGRESS to 0.34f, KEY_STATUS to "ZipInputStream: $count öğe ($entry)"))
            }
            extractDir.deleteRecursively()

            val settings = app.settingsDataStore.settingsFlow.first()
            val target = LanguageOption.fromCode(targetLangCode)
            val source = session.metadata.detectedSourceLanguage

            setProgress(workDataOf(KEY_PROGRESS to 0.42f, KEY_STATUS to "Arka plan: ${extraction.resources.size} kaynak çevriliyor (${target.displayNameTr})..."))
            val translated = app.repository.translateResources(
                items = session.resources,
                sourceLanguage = source,
                targetLanguage = target,
                mode = settings.translationMode,
                onlineConfig = settings.onlineApiConfig,
                protectionConfig = settings.protectionConfig,
                scopeConfig = settings.resourceScopeConfig,
                appName = session.metadata.appLabel,
                packageName = session.metadata.packageName,
                onProgress = { done, total ->
                    val frac = 0.42f + (done.toFloat() / total.coerceAtLeast(1).toFloat()) * 0.38f
                    setProgressAsync(workDataOf(KEY_PROGRESS to frac, KEY_STATUS to "Arka plan: $done / $total"))
                },
                onLog = { _, _, _ -> }
            )

            setProgress(workDataOf(KEY_PROGRESS to 0.85f, KEY_STATUS to "Arka plan: APK paketleniyor & v1/v2/v3 imzalanıyor..."))
            val output = app.repository.rebuildSignAndVerifyApk(
                session = session,
                translatedItems = translated,
                sourceLanguage = source,
                targetLanguage = target,
                outputTreeUriString = settings.outputTreeUri,
                scopeConfig = settings.resourceScopeConfig,
                onStage = { stage ->
                    setProgressAsync(workDataOf(KEY_PROGRESS to 0.92f, KEY_STATUS to stage.labelTr))
                },
                onLog = { _, _, _ -> }
            )

            Result.success(
                Data.Builder()
                    .putString(KEY_STATUS, "Tamamlandı: ${output.fileName}")
                    .putString(KEY_OUTPUT_PATH, output.workspaceFilePath)
                    .putString(KEY_OUTPUT_SHA256, output.outputSha256)
                    .putFloat(KEY_PROGRESS, 1f)
                    .build()
            )
        } catch (ex: Exception) {
            Result.failure(workDataOf(KEY_STATUS to (ex.message ?: "Arka plan görevi başarısız.")))
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "apk_translator_background_build"
        const val KEY_APK_URI = "apk_uri"
        const val KEY_TARGET_LANG = "target_lang"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_OUTPUT_SHA256 = "output_sha256"

        fun enqueueOneClickBuild(
            context: Context,
            apkUriString: String,
            targetLangCode: String,
            requireNetwork: Boolean
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (requireNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                .build()

            val req = OneTimeWorkRequestBuilder<BackgroundTranslationWorker>()
                .setInputData(
                    workDataOf(
                        KEY_APK_URI to apkUriString,
                        KEY_TARGET_LANG to targetLangCode
                    )
                )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancelBackgroundBuild(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        fun observeWorkStatus(context: Context): Flow<WorkInfo?> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
                .map { it.firstOrNull() }
        }
    }
}
