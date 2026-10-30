package com.hartmannstudios.psalmwell.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hartmannstudios.psalmwell.model.JournalEntry
import com.hartmannstudios.psalmwell.model.SavedVerse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.psalmwellStore by preferencesDataStore("psalmwell")

/** Everything that stays on this device: welcome state, reminder, saved verses and the private journal. */
class LocalStore(private val context: Context) {
    private val welcomeSeen = booleanPreferencesKey("welcome_seen")
    private val reminderEnabled = booleanPreferencesKey("reminder_enabled")
    private val reminderHour = intPreferencesKey("reminder_hour")
    private val savedJson = stringPreferencesKey("saved_verses")
    private val journalJson = stringPreferencesKey("journal")
    private val textScale = intPreferencesKey("reading_text_scale")

    private val data: Flow<Preferences> = context.psalmwellStore.data.catch { emit(emptyPreferences()) }

    val welcomeDone: Flow<Boolean> = data.map { it[welcomeSeen] ?: false }
    val reminder: Flow<Pair<Boolean, Int>> = data.map { (it[reminderEnabled] ?: false) to (it[reminderHour] ?: 8) }
    val readingScale: Flow<Int> = data.map { it[textScale] ?: 100 }
    val saved: Flow<List<SavedVerse>> = data.map { parseSaved(it[savedJson]) }
    val journal: Flow<List<JournalEntry>> = data.map { parseJournal(it[journalJson]) }

    fun rulesAccepted(uid: String): Flow<Boolean> = data.map { (it[rulesKey(uid)] ?: 0) >= RULES_VERSION }

    suspend fun setWelcomeDone() { context.psalmwellStore.edit { it[welcomeSeen] = true } }

    suspend fun setReminder(enabled: Boolean, hour: Int) {
        context.psalmwellStore.edit {
            it[reminderEnabled] = enabled
            it[reminderHour] = hour.coerceIn(0, 23)
        }
    }

    suspend fun setReadingScale(percent: Int) { context.psalmwellStore.edit { it[textScale] = percent.coerceIn(80, 160) } }

    suspend fun acceptRules(uid: String) { context.psalmwellStore.edit { it[rulesKey(uid)] = RULES_VERSION } }

    suspend fun toggleSaved(reference: String, text: String) {
        context.psalmwellStore.edit { prefs ->
            val list = parseSaved(prefs[savedJson]).toMutableList()
            val existing = list.indexOfFirst { it.reference == reference }
            if (existing >= 0) list.removeAt(existing) else list.add(0, SavedVerse(reference, text, System.currentTimeMillis()))
            prefs[savedJson] = JSONArray().apply {
                list.take(MAX_SAVED).forEach { put(JSONObject().put("r", it.reference).put("t", it.text).put("at", it.savedAt)) }
            }.toString()
        }
    }

    suspend fun saveJournal(id: String?, dayKey: String, reference: String, body: String) {
        context.psalmwellStore.edit { prefs ->
            val list = parseJournal(prefs[journalJson]).toMutableList()
            val clean = body.trim().take(MAX_JOURNAL_CHARS)
            val index = if (id == null) -1 else list.indexOfFirst { it.id == id }
            if (clean.isEmpty()) {
                if (index >= 0) list.removeAt(index)
            } else {
                val entry = JournalEntry(id ?: UUID.randomUUID().toString(), dayKey, reference, clean, System.currentTimeMillis())
                if (index >= 0) list[index] = entry else list.add(0, entry)
            }
            prefs[journalJson] = JSONArray().apply {
                list.sortedByDescending { it.updatedAt }.take(MAX_JOURNAL).forEach {
                    put(JSONObject().put("id", it.id).put("d", it.dayKey).put("r", it.reference).put("b", it.body).put("at", it.updatedAt))
                }
            }.toString()
        }
    }

    suspend fun deleteJournal(id: String) = saveJournal(id, "", "", "")

    private fun rulesKey(uid: String) = intPreferencesKey("rules_accepted_" + uid.take(64))

    private fun parseSaved(raw: String?): List<SavedVerse> = runCatching {
        val array = JSONArray(raw ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val ref = o.optString("r"); val text = o.optString("t")
            if (ref.isBlank()) null else SavedVerse(ref, text, o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun parseJournal(raw: String?): List<JournalEntry> = runCatching {
        val array = JSONArray(raw ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); val body = o.optString("b")
            if (id.isBlank() || body.isBlank()) null else JournalEntry(id, o.optString("d"), o.optString("r"), body, o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    companion object {
        const val RULES_VERSION = 1
        private const val MAX_SAVED = 500
        private const val MAX_JOURNAL = 1000
        private const val MAX_JOURNAL_CHARS = 4000
    }
}
