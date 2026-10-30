package com.hartmannstudios.psalmwell.data

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

data class Account(val uid: String, val email: String?, val displayName: String?)

class AuthRepository(private val context: Context) {
    private val auth: FirebaseAuth? get() = if (FirebaseGate.available) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val credentials by lazy { CredentialManager.create(context) }

    val googleClientId: String? by lazy {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id == 0) null else runCatching { context.getString(id) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    val account: Flow<Account?> get() {
        val a = auth ?: return flowOf(null)
        return callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
            a.addAuthStateListener(listener)
            trySend(a.currentUser?.toAccount())
            awaitClose { a.removeAuthStateListener(listener) }
        }
    }

    fun current(): Account? = auth?.currentUser?.toAccount()

    suspend fun signIn(email: String, password: String) {
        val a = requireAuth()
        a.signInWithEmailAndPassword(email.trim(), password).await()
        ensureProfile(null)
    }

    suspend fun register(email: String, password: String, displayName: String) {
        val a = requireAuth()
        val result = a.createUserWithEmailAndPassword(email.trim(), password).await()
        runCatching { result.user?.sendEmailVerification()?.await() }
        ensureProfile(displayName)
    }

    suspend fun resetPassword(email: String) {
        requireAuth().sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signInWithGoogle(activity: Activity) {
        val clientId = googleClientId ?: error("Google sign-in is not available. Please use email instead.")
        val a = requireAuth()
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = credentials.getCredential(activity, request)
        val google = GoogleIdTokenCredential.createFrom(result.credential.data)
        a.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        ensureProfile(null)
    }

    suspend fun updateDisplayName(name: String) {
        val user = requireUser()
        val clean = cleanName(name) ?: error("Please enter a name between 1 and 40 characters.")
        db.batch()
            .set(db.collection("users").document(user.uid), mapOf("displayName" to clean, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .set(db.collection("publicProfiles").document(user.uid), mapOf("uid" to user.uid, "displayName" to clean))
            .commit().await()
    }

    suspend fun acceptCommunityRules(version: Int) {
        val user = requireUser()
        db.collection("users").document(user.uid)
            .set(mapOf("rulesAcceptedVersion" to version, "rulesAcceptedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }

    suspend fun idToken(): String = requireUser().getIdToken(false).await().token ?: error("Please sign in again.")

    suspend fun signOut() {
        auth?.signOut()
        runCatching { credentials.clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Deletes the private profile, blocked list, public profile and finally the sign-in account itself. */
    suspend fun deletePrivateDataAndAccount() {
        val user = requireUser()
        val userDoc = db.collection("users").document(user.uid)
        while (true) {
            val page = userDoc.collection("blocked").limit(400).get().await()
            if (page.isEmpty) break
            val batch = db.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        userDoc.delete().await()
        db.collection("publicProfiles").document(user.uid).delete().await()
        user.delete().await()
        runCatching { credentials.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun ensureProfile(nameOverride: String?) {
        val user = requireUser()
        val name = cleanName(nameOverride) ?: cleanName(user.displayName) ?: cleanName(user.email?.substringBefore('@')) ?: "Friend"
        val existing = runCatching { db.collection("publicProfiles").document(user.uid).get().await() }.getOrNull()
        val publicName = if (nameOverride == null) existing?.getString("displayName") ?: name else name
        db.batch()
            .set(db.collection("users").document(user.uid), mapOf("uid" to user.uid, "displayName" to publicName, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .set(db.collection("publicProfiles").document(user.uid), mapOf("uid" to user.uid, "displayName" to publicName))
            .commit().await()
    }

    private fun requireAuth(): FirebaseAuth = auth ?: error("Accounts are unavailable right now. Please try again later.")
    private fun requireUser(): FirebaseUser = auth?.currentUser ?: error("Please sign in first.")

    private fun FirebaseUser.toAccount() = Account(uid, email, displayName)

    companion object {
        fun cleanName(raw: String?): String? = raw?.replace(Regex("\\s+"), " ")?.trim()?.take(40)?.takeIf { it.isNotEmpty() }
    }
}
