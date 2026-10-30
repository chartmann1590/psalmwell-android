package com.hartmannstudios.psalmwell.i18n

import com.google.mlkit.nl.translate.TranslateLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationTest {
    @Test
    fun placeholdersAndNamesSurviveTranslation() {
        val p = Placeholders.protect("Downloading the %1\$s language pack for Psalmwell on Google Play")
        assertFalse(p.text.contains("Psalmwell"))
        assertFalse(p.text.contains("%1"))
        assertEquals(listOf("%1\$s", "Google Play", "Psalmwell"), p.placeholders.sorted())
        // A translation that keeps every token restores them in its own word order.
        val fake = p.text.replace("Downloading the", "Descargando el")
        val restored = Placeholders.restore(fake, p.placeholders)
        assertEquals("Descargando el %1\$s language pack for Psalmwell on Google Play", restored)
    }

    @Test
    fun brokenTokensFallBackToEnglish() {
        val p = Placeholders.protect("Psalmwell %1\$s")
        assertNull(Placeholders.restore("sin marcadores", p.placeholders))
        assertNull(Placeholders.restore(p.text + " " + p.text, p.placeholders))
    }

    @Test
    fun onlyTextWithWordsIsTranslated() {
        assertFalse(Placeholders.needsTranslation("150"))
        assertFalse(Placeholders.needsTranslation("12:00"))
        assertFalse(Placeholders.needsTranslation("Psalmwell"))
        assertTrue(Placeholders.needsTranslation("Verse of the day"))
    }

    @Test
    fun formatFallsBackWhenTranslatedPatternIsBroken() {
        assertEquals("Psalm 23", Placeholders.format("Psalm %d", "Psalm %d", arrayOf(23)))
        assertEquals("Psalm 23", Placeholders.format("Salmo %q", "Psalm %d", arrayOf(23)))
    }

    /** Every language the Play listing is published in has an ML Kit on-device model. */
    @Test
    fun everyStoreListingLanguageIsSupported() {
        val listingLocales = listOf(
            "es-419", "es-ES", "pt-BR", "fr-FR", "de-DE", "it-IT", "ja-JP", "ko-KR", "zh-CN", "zh-TW",
            "hi-IN", "id", "ru-RU", "tr-TR", "ar", "pl-PL", "nl-NL",
        )
        val supported = TranslateLanguage.getAllLanguages().toSet()
        for (tag in listingLocales) {
            val code = TranslateLanguage.fromLanguageTag(tag.substringBefore('-'))
            assertNotNull("no ML Kit language for $tag", code)
            assertTrue("$tag -> $code not in ML Kit", code in supported)
        }
    }

    @Test
    fun rightToLeftLanguagesMirrorTheLayout() {
        assertTrue(Languages.isRtl("ar"))
        assertFalse(Languages.isRtl("es"))
    }
}
