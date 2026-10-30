package com.hartmannstudios.psalmwell.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase powers accounts and public reflections only. If it is not configured or fails to start,
 * the rest of the app keeps working and community screens explain that reflections are unavailable.
 */
object FirebaseGate {
    @Volatile var available: Boolean = false
        private set

    fun init(context: Context) {
        available = runCatching {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        }.getOrDefault(false)
    }

    fun log(message: String) {
        if (available) runCatching { FirebaseCrashlytics.getInstance().log(message) }
    }

    fun record(error: Throwable) {
        if (available) runCatching { FirebaseCrashlytics.getInstance().recordException(error) }
    }
}
