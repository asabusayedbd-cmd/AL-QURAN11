package com.example.feature.tafsir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.database.AyahEntity
import com.example.core.database.AyahTafsirEntity
import com.example.core.database.AyahTranslationEntity
import com.example.core.database.TafsirSourceEntity
import com.example.ui.theme.banglaSafeBodyStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TafsirSheet(
    ayah: AyahEntity,
    surahName: String,
    translations: List<AyahTranslationEntity>,
    tafsirSources: List<TafsirSourceEntity>,
    loadedTafsirs: List<AyahTafsirEntity>,
    selectedTafsirSourceId: Int,
    existingNoteText: String,
    isFetchingOnlineTafsir: Boolean,
    tafsirError: String? = null,
    translationFontSizeSp: Float,
    onSelectTafsirSource: (TafsirSourceEntity) -> Unit,
    onSaveNote: (String) -> Unit,
    onShareAsCard: () -> Unit,
    onDismiss: () -> Unit
) {
    var noteDraft by remember(ayah.verseKey, existingNoteText) { mutableStateOf(existingNoteText) }
    var langFilter by remember { mutableStateOf("ALL") }

    val availableLangs = remember(tafsirSources) {
        listOf("ALL") + tafsirSources.map { it.languageCode.uppercase() }.distinct()
    }
    val filteredSources = remember(tafsirSources, langFilter, selectedTafsirSourceId) {
        val base = if (langFilter == "ALL") {
            tafsirSources
        } else {
            tafsirSources.filter { it.languageCode.equals(langFilter, ignoreCase = true) }
        }
        base.sortedByDescending { it.id == selectedTafsirSourceId }
    }

    val activeSource = tafsirSources.firstOrNull { it.id == selectedTafsirSourceId } ?: tafsirSources.firstOrNull()
    val activeTafsir = loadedTafsirs.firstOrNull { it.sourceId == selectedTafsirSourceId }

    LaunchedEffect(selectedTafsirSourceId, ayah.id) {
        if (activeTafsir == null && activeSource != null) {
            onSelectTafsirSource(activeSource)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tafsir_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "$surahName • ${ayah.verseKey} • ${tafsirSources.size} Verified Tafsirs",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedButton(
                    onClick = onShareAsCard,
                    modifier = Modifier.testTag("tafsir_share_card_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.share_as_card))
                }
            }

            // Language filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(availableLangs) { lang ->
                    FilterChip(
                        selected = langFilter == lang,
                        onClick = { langFilter = lang },
                        label = {
                            val label = when (lang) {
                                "ALL" -> "All (${tafsirSources.size})"
                                "BN" -> "বাংলা"
                                "EN" -> "English"
                                "AR" -> "العربية"
                                "UR" -> "اردو"
                                "RU" -> "Русский"
                                else -> lang
                            }
                            Text(label)
                        }
                    )
                }
            }

            // Scrollable Switchable Tafsir sources (supports all 18+ classical & contemporary Tafsirs)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredSources, key = { it.id }) { source ->
                    val isCached = loadedTafsirs.any { it.sourceId == source.id }
                    FilterChip(
                        selected = source.id == selectedTafsirSourceId,
                        onClick = { onSelectTafsirSource(source) },
                        leadingIcon = if (isCached) {
                            { Icon(Icons.Default.CheckCircle, contentDescription = "Cached offline", modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text("${source.tafsirName} (${source.languageCode.uppercase()})") },
                        modifier = Modifier.testTag("tafsir_source_chip_${source.id}")
                    )
                }
            }

            // Verified Tafsir body (proper RTL alignment for Arabic / Urdu Tafsirs)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isFetchingOnlineTafsir) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading verified Tafsir from Quran Foundation API…", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (activeTafsir != null) {
                        Text(
                            text = "${activeTafsir.tafsirName} (${activeTafsir.languageCode.uppercase()})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val cleanBody = activeTafsir.tafsirHtmlOrMarkdown
                            .replace(Regex("<[^>]+>"), "\n")
                            .replace("&quot;", "\"")
                            .replace("&#39;", "'")
                            .replace("&rsquo;", "’")
                            .replace("&nbsp;", " ")
                            .replace("&amp;", "&")
                            .replace(Regex("\n{2,}"), "\n\n")
                            .trim()
                        val isRtlTafsir = activeTafsir.languageCode.lowercase() in setOf("ar", "ur")
                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (isRtlTafsir) LayoutDirection.Rtl else LayoutDirection.Ltr
                        ) {
                            Text(
                                text = cleanBody,
                                style = banglaSafeBodyStyle(translationFontSizeSp, activeTafsir.languageCode),
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        if (!tafsirError.isNullOrBlank()) {
                            Text(
                                text = "Could not load online Tafsir: $tafsirError",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "This Tafsir entry is not yet cached locally for ${ayah.verseKey}. Tap below to fetch from the verified Quran Foundation repository.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (activeSource != null) {
                            FilledTonalButton(onClick = { onSelectTafsirSource(activeSource) }) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (!tafsirError.isNullOrBlank()) "Retry ${activeSource.tafsirName}" else "Fetch ${activeSource.tafsirName}")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Personal Note / Reflection editor
            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                label = { Text(stringResource(R.string.personal_note_hint)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tafsir_note_input")
            )

            Button(
                onClick = {
                    if (noteDraft.isNotBlank()) {
                        onSaveNote(noteDraft)
                    }
                },
                enabled = noteDraft.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_note_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.save_note))
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
