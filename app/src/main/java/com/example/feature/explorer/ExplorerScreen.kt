package com.example.feature.explorer

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.localization.BidiUtils
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.banglaSafeBodyStyle

private data class JuzStarterInfo(val juz: Int, val surahId: Int, val ayahNumber: Int, val label: String)
private data class RabbanaDuaItem(val surahId: Int, val ayahNumber: Int, val title: String, val verseKey: String)

private val JUZ_MAP = listOf(
    JuzStarterInfo(1, 1, 1, "Al-Fatihah 1:1"),
    JuzStarterInfo(2, 2, 142, "Al-Baqarah 2:142"),
    JuzStarterInfo(3, 2, 253, "Al-Baqarah 2:253"),
    JuzStarterInfo(4, 3, 93, "Ali 'Imran 3:93"),
    JuzStarterInfo(5, 4, 24, "An-Nisa 4:24"),
    JuzStarterInfo(6, 4, 148, "An-Nisa 4:148"),
    JuzStarterInfo(7, 5, 82, "Al-Ma'idah 5:82"),
    JuzStarterInfo(8, 6, 111, "Al-An'am 6:111"),
    JuzStarterInfo(9, 7, 88, "Al-A'raf 7:88"),
    JuzStarterInfo(10, 8, 41, "Al-Anfal 8:41"),
    JuzStarterInfo(11, 9, 93, "At-Tawbah 9:93"),
    JuzStarterInfo(12, 11, 6, "Hud 11:6"),
    JuzStarterInfo(13, 12, 53, "Yusuf 12:53"),
    JuzStarterInfo(14, 15, 1, "Al-Hijr 15:1"),
    JuzStarterInfo(15, 17, 1, "Al-Isra 17:1"),
    JuzStarterInfo(16, 18, 75, "Al-Kahf 18:75"),
    JuzStarterInfo(17, 21, 1, "Al-Anbya 21:1"),
    JuzStarterInfo(18, 23, 1, "Al-Mu'minun 23:1"),
    JuzStarterInfo(19, 25, 21, "Al-Furqan 25:21"),
    JuzStarterInfo(20, 27, 56, "An-Naml 27:56"),
    JuzStarterInfo(21, 29, 46, "Al-'Ankabut 29:46"),
    JuzStarterInfo(22, 33, 31, "Al-Ahzab 33:31"),
    JuzStarterInfo(23, 36, 28, "Ya-Sin 36:28"),
    JuzStarterInfo(24, 39, 32, "Az-Zumar 39:32"),
    JuzStarterInfo(25, 41, 47, "Fussilat 41:47"),
    JuzStarterInfo(26, 46, 1, "Al-Ahqaf 46:1"),
    JuzStarterInfo(27, 51, 31, "Adh-Dhariyat 51:31"),
    JuzStarterInfo(28, 58, 1, "Al-Mujadila 58:1"),
    JuzStarterInfo(29, 67, 1, "Al-Mulk 67:1"),
    JuzStarterInfo(30, 78, 1, "An-Naba 78:1")
)

private val RABBANA_DUAS = listOf(
    RabbanaDuaItem(2, 201, "Rabbana atina fid-dunya hasanah (Good in both worlds)", "2:201"),
    RabbanaDuaItem(3, 8, "Rabbana la tuzigh qulubana (Steadfast heart)", "3:8"),
    RabbanaDuaItem(1, 5, "Iyyaka na'budu wa iyyaka nasta'in (Guidance & Help)", "1:5"),
    RabbanaDuaItem(1, 6, "Ihdinas-siratal-mustaqim (Straight Path)", "1:6"),
    RabbanaDuaItem(2, 255, "Ayat al-Kursi (Protection & Sovereignty)", "2:255")
)

