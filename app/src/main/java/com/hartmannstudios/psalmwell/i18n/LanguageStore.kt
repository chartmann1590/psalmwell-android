package com.hartmannstudios.psalmwell.i18n

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.languageStore by preferencesDataStore("psalmwell_language")

/** Persists the chosen app language. A null language means the reader has never picked one. */
class LanguageStore(private val context: Context) {
    private val keyLang = stringPreferencesKey("selected_lang")
    private val keyMetered = booleanPreferencesKey("allow_mobile_data")

    val selectedLang: Flow<String?> = context.languageStore.data.map { it[keyLang] }
    val allowMobileData: Flow<Boolean> = context.languageStore.data.map { it[keyMetered] ?: false }

    suspend fun savedLang(): String? = selectedLang.first()
    suspend fun setLang(code: String) { context.languageStore.edit { it[keyLang] = code } }
    suspend fun setAllowMobileData(allow: Boolean) { context.languageStore.edit { it[keyMetered] = allow } }
}
