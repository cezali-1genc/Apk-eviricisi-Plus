package com.example.apktranslatorpro

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.apktranslatorpro.data.local.AppUiLanguage
import com.example.apktranslatorpro.data.local.AppUserSettings
import com.example.apktranslatorpro.data.local.RecentApkDatabase
import com.example.apktranslatorpro.data.local.RecentApkEntity
import com.example.apktranslatorpro.data.local.RecentApkRepository
import com.example.apktranslatorpro.data.local.SettingsDataStore
import com.example.apktranslatorpro.domain.model.LanguageOption
import com.example.apktranslatorpro.ui.viewmodel.FilePickerViewModel
import com.example.apktranslatorpro.ui.viewmodel.LanguageManagerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomAndTrialLimitTest {

    private lateinit var context: Context
    private lateinit var db: RecentApkDatabase
    private lateinit var repo: RecentApkRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RecentApkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RecentApkRepository(db.recentApkDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `recent apk room database persists hashes paths and timestamps`() = runBlocking {
        val item = RecentApkEntity(
            sha256Hex = "a".repeat(64),
            fileName = "demo_signed.apk",
            originalUriOrPath = "content://downloads/demo.apk",
            packageName = "com.demo.app",
            appLabel = "Demo App",
            fileSizeBytes = 4096L,
            outputPath = "/tmp/demo_signed.apk",
            lastAction = "Çevrildi & İmzalandı (TR)"
        )
        repo.recordApk(item)

        val loaded = repo.recentApksFlow.first()
        assertEquals(1, loaded.size)
        assertEquals("demo_signed.apk", loaded[0].fileName)
        assertEquals("a".repeat(64), loaded[0].sha256Hex)

        repo.deleteApk("a".repeat(64))
        assertTrue(repo.recentApksFlow.first().isEmpty())
    }

    @Test
    fun `guest trial enforces 2 translation cap and supports 3 app languages`() = runBlocking {
        val store = SettingsDataStore(context)
        val initial = store.settingsFlow.first()
        assertFalse(initial.isGuestTrialLimitReached)
        assertEquals(AppUserSettings.MAX_GUEST_TRIAL_TRANSLATIONS, initial.guestTranslationsRemaining)

        store.setAppUiLanguage(AppUiLanguage.DE)
        store.setGuestTrialActive(true)
        store.incrementGuestTranslationsUsed()
        val afterOne = store.settingsFlow.first()
        assertEquals(AppUiLanguage.DE, afterOne.appUiLanguage)
        assertEquals(1, afterOne.guestTranslationsRemaining)
        assertFalse(afterOne.isGuestTrialLimitReached)

        store.incrementGuestTranslationsUsed()
        val afterTwo = store.settingsFlow.first()
        assertEquals(0, afterTwo.guestTranslationsRemaining)
        assertTrue(afterTwo.isGuestTrialLimitReached)
    }

    @Test
    fun `file picker viewModel computes SHA256 for selected file uri`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = FilePickerViewModel(app)
        val tempApk = File(app.cacheDir, "checksum_test.apk").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4))
        }
        try {
            vm.onApkUriPicked(Uri.fromFile(tempApk))
            val deadline = System.currentTimeMillis() + 5_000L
            while (vm.state.value.sha256Checksum.isBlank() && System.currentTimeMillis() < deadline) {
                Thread.sleep(30)
            }
            assertEquals(64, vm.state.value.sha256Checksum.length)
            assertTrue(vm.state.value.isVerifiedReadOnly)
        } finally {
            tempApk.delete()
        }
    }

    @Test
    fun `language manager viewModel manages bulk translation queue and locales`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = LanguageManagerViewModel(app)
        assertTrue(vm.state.value.availableMlKitCodes.isNotEmpty())
        vm.toggleQueueLocale(LanguageOption.GERMAN)
        vm.setQueuedResourceFiles(listOf("res/values/strings.xml", "res/values-en/strings.xml"))
        vm.buildBulkQueuePreview()
        assertTrue(vm.state.value.bulkQueueJobs.any { it.targetLocale == LanguageOption.GERMAN })
    }
}
