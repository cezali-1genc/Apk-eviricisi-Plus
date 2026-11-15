package com.example.apktranslatorpro

import android.app.Application
import com.example.apktranslatorpro.core.apk.ApkPackager
import com.example.apktranslatorpro.core.apk.ApktoolService
import com.example.apktranslatorpro.core.git.GitRepositoryService
import com.example.apktranslatorpro.core.signing.ApkSignerEngine
import com.example.apktranslatorpro.core.translation.GeminiTranslationEngine
import com.example.apktranslatorpro.core.translation.MlKitTranslationEngine
import com.example.apktranslatorpro.core.translation.OnlineTranslationEngine
import com.example.apktranslatorpro.data.local.ProjectDatabase
import com.example.apktranslatorpro.data.local.ProjectRepository
import com.example.apktranslatorpro.data.local.RecentApkDatabase
import com.example.apktranslatorpro.data.local.RecentApkRepository
import com.example.apktranslatorpro.data.local.SettingsDataStore
import com.example.apktranslatorpro.data.local.TranslationCacheDatabase
import com.example.apktranslatorpro.data.repository.ApkTranslationRepository
import com.google.firebase.FirebaseApp

class ApkTranslatorApp : Application() {

    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var database: TranslationCacheDatabase
        private set
    lateinit var recentApkDatabase: RecentApkDatabase
        private set
    lateinit var recentApkRepository: RecentApkRepository
        private set
    lateinit var projectDatabase: ProjectDatabase
        private set
    lateinit var projectRepository: ProjectRepository
        private set
    lateinit var gitRepositoryService: GitRepositoryService
        private set
    lateinit var apktoolService: ApktoolService
        private set
    lateinit var mlKitEngine: MlKitTranslationEngine
        private set
    lateinit var onlineEngine: OnlineTranslationEngine
        private set
    lateinit var geminiEngine: GeminiTranslationEngine
        private set
    lateinit var signerEngine: ApkSignerEngine
        private set
    lateinit var repository: ApkTranslationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        }
        settingsDataStore = SettingsDataStore(this)
        database = TranslationCacheDatabase.getInstance(this)
        recentApkDatabase = RecentApkDatabase.getInstance(this)
        recentApkRepository = RecentApkRepository(recentApkDatabase.recentApkDao())
        projectDatabase = ProjectDatabase.getInstance(this)
        projectRepository = ProjectRepository(projectDatabase.projectDao())
        gitRepositoryService = GitRepositoryService(this)
        val packager = ApkPackager()
        apktoolService = ApktoolService(this, packager)
        mlKitEngine = MlKitTranslationEngine(this)
        onlineEngine = OnlineTranslationEngine(this)
        geminiEngine = GeminiTranslationEngine()
        signerEngine = ApkSignerEngine(this)
        repository = ApkTranslationRepository(
            context = this,
            packager = packager,
            signerEngine = signerEngine,
            mlKitEngine = mlKitEngine,
            onlineEngine = onlineEngine,
            geminiEngine = geminiEngine,
            cacheDao = database.translationCacheDao(),
            historyDao = database.jobHistoryDao()
        )
    }
}
