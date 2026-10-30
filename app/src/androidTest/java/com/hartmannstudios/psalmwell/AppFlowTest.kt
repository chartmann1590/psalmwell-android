package com.hartmannstudios.psalmwell

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hartmannstudios.psalmwell.ads.Ads
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks every main screen without an account (the path a Play reviewer or pre-launch robot takes)
 * and saves screenshots of each screen for the store listing.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    init { Ads.disabledForTests = true }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun snackbarShowing() = listOf("Saved to your", "Removed from saved", "Journal entry removed").any {
        rule.onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Captures only the app's own window (no system bars or system dialogs), after any snackbar is gone. */
    private fun shot(name: String) {
        rule.waitForIdle()
        repeat(30) {
            if (!snackbarShowing()) return@repeat
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitForIdle()
        }
        Thread.sleep(600)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun passWelcome() {
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("welcome_start").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("screen_today").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithTag("welcome_start").fetchSemanticsNodes().isNotEmpty()) {
            shot("00_welcome")
            rule.onNodeWithTag("welcome_start").performClick()
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("screen_today").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun fullGuestJourney() {
        passWelcome()
        rule.onNodeWithTag("verse_text").assertIsDisplayed()
        shot("01_today")

        rule.onNodeWithTag("action_save").performClick()
        rule.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("psalm_of_day"))
        rule.onNodeWithTag("psalm_of_day").performClick()
        rule.onNodeWithTag("screen_reader").assertIsDisplayed()
        rule.onNodeWithTag("reader_next").performClick()
        rule.onNodeWithTag("reader_back").performClick()

        rule.onNodeWithTag("tab_psalms").performClick()
        rule.onNodeWithTag("psalm_search").performTextInput("23")
        rule.onNodeWithTag("psalm_row_23").performClick()
        rule.onNodeWithText("Yahweh is my shepherd", substring = true).assertIsDisplayed()
        shot("02_psalm_reader")
        rule.onNodeWithTag("reader_back").performClick()
        rule.onNodeWithTag("screen_psalms").assertIsDisplayed()

        rule.onNodeWithTag("tab_reflect").performClick()
        rule.onNodeWithTag("screen_reflections").assertIsDisplayed()
        shot("03_reflections")

        rule.onNodeWithTag("tab_journal").performClick()
        rule.onNodeWithTag("journal_new").performClick()
        rule.onNodeWithTag("journal_text").performTextInput("Grateful for a quiet morning and this reminder of peace.")
        rule.onNodeWithTag("journal_save").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Grateful for a quiet morning", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("04_journal")
        rule.onNodeWithTag("seg_saved").performClick()
        shot("05_saved")

        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithTag("screen_settings").assertIsDisplayed()
        shot("06_settings")
        rule.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("settings_blocked"))
        rule.onNodeWithTag("settings_blocked").performClick()
        rule.onNodeWithTag("blocked_dialog").assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()

        rule.onNodeWithTag("tab_today").performClick()
        rule.onNodeWithTag("screen_today").assertIsDisplayed()
    }

    @Test fun rotationKeepsState() {
        passWelcome()
        rule.onNodeWithTag("tab_psalms").performClick()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("screen_psalms").fetchSemanticsNodes().isNotEmpty() }
    }
}
