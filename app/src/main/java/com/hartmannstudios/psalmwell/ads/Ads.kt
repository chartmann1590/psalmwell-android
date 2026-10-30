package com.hartmannstudios.psalmwell.ads

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.hartmannstudios.psalmwell.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ads are a single banner, requested only after the Google consent flow says ads may be requested.
 * Automated test devices (Firebase Test Lab / Play pre-launch) never request ads.
 */
object Ads {
    private val started = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired
    @Volatile var disabledForTests: Boolean = false

    fun isAutomatedTestDevice(context: Context): Boolean = runCatching {
        Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
    }.getOrDefault(false)

    fun gatherConsentAndStart(activity: Activity) {
        if (disabledForTests || isAutomatedTestDevice(activity)) return
        val info = runCatching { UserMessagingPlatform.getConsentInformation(activity) }.getOrNull() ?: return
        runCatching {
            info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                if (activity.isFinishing || activity.isDestroyed) return@requestConsentInfoUpdate
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    update(info)
                    if (info.canRequestAds()) startSdk(activity)
                }
            }, { _ -> update(info) })
        }
        // Consent obtained in a previous session lets ads start right away.
        if (info.canRequestAds()) startSdk(activity)
    }

    private fun update(info: ConsentInformation) {
        _privacyOptionsRequired.value = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun startSdk(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        runCatching {
            MobileAds.initialize(activity.applicationContext) { _ready.value = true }
        }.onFailure { started.set(false) }
    }

    fun showPrivacyOptions(activity: Activity) {
        runCatching { UserMessagingPlatform.showPrivacyOptionsForm(activity) { } }
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val width = LocalConfiguration.current.screenWidthDp
    val size = remember(width) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width) }
    val view = remember(context) {
        AdView(context).apply {
            setAdSize(size)
            adUnitId = BuildConfig.BANNER_AD_UNIT_ID
            runCatching { loadAd(AdRequest.Builder().build()) }
        }
    }
    DisposableEffect(view) { onDispose { runCatching { view.destroy() } } }
    AndroidView(modifier = modifier.fillMaxWidth().height(size.height.coerceAtLeast(50).dp), factory = { view })
}
