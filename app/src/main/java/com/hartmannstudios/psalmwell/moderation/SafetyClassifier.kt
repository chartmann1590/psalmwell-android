package com.hartmannstudios.psalmwell.moderation

import com.hartmannstudios.psalmwell.model.ModerationResult
import java.text.Normalizer

/**
 * First line of moderation, run on the device before anything is sent. The Psalmwell API repeats
 * a stricter check on the server, and people can report anything that gets through.
 */
object SafetyClassifier {
    const val MAX_LENGTH = 500

    fun check(raw: String): ModerationResult {
        val text = raw.trim()
        if (text.isEmpty()) return ModerationResult(false, setOf("empty"), "Write something before posting.")
        if (text.length > MAX_LENGTH) return ModerationResult(false, setOf("length"), "Please keep reflections under $MAX_LENGTH characters.")
        if (LINK.containsMatchIn(text)) return ModerationResult(false, setOf("link"), "Links aren't allowed in reflections. Please remove the link.")
        if (CONTACT.containsMatchIn(text)) return ModerationResult(false, setOf("personal information"), "For everyone's safety, please don't share phone numbers or email addresses.")
        val normalized = normalize(text)
        val compact = normalized.replace(Regex("[^a-z]"), "")
        val spaced = " " + normalized.replace(Regex("[^a-z']+"), " ").trim() + " "
        val found = linkedSetOf<String>()
        if (COMPACT_TERMS.any { compact.contains(it) }) found += "strong profanity"
        TERMS.forEach { (category, words) ->
            if (words.any { w -> spaced.contains(" $w ") }) found += category
        }
        return if (found.isEmpty()) ModerationResult(true)
        else ModerationResult(false, found, "Please rephrase this reflection. It may include ${found.joinToString()}, which isn't allowed by the community rules.")
    }

    internal fun normalize(input: String): String = Normalizer.normalize(input, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace('0', 'o').replace('1', 'i').replace('3', 'e').replace('4', 'a').replace('5', 's').replace('7', 't').replace('@', 'a').replace('$', 's')
        .replace(Regex("(.)\\1{2,}"), "$1")

    private val LINK = Regex("(https?://|www\\.|\\b[a-z0-9-]+\\.(com|net|org|io|ly|me|xyz|info|app|link|click|ru|tk)\\b)", RegexOption.IGNORE_CASE)
    private val CONTACT = Regex("([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})|(\\+?\\d[\\d\\s().-]{8,}\\d)", RegexOption.IGNORE_CASE)

    private val COMPACT_TERMS = listOf("fuck", "motherfucker", "nigger", "faggot")

    private val TERMS: Map<String, List<String>> = mapOf(
        "strong profanity" to listOf("fuck", "fucking", "fucker", "motherfucker", "shit", "bullshit", "bitch", "cunt", "asshole", "bastard", "dickhead", "piss off"),
        "sexual content" to listOf("porn", "porno", "nude", "nudes", "naked pics", "sex chat", "sexting", "onlyfans", "horny", "blowjob", "dick pic", "send pics", "hookup"),
        "harassment" to listOf("idiot", "moron", "retard", "loser", "worthless", "kill yourself", "kys", "shut up", "nobody likes you", "you are stupid", "you're stupid", "go die"),
        "threats" to listOf("i will kill", "i'll kill you", "im going to kill you", "shoot you", "stab you", "beat you up", "find where you live"),
        "hate speech" to listOf("subhuman", "vermin", "inferior race", "go back to your country", "nigger", "nigga", "faggot", "fag", "kike", "spic", "chink", "tranny"),
        "child safety" to listOf("child porn", "cp links", "minor nudes", "pedo", "pedophile"),
        "spam" to listOf("buy now", "free money", "crypto giveaway", "click here", "dm me", "whatsapp me", "telegram me", "earn money fast", "promo code"),
        "self-harm" to listOf("i want to kill myself", "how to kill myself", "end my life", "suicide method"),
    )
}
