package com.example.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.arabicScriptFont

private data class FlashcardItem(val arabic: String, val transliteration: String, val meaning: String, val root: String)

private val FLASHCARDS = listOf(
    FlashcardItem("اللَّه", "Allāh", "God / Allah", "أ ل ه"),
    FlashcardItem("الرَّحْمَـٰن", "Ar-Rahmān", "The Most Gracious", "ر ح م"),
    FlashcardItem("الرَّحِيم", "Ar-Rahīm", "The Most Merciful", "ر ح م"),
    FlashcardItem("الْعَالَمِين", "Al-'Ālamīn", "The Worlds / Universes", "ع ل م"),
    FlashcardItem("اهدِنَا", "Ihdinā", "Guide us", "ه د ي"),
    FlashcardItem("الصِّرَاط", "As-Sirāt", "The Path", "ص ر ط"),
    FlashcardItem("الْمُتَّقِين", "Al-Muttaqīn", "The Righteous / God-conscious", "و ق ي"),
    FlashcardItem("الصَّلَاة", "As-Salāt", "The Prayer", "ص ل و"),
    FlashcardItem("الْغَيْب", "Al-Ghaib", "The Unseen", "غ ي ب"),
    FlashcardItem("الْفَلَاح", "Al-Falāh", "Success / Prosperity", "ف ل ح")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyFlashcardsSheet(
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var showMeaning by remember { mutableStateOf(false) }

    val card = FLASHCARDS[currentIndex]

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Quranic Vocabulary Flashcards (${currentIndex + 1}/${FLASHCARDS.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            ElevatedCard(
                onClick = { showMeaning = !showMeaning },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!showMeaning) {
                        Text(
                            text = card.arabic,
                            fontFamily = arabicScriptFont(com.example.core.localization.QuranScriptType.UTHMANI),
                            fontSize = 42.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap to reveal meaning & root",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = card.meaning,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Transliteration: ${card.transliteration}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Root Letters: ${card.root}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (currentIndex > 0) {
                            currentIndex--
                            showMeaning = false
                        }
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Previous")
                }
                Button(
                    onClick = {
                        if (currentIndex < FLASHCARDS.size - 1) {
                            currentIndex++
                            showMeaning = false
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (currentIndex < FLASHCARDS.size - 1) "Next" else "Finish")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
