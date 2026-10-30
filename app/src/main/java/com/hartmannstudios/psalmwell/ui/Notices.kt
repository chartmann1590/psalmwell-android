package com.hartmannstudios.psalmwell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hartmannstudios.psalmwell.i18n.RawText
import com.hartmannstudios.psalmwell.i18n.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Who made what Psalmwell is built on. Translation credit first: Google ML Kit's terms ask for it. */
private val CREDITS = listOf(
    "Google ML Kit (on-device translation)" to "Translations in Psalmwell are made on your phone by Google ML Kit, used under the ML Kit Terms of Service: https://developers.google.com/ml-kit/terms. Machine translations can contain mistakes.",
    "World English Bible" to "Scripture text. Public domain.",
    "Firebase, Google Mobile Ads SDK, User Messaging Platform, Google Identity" to "Google, used under the Google APIs Terms of Service and the Android Software Development Kit License.",
    "AndroidX, Jetpack Compose, Material Components, WorkManager, DataStore" to "The Android Open Source Project, Apache License 2.0.",
    "Kotlin, kotlinx.coroutines" to "JetBrains s.r.o. and contributors, Apache License 2.0.",
)

@Composable
fun OpenSourceNoticesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Full third-party notices shipped inside the libraries (generated at build time into assets).
    val notices by produceState(initialValue = emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("open_source_notices.txt").bufferedReader().use { it.readText() } }
                .getOrDefault("").split("\u000C").map { it.trim() }.filter { it.isNotEmpty() }
                .map { block -> block.substringBefore('\n') to block.substringAfter('\n', "") }
        }
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("notices_dialog"),
        title = { Text("Open-source licenses and notices") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CREDITS) { (name, body) ->
                    Column {
                        RawText(name, fontWeight = FontWeight.SemiBold)
                        Text(body, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (notices.isNotEmpty()) item {
                    Column {
                        Text("Third-party notices", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("notices_third_party"))
                        Text("Notices shipped with the libraries above. Tap a name to read its license.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(notices) { (names, text) ->
                    Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == names) null else names }) {
                        RawText(names, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        if (expanded == names) RawText(text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
