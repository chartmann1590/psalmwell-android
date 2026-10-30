package com.hartmannstudios.psalmwell.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hartmannstudios.psalmwell.BuildConfig
import com.hartmannstudios.psalmwell.i18n.RawText
import com.hartmannstudios.psalmwell.i18n.Text
import com.hartmannstudios.psalmwell.i18n.Translator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Links {
    val site: String get() = BuildConfig.SITE_URL.trimEnd('/')
    val privacy get() = "$site/privacy"
    val rules get() = "$site/community-rules"
    val childSafety get() = "$site/child-safety"
    val support get() = "$site/support"
    val deleteAccount get() = "$site/delete-account"
}

fun Context.openUrl(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }

fun Context.emailSupport(subject: String): Boolean = try {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + BuildConfig.SUPPORT_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent); true
} catch (e: ActivityNotFoundException) { openUrl(Links.support) }

/** "Verse" — Reference (WEB), in the reader's language when a translation is on the phone. */
fun verseShareText(text: String, reference: String): String {
    val t = Translator.instance
    return "\u201C${t?.trNow(text) ?: text}\u201D \u2014 ${t?.trNow(reference) ?: reference} (WEB)"
}

fun Context.shareText(text: String) {
    try {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), Translator.instance?.trNow("Share verse") ?: "Share verse")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) { }
}

fun formatMillis(millis: Long?): String = if (millis == null) "Just now" else runCatching {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}.getOrDefault("")

fun formatHour(hour: Int): String = when {
    hour == 0 -> "12:00 AM"
    hour < 12 -> "$hour:00 AM"
    hour == 12 -> "12:00 PM"
    else -> "${hour - 12}:00 PM"
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoCard(icon: ImageVector, text: String, button: String? = null, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            if (button != null) Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
fun Initial(name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            RawText(name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun FeatureRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
