package com.hartmannstudios.psalmwell

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hartmannstudios.psalmwell.ads.Ads
import com.hartmannstudios.psalmwell.i18n.ModelState
import com.hartmannstudios.psalmwell.i18n.Translator
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switches the app to Spanish with on-device ML Kit translation and checks the screens follow,
 * then switches back to English. Needs the emulator to reach Google's model download; without a
 * network the Spanish part is skipped (the app keeps English, which the first test checks).
 */
@RunWith(AndroidJUnit4::class)
class TranslationSmokeTest {
    init { Ads.disabledForTests = true }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val translator: Translator
        get() = (rule.activity.application as PsalmwellApplication).container.translator

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(600)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun toToday() {
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("welcome_start").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("screen_today").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithTag("welcome_start").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag("welcome_start").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("screen_today").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun backToEnglish() {
        translator.selectLanguage("", allowMobileData = false)
        val t0 = System.currentTimeMillis()
        while (translator.lang.value.isNotEmpty() && System.currentTimeMillis() - t0 < 10_000) Thread.sleep(100)
    }

    @Test fun languageSettingsAndNoticesShow() {
        toToday()
        rule.onNodeWithTag("tab_settings").performClick()
        // Scroll to the end first so the language row sits mid-screen, clear of the navigation bar.
        rule.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("settings_notices"))
        rule.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("settings_language"))
        rule.onNodeWithTag("settings_language").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("language_dialog").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("language_dialog").assertIsDisplayed()
        rule.onNodeWithTag("language_search").performTextInput("Spanish")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("language_es").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("language_es").assertIsDisplayed()
        rule.onAllNodesWithText("Close")[0].performClick()
        rule.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("settings_notices"))
        rule.onNodeWithTag("settings_notices").performClick()
        rule.onNodeWithTag("notices_dialog").assertIsDisplayed()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("notices_third_party").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("Google ML Kit", substring = true).fetchSemanticsNodes().isNotEmpty())
        shot("10_notices")
        rule.onAllNodesWithText("Close")[0].performClick()
    }

    @Test fun switchToSpanishAndBack() {
        toToday()
        translator.selectLanguage("es", allowMobileData = true)
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 240_000) {
            val s = translator.modelState.value
            if (s is ModelState.Ready || s is ModelState.Failed) break
            Thread.sleep(500)
        }
        val state = translator.modelState.value
        Log.i("TranslationSmokeTest", "model state after switch: $state")
        assumeTrue("Spanish pack not downloaded on this emulator: $state", state is ModelState.Ready)

        val es = runBlocking { translator.tr("Verse of the day") }
        Log.i("TranslationSmokeTest", "Verse of the day -> $es")
        assertNotEquals("Verse of the day", es)
        assertTrue(es.isNotBlank())

        // The Today screen follows: the English heading disappears once its translation is cached.
        rule.waitUntil(60_000) { rule.onAllNodesWithText("VERSE OF THE DAY").fetchSemanticsNodes().isEmpty() }
        Thread.sleep(3_000)
        shot("11_today_es")
        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("settings_language"))
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Español").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(3_000)
        shot("12_settings_es")
    }
}
