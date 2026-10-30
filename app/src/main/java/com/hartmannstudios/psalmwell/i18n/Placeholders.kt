package com.hartmannstudios.psalmwell.i18n

/**
 * Pure helpers that keep printf placeholders (%s, %1$s, %d, %2$d, %.1f, %%) intact through machine
 * translation: placeholders are swapped for ⟦n⟧ tokens before translating and restored after.
 * If the translation lost or duplicated a token, [restore] returns null and the caller keeps English.
 */
object Placeholders {
    private val FORMAT = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXcbeEgGoh%n]""")
    private val TOKEN = Regex("""⟦\s*(\d+)\s*⟧""")
    private val HAS_LETTER = Regex("""\p{L}""")

    data class Protected(val text: String, val placeholders: List<String>)

    /** Brand and product names that must reach the screen untranslated. */
    val KEEP: List<String> = listOf("Psalmwell", "Hartmann Studios", "Google Play", "Google ML Kit", "ML Kit", "Google", "WEB")

    private val KEEP_RE = Regex(KEEP.sortedByDescending { it.length }.joinToString("|") { "\\b" + Regex.escape(it) + "\\b" })

    fun protect(src: String): Protected {
        val found = mutableListOf<String>()
        val formatted = FORMAT.replace(src) { m ->
            found += m.value
            "⟦${found.size - 1}⟧"
        }
        val text = KEEP_RE.replace(formatted) { m ->
            found += m.value
            "⟦${found.size - 1}⟧"
        }
        return Protected(text, found)
    }

    /** @return the translated text with placeholders back in place, or null if any token is missing or repeated. */
    fun restore(translated: String, placeholders: List<String>): String? {
        val seen = IntArray(placeholders.size)
        var bad = false
        val out = TOKEN.replace(translated) { m ->
            val i = m.groupValues[1].toInt()
            if (i !in placeholders.indices) { bad = true; m.value } else { seen[i]++; placeholders[i] }
        }
        if (bad || seen.any { it != 1 }) return null
        return out
    }

    /** Strings without any letter (numbers, punctuation, emoji, "00:00") never need translating. */
    fun needsTranslation(src: String): Boolean = src.isNotBlank() && HAS_LETTER.containsMatchIn(protect(src).text.replace(TOKEN, ""))

    /** Safe String.format: falls back to the English pattern when a translated pattern does not fit the args. */
    fun format(pattern: String, fallback: String, args: Array<out Any>): String {
        if (args.isEmpty()) return pattern
        return try { String.format(pattern, *args) } catch (_: Exception) {
            try { String.format(fallback, *args) } catch (_: Exception) { fallback }
        }
    }
}
