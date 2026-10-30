package com.hartmannstudios.psalmwell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hartmannstudios.psalmwell.data.Account
import com.hartmannstudios.psalmwell.model.BlockedUser
import com.hartmannstudios.psalmwell.model.JournalEntry
import com.hartmannstudios.psalmwell.model.SavedVerse
import com.hartmannstudios.psalmwell.ui.theme.ScriptureFont

@Composable
fun JournalScreen(
    entries: List<JournalEntry>,
    saved: List<SavedVerse>,
    padding: PaddingValues,
    onNew: () -> Unit,
    onEdit: (JournalEntry) -> Unit,
    onRemoveSaved: (SavedVerse) -> Unit,
    onShareSaved: (SavedVerse) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("screen_journal"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 20.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Journal", "Private notes and saved verses. They stay on this phone and never need an account.") }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = tab == 0, onClick = { tab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.testTag("seg_journal")) { Text("Journal (${entries.size})") }
                SegmentedButton(selected = tab == 1, onClick = { tab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.testTag("seg_saved")) { Text("Saved (${saved.size})") }
            }
        }
        if (tab == 0) {
            item { Button(onClick = onNew, modifier = Modifier.fillMaxWidth().testTag("journal_new")) { Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(8.dp)); Text("New entry on today's verse") } }
            if (entries.isEmpty()) item { InfoCard(Icons.Default.EditNote, "Write down a prayer, a thought or what a verse means to you. Only you can see your journal.") }
            items(entries, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().clickable { onEdit(e) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(e.reference.ifBlank { "Note" } + " · " + e.dayKey, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(e.body, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            if (saved.isEmpty()) item { InfoCard(Icons.Default.Bookmark, "Verses you save from Today or the Psalms appear here.") }
            items(saved, key = { it.reference }) { s ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.reference, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(s.text, fontFamily = ScriptureFont, style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { onShareSaved(s) }) { Icon(Icons.Default.Share, "Share") }
                            IconButton(onClick = { onRemoveSaved(s) }) { Icon(Icons.Default.DeleteOutline, "Remove") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JournalEditor(initial: JournalEntry?, reference: String, onSave: (String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var text by rememberSaveable(initial?.id) { mutableStateOf(initial?.body.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("journal_editor"),
        title = { Text(if (initial == null) "New journal entry" else "Edit entry") },
        text = {
            Column {
                Text(reference + " · private, stored only on this phone", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it.take(4000) }, minLines = 6, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 160.dp).testTag("journal_text"), label = { Text("Your thoughts") })
            }
        },
        confirmButton = { Button(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("journal_save")) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun SettingsScreen(
    account: Account?,
    available: Boolean,
    blocked: List<BlockedUser>,
    reminderEnabled: Boolean,
    reminderHour: Int,
    readingScale: Int,
    privacyOptionsRequired: Boolean,
    versionName: String,
    padding: PaddingValues,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onEditName: () -> Unit,
    onDeleteAccount: () -> Unit,
    onUnblock: (BlockedUser) -> Unit,
    onReminder: (Boolean, Int) -> Unit,
    onReadingScale: (Int) -> Unit,
    onRules: () -> Unit,
    onOpen: (String) -> Unit,
    onContact: () -> Unit,
    onAdsPrivacy: () -> Unit,
) {
    var hour by remember(reminderHour) { mutableFloatStateOf(reminderHour.toFloat()) }
    var scale by remember(readingScale) { mutableFloatStateOf(readingScale.toFloat()) }
    var blockedOpen by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("screen_settings"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 20.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionTitle("Settings") }
        item {
            SettingsCard("Account") {
                if (account == null) {
                    Text(if (available) "You're reading as a guest. Sign in only if you want to share reflections, report or block." else "Accounts are unavailable right now.", style = MaterialTheme.typography.bodyMedium)
                    if (available) Button(onClick = onSignIn, modifier = Modifier.padding(top = 8.dp).testTag("settings_sign_in")) { Text("Sign in or create account") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account.displayName ?: "Signed in", fontWeight = FontWeight.SemiBold)
                            Text(account.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onEditName) { Text("Display name") }
                        OutlinedButton(onClick = onSignOut, modifier = Modifier.testTag("settings_sign_out")) { Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(6.dp)); Text("Sign out") }
                    }
                    TextButton(onClick = onDeleteAccount, modifier = Modifier.testTag("settings_delete_account")) {
                        Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp))
                        Text("Delete account and my reflections", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            SettingsCard("Community & safety") {
                LinkRow(Icons.Default.Shield, "Community rules", onRules)
                LinkRow(Icons.Default.Block, "Blocked people (${blocked.size})", { blockedOpen = true }, Modifier.testTag("settings_blocked"))
                LinkRow(Icons.Default.MailOutline, "Contact & appeals", onContact)
                LinkRow(Icons.Default.ChildCare, "Child safety standards", { onOpen(Links.childSafety) })
            }
        }
        item {
            SettingsCard("Daily reminder") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Verse of the day notification")
                        Text(if (reminderEnabled) "Around ${formatHour(hour.toInt())}" else "Off", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = reminderEnabled, onCheckedChange = { onReminder(it, hour.toInt()) }, modifier = Modifier.testTag("settings_reminder"))
                }
                if (reminderEnabled) Slider(value = hour, onValueChange = { hour = it }, onValueChangeFinished = { onReminder(true, hour.toInt()) }, valueRange = 0f..23f, steps = 22)
            }
        }
        item {
            SettingsCard("Reading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, null); Spacer(Modifier.width(10.dp))
                    Text("Psalm text size: ${scale.toInt()}%", Modifier.weight(1f))
                }
                Slider(value = scale, onValueChange = { scale = it }, onValueChangeFinished = { onReadingScale(scale.toInt()) }, valueRange = 80f..160f, steps = 7)
            }
        }
        item {
            SettingsCard("Privacy") {
                LinkRow(Icons.Default.PrivacyTip, "Privacy policy", { onOpen(Links.privacy) })
                if (privacyOptionsRequired) LinkRow(Icons.Default.Tune, "Ad privacy choices", onAdsPrivacy)
                LinkRow(Icons.Default.Gavel, "Delete account on the web", { onOpen(Links.deleteAccount) })
            }
        }
        item {
            SettingsCard("About") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null); Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Psalmwell $versionName by Hartmann Studios")
                        Text("Scripture: World English Bible, public domain. Contains ads.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (blockedOpen) AlertDialog(
        onDismissRequest = { blockedOpen = false },
        modifier = Modifier.testTag("blocked_dialog"),
        title = { Text("Blocked people") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (blocked.isEmpty()) Text("You haven't blocked anyone. To block someone, open the menu on one of their reflections.")
                blocked.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, Modifier.weight(1f))
                        TextButton(onClick = { onUnblock(b) }) { Text("Unblock") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { blockedOpen = false }) { Text("Done") } },
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column {
        Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
    }
}
