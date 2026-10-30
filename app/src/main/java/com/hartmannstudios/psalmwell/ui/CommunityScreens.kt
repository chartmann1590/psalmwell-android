package com.hartmannstudios.psalmwell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hartmannstudios.psalmwell.i18n.RawText
import com.hartmannstudios.psalmwell.i18n.T
import com.hartmannstudios.psalmwell.i18n.Text
import com.hartmannstudios.psalmwell.model.DailyVerse
import com.hartmannstudios.psalmwell.model.Reflection
import com.hartmannstudios.psalmwell.model.ReportReason
import com.hartmannstudios.psalmwell.moderation.SafetyClassifier
import com.hartmannstudios.psalmwell.ui.theme.ScriptureFont

@Composable
fun ReflectionsScreen(
    verse: DailyVerse,
    available: Boolean,
    signedIn: Boolean,
    myUid: String?,
    reflections: List<Reflection>,
    padding: PaddingValues,
    onSignIn: () -> Unit,
    onCompose: () -> Unit,
    onDelete: (Reflection) -> Unit,
    onReport: (Reflection) -> Unit,
    onReportUser: (Reflection) -> Unit,
    onBlock: (Reflection) -> Unit,
    onRules: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("screen_reflections"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 20.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Reflections", "What today's verse means to people. Kind, moderated, and open to read.") }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp)) {
                    Text(verse.reference, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(verse.text, fontFamily = ScriptureFont, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        when {
            !available -> item { InfoCard(Icons.Default.CloudOff, "Reflections are unavailable right now. Reading, the Psalms, saved verses and your journal still work.") }
            !signedIn -> item { InfoCard(Icons.Default.ChatBubbleOutline, "Anyone can read reflections. Sign in to share your own.", "Sign in to reflect", onSignIn, Modifier.testTag("reflect_sign_in")) }
            else -> item {
                Button(onClick = onCompose, modifier = Modifier.fillMaxWidth().testTag("compose_reflection")) {
                    Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(8.dp)); Text("Share a reflection")
                }
            }
        }
        if (available && reflections.isEmpty()) item {
            Text("No reflections yet today.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        items(reflections, key = { it.id }) { r ->
            ReflectionCard(r, own = r.authorUid == myUid, signedIn = signedIn, onDelete = { onDelete(r) }, onReport = { onReport(r) }, onReportUser = { onReportUser(r) }, onBlock = { onBlock(r) }, onSignIn = onSignIn)
        }
        item {
            TextButton(onClick = onRules, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Shield, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Community rules and how moderation works")
            }
        }
    }
}

@Composable
private fun ReflectionCard(r: Reflection, own: Boolean, signedIn: Boolean, onDelete: () -> Unit, onReport: () -> Unit, onReportUser: () -> Unit, onBlock: () -> Unit, onSignIn: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("reflection_card"), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initial(r.authorName)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    RawText(r.authorName + if (own) " (" + T("you") + ")" else "", fontWeight = FontWeight.SemiBold)
                    if (r.createdAtMillis == null) Text("Just now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) else RawText(formatMillis(r.createdAtMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("reflection_menu")) { Icon(Icons.Default.MoreVert, T("More options")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (own) {
                            DropdownMenuItem(text = { Text("Delete my reflection") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { menu = false; onDelete() })
                        } else if (!signedIn) {
                            DropdownMenuItem(text = { Text("Sign in to report or block") }, onClick = { menu = false; onSignIn() })
                        } else {
                            DropdownMenuItem(text = { Text("Report reflection") }, leadingIcon = { Icon(Icons.Default.Flag, null) }, onClick = { menu = false; onReport() }, modifier = Modifier.testTag("menu_report"))
                            DropdownMenuItem(text = { Text("Report user") }, leadingIcon = { Icon(Icons.Default.PersonOff, null) }, onClick = { menu = false; onReportUser() }, modifier = Modifier.testTag("menu_report_user"))
                            DropdownMenuItem(text = { Text("Block user") }, leadingIcon = { Icon(Icons.Default.Block, null) }, onClick = { menu = false; onBlock() }, modifier = Modifier.testTag("menu_block"))
                        }
                    }
                }
            }
            Spacer(Modifier.size(10.dp))
            RawText(r.body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun RulesDialog(busy: Boolean, requireAcceptance: Boolean, onAccept: () -> Unit, onDismiss: () -> Unit, onOpenFull: () -> Unit) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("rules_dialog"),
        title = { Text("Community rules") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Psalmwell reflections are a kind place for everyone 13 and older. When you post, you agree to:")
                listOf(
                    "Be kind. No harassment, bullying, insults or threats.",
                    "No hate speech or attacks on any group of people.",
                    "No sexual content, nudity or anything that endangers children. We report child sexual abuse material to the authorities.",
                    "No spam, ads, links, scams or personal contact details.",
                    "Respectful disagreement and honest questions are welcome.",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                Text("Every reflection is checked on your device and again on our server. Anyone can report a reflection or a person, and you can block people you don't want to see. Reported content is reviewed by a person, usually within 24 hours, and accounts that break the rules are removed. You can appeal a decision from Settings > Contact & appeals.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onOpenFull, contentPadding = PaddingValues(0.dp)) { Text("Read the full community rules") }
                if (requireAcceptance) Row(Modifier.fillMaxWidth().selectable(selected = agreed, onClick = { agreed = !agreed }, role = Role.Checkbox).testTag("rules_checkbox"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agreed, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text("I agree to the community rules", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            if (requireAcceptance) Button(onClick = onAccept, enabled = agreed && !busy, modifier = Modifier.testTag("rules_accept")) { Text("Agree and continue") }
            else TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = { if (requireAcceptance) TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
fun ComposeDialog(busy: Boolean, verse: DailyVerse, onPost: (String, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.testTag("compose_dialog"),
        title = { Text("Share a reflection") },
        text = {
            Column {
                Text("On ${verse.reference}. Your display name is shown with your reflection.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(SafetyClassifier.MAX_LENGTH); feedback = null },
                    label = { Text("What does this verse stir in you?") }, minLines = 4,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 120.dp).testTag("compose_text"),
                    supportingText = { Text("${text.length}/${SafetyClassifier.MAX_LENGTH}") },
                )
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = { onPost(text) { error -> if (error == null) onDismiss() else feedback = error } }, enabled = text.isNotBlank() && !busy, modifier = Modifier.testTag("compose_post")) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Post")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
fun ReportDialog(title: String, explanation: String, busy: Boolean, onSubmit: (String, String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf(ReportReason.HARASSMENT.code) }
    var details by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("report_dialog"),
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(explanation, style = MaterialTheme.typography.bodySmall)
                ReportReason.entries.forEach { r ->
                    Row(Modifier.fillMaxWidth().selectable(selected = reason == r.code, onClick = { reason = r.code }, role = Role.RadioButton).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r.code, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(r.label)
                    }
                }
                OutlinedTextField(details, { details = it.take(500) }, label = { Text("Details (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        },
        confirmButton = { Button(onClick = { onSubmit(reason, details.trim()) }, enabled = !busy, modifier = Modifier.testTag("report_submit")) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BlockDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("block_dialog"),
        title = { Text("Block $name?") },
        text = { Text("You won't see reflections from $name anymore. They aren't notified. You can unblock them anytime in Settings > Blocked people.") },
        confirmButton = { Button(onClick = onConfirm, modifier = Modifier.testTag("block_confirm")) { Text("Block") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AuthDialog(busy: Boolean, googleAvailable: Boolean, onEmail: (email: String, password: String, register: Boolean, name: String) -> Unit, onGoogle: () -> Unit, onReset: (String) -> Unit, onDismiss: () -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val valid = email.contains('@') && password.length >= 6 && (!register || name.isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("auth_dialog"),
        title = { Text(if (register) "Create an account" else "Sign in") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("An account is only needed to share, report or block in Reflections. Reading, the Psalms and your journal never need one.", style = MaterialTheme.typography.bodySmall)
                if (register) OutlinedTextField(name, { name = it.take(40) }, label = { Text("Display name (shown publicly)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("auth_name"))
                OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth().testTag("auth_email"))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth().testTag("auth_password"))
                Button(onClick = { onEmail(email, password, register, name) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().testTag("auth_submit")) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (register) "Create account" else "Sign in")
                }
                if (googleAvailable) OutlinedButton(onClick = onGoogle, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { register = !register }) { Text(if (register) "I have an account" else "Create account") }
                    if (!register) TextButton(onClick = { onReset(email) }, enabled = email.contains('@') && !busy) { Text("Forgot password") }
                }
                if (register) Text("By creating an account you agree to the community rules and privacy policy. You must be 13 or older.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
