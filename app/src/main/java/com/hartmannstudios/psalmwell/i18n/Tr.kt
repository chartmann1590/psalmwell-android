package com.hartmannstudios.psalmwell.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Current language + cached translations, provided once at the root of the UI. */
data class TrState(val lang: String, val map: Map<String, String>, val translator: Translator?)

val LocalTr = compositionLocalOf { TrState(Languages.ORIGINAL, emptyMap(), null) }

/** Wrap the whole app UI in this so every [T] call and every [Text] follows the selected language. */
@Composable
fun ProvideTranslations(translator: Translator, content: @Composable () -> Unit) {
    val lang by translator.lang.collectAsStateWithLifecycle()
    val map by translator.cache.collectAsStateWithLifecycle()
    val direction = if (Languages.isRtl(lang)) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalTr provides TrState(lang, map, translator),
        LocalLayoutDirection provides direction,
        content = content,
    )
}

/** Any English text (labels, verses, messages) in the selected language; English until the translation is ready. */
@Composable
fun T(text: String): String {
    val state = LocalTr.current
    if (state.lang.isEmpty() || !Placeholders.needsTranslation(text)) return text
    val hit = state.map[text]
    if (hit == null) {
        val t = state.translator
        LaunchedEffect(text, state.lang) { t?.request(text) }
    }
    return hit ?: text
}

/** True while the app is shown in a language other than its original English. */
@Composable
fun isTranslated(): Boolean = LocalTr.current.lang.isNotEmpty()

/** A printf pattern in the selected language, filled with [args] (names and numbers stay as given). */
@Composable
fun trf(pattern: String, vararg args: Any): String = Placeholders.format(T(pattern), pattern, args)