@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel,
    initialFocusSearch: Boolean = false,
    onOpenReader: (surahId: Int, ayahNumber: Int) -> Unit,
    onOpenPage: (surahId: Int, pageNumber: Int) -> Unit = { surahId, _ -> onOpenReader(surahId, 1) },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(initialFocusSearch) {
        if (initialFocusSearch) {
            kotlinx.coroutines.delay(120L)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    val tabTitles = listOf(
        stringResource(R.string.tab_surah),
        stringResource(R.string.tab_juz),
        stringResource(R.string.tab_page),
        stringResource(R.string.tab_duas)
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Al-Quran Surah Explorer Header & Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "114 Surahs • 30 Juz • 604 Pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .testTag("explorer_search_input")
                )

                if (state.searchQuery.isNotBlank()) {
                    Text(
                        text = "${state.matchedSurahs.size} Surahs • ${state.searchResults.size} Ayah matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.searchQuery.isNotBlank()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.matchedSurahs.isNotEmpty()) {
                        items(state.matchedSurahs, key = { "surah_${it.id}" }) { surah ->
                            val title = BidiUtils.extractLocalizedSurahName(
                                surah.localizedNamesJson,
                                state.preferences.uiLanguageCode
                            ).ifBlank { surah.nameTransliteration }
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenReader(surah.id, 1) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Surah ${surah.id}: $title",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${surah.ayahCount} Ayahs • Page ${surah.startPage}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = surah.nameArabic,
                                        fontFamily = arabicScriptFont(state.preferences.scriptType),
                                        fontSize = 22.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    items(state.searchResults, key = { it.ayah.id }) { res ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_result_${res.ayah.verseKey}")
                                .clickable { onOpenReader(res.ayah.surahId, res.ayah.ayahNumber) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${res.surahName} • ${BidiUtils.wrapVerseKey(res.ayah.verseKey)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = res.ayah.textUthmani,
                                    fontFamily = arabicScriptFont(state.preferences.scriptType),
                                    fontSize = 22.sp,
                                    lineHeight = 36.sp
                                )
                                res.matchedTranslation?.let { tr ->
                                    Text(
                                        text = tr.translatedText,
                                        style = banglaSafeBodyStyle(14f, tr.languageCode),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                TabRow(selectedTabIndex = selectedTab) {
                    tabTitles.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("explorer_tab_$idx")
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = state.surahs,
                                key = { it.id },
                                contentType = { "surah_row" }
                            ) { surah ->
                                val localizedName = remember(surah.localizedNamesJson, state.preferences.uiLanguageCode) {
                                    BidiUtils.extractLocalizedSurahName(
                                        surah.localizedNamesJson,
                                        state.preferences.uiLanguageCode
                                    ).ifBlank { surah.nameTransliteration }
                                }
                                val isDownloading = state.downloadingSurahId == surah.id

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("surah_row_${surah.id}")
                                        .clickable { onOpenReader(surah.id, 1) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = BidiUtils.formatNumber(surah.id, state.preferences.uiLanguageCode),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = localizedName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${surah.revelationPlace.replaceFirstChar { it.uppercase() }} • ${BidiUtils.formatNumber(surah.ayahCount, state.preferences.uiLanguageCode)} Ayahs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = surah.nameArabic,
                                        fontFamily = arabicScriptFont(state.preferences.scriptType),
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isDownloading) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    } else if (surah.isDownloaded) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = stringResource(R.string.downloaded_badge),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        IconButton(
                                            onClick = { viewModel.downloadSurah(surah.id) },
                                            modifier = Modifier.testTag("download_surah_${surah.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.CloudDownload,
                                                contentDescription = stringResource(R.string.download_surah),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                            }
                        }
                    }

                    1 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(JUZ_MAP, key = { it.juz }) { juzItem ->
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("juz_card_${juzItem.juz}")
                                        .clickable { onOpenReader(juzItem.surahId, juzItem.ayahNumber) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Juz ${BidiUtils.formatNumber(juzItem.juz, state.preferences.uiLanguageCode)}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = juzItem.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 84.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                count = 604,
                                key = { idx -> idx + 1 },
                                contentType = { "mushaf_page_chip" }
                            ) { idx ->
                                val page = idx + 1
                                AssistChip(
                                    onClick = {
                                        val resolvedSurah = state.surahs
                                            .lastOrNull { it.startPage <= page }?.id
                                            ?: when {
                                                page == 1 -> 1
                                                page <= 49 -> 2
                                                page >= 604 -> 114
                                                else -> ((page * 114) / 604).coerceIn(1, 114)
                                            }
                                        onOpenPage(resolvedSurah, page)
                                    },
                                    label = { Text("P. $page") },
                                    modifier = Modifier.testTag("mushaf_page_$page")
                                )
                            }
                        }
                    }

                    3 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(RABBANA_DUAS, key = { it.verseKey }) { dua ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dua_card_${dua.verseKey}")
                                        .clickable { onOpenReader(dua.surahId, dua.ayahNumber) }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = BidiUtils.wrapVerseKey(dua.verseKey),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = dua.title, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
