package com.example.apktranslatorpro

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.apktranslatorpro.ui.AuthenticatedAppShell
import com.example.apktranslatorpro.ui.viewmodel.MainViewModel
import com.example.ui.theme.ApkTranslatorProTheme
import com.example.ui.theme.AppThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class MainActivityRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `unauthenticated cold launch displays Google Sign-In screen`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class AuthenticatedShellRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `authenticated workspace tools and all six navigation tabs render cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)

        composeTestRule.setContent {
            ApkTranslatorProTheme(themeMode = AppThemeMode.DARK) {
                AuthenticatedAppShell(
                    viewModel = vm,
                    cloudViewModel = null,
                    userDisplayName = "Serhat Güner",
                    userEmail = "serhat@example.com"
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("select_apk_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workspace_lazy_list")
            .performScrollToNode(hasTestTag("toggle_strict_strings_only"))
        composeTestRule.onNodeWithTag("toggle_strict_strings_only").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("scope_chip_default").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("scope_chip_tr").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("scope_chip_en").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("workspace_lazy_list")
            .performScrollToNode(hasTestTag("start_translation_button"))
        composeTestRule.onNodeWithTag("start_translation_button").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tools").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tools_pick_apk_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("verify_saf_integrity_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_rules").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_preserve_placeholders").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_preserve_markup").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_exclude_terms").assertIsDisplayed()
        composeTestRule.onNodeWithTag("translation_rules_lazy_list")
            .performScrollToNode(hasTestTag("excluded_term_input"))
        composeTestRule.onNodeWithTag("excluded_term_input").performScrollTo().performTextInput("Cezalı 1Genç")
        composeTestRule.onNodeWithTag("add_excluded_term_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("excluded_term_chip_cezalı 1genç")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("excluded_term_chip_cezalı 1genç").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_lazy_list")
            .performScrollToNode(hasTestTag("save_api_config_button"))
        composeTestRule.onNodeWithTag("save_api_config_button").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_models").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("models_lazy_list")
            .performScrollToNode(hasTestTag("download_active_pair_button"))
        composeTestRule.onNodeWithTag("download_active_pair_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("refresh_models_button").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_preview").performClick()
        composeTestRule.waitForIdle()
    }
}
