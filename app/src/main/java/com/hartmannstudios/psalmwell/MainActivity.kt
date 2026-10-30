package com.hartmannstudios.psalmwell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hartmannstudios.psalmwell.ads.Ads
import com.hartmannstudios.psalmwell.i18n.ProvideTranslations
import com.hartmannstudios.psalmwell.ui.PsalmwellRoot
import com.hartmannstudios.psalmwell.ui.theme.PsalmwellTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val translator = (application as PsalmwellApplication).container.translator
        setContent { PsalmwellTheme { ProvideTranslations(translator) { PsalmwellRoot() } } }
        Ads.gatherConsentAndStart(this)
    }
}
