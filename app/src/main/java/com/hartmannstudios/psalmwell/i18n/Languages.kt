package com.hartmannstudios.psalmwell.i18n

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

data class AppLanguage(
    /** ML Kit TranslateLanguage code; "" = the app's original English. */
    val code: String,
    /** The language's name in its own language ("Español", "Deutsch"). */
    val nativeName: String,
    /** The language's name in English, for search. */
    val englishName: String,
)

object Languages {
    const val ORIGINAL = ""

    /** Every language ML Kit on-device translation supports, original English first. */
    fun all(): List<AppLanguage> {
        val list = TranslateLanguage.getAllLanguages()
            .filter { it != TranslateLanguage.ENGLISH }
            .map { code -> fromCode(code) }
            .sortedBy { it.nativeName.lowercase(Locale.ROOT) }
        return listOf(AppLanguage(ORIGINAL, "English", "English (original)")) + list
    }

    fun fromCode(code: String): AppLanguage {
        if (code.isEmpty() || code == TranslateLanguage.ENGLISH) return AppLanguage(ORIGINAL, "English", "English (original)")
        val locale = Locale.forLanguageTag(code)
        val native = locale.getDisplayLanguage(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        val english = locale.getDisplayLanguage(Locale.ENGLISH)
        return AppLanguage(code, native.ifBlank { code }, english.ifBlank { code })
    }

    /** The phone's own language when ML Kit supports it (suggested first in the picker). */
    fun deviceSuggestion(): String? {
        val tag = Locale.getDefault().language
        val code = TranslateLanguage.fromLanguageTag(tag) ?: return null
        return if (code == TranslateLanguage.ENGLISH) null else code
    }

    /** Languages written right to left: the UI mirrors for these. */
    fun isRtl(code: String): Boolean = code in setOf("ar", "fa", "he", "ur")

    fun matches(lang: AppLanguage, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return lang.nativeName.contains(q, ignoreCase = true) || lang.englishName.contains(q, ignoreCase = true) ||
            lang.code.equals(q, ignoreCase = true)
    }
}
