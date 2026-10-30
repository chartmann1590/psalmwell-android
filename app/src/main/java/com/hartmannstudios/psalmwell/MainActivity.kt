package com.hartmannstudios.psalmwell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hartmannstudios.psalmwell.ads.Ads
import com.hartmannstudios.psalmwell.ui.PsalmwellRoot
import com.hartmannstudios.psalmwell.ui.theme.PsalmwellTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PsalmwellTheme { PsalmwellRoot() } }
        Ads.gatherConsentAndStart(this)
    }
}
