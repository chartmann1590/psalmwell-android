package com.hartmannstudios.psalmwell.i18n

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

/** One line for Settings: the current language, the language-pack status, and a button to change it. */
@Composable
fun LanguageSettingsContent(modifier: Modifier = Modifier) {
    val translator = LocalTr.current.translator
    val lang by (translator?.lang ?: MutableStateFlow("")).collectAsStateWithLifecycle()
    val model by (translator?.modelState ?: MutableStateFlow<ModelState>(ModelState.NotNeeded)).collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    val current = Languages.fromCode(lang)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp).testTag("settings_language"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RawText(current.nativeName, fontWeight = FontWeight.SemiBold)
                if (current.code.isNotEmpty()) RawText(current.englishName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { open = true }) { Text("Change") }
        }
        ModelStatus(model, onRetry = { mobile -> translator?.retry(mobile) })
        if (lang.isNotEmpty()) Text(
            "Translated on your phone by Google ML Kit. Machine translations can contain mistakes; Scripture and the app were written in English.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open && translator != null) LanguagePickerDialog(translator, current = lang, onDismiss = { open = false })
}

@Composable
private fun ModelStatus(model: ModelState, onRetry: (Boolean) -> Unit) {
    fun name(code: String) = Languages.fromCode(code).nativeName
    when (model) {
        is ModelState.Checking -> StatusLine(trf("Preparing %1\$s…", name(model.lang)), progress = true)
        is ModelState.Downloading -> StatusLine(trf("Downloading the %1\$s language pack (about 30 MB). Psalmwell stays in English until it's ready.", name(model.lang)), progress = true)
        is ModelState.WaitingForWifi -> Column {
            StatusLine(trf("Waiting for Wi-Fi to download the %1\$s language pack (about 30 MB). Psalmwell stays in English until then.", name(model.lang)), progress = false)
            OutlinedButton(onClick = { onRetry(true) }, modifier = Modifier.padding(vertical = 4.dp)) { Text("Download now with mobile data") }
        }
        is ModelState.Failed -> Column {
            StatusLine(trf("The %1\$s language pack couldn't be downloaded, so Psalmwell is showing English.", name(model.lang)), progress = false)
            OutlinedButton(onClick = { onRetry(false) }, modifier = Modifier.padding(vertical = 4.dp)) { Text("Try again") }
        }
        else -> Unit
    }
}

@Composable
private fun StatusLine(text: String, progress: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("language_status")) {
        RawText(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (progress) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

/** Search + list of every language ML Kit translates to, the phone's language first. */
@Composable
fun LanguagePickerDialog(translator: Translator, current: String, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val allowMobile by translator.store.allowMobileData.collectAsStateWithLifecycle(initialValue = false)
    var mobile by remember(allowMobile) { mutableStateOf(allowMobile) }
    val all = remember {
        val list = Languages.all()
        val device = Languages.deviceSuggestion()
        if (device == null) list else list.sortedBy { if (it.code == device) 0 else if (it.code.isEmpty()) 1 else 2 }
    }
    val shown = remember(query) { all.filter { Languages.matches(it, query) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("language_dialog"),
        title = { Text("App language") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Search languages") },
                    modifier = Modifier.fillMaxWidth().testTag("language_search"),
                )
                Row(Modifier.fillMaxWidth().clickable { mobile = !mobile }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = mobile, onCheckedChange = { mobile = it })
                    Text("Also download over mobile data", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(shown, key = { it.code.ifEmpty { "en" } }) { l ->
                        Row(
                            Modifier.fillMaxWidth().clickable { translator.selectLanguage(l.code, mobile); onDismiss() }
                                .padding(vertical = 10.dp).testTag("language_" + l.code.ifEmpty { "en" }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                RawText(l.nativeName, fontWeight = if (l.code == current) FontWeight.SemiBold else null)
                                RawText(l.englishName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (l.code == current) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .12f))
                    }
                }
                Text(
                    "Translation runs on your phone with Google ML Kit. Each language is a one-time download of about 30 MB, over Wi-Fi unless you allow mobile data.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
