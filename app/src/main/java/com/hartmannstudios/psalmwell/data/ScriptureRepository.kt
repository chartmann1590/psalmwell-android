package com.hartmannstudios.psalmwell.data

import android.content.Context
import com.hartmannstudios.psalmwell.model.DailyVerse
import com.hartmannstudios.psalmwell.model.PsalmVerse
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Scripture bundled with the app: the World English Bible (public domain), read from assets.
 * Nothing here touches the network, so reading works offline and without an account.
 */
class ScriptureRepository(private val context: Context) {
    private val dailyVerses: List<Pair<String, String>> by lazy {
        readLines("daily_verses.tsv").mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
        }.ifEmpty { listOf(FALLBACK) }
    }

    private val psalms: Map<Int, List<PsalmVerse>> by lazy {
        readLines("psalms.tsv").mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            val chapter = parts.getOrNull(0)?.toIntOrNull()
            val verse = parts.getOrNull(1)?.toIntOrNull()
            val text = parts.getOrNull(2)
            if (chapter == null || verse == null || text.isNullOrBlank()) null else PsalmVerse(chapter, verse, text)
        }.groupBy { it.chapter }
    }

    fun today(): DailyVerse = forDate(LocalDate.now(ZoneOffset.UTC))

    fun forDate(date: LocalDate): DailyVerse {
        val list = dailyVerses
        val entry = list[Math.floorMod(date.toEpochDay(), list.size.toLong()).toInt()]
        return DailyVerse(date.format(DateTimeFormatter.ISO_DATE), entry.first, entry.second)
    }

    /** A Psalm suggested for today, cycling through all 150. */
    fun psalmOfTheDay(date: LocalDate = LocalDate.now(ZoneOffset.UTC)): Int =
        Math.floorMod(date.toEpochDay(), PSALM_COUNT.toLong()).toInt() + 1

    fun psalm(chapter: Int): List<PsalmVerse> = psalms[chapter].orEmpty()

    fun psalmPreview(chapter: Int): String = psalm(chapter).firstOrNull()?.text.orEmpty()

    private fun readLines(name: String): List<String> = runCatching {
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { reader -> reader.readLines().filter { it.isNotBlank() } }
    }.getOrDefault(emptyList())

    companion object {
        const val PSALM_COUNT = 150
        private val FALLBACK = "Psalm 119:105" to "Your word is a lamp to my feet, and a light for my path."
        fun utcDayKey(): String = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE)
    }
}
