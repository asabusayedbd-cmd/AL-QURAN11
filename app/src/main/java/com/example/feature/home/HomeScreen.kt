package com.example.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.core.ibadah.PrayerTimeCalculator
import com.example.core.ibadah.PrayerTimesSummary
import com.example.core.localization.BidiUtils
import com.example.ui.components.ShareCardGenerator
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.banglaSafeBodyStyle
import java.util.Calendar
import kotlin.math.roundToInt

private data class QuickShortcut(val surahId: Int, val ayahNumber: Int, val title: String, val subtitle: String)

private val QUICK_SHORTCUTS = listOf(
    QuickShortcut(18, 1, "Al-Kahf", "Surah 18"),
    QuickShortcut(67, 1, "Al-Mulk", "Surah 67"),
    QuickShortcut(55, 1, "Ar-Rahman", "Surah 55"),
    QuickShortcut(36, 1, "Ya-Sin", "Surah 36"),
    QuickShortcut(2, 255, "Ayat al-Kursi", "2:255"),
    QuickShortcut(112, 1, "Al-Ikhlas", "Surah 112")
)

private val TASBEEH_PHRASES = listOf(
    Triple("سُبْحَانَ اللَّهِ", "SubhanAllah", "Glory be to Allah"),
    Triple("الْحَمْدُ لِلَّهِ", "Alhamdulillah", "All praise is for Allah"),
    Triple("اللَّهُ أَكْبَرُ", "Allahu Akbar", "Allah is the Greatest"),
    Triple("أَسْتَغْفِرُ اللَّهَ", "Astaghfirullah", "I seek forgiveness from Allah")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenReader: (surahId: Int, ayahNumber: Int) -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDua: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showQiblaSheet by remember { mutableStateOf(false) }
    var showTasbeehSheet by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }
    var showPrayerScheduleSheet by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchLastCoarseLocation(context) { lat, lon ->
                viewModel.updateLocation("GPS Location", lat, lon)
            }
        }
    }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greetingRes = when (hour) {
        in 4..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        in 17..20 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Serene Hero Greeting Banner with Al-Quran Brand Header
            item(key = "home_hero_banner", contentType = "hero") {
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(196.dp)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(R.drawable.img_hero_crescent)
                                .size(900, 420)
                                .crossfade(160)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xC2082821),
                                            Color(0xF00A3B31)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF0E4839),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(R.drawable.ic_quran_logo_fg_1795445024163)
                                            .size(96, 96)
                                            .build(),
                                        contentDescription = "Al-Quran emblem",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF3CF82)
                                    )
                                    Text(
                                        text = stringResource(greetingRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFFFAF7F0)
                                    )
                                }
                                Surface(
                                    color = Color(0x28F3CF82),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "114 Surahs",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF3CF82),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "${state.hijriDate}  •  ${state.gregorianDate}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFEBD9B3)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0x33FFFFFF),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Flare,
                                            contentDescription = null,
                                            tint = Color(0xFFF3CF82),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.streak_days_fmt,
                                                BidiUtils.formatNumber(state.currentStreakDays, state.preferences.uiLanguageCode)
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White
                                        )
                                    }
                                }

                                Text(
                                    text = "${state.prayerTimes.nextPrayerName} • ${state.prayerTimes.nextPrayerTime}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFFF3CF82)
                                )
                            }
                        }
                    }
                }
            }

            // Continue Reading & Daily Habit Progress Row
            item(key = "home_continue_reading", contentType = "card") {
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("continue_reading_card")
                        .clickable {
                            onOpenReader(state.progress.lastSurahId, state.progress.lastAyahNumber)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.continue_reading),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.lastSurahName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(
                                    R.string.ayah_progress_fmt,
                                    BidiUtils.formatNumber(state.progress.lastAyahNumber, state.preferences.uiLanguageCode),
                                    BidiUtils.formatNumber(state.progress.lastJuzNumber, state.preferences.uiLanguageCode)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                onOpenReader(state.progress.lastSurahId, state.progress.lastAyahNumber)
                            },
                            modifier = Modifier.testTag("continue_reading_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.nav_quran))
                        }
                    }
                }
            }

            // Daily Quran Goal & 7-Day Consistency
            item(key = "home_daily_goal", contentType = "card") {
                OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val goal = state.progress.dailyAyahGoal.coerceAtLeast(1)
                        val progressFraction = (state.todayAyahsRead.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val ringColor = MaterialTheme.colorScheme.primary

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(74.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = trackColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progressFraction,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                text = "${(progressFraction * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.daily_goal_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(
                                    R.string.daily_goal_progress_fmt,
                                    BidiUtils.formatNumber(state.todayAyahsRead, state.preferences.uiLanguageCode),
                                    BidiUtils.formatNumber(goal, state.preferences.uiLanguageCode)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // 7-day bar indicator
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val recent = state.streaks.take(7).reversed()
                                val padCount = (7 - recent.size).coerceAtLeast(0)
                                repeat(padCount) {
                                    Box(
                                        modifier = Modifier
                                            .width(14.dp)
                                            .height(12.dp)
                                            .clip(MaterialTheme.shapes.extraSmall)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                recent.forEach { day ->
                                    val barHeight = (10 + (day.ayahsReadCount.coerceAtMost(30))).dp
                                    Box(
                                        modifier = Modifier
                                            .width(14.dp)
                                            .height(barHeight)
                                            .clip(MaterialTheme.shapes.extraSmall)
                                            .background(
                                                if (day.ayahsReadCount > 0) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Verse of the Day Card
            state.verseOfTheDay?.let { votd ->
                item(key = "home_votd", contentType = "card") {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verse_of_the_day_card")
                            .clickable { onOpenReader(votd.ayah.surahId, votd.ayah.ayahNumber) }
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.verse_of_the_day),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${votd.surahName} • ${BidiUtils.wrapVerseKey(votd.ayah.verseKey)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    text = votd.ayah.textUthmani,
                                    fontFamily = arabicScriptFont(state.preferences.scriptType),
                                    fontSize = 24.sp,
                                    lineHeight = 40.sp,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            votd.translation?.let { tr ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = tr.translatedText,
                                    style = banglaSafeBodyStyle(15f, tr.languageCode),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "— ${tr.translatorName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleVerseOfDayBookmark(votd) },
                                    modifier = Modifier.testTag("votd_bookmark_button")
                                ) {
                                    Icon(
                                        imageVector = if (votd.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = stringResource(R.string.cd_bookmark_ayah),
                                        tint = if (votd.isBookmarked) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        ShareCardGenerator.shareAyahAsImageCard(
                                            context = context,
                                            surahName = votd.surahName,
                                            verseKey = votd.ayah.verseKey,
                                            arabicText = votd.ayah.textUthmani,
                                            translationText = votd.translation?.translatedText.orEmpty(),
                                            translatorName = votd.translation?.translatorName ?: "Quran Translation"
                                        )
                                    },
                                    modifier = Modifier.testTag("votd_share_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = stringResource(R.string.cd_share_ayah),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Shortcuts
            item(key = "home_shortcuts", contentType = "row") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.quick_shortcuts),
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = onOpenExplorer, modifier = Modifier.testTag("open_explorer_shortcut")) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.nav_quran))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(QUICK_SHORTCUTS) { shortcut ->
                            OutlinedCard(
                                modifier = Modifier
                                    .width(142.dp)
                                    .testTag("shortcut_${shortcut.surahId}_${shortcut.ayahNumber}")
                                    .clickable { onOpenReader(shortcut.surahId, shortcut.ayahNumber) }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = shortcut.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = shortcut.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Daily Ibadah & Prayer Card (Sadiq-inspired calm widget)
            item(key = "home_prayer_card", contentType = "card") {
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrayerScheduleSheet = true }
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(stringResource(R.string.prayer_times_title), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = state.preferences.locationName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = { showCityPicker = true },
                                    modifier = Modifier.testTag("pick_city_button")
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = "Pick city")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            PrayerCell("Fajr", state.prayerTimes.fajr)
                            PrayerCell("Dhuhr", state.prayerTimes.dhuhr)
                            PrayerCell("Asr", state.prayerTimes.asr)
                            PrayerCell("Maghrib", state.prayerTimes.maghrib)
                            PrayerCell("Isha", state.prayerTimes.isha)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showQiblaSheet = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_qibla_button")
                            ) {
                                Icon(Icons.Default.Explore, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.qibla_compass_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(
                                onClick = { showTasbeehSheet = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_tasbeeh_button")
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.tasbeeh_counter_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                         Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        fetchLastCoarseLocation(context) { lat, lon ->
                                            viewModel.updateLocation("GPS Location", lat, lon)
                                        }
                                    } else {
                                        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                },
                                modifier = Modifier.testTag("gps_location_button")
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.detect_location))
                            }
                            TextButton(onClick = onOpenLibrary, modifier = Modifier.testTag("home_open_library_button")) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.nav_bookmarks))
                            }
                        }
                    }
                }
            }

            // Quran Knowledge Quiz Card
            item(key = "home_quiz_card") {
                var showQuizSheet by remember { mutableStateOf(false) }
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showQuizSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quranic Knowledge Quiz", style = MaterialTheme.typography.titleMedium)
                            Text("Test your knowledge of Surahs, Juz, and Quranic facts offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = { showQuizSheet = true }) {
                            Text("Play Quiz")
                        }
                    }
                }
                if (showQuizSheet) {
                    QuranQuizSheet(onDismiss = { showQuizSheet = false })
                }
            }

            // Authentic Duas & Ruqyah Card
            item(key = "home_dua_card") {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenDua),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Authentic Duas & Ruqyah", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("150+ Topic-based Duas with Sahih Hadith references & flashcard sharing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = onOpenDua) {
                            Text("Explore Duas")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showCityPicker) {
        var cityQuery by remember { mutableStateOf("") }
        val filteredPresets = remember(cityQuery) {
            if (cityQuery.isBlank()) {
                PrayerTimeCalculator.CITY_PRESETS
            } else {
                PrayerTimeCalculator.CITY_PRESETS.filter {
                    it.name.contains(cityQuery, ignoreCase = true)
                }
            }
        }
        ModalBottomSheet(onDismissRequest = { showCityPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.prayer_times_title), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = cityQuery,
                    onValueChange = { cityQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("city_search_input"),
                    placeholder = { Text("Search city or country...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (cityQuery.isNotEmpty()) {
                            IconButton(onClick = { cityQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear city search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPresets, key = { it.name }) { preset ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (preset.name == state.preferences.locationName) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("city_preset_${preset.name}")
                                .clickable {
                                    viewModel.updateLocation(preset.name, preset.latitude, preset.longitude)
                                    showCityPicker = false
                                }
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showQiblaSheet) {
        QiblaCompassSheet(
            qiblaBearingDeg = state.qiblaBearingDegrees,
            locationName = state.preferences.locationName,
            onDismiss = { showQiblaSheet = false },
            onOpenCityPicker = { showCityPicker = true }
        )
    }

    if (showPrayerScheduleSheet) {
        PrayerScheduleSheet(
            prayerTimes = state.prayerTimes,
            locationName = state.preferences.locationName,
            hanafiAsr = state.preferences.hanafiAsr,
            onToggleHanafiAsr = { viewModel.setHanafiAsr(it) },
            onDismiss = { showPrayerScheduleSheet = false },
            onOpenCityPicker = { showCityPicker = true }
        )
    }

    if (showTasbeehSheet) {
        val haptics = LocalHapticFeedback.current
        val phrase = TASBEEH_PHRASES[state.tasbeehPhraseIndex]
        ModalBottomSheet(onDismissRequest = { showTasbeehSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.tasbeeh_counter_title), style = MaterialTheme.typography.titleLarge)
                    IconButton(
                        onClick = { viewModel.resetTodayTasbeeh() },
                        modifier = Modifier.testTag("tasbeeh_reset_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Tasbeeh")
                    }
                }

                FilterChip(
                    selected = true,
                    onClick = { viewModel.cycleTasbeehPhrase() },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = { Text("${phrase.second} — ${phrase.third}") },
                    modifier = Modifier.testTag("tasbeeh_phrase_chip")
                )

                Text(
                    text = phrase.first,
                    fontFamily = arabicScriptFont(state.preferences.scriptType),
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(164.dp)
                        .testTag("tasbeeh_tap_circle")
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.incrementTasbeeh()
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = BidiUtils.formatNumber(state.todayTasbeehCount, state.preferences.uiLanguageCode),
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 40.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "${state.todayTasbeehCount % 33} / 33",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PrayerCell(name: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QiblaCompassSheet(
    qiblaBearingDeg: Float,
    locationName: String,
    onDismiss: () -> Unit,
    onOpenCityPicker: () -> Unit
) {
    val context = LocalContext.current
    var azimuthDeg by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                azimuthDeg = ((deg + 360f) % 360f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val relativeAngle = ((qiblaBearingDeg - azimuthDeg) + 360f) % 360f
    val ringColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary
    val goldColor = MaterialTheme.colorScheme.secondary

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.qibla_compass_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = "$locationName • Bearing ${qiblaBearingDeg.roundToInt()}° from North",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onOpenCityPicker()
                },
                modifier = Modifier.testTag("qibla_change_city_button")
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Change City")
            }

            Box(
                modifier = Modifier
                    .size(210.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    drawCircle(color = ringColor, radius = radius, style = Stroke(width = 6.dp.toPx()))
                    rotate(degrees = relativeAngle) {
                        drawLine(
                            color = primaryColor,
                            start = center,
                            end = Offset(center.x, center.y - radius * 0.78f),
                            strokeWidth = 7.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = goldColor,
                            radius = 10.dp.toPx(),
                            center = Offset(center.x, center.y - radius * 0.78f)
                        )
                    }
                    drawCircle(color = primaryColor, radius = 8.dp.toPx(), center = center)
                }
            }

            Button(onClick = onDismiss) {
                Text("Close")
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

private fun fetchLastCoarseLocation(context: Context, onResult: (Double, Double) -> Unit) {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
    val loc = try {
        lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
    } catch (_: SecurityException) {
        null
    }
    if (loc != null) {
        onResult(loc.latitude, loc.longitude)
    }
}

private data class PrayerRow(val name: String, val time: String, val isFard: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerScheduleSheet(
    prayerTimes: PrayerTimesSummary,
    locationName: String,
    hanafiAsr: Boolean,
    onToggleHanafiAsr: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenCityPicker: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Prayer Schedule & Timings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(locationName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onOpenCityPicker()
                    },
                    modifier = Modifier.testTag("schedule_change_city_button")
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change City")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Asr Madhab:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(
                    selected = !hanafiAsr,
                    onClick = { onToggleHanafiAsr(false) },
                    label = { Text("Standard (Shafi'i)") },
                    modifier = Modifier.testTag("madhab_standard_chip")
                )
                FilterChip(
                    selected = hanafiAsr,
                    onClick = { onToggleHanafiAsr(true) },
                    label = { Text("Hanafi") },
                    modifier = Modifier.testTag("madhab_hanafi_chip")
                )
            }

            // Next Prayer Banner
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Next Prayer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(prayerTimes.nextPrayerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(prayerTimes.nextPrayerTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(prayerTimes.countdownText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val prayers = listOf(
                PrayerRow("Imsak (Sahri End)", prayerTimes.imsak, false),
                PrayerRow("Fajr", prayerTimes.fajr, true),
                PrayerRow("Sunrise", prayerTimes.sunrise, false),
                PrayerRow("Dhuhr", prayerTimes.dhuhr, true),
                PrayerRow("Asr", prayerTimes.asr, true),
                PrayerRow("Sunset", prayerTimes.sunset, false),
                PrayerRow("Maghrib", prayerTimes.maghrib, true),
                PrayerRow("Isha", prayerTimes.isha, true),
                PrayerRow("Tahajjud (Last Third)", prayerTimes.tahajjud, false),
                PrayerRow("Midnight", prayerTimes.midnight, false)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(prayers) { row ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (row.isFard) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (row.isFard) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                text = row.time,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (row.isFard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
