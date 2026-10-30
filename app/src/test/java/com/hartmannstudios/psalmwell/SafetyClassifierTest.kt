package com.hartmannstudios.psalmwell

import com.hartmannstudios.psalmwell.moderation.SafetyClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyClassifierTest {
    @Test fun kindReflectionsPass() {
        listOf(
            "This verse reminds me that I am never alone, even on hard days.",
            "Praying for everyone who needs strength today.",
            "Like a bridegroom coming out of his room, the sun rises. Beautiful image.",
            "I'm closer to peace than I was last year.",
            "God will not hurt you; he holds you.",
        ).forEach { assertTrue(it, SafetyClassifier.check(it).allowed) }
    }

    @Test fun abuseIsBlocked() {
        listOf(
            "you are an idiot",
            "f u c k this",
            "fvck".replace('v', 'u') + " off",
            "kill yourself",
            "buy now at cheap-pills.com",
            "dm me on telegram me @ +1 555 123 4567",
            "send pics",
            "",
            "a".repeat(SafetyClassifier.MAX_LENGTH + 1),
        ).forEach { assertFalse(it, SafetyClassifier.check(it).allowed) }
    }

    @Test fun obfuscationIsNormalized() {
        assertFalse(SafetyClassifier.check("sh1t").allowed)
        assertFalse(SafetyClassifier.check("FUUUUCK").allowed)
    }
}
