package com.example.feature.library

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.localization.BidiUtils
import com.example.ui.theme.banglaSafeBodyStyle
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenReader: (surahId: Int, ayahNumber: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedCollectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.collections_title)) },
                    modifier = Modifier.testTag("library_tab_bookmarks")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.library_notes_tab_fmt, state.notes.size)) },
                    modifier = Modifier.testTag("library_tab_notes")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.khatam_plan_title)) },
                    modifier = Modifier.testTag("library_tab_khatam")
                )
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCollectionId == null,
                                        onClick = { selectedCollectionId = null },
                                        label = { Text(stringResource(R.string.library_all_fmt, state.bookmarks.size)) }
                                    )
                                }
                                items(state.collections, key = { it.id }) { col ->
                                    FilterChip(
                                        selected = selectedCollectionId == col.id,
                                        onClick = { selectedCollectionId = col.id },
                                        label = { Text(col.name) }
                                    )
                                }
                                item {
                                    FilledTonalButton(
                                        onClick = { showNewFolderDialog = true },
                                        modifier = Modifier.testTag("create_collection_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.library_folder))
                                    }
                                }
                            }
                        }

                        val filteredBookmarks = if (selectedCollectionId == null) {
                            state.bookmarks
                        } else {
                            state.bookmarks.filter { it.collectionId == selectedCollectionId }
                        }

                        if (filteredBookmarks.isEmpty()) {
                            item {
                                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.library_empty_bookmarks),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(filteredBookmarks, key = { it.id }) { bm ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bookmark_card_${bm.verseKey}")
                                        .clickable { onOpenReader(bm.surahId, bm.ayahNumber) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Bookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bm.label ?: "Ayah ${bm.verseKey}",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = BidiUtils.wrapVerseKey(bm.verseKey),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteBookmark(bm.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.notes.isEmpty()) {
                            item {
                                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.library_empty_notes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(state.notes, key = { it.id }) { note ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenReader(note.surahId, note.ayahNumber) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = BidiUtils.wrapVerseKey(note.verseKey),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = note.noteText,
                                                style = banglaSafeBodyStyle(15f, state.preferences.uiLanguageCode)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.khatam_plan_title),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.library_khatam_progress_fmt,
                                            state.khatamEstimate.completedAyahs,
                                            state.khatamEstimate.totalAyahsInQuran,
                                            (state.khatamEstimate.completionPercentage * 100).roundToInt()
                                        ),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    LinearProgressIndicator(
                                        progress = { state.khatamEstimate.completionPercentage },
                                        modifier = Modifier.fillMaxWidth().height(8.dp)
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.library_khatam_pace_fmt,
                                            state.progress.khatamTargetDays,
                                            state.khatamEstimate.requiredAyahsPerDay
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(15, 30, 60, 90).forEach { days ->
                                            FilterChip(
                                                selected = state.progress.khatamTargetDays == days,
                                                onClick = { viewModel.updateKhatamTargetDays(days) },
                                                label = { Text("${days}d") }
                                            )
                                        }
                                    }

                                    Text(
                                        text = stringResource(R.string.library_daily_goal_fmt, state.progress.dailyAyahGoal),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(10, 20, 40, 60).forEach { goal ->
                                            FilterChip(
                                                selected = state.progress.dailyAyahGoal == goal,
                                                onClick = { viewModel.updateDailyGoal(goal) },
                                                label = { Text("$goal") }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            val perSalah = (state.khatamEstimate.requiredAyahsPerDay / 5).coerceAtLeast(1)
                            val remainder = (state.khatamEstimate.requiredAyahsPerDay % 5)
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.library_routine_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.library_routine_desc_fmt,
                                            perSalah,
                                            perSalah + remainder,
                                            state.khatamEstimate.requiredAyahsPerDay,
                                            state.currentStreakDays,
                                            state.todayAyahsRead,
                                            state.progress.dailyAyahGoal
                                        ),
                                        style = banglaSafeBodyStyle(14f, state.preferences.uiLanguageCode),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        item {
                            var showFlashcards by remember { mutableStateOf(false) }
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(stringResource(R.string.library_vocab_title), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.library_vocab_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FilledTonalButton(
                                        onClick = { showFlashcards = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.library_vocab_button))
                                    }
                                }
                            }
                            if (showFlashcards) {
                                VocabularyFlashcardsSheet(onDismiss = { showFlashcards = false })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.library_new_folder_title)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(stringResource(R.string.library_folder_name_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createCollection(newFolderName)
                        newFolderName = ""
                        showNewFolderDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
