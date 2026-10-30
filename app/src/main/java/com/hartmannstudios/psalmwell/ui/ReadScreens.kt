package com.hartmannstudios.psalmwell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hartmannstudios.psalmwell.i18n.RawText
import com.hartmannstudios.psalmwell.i18n.T
import com.hartmannstudios.psalmwell.i18n.Text
import com.hartmannstudios.psalmwell.i18n.isTranslated
import com.hartmannstudios.psalmwell.model.DailyVerse
import com.hartmannstudios.psalmwell.model.PsalmVerse
import com.hartmannstudios.psalmwell.ui.theme.Amber
import com.hartmannstudios.psalmwell.ui.theme.ScriptureFont
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    verse: DailyVerse,
    saved: Boolean,
    psalmOfTheDay: Int,
    psalmPreview: String,
    reflectionCount: Int,
    padding: PaddingValues,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onJournal: () -> Unit,
    onReflect: () -> Unit,
    onOpenPsalm: (Int) -> Unit,
) {
    val top = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .8f), MaterialTheme.colorScheme.background))
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) }
    LazyColumn(
        Modifier.fillMaxSize().background(top).testTag("screen_today"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 20.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("Psalmwell", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            RawText(today, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VERSE OF THE DAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, letterSpacing = 2.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(verse.text, fontFamily = ScriptureFont, fontSize = 24.sp, lineHeight = 34.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("verse_text"))
                    Spacer(Modifier.height(18.dp))
                    Text(verse.reference, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(if (isTranslated()) "World English Bible · translated on your phone by Google ML Kit" else "World English Bible", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        VerseAction(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (saved) "Saved" else "Save", "action_save", onSave)
                        VerseAction(Icons.Default.Share, "Share", "action_share", onShare)
                        VerseAction(Icons.Default.EditNote, "Journal", "action_journal", onJournal)
                        VerseAction(Icons.Default.ChatBubbleOutline, "Reflect", "action_reflect", onReflect)
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().clickable { onOpenPsalm(psalmOfTheDay) }.testTag("psalm_of_day"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .7f)),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Psalm of the day · Psalm $psalmOfTheDay", fontWeight = FontWeight.SemiBold)
                        Text(psalmPreview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontFamily = ScriptureFont)
                    }
                    TextButton(onClick = { onOpenPsalm(psalmOfTheDay) }) { Text("Read") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onReflect), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        if (reflectionCount == 0) "No reflections yet today. Read what others share, or add your own."
                        else "$reflectionCount ${if (reflectionCount == 1) "reflection" else "reflections"} on today's verse",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Text("A new verse arrives for everyone at midnight UTC. Scripture text: World English Bible, public domain.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun VerseAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit) {
    val tl = T(label)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 64.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.testTag(tag).semantics { contentDescription = tl }) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PsalmsScreen(padding: PaddingValues, preview: (Int) -> String, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val numbers = remember(query) {
        val n = query.trim().toIntOrNull()
        if (query.isBlank()) (1..150).toList() else (1..150).filter { it == n || it.toString().startsWith(query.trim()) }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("screen_psalms"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 20.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("The Psalms", "All 150 Psalms from the World English Bible. Read offline, anytime.") }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it.filter(Char::isDigit).take(3) },
                leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Go to Psalm number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("psalm_search"),
            )
        }
        if (numbers.isEmpty()) item { Text("There are 150 Psalms. Try a number from 1 to 150.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(numbers, key = { it }) { n ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(n) }.testTag("psalm_row_$n"), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(56.dp)) { Text("$n", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
                    Text(preview(n), maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = ScriptureFont, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PsalmReader(
    chapter: Int,
    verses: List<PsalmVerse>,
    savedRefs: Set<String>,
    scale: Int,
    onScale: (Int) -> Unit,
    onBack: () -> Unit,
    onNavigate: (Int) -> Unit,
    onToggleSave: (PsalmVerse) -> Unit,
    onShare: (PsalmVerse) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(chapter) { listState.scrollToItem(0) }
    var selected by remember(chapter) { mutableStateOf<Int?>(null) }
    val fontSize = (19 * scale / 100f).sp
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("screen_reader")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, T("Back")) }
            Text("Psalm $chapter", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { onScale(scale - 10) }, enabled = scale > 80) { Icon(Icons.Default.Remove, T("Smaller text")) }
            IconButton(onClick = { onScale(scale + 10) }, enabled = scale < 160) { Icon(Icons.Default.Add, T("Larger text")) }
        }
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (verses.isEmpty()) item { Text("This Psalm couldn't be loaded.", color = MaterialTheme.colorScheme.error) }
            items(verses, key = { it.verse }) { v ->
                val isSaved = v.reference in savedRefs
                Column(Modifier.fillMaxWidth().clickable { selected = if (selected == v.verse) null else v.verse }.padding(vertical = 2.dp)) {
                    Row {
                        Text("${v.verse}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp).padding(top = 4.dp))
                        Text(v.text, fontFamily = ScriptureFont, fontSize = fontSize, lineHeight = fontSize * 1.45f,
                            color = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    }
                    if (selected == v.verse) Row(Modifier.padding(start = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onToggleSave(v) }) { Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null); Spacer(Modifier.width(6.dp)); Text(if (isSaved) "Saved" else "Save") }
                        OutlinedButton(onClick = { onShare(v) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share") }
                    }
                }
            }
            item {
                Text(if (isTranslated()) "Tap a verse to save or share it. World English Bible (public domain), machine-translated on your phone by Google ML Kit; the English text is the original." else "Tap a verse to save or share it. World English Bible (public domain).", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { onNavigate(chapter - 1) }, enabled = chapter > 1, modifier = Modifier.testTag("reader_prev")) { Text("Previous") }
            OutlinedButton(onClick = { onNavigate(chapter + 1) }, enabled = chapter < 150, modifier = Modifier.testTag("reader_next")) { Text("Next") }
        }
    }
}
