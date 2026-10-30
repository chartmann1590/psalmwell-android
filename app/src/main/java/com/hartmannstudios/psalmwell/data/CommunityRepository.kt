package com.hartmannstudios.psalmwell.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hartmannstudios.psalmwell.BuildConfig
import com.hartmannstudios.psalmwell.model.BlockedUser
import com.hartmannstudios.psalmwell.model.Reflection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class CommunityException(message: String) : Exception(message)

/**
 * Public reflections live in Firestore and are written only by the Psalmwell API, which re-checks
 * every post on the server. Reads never throw into the UI: failures become an empty list.
 */
class CommunityRepository(private val auth: AuthRepository) {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun reflections(dayKey: String): Flow<List<Reflection>> {
        if (!FirebaseGate.available) return flowOf(emptyList())
        return callbackFlow {
            val registration = db.collection("days").document(dayKey).collection("reflections")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { value, error ->
                    if (error != null) {
                        FirebaseGate.log("reflections listener: ${error.code}")
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    trySend(value?.documents.orEmpty().mapNotNull { doc ->
                        if (doc.getString("status") != "visible") return@mapNotNull null
                        val uid = doc.getString("authorUid") ?: return@mapNotNull null
                        val body = doc.getString("body") ?: return@mapNotNull null
                        Reflection(
                            id = doc.id,
                            dayKey = dayKey,
                            authorUid = uid,
                            authorName = doc.getString("authorName")?.takeIf { it.isNotBlank() } ?: "Friend",
                            body = body,
                            createdAtMillis = runCatching { doc.getTimestamp("createdAt")?.toDate()?.time }.getOrNull(),
                        )
                    })
                }
            awaitClose { registration.remove() }
        }.catch { emit(emptyList()) }
    }

    fun blocked(uid: String?): Flow<List<BlockedUser>> {
        if (!FirebaseGate.available || uid == null) return flowOf(emptyList())
        return callbackFlow {
            val registration = db.collection("users").document(uid).collection("blocked")
                .addSnapshotListener { value, error ->
                    if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                    trySend(value?.documents.orEmpty().map { BlockedUser(it.id, it.getString("name") ?: "Blocked person") })
                }
            awaitClose { registration.remove() }
        }.catch { emit(emptyList()) }
    }

    suspend fun block(myUid: String, target: BlockedUser) {
        db.collection("users").document(myUid).collection("blocked").document(target.uid)
            .set(mapOf("name" to target.name.take(40), "blockedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun unblock(myUid: String, targetUid: String) {
        db.collection("users").document(myUid).collection("blocked").document(targetUid).delete().await()
    }

    suspend fun deleteOwn(reflection: Reflection) {
        db.collection("days").document(reflection.dayKey).collection("reflections").document(reflection.id).delete().await()
    }

    suspend fun post(dayKey: String, reference: String, body: String): String {
        val response = call("POST", "/v1/reflections", JSONObject().put("dayKey", dayKey).put("reference", reference).put("body", body.trim()))
        return response.optString("id")
    }

    suspend fun reportReflection(reflection: Reflection, reason: String, details: String) {
        call("POST", "/v1/reports", JSONObject()
            .put("type", "reflection").put("dayKey", reflection.dayKey).put("reflectionId", reflection.id)
            .put("targetUid", reflection.authorUid).put("reason", reason).put("details", details.take(500)))
    }

    suspend fun reportUser(targetUid: String, reason: String, details: String, dayKey: String?, reflectionId: String?) {
        call("POST", "/v1/reports", JSONObject()
            .put("type", "user").put("targetUid", targetUid).put("reason", reason).put("details", details.take(500))
            .put("dayKey", dayKey ?: JSONObject.NULL).put("reflectionId", reflectionId ?: JSONObject.NULL))
    }

    /** Removes the account on the server: reflections, profile, blocked list and the sign-in account. */
    suspend fun deleteAccountEverywhere() {
        call("DELETE", "/v1/account", null)
    }

    private suspend fun call(method: String, path: String, body: JSONObject?): JSONObject {
        if (!FirebaseGate.available) throw CommunityException("Community features are unavailable right now.")
        val token = auth.idToken()
        return withContext(Dispatchers.IO) {
            val connection = (URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = method
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-Client", "psalmwell-android/${BuildConfig.VERSION_NAME}")
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                if (body != null) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (code !in 200..299) throw CommunityException(json.optString("error").ifBlank { "Something went wrong (code $code). Please try again." })
                json
            } catch (e: IOException) {
                throw CommunityException("You appear to be offline. Please check your connection and try again.")
            } finally {
                connection.disconnect()
            }
        }
    }
}
