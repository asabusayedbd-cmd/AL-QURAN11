package com.example.feature.library

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.database.FlashcardWordProjection
import com.example.core.di.AppContainer
import com.example.core.localization.BidiUtils
import com.example.core.localization.QuranScriptType
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.banglaSafeBodyStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class FlashcardDeckMode {
    LONG_AYAHS,
    SURAH,
    ESSENTIAL,
    BOOKMARKED
}

private data class LongAyahPreset(
    val surahId: Int,
    val verseKeys: List<String>,
    val label: String
)

private val LONG_AYAH_PRESETS = listOf(
    LongAyahPreset(2, listOf("2:255"), "2:255 Ayatul Kursi"),
    LongAyahPreset(2, listOf("2:282"), "2:282 Longest Ayah"),
    LongAyahPreset(2, listOf("2:286"), "2:286 Dua Closing"),
    LongAyahPreset(4, listOf("4:176"), "4:176 Al-Kalalah"),
    LongAyahPreset(24, listOf("24:35"), "24:35 Ayah an-Nur"),
    LongAyahPreset(33, listOf("33:35"), "33:35 Virtues"),
    LongAyahPreset(59, listOf("59:22", "59:23", "59:24"), "59:22-24 Asmaul Husna")
)

private val ESSENTIAL_VERSE_KEYS = listOf(
    "1:1", "1:2", "1:3", "1:4", "1:5", "1:6", "1:7",
    "2:2", "2:3", "112:1", "112:2", "112:3", "112:4"
)

