package com.hartmannstudios.psalmwell.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hartmannstudios.psalmwell.BuildConfig
import com.hartmannstudios.psalmwell.MainViewModel
import com.hartmannstudios.psalmwell.R
import com.hartmannstudios.psalmwell.ads.Ads
import com.hartmannstudios.psalmwell.ads.BannerAd
import com.hartmannstudios.psalmwell.i18n.Text
import com.hartmannstudios.psalmwell.i18n.Translator
import com.hartmannstudios.psalmwell.model.BlockedUser
import com.hartmannstudios.psalmwell.model.JournalEntry
import com.hartmannstudios.psalmwell.model.Reflection

private enum class Tab(val label: String, val tag: String) { TODAY("Today", "tab_today"), PSALMS("Psalms", "tab_psalms"), REFLECT("Reflect", "tab_reflect"), JOURNAL("Journal", "tab_journal"), SETTINGS("Settings", "tab_settings") }

private sealed interface Pending {
    data object Compose : Pending
    data class Report(val reflection: Reflection) : Pending
    data class ReportUser(val reflection: Reflection) : Pending
    data class Block(val reflection: Reflection) : Pending
}

@Composable
fun PsalmwellRoot(vm: MainViewModel = viewModel()) {
    val welcome by vm.welcomeDone.collectAsState()
    when (welcome) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        false -> WelcomeScreen(onStart = { vm.finishWelcome() })
        true -> MainShell(vm)
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp).testTag("screen_welcome"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(painterResource(R.drawable.ic_psalmwell_mark), null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Welcome to Psalmwell", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text("A quiet daily moment with Scripture.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        FeatureRow(Icons.Default.WbSunny, "A verse every day", "The same verse for everyone, with an optional daily reminder.")
        FeatureRow(Icons.AutoMirrored.Filled.MenuBook, "All 150 Psalms", "Read the full Psalms offline, from the public-domain World English Bible.")
        FeatureRow(Icons.Default.EditNote, "Private journal", "Save verses and keep notes that stay on your phone.")
        FeatureRow(Icons.Default.ChatBubbleOutline, "Kind reflections", "Read what others share. Sign in only if you want to post.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("welcome_start")) { Text("Start reading") }
        Text("No account needed to read.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MainShell(vm: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var openPsalm by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAuth by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var rulesForPosting by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var journalEditing by remember { mutableStateOf<JournalEntry?>(null) }
    var journalNew by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val account by vm.account.collectAsState()
    val reflections by vm.reflections.collectAsState()
    val blocked by vm.blocked.collectAsState()
    val rulesAccepted by vm.rulesAccepted.collectAsState()
    val saved by vm.saved.collectAsState()
    val journal by vm.journal.collectAsState()
    val reminder by vm.reminder.collectAsState()
    val scale by vm.readingScale.collectAsState()
    val busy by vm.busy.collectAsState()
    val adsReady by Ads.ready.collectAsState()
    val privacyRequired by Ads.privacyOptionsRequired.collectAsState()
    val savedRefs = remember(saved) { saved.map { it.reference }.toSet() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(Translator.instance?.tr(it.text) ?: it.text) } }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setReminder(granted, reminder.second)
    }

    fun startCompose() {
        when {
            account == null -> showAuth = true
            !rulesAccepted -> { rulesForPosting = true; showRules = true }
            else -> pending = Pending.Compose
        }
    }

    BackHandler(enabled = openPsalm != null) { openPsalm = null }
    BackHandler(enabled = openPsalm == null && tab != Tab.TODAY) { tab = Tab.TODAY }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (openPsalm == null) Column {
                if (adsReady && BuildConfig.BANNER_AD_UNIT_ID.isNotBlank()) BannerAd()
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }, modifier = Modifier.testTag(t.tag),
                            icon = {
                                Icon(when (t) {
                                    Tab.TODAY -> Icons.Default.WbSunny
                                    Tab.PSALMS -> Icons.AutoMirrored.Filled.MenuBook
                                    Tab.REFLECT -> Icons.Default.ChatBubbleOutline
                                    Tab.JOURNAL -> Icons.Default.Bookmark
                                    Tab.SETTINGS -> Icons.Default.Settings
                                }, null)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val chapter = openPsalm
        if (chapter != null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                PsalmReader(
                    chapter = chapter, verses = remember(chapter) { vm.psalm(chapter) }, savedRefs = savedRefs, scale = scale,
                    onScale = vm::setReadingScale, onBack = { openPsalm = null }, onNavigate = { openPsalm = it.coerceIn(1, 150) },
                    onToggleSave = { vm.toggleSaved(it.reference, it.text) },
                    onShare = { context.shareText(verseShareText(it.text, it.reference)) },
                )
            }
        } else when (tab) {
            Tab.TODAY -> TodayScreen(
                verse = vm.verse, saved = vm.verse.reference in savedRefs, psalmOfTheDay = vm.psalmOfTheDay,
                psalmPreview = remember { vm.psalmPreview(vm.psalmOfTheDay) }, reflectionCount = reflections.size, padding = padding,
                onSave = { vm.toggleSaved(vm.verse.reference, vm.verse.text) },
                onShare = { context.shareText(verseShareText(vm.verse.text, vm.verse.reference)) },
                onJournal = { journalNew = true },
                onReflect = { tab = Tab.REFLECT },
                onOpenPsalm = { openPsalm = it },
            )
            Tab.PSALMS -> PsalmsScreen(padding, vm::psalmPreview) { openPsalm = it }
            Tab.REFLECT -> ReflectionsScreen(
                verse = vm.verse, available = vm.communityAvailable, signedIn = account != null, myUid = account?.uid,
                reflections = reflections, padding = padding,
                onSignIn = { showAuth = true }, onCompose = ::startCompose,
                onDelete = vm::deleteReflection,
                onReport = { pending = Pending.Report(it) },
                onReportUser = { pending = Pending.ReportUser(it) },
                onBlock = { pending = Pending.Block(it) },
                onRules = { rulesForPosting = false; showRules = true },
            )
            Tab.JOURNAL -> JournalScreen(
                entries = journal, saved = saved, padding = padding,
                onNew = { journalNew = true }, onEdit = { journalEditing = it },
                onRemoveSaved = { vm.toggleSaved(it.reference, it.text) },
                onShareSaved = { context.shareText(verseShareText(it.text, it.reference)) },
            )
            Tab.SETTINGS -> SettingsScreen(
                account = account, available = vm.communityAvailable, blocked = blocked,
                reminderEnabled = reminder.first, reminderHour = reminder.second, readingScale = scale,
                privacyOptionsRequired = privacyRequired, versionName = BuildConfig.VERSION_NAME, padding = padding,
                onSignIn = { showAuth = true }, onSignOut = vm::signOut, onEditName = { editName = true },
                onDeleteAccount = { confirmDelete = true }, onUnblock = vm::unblock,
                onReminder = { enabled, hour ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && !com.hartmannstudios.psalmwell.reminder.ReminderScheduler.canNotify(context)) {
                        vm.setReminder(false, hour)
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else vm.setReminder(enabled, hour)
                },
                onReadingScale = vm::setReadingScale,
                onRules = { rulesForPosting = false; showRules = true },
                onOpen = { context.openUrl(it) },
                onContact = { context.emailSupport("Psalmwell support or appeal") },
                onAdsPrivacy = { activity?.let(Ads::showPrivacyOptions) },
            )
        }
    }

    if (showAuth) AuthDialog(
        busy = busy, googleAvailable = vm.googleSignInAvailable,
        onEmail = { email, password, register, name ->
            if (register) vm.register(email, password, name) { showAuth = false } else vm.signIn(email, password) { showAuth = false }
        },
        onGoogle = { activity?.let { a -> vm.signInWithGoogle(a) { showAuth = false } } },
        onReset = vm::resetPassword,
        onDismiss = { showAuth = false },
    )

    if (showRules) RulesDialog(
        busy = busy,
        requireAcceptance = rulesForPosting && !rulesAccepted,
        onAccept = { vm.acceptRules { showRules = false; pending = Pending.Compose } },
        onDismiss = { showRules = false },
        onOpenFull = { context.openUrl(Links.rules) },
    )

    when (val p = pending) {
        Pending.Compose -> ComposeDialog(busy, vm.verse, onPost = { text, result -> vm.post(text, result) }, onDismiss = { pending = null })
        is Pending.Report -> ReportDialog(
            "Report reflection", "Tell us what's wrong with this reflection. Reports are private and reviewed by a person.", busy,
            onSubmit = { reason, details -> vm.reportReflection(p.reflection, reason, details); pending = null }, onDismiss = { pending = null },
        )
        is Pending.ReportUser -> ReportDialog(
            "Report ${p.reflection.authorName}", "Report this person if their behavior breaks the community rules. They won't know who reported them.", busy,
            onSubmit = { reason, details -> vm.reportUser(BlockedUser(p.reflection.authorUid, p.reflection.authorName), reason, details, p.reflection); pending = null },
            onDismiss = { pending = null },
        )
        is Pending.Block -> BlockDialog(p.reflection.authorName,
            onConfirm = { vm.block(BlockedUser(p.reflection.authorUid, p.reflection.authorName)); pending = null }, onDismiss = { pending = null })
        null -> Unit
    }

    if (journalNew || journalEditing != null) {
        val editing = journalEditing
        JournalEditor(
            initial = editing, reference = editing?.reference?.ifBlank { null } ?: vm.verse.reference,
            onSave = { text ->
                vm.saveJournal(editing?.id, editing?.dayKey ?: vm.verse.dayKey, editing?.reference ?: vm.verse.reference, text)
                journalNew = false; journalEditing = null
            },
            onDelete = editing?.let { e -> { vm.deleteJournal(e.id); journalEditing = null } },
            onDismiss = { journalNew = false; journalEditing = null },
        )
    }

    if (editName) {
        var name by rememberSaveable { mutableStateOf(account?.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Display name") },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Shown with your reflections") }) },
            confirmButton = { Button(onClick = { vm.updateName(name); editName = false }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        modifier = Modifier.testTag("delete_dialog"),
        icon = { Icon(Icons.Default.Shield, null) },
        title = { Text("Delete your account?") },
        text = { Text("This permanently deletes your account, your public profile, your reflections and your blocked list. Your journal and saved verses stay on this phone. This can't be undone.") },
        confirmButton = { Button(onClick = { vm.deleteAccount(); confirmDelete = false }, modifier = Modifier.testTag("delete_confirm")) { Text("Delete permanently") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
