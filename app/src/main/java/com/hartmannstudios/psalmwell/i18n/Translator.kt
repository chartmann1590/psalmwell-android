package com.hartmannstudios.psalmwell.i18n

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Where the language pack for the chosen language stands. */
sealed interface ModelState {
    data object NotNeeded : ModelState
    data class Checking(val lang: String) : ModelState
    data class WaitingForWifi(val lang: String) : ModelState
    data class Downloading(val lang: String) : ModelState
    data class Ready(val lang: String) : ModelState
    data class Failed(val lang: String, val reason: String) : ModelState
}

/**
 * On-device translation of the app's English text with Google ML Kit.
 *
 * Every string the UI draws goes through [request]; translations are cached per language on the
 * phone, so each string is translated once. Until the language pack is downloaded (Wi-Fi only
 * unless the reader allows mobile data) the app keeps showing English, so nothing is ever blank.
 */
class Translator(private val context: Context) {
    val store = LanguageStore(context)
    private val files = TranslationCache(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lang = MutableStateFlow(Languages.ORIGINAL)
    /** ML Kit code of the language the UI is shown in; "" = original English. */
    val lang: StateFlow<String> = _lang.asStateFlow()

    private val _cache = MutableStateFlow<Map<String, String>>(emptyMap())
    /** English source -> translation, for the current language. */
    val cache: StateFlow<Map<String, String>> = _cache.asStateFlow()

    private val _model = MutableStateFlow<ModelState>(ModelState.NotNeeded)
    val modelState: StateFlow<ModelState> = _model.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    private val translateMutex = Mutex()
    private var client: com.google.mlkit.nl.translate.Translator? = null
    private var clientLang: String? = null
    private val requested = ConcurrentHashMap.newKeySet<String>()
    private val queue = Channel<String>(Channel.UNLIMITED)
    private var selectJob: Job? = null
    private var saveJob: Job? = null

    init {
        instance = this
        scope.launch {
            val saved = runCatching { store.savedLang() }.getOrNull()
            if (!saved.isNullOrEmpty()) {
                _cache.value = files.load(saved)
                _lang.value = saved
                _model.value = ModelState.Ready(saved)
            }
            _loaded.value = true
            when {
                // Never chosen: follow the phone's language when ML Kit supports it.
                saved == null -> Languages.deviceSuggestion()?.let { device ->
                    selectLanguage(device, runCatching { store.allowMobileData.first() }.getOrDefault(false), persistWhenReady = true)
                }
                saved.isNotEmpty() -> {
                    val have = runCatching { isDownloaded(saved) }.getOrDefault(true)
                    if (!have) selectLanguage(saved, runCatching { store.allowMobileData.first() }.getOrDefault(false))
                }
            }
        }
        scope.launch {
            for (src in queue) {
                val l = _lang.value
                if (l.isEmpty() || _cache.value.containsKey(src)) { requested.remove(src); continue }
                val out = translateRaw(src, l)
                requested.remove(src)
                if (out != null && l == _lang.value) keep(l, src, out)
            }
        }
    }

    /** Immediate lookup for non-Compose code. A miss returns English and queues a translation. */
    fun trNow(src: String): String {
        if (_lang.value.isEmpty() || !Placeholders.needsTranslation(src)) return src
        return _cache.value[src] ?: run { request(src); src }
    }

    /** Translates [src] now (waits for ML Kit on a cache miss); English when no pack is available. */
    suspend fun tr(src: String): String {
        _loaded.first { it }
        val l = _lang.value
        if (l.isEmpty() || !Placeholders.needsTranslation(src)) return src
        _cache.value[src]?.let { return it }
        val out = translateRaw(src, l) ?: return src
        if (l == _lang.value) keep(l, src, out)
        return out
    }

    fun request(src: String) {
        if (_lang.value.isEmpty()) return
        if (requested.add(src)) queue.trySend(src)
    }

    /**
     * Switches the app language. For anything but English this checks for the ML Kit language pack,
     * downloads it when missing (Wi-Fi only unless [allowMobileData]) and only then switches, so the
     * screen never shows half a switch. If the pack cannot be downloaded the app stays in English.
     */
    fun selectLanguage(code: String, allowMobileData: Boolean, persistWhenReady: Boolean = true) {
        selectJob?.cancel()
        selectJob = scope.launch {
            store.setAllowMobileData(allowMobileData)
            if (code.isEmpty() || code == TranslateLanguage.ENGLISH) {
                store.setLang(Languages.ORIGINAL)
                _lang.value = Languages.ORIGINAL
                _cache.value = emptyMap()
                _model.value = ModelState.NotNeeded
                closeClient()
                return@launch
            }
            _model.value = ModelState.Checking(code)
            val have = runCatching { isDownloaded(code) }.getOrDefault(false)
            if (!have) {
                val wifiOnly = !allowMobileData
                _model.value = if (wifiOnly && !onUnmeteredNetwork()) ModelState.WaitingForWifi(code) else ModelState.Downloading(code)
                val conditions = if (wifiOnly) DownloadConditions.Builder().requireWifi().build() else DownloadConditions.Builder().build()
                val result = runCatching {
                    RemoteModelManager.getInstance().download(TranslateRemoteModel.Builder(code).build(), conditions).await()
                }
                if (result.isFailure) {
                    Log.w(TAG, "language pack download failed", result.exceptionOrNull())
                    _model.value = ModelState.Failed(code, result.exceptionOrNull()?.localizedMessage ?: "download failed")
                    return@launch
                }
            }
            closeClient()
            _cache.value = files.load(code)
            if (persistWhenReady) store.setLang(code)
            _lang.value = code
            _model.value = ModelState.Ready(code)
        }
    }

    /** Retries the language whose pack is still missing (Retry / use mobile data buttons). */
    fun retry(allowMobileData: Boolean) {
        when (val s = _model.value) {
            is ModelState.Failed -> selectLanguage(s.lang, allowMobileData)
            is ModelState.WaitingForWifi -> selectLanguage(s.lang, allowMobileData)
            is ModelState.Downloading -> selectLanguage(s.lang, allowMobileData)
            else -> Unit
        }
    }

    private fun keep(lang: String, src: String, out: String) {
        _cache.value = _cache.value + (src to out)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1_500)
            if (_lang.value == lang) files.save(lang, _cache.value)
        }
    }

    private suspend fun isDownloaded(code: String): Boolean =
        RemoteModelManager.getInstance().isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await()

    private suspend fun translateRaw(src: String, code: String): String? = translateMutex.withLock {
        val p = Placeholders.protect(src)
        val c = clientFor(code) ?: return@withLock null
        val translated = runCatching { withTimeoutOrNull(20_000) { c.translate(p.text).await() } }
            .onFailure { Log.w(TAG, "translate failed", it) }.getOrNull() ?: return@withLock null
        Placeholders.restore(translated, p.placeholders)
    }

    private fun clientFor(code: String): com.google.mlkit.nl.translate.Translator? {
        if (clientLang == code) client?.let { return it }
        closeClient()
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(code)
            .build()
        return runCatching { Translation.getClient(options) }.getOrNull()?.also { client = it; clientLang = code }
    }

    private fun closeClient() {
        runCatching { client?.close() }
        client = null
        clientLang = null
    }

    private fun onUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        private const val TAG = "PsalmwellTranslator"
        @Volatile var instance: Translator? = null
            private set
    }
}
