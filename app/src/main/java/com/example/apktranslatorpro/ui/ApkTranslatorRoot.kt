package com.example.apktranslatorpro.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.apktranslatorpro.data.local.AppUiLanguage
import com.example.apktranslatorpro.data.remote.FirestoreSyncRepository
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.domain.model.OutputApkResult
import com.example.apktranslatorpro.domain.model.PipelineStage
import com.example.apktranslatorpro.ui.auth.AuthScreen
import com.example.apktranslatorpro.ui.auth.GoogleSignInHelper
import com.example.apktranslatorpro.ui.auth.authStateFlow
import com.example.apktranslatorpro.ui.components.GlobalBuildProgressBar
import com.example.apktranslatorpro.ui.components.launchApkDocumentPicker
import com.example.apktranslatorpro.ui.components.rememberApkSafPickerLauncher
import com.example.apktranslatorpro.ui.screens.ApkToolsScreen
import com.example.apktranslatorpro.ui.screens.ModelsAndCacheScreen
import com.example.apktranslatorpro.ui.screens.PreviewScreen
import com.example.apktranslatorpro.ui.screens.SettingsAndBrandScreen
import com.example.apktranslatorpro.ui.screens.SplashScreen
import com.example.apktranslatorpro.ui.screens.TranslationSettingsScreen
import com.example.apktranslatorpro.ui.screens.WorkspaceScreen
import com.example.apktranslatorpro.ui.viewmodel.BuildProcessStep
import com.example.apktranslatorpro.ui.viewmodel.BuildProcessViewModel
import com.example.apktranslatorpro.ui.viewmodel.CloudSyncViewModel
import com.example.apktranslatorpro.ui.viewmodel.FilePickerViewModel
import com.example.apktranslatorpro.ui.viewmodel.LanguageManagerViewModel
import com.example.apktranslatorpro.ui.viewmodel.MainViewModel
import com.example.ui.theme.ApkTranslatorProTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Locale

@Serializable
object WorkspaceRoute

@Serializable
object ApkToolsRoute

@Serializable
object PreviewRoute

@Serializable
object TranslationRulesRoute

@Serializable
object ModelsRoute

@Serializable
object SettingsRoute

