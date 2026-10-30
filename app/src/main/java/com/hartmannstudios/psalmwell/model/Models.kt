package com.hartmannstudios.psalmwell.model

/** A verse shown on the Today screen. The same verse is shared by everyone for a UTC calendar day. */
data class DailyVerse(
    val dayKey: String,
    val reference: String,
    val text: String,
)

data class PsalmVerse(val chapter: Int, val verse: Int, val text: String) {
    val reference: String get() = "Psalm $chapter:$verse"
}

/** A verse the person saved on this device. */
data class SavedVerse(val reference: String, val text: String, val savedAt: Long)

/** A private journal note, kept on this device only. */
data class JournalEntry(val id: String, val dayKey: String, val reference: String, val body: String, val updatedAt: Long)

/** A public reflection on the verse of the day. */
data class Reflection(
    val id: String,
    val dayKey: String,
    val authorUid: String,
    val authorName: String,
    val body: String,
    val createdAtMillis: Long?,
)

data class BlockedUser(val uid: String, val name: String)

data class ModerationResult(
    val allowed: Boolean,
    val categories: Set<String> = emptySet(),
    val explanation: String = "",
)

enum class ReportReason(val code: String, val label: String) {
    HARASSMENT("harassment", "Harassment or bullying"),
    HATE("hate", "Hate speech"),
    THREAT("threat", "Threats or violence"),
    SEXUAL("sexual", "Sexual content"),
    CHILD_SAFETY("child_safety", "Child safety concern"),
    SPAM("spam", "Spam or scam"),
    SELF_HARM("self_harm", "Self-harm"),
    OTHER("other", "Something else"),
}
