package com.hartmannstudios.psalmwell

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.firebase.auth.FirebaseAuthException
import com.hartmannstudios.psalmwell.data.Account
import com.hartmannstudios.psalmwell.data.FirebaseGate
import com.hartmannstudios.psalmwell.data.LocalStore
import com.hartmannstudios.psalmwell.model.BlockedUser
import com.hartmannstudios.psalmwell.model.JournalEntry
import com.hartmannstudios.psalmwell.model.Reflection
import com.hartmannstudios.psalmwell.model.SavedVerse
import com.hartmannstudios.psalmwell.moderation.SafetyClassifier
import com.hartmannstudios.psalmwell.reminder.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiMessage(val text: String, val isError: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val c = (application as PsalmwellApplication).container
    val communityAvailable: Boolean = FirebaseGate.available
    val googleSignInAvailable: Boolean get() = c.auth.googleClientId != null

    val verse = c.scripture.today()
    val psalmOfTheDay = c.scripture.psalmOfTheDay()
    fun psalm(chapter: Int) = c.scripture.psalm(chapter)
    fun psalmPreview(chapter: Int) = c.scripture.psalmPreview(chapter)

    val account: StateFlow<Account?> = c.auth.account.catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.auth.current())

    val blocked: StateFlow<List<BlockedUser>> = account.flatMapLatest { c.community.blocked(it?.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reflections: StateFlow<List<Reflection>> = combine(c.community.reflections(verse.dayKey), blocked) { list, blockedUsers ->
        val hidden = blockedUsers.map { it.uid }.toSet()
        list.filterNot { it.authorUid in hidden }
    }.catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rulesAccepted: StateFlow<Boolean> = account.flatMapLatest { if (it == null) flowOf(false) else c.store.rulesAccepted(it.uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val welcomeDone: StateFlow<Boolean?> = c.store.welcomeDone.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val saved: StateFlow<List<SavedVerse>> = c.store.saved.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val journal: StateFlow<List<JournalEntry>> = c.store.journal.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reminder: StateFlow<Pair<Boolean, Int>> = c.store.reminder.stateIn(viewModelScope, SharingStarted.Eagerly, false to 8)
    val readingScale: StateFlow<Int> = c.store.readingScale.stateIn(viewModelScope, SharingStarted.Eagerly, 100)

    val busy = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun finishWelcome() = viewModelScope.launch { c.store.setWelcomeDone() }

    fun toggleSaved(reference: String, text: String) = viewModelScope.launch {
        val wasSaved = saved.value.any { it.reference == reference }
        c.store.toggleSaved(reference, text)
        say(if (wasSaved) "Removed from saved verses" else "Saved to your verses")
    }

    fun saveJournal(id: String?, dayKey: String, reference: String, body: String) = viewModelScope.launch {
        c.store.saveJournal(id, dayKey, reference, body)
        say(if (body.isBlank()) "Journal entry removed" else "Saved to your private journal")
    }

    fun deleteJournal(id: String) = viewModelScope.launch { c.store.deleteJournal(id); say("Journal entry deleted") }

    fun setReadingScale(percent: Int) = viewModelScope.launch { c.store.setReadingScale(percent) }

    fun setReminder(enabled: Boolean, hour: Int) = viewModelScope.launch {
        c.store.setReminder(enabled, hour)
        ReminderScheduler.schedule(getApplication(), enabled, hour)
        say(if (enabled) "Daily reminder set" else "Daily reminder turned off")
    }

    fun signIn(email: String, password: String, onDone: () -> Unit) = task("Signed in", onDone) { c.auth.signIn(email, password) }
    fun register(email: String, password: String, name: String, onDone: () -> Unit) =
        task("Account created. Check your email to verify it.", onDone) { c.auth.register(email, password, name) }
    fun resetPassword(email: String) = task("Password reset email sent") { c.auth.resetPassword(email) }
    fun signInWithGoogle(activity: Activity, onDone: () -> Unit) = task("Signed in", onDone) { c.auth.signInWithGoogle(activity) }
    fun signOut() = task("Signed out") { c.auth.signOut() }
    fun updateName(name: String) = task("Display name updated") { c.auth.updateDisplayName(name) }

    fun acceptRules(onDone: () -> Unit) {
        val uid = account.value?.uid ?: return
        task(null, onDone) {
            c.auth.acceptCommunityRules(LocalStore.RULES_VERSION)
            c.store.acceptRules(uid)
        }
    }

    fun post(text: String, onResult: (String?) -> Unit) {
        val local = SafetyClassifier.check(text)
        if (!local.allowed) { onResult(local.explanation); return }
        viewModelScope.launch {
            busy.value = true
            try {
                c.community.post(verse.dayKey, verse.reference, text)
                say("Your reflection is posted")
                onResult(null)
            } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                onResult(readable(e))
            } finally { busy.value = false }
        }
    }

    fun deleteReflection(reflection: Reflection) = task("Reflection deleted") { c.community.deleteOwn(reflection) }

    fun reportReflection(reflection: Reflection, reason: String, details: String) =
        task("Thank you. Our moderators will review this reflection.") { c.community.reportReflection(reflection, reason, details) }

    fun reportUser(target: BlockedUser, reason: String, details: String, from: Reflection?) =
        task("Thank you. Our moderators will review this account.") { c.community.reportUser(target.uid, reason, details, from?.dayKey, from?.id) }

    fun block(target: BlockedUser) {
        val me = account.value?.uid ?: return
        task("${target.name} is blocked. You won't see their reflections.") { c.community.block(me, target) }
    }

    fun unblock(target: BlockedUser) {
        val me = account.value?.uid ?: return
        task("${target.name} is unblocked") { c.community.unblock(me, target.uid) }
    }

    fun deleteAccount() = task("Your account and reflections were deleted") {
        c.community.deleteAccountEverywhere()
        c.auth.signOut()
    }

    private fun task(success: String?, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
                if (success != null) say(success)
                onDone()
            } catch (e: CancellationException) { throw e } catch (e: GetCredentialCancellationException) {
                // The person closed the Google account picker; nothing to report.
            } catch (e: Throwable) {
                FirebaseGate.record(e)
                _messages.tryEmit(UiMessage(readable(e), true))
            } finally { busy.value = false }
        }
    }

    private fun say(text: String) { _messages.tryEmit(UiMessage(text)) }

    private fun readable(e: Throwable): String {
        if (e is FirebaseAuthException) return when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "That email and password don't match an account."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already uses that email. Try signing in."
            "ERROR_WEAK_PASSWORD" -> "Please choose a password with at least 6 characters."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please wait a moment and try again."
            "ERROR_USER_DISABLED" -> "This account has been disabled. Contact support if you think this is a mistake."
            else -> "Sign-in didn't work. Please try again."
        }
        val msg = e.message?.substringBefore(" [")?.trim()
        return if (msg.isNullOrBlank() || msg.length > 200) "Something went wrong. Please try again." else msg
    }
}