private data class TopDestination<T : Any>(
    val route: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun ApkTranslatorRoot(
    viewModel: MainViewModel,
    auth: FirebaseAuth = remember { FirebaseAuth.getInstance() }
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var showSplash by rememberSaveable { mutableStateOf(true) }

    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, settings.appUiLanguage) {
        applyUiLocale(baseContext, settings.appUiLanguage)
    }

    CompositionLocalProvider(LocalContext provides localizedContext) {
        ApkTranslatorProTheme(themeMode = settings.themeMode) {
            if (showSplash) {
                SplashScreen(onFinished = { showSplash = false })
            } else {
                val user = currentUser
                val canEnterGuest = settings.isGuestTrialActive && !settings.isGuestTrialLimitReached
                when {
                    user != null -> {
                        val cloudViewModel: CloudSyncViewModel = viewModel(
                            key = user.uid,
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    CloudSyncViewModel(
                                        syncRepository = FirestoreSyncRepository(app.applicationContext),
                                        currentUserId = user.uid,
                                        userDisplayName = user.displayName ?: user.email ?: "Cezalı 1Genç Kullanıcısı",
                                        userEmail = user.email
                                    )
                                }
                            }
                        )
                        AuthenticatedAppShell(
                            viewModel = viewModel,
                            cloudViewModel = cloudViewModel,
                            isGuestMode = false,
                            userDisplayName = user.displayName ?: "Kullanıcı",
                            userEmail = user.email
                        )
                    }
                    canEnterGuest -> {
                        AuthenticatedAppShell(
                            viewModel = viewModel,
                            cloudViewModel = null,
                            isGuestMode = true,
                            userDisplayName = "Misafir Kullanıcı",
                            userEmail = null
                        )
                    }
                    else -> {
                        AuthScreen(
                            selectedAppLanguage = settings.appUiLanguage,
                            guestTranslationsUsed = settings.guestTranslationsUsed,
                            onSelectAppLanguage = viewModel::setAppUiLanguage,
                            onGuestTrialLoginClick = viewModel::enterGuestTrial
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedAppShell(
    viewModel: MainViewModel,
    cloudViewModel: CloudSyncViewModel?,
    isGuestMode: Boolean = false,
    userDisplayName: String,
    userEmail: String?
) {
    val filePickerViewModel: FilePickerViewModel = viewModel(factory = FilePickerViewModel.Factory)
    val buildProcessViewModel: BuildProcessViewModel = viewModel(factory = BuildProcessViewModel.Factory)
    val languageManagerViewModel: LanguageManagerViewModel = viewModel(factory = LanguageManagerViewModel.Factory)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filePickerState by filePickerViewModel.state.collectAsStateWithLifecycle()
    val buildState by buildProcessViewModel.state.collectAsStateWithLifecycle()
    val langManagerState by languageManagerViewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val cacheCount by viewModel.cacheCountState.collectAsStateWithLifecycle()
    val recentJobs by viewModel.recentJobsState.collectAsStateWithLifecycle()
    val recentApks by viewModel.recentApksState.collectAsStateWithLifecycle()

    val cloudProjectsState = cloudViewModel?.cloudProjectsState?.collectAsStateWithLifecycle()?.value
        ?: com.example.apktranslatorpro.ui.viewmodel.CloudProjectsUiState.Success(emptyList())
    val cloudSyncMessage = cloudViewModel?.syncMessage?.collectAsStateWithLifecycle()?.value

    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var lastRecordedInputSha by remember { mutableStateOf<String?>(null) }
    var lastRecordedOutputSha by remember { mutableStateOf<String?>(null) }

    // Keep GlobalBuildProgressBar synchronized with MainViewModel pipeline progress
    LaunchedEffect(uiState.isRunning, uiState.isInspecting, uiState.stage, uiState.progress, uiState.progressDetail) {
        if (uiState.isRunning || uiState.isInspecting) {
            val mappedStep = when (uiState.stage) {
                PipelineStage.VERIFYING_INPUT, PipelineStage.ANALYZING_APK, PipelineStage.EXTRACTING_RESOURCES ->
                    BuildProcessStep.DECOMPILING
                PipelineStage.TRANSLATING -> BuildProcessStep.TRANSLATING
                PipelineStage.REBUILDING_RESOURCES -> BuildProcessStep.REBUILDING_RESOURCES
                PipelineStage.PACKAGING_APK -> BuildProcessStep.ZIPALIGNING
                PipelineStage.SIGNING_APK -> BuildProcessStep.SIGNING
                PipelineStage.VERIFYING_OUTPUT, PipelineStage.SAVING_OUTPUT -> BuildProcessStep.VERIFYING
                PipelineStage.COMPLETED -> BuildProcessStep.COMPLETED
                PipelineStage.ERROR -> BuildProcessStep.ERROR
                PipelineStage.IDLE -> BuildProcessStep.IDLE
            }
            buildProcessViewModel.syncPipelineStep(
                step = mappedStep,
                progress = uiState.progress,
                detail = uiState.progressDetail,
                busy = true
            )
        } else if (uiState.stage == PipelineStage.COMPLETED || uiState.stage == PipelineStage.IDLE) {
            if (!buildState.isBusy) {
                buildProcessViewModel.syncPipelineStep(BuildProcessStep.IDLE, 0f, "", false)
            }
        }
    }

    LaunchedEffect(uiState.apkMetadata?.sha256) {
        val meta = uiState.apkMetadata
        if (meta != null && meta.sha256 != lastRecordedInputSha) {
            lastRecordedInputSha = meta.sha256
            cloudViewModel?.recordInspectedApk(
                metadata = meta,
                targetLangCode = uiState.targetLanguage.code,
                translationMode = settings.translationMode
            )
        }
    }

    LaunchedEffect(uiState.outputResult?.outputSha256) {
        val out = uiState.outputResult
        val meta = uiState.apkMetadata
        if (out != null && meta != null && out.outputSha256 != lastRecordedOutputSha) {
            lastRecordedOutputSha = out.outputSha256
            cloudViewModel?.recordSignedOutput(
                metadata = meta,
                output = out,
                targetLangCode = uiState.targetLanguage.code,
                translationMode = settings.translationMode
            )
        }
    }

    val pickApkLauncher = rememberApkSafPickerLauncher { uri ->
        filePickerViewModel.onApkUriPicked(uri)
        viewModel.onApkSelected(uri)
    }

    val pickOutputFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            val label = doc?.name ?: treeUri.lastPathSegment ?: "Seçilen Klasör"
            viewModel.onOutputFolderSelected(treeUri, label)
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { targetUri ->
        if (targetUri != null) {
            viewModel.saveOutputToDocumentUri(targetUri)
        }
    }

    LaunchedEffect(
        uiState.errorMessage,
        uiState.statusBannerMessage,
        cloudSyncMessage,
        buildState.statusMessage,
        buildState.errorMessage,
        langManagerState.statusMessage,
        langManagerState.errorMessage
    ) {
        val msg = uiState.errorMessage
            ?: uiState.statusBannerMessage
            ?: buildState.errorMessage
            ?: buildState.statusMessage
            ?: langManagerState.errorMessage
            ?: langManagerState.statusMessage
            ?: cloudSyncMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessages()
            buildProcessViewModel.clearBanner()
            languageManagerViewModel.clearMessages()
            cloudViewModel?.clearSyncMessage()
        }
    }

    val workspaceLabel = stringResource(R.string.nav_workspace)
    val toolsLabel = stringResource(R.string.nav_tools)
    val previewLabel = stringResource(R.string.nav_preview)
    val rulesLabel = stringResource(R.string.nav_rules)
    val modelsLabel = stringResource(R.string.nav_models)
    val settingsLabel = stringResource(R.string.nav_settings)

    val destinations = remember(workspaceLabel, toolsLabel, previewLabel, rulesLabel, modelsLabel, settingsLabel) {
        listOf(
            TopDestination(
                route = WorkspaceRoute,
                label = workspaceLabel,
                selectedIcon = Icons.Filled.Build,
                unselectedIcon = Icons.Outlined.Build,
                testTag = "nav_workspace"
            ),
            TopDestination(
                route = ApkToolsRoute,
                label = toolsLabel,
                selectedIcon = Icons.Filled.Security,
                unselectedIcon = Icons.Outlined.Security,
                testTag = "nav_tools"
            ),
            TopDestination(
                route = PreviewRoute,
                label = previewLabel,
                selectedIcon = Icons.Filled.Preview,
                unselectedIcon = Icons.Outlined.Preview,
                testTag = "nav_preview"
            ),
            TopDestination(
                route = TranslationRulesRoute,
                label = rulesLabel,
                selectedIcon = Icons.Filled.Tune,
                unselectedIcon = Icons.Outlined.Tune,
                testTag = "nav_rules"
            ),
            TopDestination(
                route = ModelsRoute,
                label = modelsLabel,
                selectedIcon = Icons.Filled.Language,
                unselectedIcon = Icons.Outlined.Language,
                testTag = "nav_models"
            ),
            TopDestination(
                route = SettingsRoute,
                label = settingsLabel,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings"
            )
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val launchApkPicker = {
        pickApkLauncher.launchApkDocumentPicker()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    GlobalBuildProgressBar(buildState = buildState)
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val selected = currentDest?.hasRoute(dest.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        destinations.forEach { dest ->
                            val selected = currentDest?.hasRoute(dest.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = WorkspaceRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<WorkspaceRoute> {
                        WorkspaceScreen(
                            uiState = uiState,
                            settings = settings,
                            onPickApkClick = launchApkPicker,
                            onVerifySafExistenceClick = {
                                filePickerViewModel.recalculateChecksum()
                                viewModel.verifySelectedSafFileIntegrity()
                            },
                            onPickOutputFolderClick = {
                                pickOutputFolderLauncher.launch(null)
                            },
                            onSelectSourceLanguage = viewModel::setSourceLanguage,
                            onSelectTargetLanguage = viewModel::setTargetLanguage,
                            onSelectTranslationMode = viewModel::setTranslationMode,
                            onUpdateResourceScopeConfig = viewModel::saveResourceScopeConfig,
                            onStartPipelineClick = {
                                viewModel.requestStartPipeline(isAuthenticatedUser = !isGuestMode)
                            },
                            onCancelPipelineClick = viewModel::cancelActivePipeline,
                            onConfirmOnlineConsent = {
                                viewModel.confirmOnlineConsentAndStart(isAuthenticatedUser = !isGuestMode)
                            },
                            onDismissOnlineConsent = viewModel::dismissOnlineConsent,
                            onInstallOutputApk = { output ->
                                val msg = launchApkInstaller(context, output)
                                if (msg != null) {
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            onShareOutputApk = { output ->
                                val msg = launchApkShare(context, output)
                                if (msg != null) {
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            onSaveOutputAs = { output ->
                                saveAsLauncher.launch(output.fileName)
                            },
                            onOpenPreviewTab = { navigateTo(PreviewRoute) },
                            onOpenRulesTab = { navigateTo(TranslationRulesRoute) },
                            onOpenToolsTab = { navigateTo(ApkToolsRoute) }
                        )
                    }

                    composable<ApkToolsRoute> {
                        ApkToolsScreen(
                            uiState = uiState,
                            filePickerState = filePickerState,
                            buildState = buildState,
                            recentApks = recentApks,
                            themeMode = settings.themeMode,
                            cloudProjectsState = cloudProjectsState,
                            onPickApkClick = launchApkPicker,
                            onVerifySafIntegrityClick = {
                                filePickerViewModel.recalculateChecksum()
                                viewModel.verifySelectedSafFileIntegrity()
                            },
                            onDecompileApkClick = {
                                val uriStr = uiState.selectedApkUri ?: return@ApkToolsScreen
                                buildProcessViewModel.decompileFromUri(Uri.parse(uriStr))
                            },
                            onRebuildApkClick = {
                                viewModel.rebuildApkFromCurrentPreview()
                            },
                            onSaveWorkingFile = { file, newContent ->
                                buildProcessViewModel.saveWorkingFile(file, newContent)
                            },
                            onBulkRenameKeys = { oldK, newK, prefix ->
                                buildProcessViewModel.performBulkKeyRename(oldK, newK, prefix)
                            },
                            onUpdateResourceTranslation = viewModel::updateResourceTranslation,
                            onExportWorkspaceZip = { targetUri ->
                                buildProcessViewModel.exportWorkspaceZip(targetUri)
                            },
                            onRegenerateSignerKey = viewModel::regenerateSignerKey,
                            onDeleteRecentApk = viewModel::deleteRecentApk,
                            onClearRecentApks = viewModel::clearRecentApks,
                            onRunGeminiAnalysisClick = {
                                viewModel.runGeminiApkAnalysis { summary ->
                                    cloudViewModel?.attachGeminiSummary(summary)
                                }
                            },
                            onDeleteCloudProject = { projId ->
                                cloudViewModel?.deleteCloudProject(projId)
                            },
                            onOpenBinaryXmlTab = { navigateTo(PreviewRoute) }
                        )
                    }

                    composable<PreviewRoute> {
                        PreviewScreen(
                            uiState = uiState,
                            generateXmlPreview = viewModel::generateStringsXmlPreview,
                            onEditTranslation = viewModel::updateResourceTranslation,
                            onSelectBinaryXml = viewModel::inspectBinaryXmlEntry,
                            onRebuildWithEditsClick = {
                                viewModel.rebuildApkFromCurrentPreview()
                                navigateTo(WorkspaceRoute)
                            }
                        )
                    }

                    composable<TranslationRulesRoute> {
                        TranslationSettingsScreen(
                            protectionConfig = settings.protectionConfig,
                            onUpdateConfig = viewModel::saveProtectionConfig,
                            onAddExcludedTerm = viewModel::addExcludedTerm,
                            onRemoveExcludedTerm = viewModel::removeExcludedTerm,
                            onResetDefaultTerms = viewModel::resetExcludedTermsToDefault
                        )
                    }

                    composable<ModelsRoute> {
                        val effectiveSource = remember(uiState.selectedSourceLanguage, uiState.apkMetadata) {
                            if (uiState.selectedSourceLanguage == LanguageOption.AUTO) {
                                uiState.apkMetadata?.detectedSourceLanguage ?: LanguageOption.ENGLISH
                            } else {
                                uiState.selectedSourceLanguage
                            }
                        }
                        val xmlPaths = remember(buildState.workspace) {
                            buildState.workspace?.let { ws ->
                                ws.resXmlFiles.mapNotNull { f -> runCatching { f.relativeTo(ws.workDir).path }.getOrNull() }
                            }.orEmpty()
                        }
                        ModelsAndCacheScreen(
                            uiState = uiState,
                            langManagerState = langManagerState,
                            availableXmlPaths = xmlPaths,
                            cacheCount = cacheCount,
                            recentJobs = recentJobs,
                            onRefreshModels = {
                                languageManagerViewModel.refreshModels()
                                viewModel.refreshOfflineModels()
                            },
                            onDownloadModel = { lang ->
                                languageManagerViewModel.downloadLanguageModel(lang)
                                viewModel.downloadOfflineModel(lang)
                            },
                            onDeleteModel = { lang ->
                                languageManagerViewModel.deleteLanguageModel(lang)
                                viewModel.deleteOfflineModel(lang)
                            },
                            onDownloadActivePair = viewModel::downloadActiveLanguagePair,
                            onToggleQueueLocale = languageManagerViewModel::toggleQueueLocale,
                            onToggleQueueFile = languageManagerViewModel::toggleQueuedResourceFile,
                            onRunBulkQueue = {
                                languageManagerViewModel.runBulkTranslationQueue(
                                    baseResources = uiState.resources,
                                    sourceLanguage = effectiveSource,
                                    workDir = buildState.workspace?.workDir
                                )
                            },
                            onClearCache = viewModel::clearTranslationCache,
                            onClearHistory = viewModel::clearJobHistory
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsAndBrandScreen(
                            uiState = uiState,
                            settings = settings,
                            isGuestMode = isGuestMode,
                            userDisplayName = userDisplayName,
                            userEmail = userEmail,
                            onSignOutClick = {
                                if (isGuestMode) {
                                    viewModel.exitGuestTrial()
                                } else {
                                    scope.launch {
                                        GoogleSignInHelper.signOut(context)
                                    }
                                }
                            },
                            onUpgradeGuestToGoogleClick = {
                                viewModel.exitGuestTrial()
                            },
                            onSelectAppUiLanguage = viewModel::setAppUiLanguage,
                            onSelectTheme = viewModel::setThemeMode,
                            onSaveOnlineApiConfig = viewModel::saveOnlineApiConfig,
                            onTestOnlineApiConnection = viewModel::testOnlineApiConnection,
                            onRegenerateSignerKey = viewModel::regenerateSignerKey
                        )
                    }
                }
            }
        }
    }
}

private fun applyUiLocale(base: Context, language: AppUiLanguage): Context {
    val locale = Locale.forLanguageTag(language.code)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

private fun launchApkInstaller(context: Context, output: OutputApkResult): String? {
    val file = File(output.workspaceFilePath)
    if (!file.exists()) return "Çıktı APK dosyası bulunamadı."

    return try {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permIntent)
            return "Lütfen 'Bilinmeyen uygulamaları yükle' iznini açıp tekrar 'Yükle' butonuna basın."
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        null
    } catch (ex: Exception) {
        "Yükleyici başlatılamadı: ${ex.message}"
    }
}

private fun launchApkShare(context: Context, output: OutputApkResult): String? {
    val file = File(output.workspaceFilePath)
    if (!file.exists()) return "Paylaşılacak APK dosyası bulunamadı."

    return try {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, output.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "İmzalı APK Paylaş").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        null
    } catch (ex: Exception) {
        "Paylaşım başlatılamadı: ${ex.message}"
    }
}
