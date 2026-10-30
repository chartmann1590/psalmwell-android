package com.hartmannstudios.psalmwell.i18n

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * Finished translations, one small JSON file per language in the app's private storage, so a
 * string is translated once and shows instantly afterwards (also offline).
 */
class TranslationCache(context: Context) {
    private val dir = File(context.filesDir, "translations")

    @Synchronized
    fun load(lang: String): Map<String, String> = runCatching {
        val f = file(lang)
        if (!f.exists()) return emptyMap()
        val json = JSONObject(f.readText())
        buildMap { json.keys().forEach { k -> put(k, json.getString(k)) } }
    }.getOrDefault(emptyMap())

    @Synchronized
    fun save(lang: String, entries: Map<String, String>) {
        runCatching {
            dir.mkdirs()
            val json = JSONObject()
            entries.forEach { (k, v) -> json.put(k, v) }
            val tmp = File(dir, "$lang.json.tmp")
            tmp.writeText(json.toString())
            tmp.renameTo(file(lang))
        }
    }

    @Synchronized
    fun clear() { runCatching { dir.deleteRecursively() } }

    private fun file(lang: String) = File(dir, "${lang.replace(Regex("[^A-Za-z-]"), "_")}.json")
}