private val POPULAR_SURAH_IDS = listOf(1, 36, 44, 55, 67, 78, 18, 2)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VocabularyFlashcardsSheet(
    uiLanguageCode: String = "en",
    scriptType: QuranScriptType = QuranScriptType.UTHMANI,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { AppContainer.getInstance(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var deckMode by remember { mutableStateOf(FlashcardDeckMode.LONG_AYAHS) }
    var selectedLongAyah by remember { mutableStateOf(LONG_AYAH_PRESETS.first()) }
    var selectedSurahId by remember { mutableIntStateOf(1) }
    var isShuffled by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var rawCards by remember { mutableStateOf<List<FlashcardWordProjection>>(emptyList()) }
    var deckCards by remember { mutableStateOf<List<FlashcardWordProjection>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showMeaning by remember { mutableStateOf(false) }
    var knownIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var reviewIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.forLanguageTag("ar")
                engine?.setPitch(0.84f)
                runCatching {
                    val arMale = engine?.voices?.firstOrNull { v ->
                        v.locale.language == "ar" && (
                            v.name.contains("male", ignoreCase = true) ||
                            !v.name.contains("female", ignoreCase = true)
                        )
                    }
                    if (arMale != null) engine?.voice = arMale
                }
                ttsReady = true
            }
        }
        engine
    }
    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(deckMode, selectedLongAyah, selectedSurahId) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            val quranDao = container.database.quranDao()
            when (deckMode) {
                FlashcardDeckMode.LONG_AYAHS -> {
                    container.seeder.ensureSurahLoadedFromAssets(selectedLongAyah.surahId)
                    if (selectedLongAyah.verseKeys.size == 1) {
                        quranDao.getFlashcardWordsForAyah(selectedLongAyah.verseKeys.first())
                    } else {
                        quranDao.getFlashcardWordsForVerseKeys(selectedLongAyah.verseKeys)
                    }
                }
                FlashcardDeckMode.SURAH -> {
                    container.seeder.ensureSurahLoadedFromAssets(selectedSurahId)
                    quranDao.getFlashcardWordsForSurah(selectedSurahId)
                }
                FlashcardDeckMode.ESSENTIAL -> {
                    container.seeder.ensureSurahLoadedFromAssets(1)
                    container.seeder.ensureSurahLoadedFromAssets(2)
                    container.seeder.ensureSurahLoadedFromAssets(112)
                    quranDao.getFlashcardWordsForVerseKeys(ESSENTIAL_VERSE_KEYS)
                }
                FlashcardDeckMode.BOOKMARKED -> {
                    val bookmarks = container.database.userLibraryDao().observeBookmarks().first()
                    if (bookmarks.isEmpty()) {
                        emptyList()
                    } else {
                        bookmarks.map { it.surahId }.distinct().forEach { sid ->
                            container.seeder.ensureSurahLoadedFromAssets(sid)
                        }
                        quranDao.getFlashcardWordsForVerseKeys(bookmarks.map { it.verseKey }.distinct())
                    }
                }
            }
        }
        rawCards = loaded
        deckCards = if (isShuffled) loaded.shuffled() else loaded
        currentIndex = 0
        showMeaning = false
        knownIds = emptySet()
        reviewIds = emptySet()
        isLoading = false
    }

    fun applyShuffle(shuffled: Boolean) {
        isShuffled = shuffled
        deckCards = if (shuffled) rawCards.shuffled() else rawCards
        currentIndex = 0
        showMeaning = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.flashcards_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { applyShuffle(!isShuffled) },
                    modifier = Modifier.testTag("flashcards_shuffle_button")
                ) {
                    Icon(
                        imageVector = if (isShuffled) Icons.Default.Shuffle else Icons.Default.Refresh,
                        contentDescription = "Toggle shuffle",
                        tint = if (isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Deck mode chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = deckMode == FlashcardDeckMode.LONG_AYAHS,
                        onClick = { deckMode = FlashcardDeckMode.LONG_AYAHS },
                        label = { Text(stringResource(R.string.flashcards_long_ayahs)) },
                        modifier = Modifier.testTag("flashcard_deck_long_ayahs")
                    )
                }
                item {
                    FilterChip(
                        selected = deckMode == FlashcardDeckMode.SURAH,
                        onClick = { deckMode = FlashcardDeckMode.SURAH },
                        label = { Text(stringResource(R.string.flashcards_surah_deck)) },
                        modifier = Modifier.testTag("flashcard_deck_surah")
                    )
                }
                item {
                    FilterChip(
                        selected = deckMode == FlashcardDeckMode.ESSENTIAL,
                        onClick = { deckMode = FlashcardDeckMode.ESSENTIAL },
                        label = { Text(stringResource(R.string.flashcards_essential_deck)) },
                        modifier = Modifier.testTag("flashcard_deck_essential")
                    )
                }
                item {
                    FilterChip(
                        selected = deckMode == FlashcardDeckMode.BOOKMARKED,
                        onClick = { deckMode = FlashcardDeckMode.BOOKMARKED },
                        label = { Text(stringResource(R.string.flashcards_bookmarks_deck)) },
                        modifier = Modifier.testTag("flashcard_deck_bookmarks")
                    )
                }
            }

            // Sub-deck selector
            if (deckMode == FlashcardDeckMode.LONG_AYAHS) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LONG_AYAH_PRESETS, key = { it.label }) { preset ->
                        FilterChip(
                            selected = selectedLongAyah == preset,
                            onClick = { selectedLongAyah = preset },
                            label = { Text(preset.label) }
                        )
                    }
                }
            } else if (deckMode == FlashcardDeckMode.SURAH) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(POPULAR_SURAH_IDS, key = { it }) { sid ->
                        FilterChip(
                            selected = selectedSurahId == sid,
                            onClick = { selectedSurahId = sid },
                            label = { Text("Surah $sid") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = { selectedSurahId = if (selectedSurahId < 114) selectedSurahId + 1 else 1 },
                            label = { Text("Next Surah (${selectedSurahId}/114)") }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (deckCards.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (deckMode == FlashcardDeckMode.BOOKMARKED) {
                                "No bookmarked Ayahs yet. Bookmark any Ayah in the Reader to build a personal flashcard deck."
                            } else {
                                "No vocabulary cards available offline for this selection yet."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val safeIndex = currentIndex.coerceIn(0, deckCards.lastIndex)
                val card = deckCards[safeIndex]
                val displayArabic = if (scriptType == QuranScriptType.INDOPAK && card.textIndopak.isNotBlank()) {
                    card.textIndopak
                } else {
                    card.textUthmani
                }
                val primaryMeaning = when {
                    uiLanguageCode.equals("bn", true) && card.meaningBn.isNotBlank() -> card.meaningBn
                    card.meaningEn.isNotBlank() -> card.meaningEn
                    card.meaningBn.isNotBlank() -> card.meaningBn
                    else -> card.transliteration
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Word ${safeIndex + 1} / ${deckCards.size} • ${BidiUtils.wrapVerseKey(card.verseKey)} (#${card.position})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Known ${knownIds.size} • Review ${reviewIds.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                LinearProgressIndicator(
                    progress = { (safeIndex + 1).toFloat() / deckCards.size.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick quarter-jump chips for long Ayahs (>25 words) so users can inspect every section smoothly
                if (deckCards.size >= 25) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0 to "Start", deckCards.size / 4 to "25%", deckCards.size / 2 to "50%", (deckCards.size * 3) / 4 to "75%").forEach { (idx, label) ->
                            FilterChip(
                                selected = safeIndex == idx,
                                onClick = {
                                    currentIndex = idx.coerceIn(0, deckCards.lastIndex)
                                    showMeaning = false
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                ElevatedCard(
                    onClick = { showMeaning = !showMeaning },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 210.dp, max = 320.dp)
                        .testTag("flashcard_surface")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${BidiUtils.wrapVerseKey(card.verseKey)} • Pos ${card.position}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (ttsReady) {
                                        tts.setPitch(0.84f)
                                        tts.setSpeechRate(0.85f)
                                        tts.speak(card.textUthmani, TextToSpeech.QUEUE_FLUSH, null, "fc_${card.wordId}")
                                    }
                                },
                                modifier = Modifier.testTag("flashcard_speak_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = stringResource(R.string.play_word_audio),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = displayArabic,
                            fontFamily = arabicScriptFont(scriptType),
                            fontSize = 40.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        if (!showMeaning) {
                            Text(
                                text = "Tap card to reveal Bangla/English meaning & root",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = primaryMeaning,
                                style = banglaSafeBodyStyle(20f, uiLanguageCode),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            if (card.meaningBn.isNotBlank() && card.meaningEn.isNotBlank() && card.meaningBn != primaryMeaning) {
                                Text(
                                    text = "বাংলা: ${card.meaningBn}",
                                    style = banglaSafeBodyStyle(15f, "bn"),
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center
                                )
                            } else if (card.meaningEn.isNotBlank() && card.meaningEn != primaryMeaning) {
                                Text(
                                    text = "English: ${card.meaningEn}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (card.transliteration.isNotBlank()) {
                                Text(
                                    text = card.transliteration,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (card.rootArabic.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "Root: ${card.rootArabic}",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (card.grammarTagJson.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = card.grammarTagJson,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Grading + Prev/Next controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            reviewIds = reviewIds + card.wordId
                            knownIds = knownIds - card.wordId
                            if (safeIndex < deckCards.lastIndex) {
                                currentIndex = safeIndex + 1
                                showMeaning = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Review Again", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = {
                            knownIds = knownIds + card.wordId
                            reviewIds = reviewIds - card.wordId
                            if (safeIndex < deckCards.lastIndex) {
                                currentIndex = safeIndex + 1
                                showMeaning = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Got It", maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (safeIndex > 0) {
                                currentIndex = safeIndex - 1
                                showMeaning = false
                            }
                        },
                        enabled = safeIndex > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Previous")
                    }
                    Button(
                        onClick = {
                            if (safeIndex < deckCards.lastIndex) {
                                currentIndex = safeIndex + 1
                                showMeaning = false
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (safeIndex < deckCards.lastIndex) "Next" else "Finish")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
