package com.example.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.view.Surface as AndroidSurface
import android.view.WindowManager
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.nativeCanvas
import com.example.R
import com.example.core.ibadah.PrayerTimeCalculator
import com.example.core.ibadah.PrayerTimesSummary
import com.example.core.localization.BidiUtils
import com.example.ui.components.ShareCardGenerator
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.banglaSafeBodyStyle
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    modifier: Modifier = Modifier,
    lastRecitedSummary: String? = null,
    onResumeRecitation: (() -> Unit)? = null
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
                                            .data(R.drawable.quran_calligraphy_icon)
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
                                        text = stringResource(R.string.home_114_surahs),
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

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                            if (!lastRecitedSummary.isNullOrBlank() && onResumeRecitation != null) {
                                FilledTonalButton(
                                    onClick = onResumeRecitation,
                                    modifier = Modifier.testTag("home_resume_recitation_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(lastRecitedSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
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
                            Text(stringResource(R.string.home_quiz_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.home_quiz_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = { showQuizSheet = true }) {
                            Text(stringResource(R.string.home_quiz_button))
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
                            Text(stringResource(R.string.home_dua_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.home_dua_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = onOpenDua) {
                            Text(stringResource(R.string.home_dua_button))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showCityPicker) {
        var cityQuery by remember { mutableStateOf("") }
        var selectedRegion by remember { mutableStateOf("All") }
        val divisionChips = remember {
            listOf("All", "Dhaka", "Chattogram", "Rajshahi", "Khulna", "Barishal", "Sylhet", "Rangpur", "Mymensingh", "Global")
        }
        val filteredPresets = remember(cityQuery, selectedRegion) {
            PrayerTimeCalculator.searchLocations(
                query = cityQuery,
                divisionFilter = if (selectedRegion == "All") null else selectedRegion
            )
        }
        ModalBottomSheet(onDismissRequest = { showCityPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.prayer_times_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(R.string.location_picker_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                fetchLastCoarseLocation(context) { lat, lon ->
                                    viewModel.updateLocation("GPS Location", lat, lon)
                                    showCityPicker = false
                                }
                            } else {
                                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        modifier = Modifier.testTag("use_gps_location_button")
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.gps_button))
                    }
                }

                OutlinedTextField(
                    value = cityQuery,
                    onValueChange = { cityQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("city_search_input"),
                    placeholder = { Text(stringResource(R.string.search_bangladesh_city_hint)) },
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

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(divisionChips, key = { it }) { region ->
                        FilterChip(
                            selected = selectedRegion == region,
                            onClick = { selectedRegion = region },
                            label = { Text(region) },
                            modifier = Modifier.testTag("region_chip_$region")
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.places_found_fmt, filteredPresets.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPresets, key = { "${it.region}_${it.name}" }) { preset ->
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (preset.banglaName.isNotBlank()) "${preset.name} (${preset.banglaName})" else preset.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val utcLabel = preset.utcOffsetHours?.let { h ->
                                        " • UTC${if (h >= 0) "+" else ""}${if (h % 1.0 == 0.0) h.toInt().toString() else h.toString()}"
                                    }.orEmpty()
                                    Text(
                                        text = "${preset.region}$utcLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (preset.name == state.preferences.locationName) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
            latitude = state.preferences.latitude,
            longitude = state.preferences.longitude,
            distanceKm = state.distanceToKaabaKm,
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
private fun localizedPrayerName(name: String): String {
    return when (name.lowercase().trim()) {
        "fajr" -> stringResource(R.string.prayer_fajr)
        "dhuhr" -> stringResource(R.string.prayer_dhuhr)
        "asr" -> stringResource(R.string.prayer_asr)
        "maghrib" -> stringResource(R.string.prayer_maghrib)
        "isha" -> stringResource(R.string.prayer_isha)
        "imsak", "imsak (sahri end)" -> stringResource(R.string.prayer_imsak)
        "sunrise" -> stringResource(R.string.prayer_sunrise)
        "sunset" -> stringResource(R.string.prayer_sunset)
        "tahajjud", "tahajjud (last third)" -> stringResource(R.string.prayer_tahajjud)
        "midnight" -> stringResource(R.string.prayer_midnight)
        else -> name
    }
}

@Composable
private fun PrayerCell(name: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = localizedPrayerName(name), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QiblaCompassSheet(
    qiblaBearingDeg: Float,
    locationName: String,
    latitude: Double,
    longitude: Double,
    distanceKm: Int,
    onDismiss: () -> Unit,
    onOpenCityPicker: () -> Unit
) {
    val context = LocalContext.current
    var azimuthDeg by remember { mutableFloatStateOf(0f) }
    var hasSensorReading by remember { mutableStateOf(false) }
    var hasHardwareCompass by remember { mutableStateOf(true) }
    var needsCalibration by remember { mutableStateOf(false) }
    var sensorSourceLabel by remember { mutableStateOf("Calibrating...") }

    val declinationDeg = remember(latitude, longitude) {
        runCatching {
            GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                0f,
                System.currentTimeMillis()
            ).declination
        }.getOrDefault(0f)
    }

    DisposableEffect(context, declinationDeg) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (sensorManager == null || (rotationSensor == null && (accelSensor == null || magnetSensor == null))) {
            hasHardwareCompass = false
            sensorSourceLabel = "No magnetometer sensor on device"
            onDispose { }
        } else {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val listener = object : SensorEventListener {
                val rawRotation = FloatArray(9)
                val remappedRotation = FloatArray(9)
                val inclinationMatrix = FloatArray(9)
                val orientation = FloatArray(3)
                val gravity = FloatArray(3)
                val geomagnetic = FloatArray(3)
                var hasGravity = false
                var hasMagnetic = false
                var receivedRotationVector = false
                var smoothedTrueAzimuth = 0f
                var initializedSmoothing = false

                private fun updateSmoothedHeading(rawMatrix: FloatArray, sourceName: String) {
                    @Suppress("DEPRECATION")
                    val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching { context.display.rotation }.getOrNull()
                            ?: windowManager?.defaultDisplay?.let { it.rotation }
                            ?: AndroidSurface.ROTATION_0
                    } else {
                        windowManager?.defaultDisplay?.let { it.rotation } ?: AndroidSurface.ROTATION_0
                    }

                    val (axisX, axisY) = when (displayRotation) {
                        AndroidSurface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        AndroidSurface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        AndroidSurface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }

                    val targetMatrix = if (SensorManager.remapCoordinateSystem(rawMatrix, axisX, axisY, remappedRotation)) {
                        remappedRotation
                    } else {
                        rawMatrix
                    }
                    SensorManager.getOrientation(targetMatrix, orientation)
                    val magneticDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    val trueNorthDeg = ((magneticDeg + declinationDeg) % 360f + 360f) % 360f

                    if (!initializedSmoothing) {
                        smoothedTrueAzimuth = trueNorthDeg
                        initializedSmoothing = true
                    } else {
                        val delta = ((trueNorthDeg - smoothedTrueAzimuth + 540f) % 360f) - 180f
                        smoothedTrueAzimuth = ((smoothedTrueAzimuth + delta * 0.22f) % 360f + 360f) % 360f
                    }
                    azimuthDeg = smoothedTrueAzimuth
                    hasSensorReading = true
                    sensorSourceLabel = sourceName
                }

                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            receivedRotationVector = true
                            SensorManager.getRotationMatrixFromVector(rawRotation, event.values)
                            updateSmoothedHeading(rawRotation, "Fusion Sensor • True North")
                        }
                        Sensor.TYPE_ACCELEROMETER -> {
                            val alpha = 0.8f
                            if (!hasGravity) {
                                System.arraycopy(event.values, 0, gravity, 0, 3)
                                hasGravity = true
                            } else {
                                gravity[0] = alpha * gravity[0] + (1f - alpha) * event.values[0]
                                gravity[1] = alpha * gravity[1] + (1f - alpha) * event.values[1]
                                gravity[2] = alpha * gravity[2] + (1f - alpha) * event.values[2]
                            }
                            if (!receivedRotationVector && hasMagnetic &&
                                SensorManager.getRotationMatrix(rawRotation, inclinationMatrix, gravity, geomagnetic)
                            ) {
                                updateSmoothedHeading(rawRotation, "Magnetic + Accel • True North")
                            }
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            val alpha = 0.8f
                            if (!hasMagnetic) {
                                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                                hasMagnetic = true
                            } else {
                                geomagnetic[0] = alpha * geomagnetic[0] + (1f - alpha) * event.values[0]
                                geomagnetic[1] = alpha * geomagnetic[1] + (1f - alpha) * event.values[1]
                                geomagnetic[2] = alpha * geomagnetic[2] + (1f - alpha) * event.values[2]
                            }
                            if (!receivedRotationVector && hasGravity &&
                                SensorManager.getRotationMatrix(rawRotation, inclinationMatrix, gravity, geomagnetic)
                            ) {
                                updateSmoothedHeading(rawRotation, "Magnetic + Accel • True North")
                            }
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                        needsCalibration = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
                    }
                }
            }

            if (rotationSensor != null) {
                sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            }
            if (accelSensor != null) {
                sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            }
            if (magnetSensor != null) {
                sensorManager.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_GAME)
            }

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val relativeAngle = if (hasSensorReading) {
        ((qiblaBearingDeg - azimuthDeg) % 360f + 360f) % 360f
    } else {
        qiblaBearingDeg
    }
    val signedDiff = ((relativeAngle + 540f) % 360f) - 180f
    val isAligned = hasSensorReading && abs(signedDiff) <= 5f

    val ringColor = if (isAligned) Color(0xFF1B8A5A) else MaterialTheme.colorScheme.outline
    val primaryColor = if (isAligned) Color(0xFF1B8A5A) else MaterialTheme.colorScheme.primary
    val goldColor = MaterialTheme.colorScheme.secondary
    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.qibla_compass_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.qibla_bearing_fmt, locationName, qiblaBearingDeg.roundToInt()) +
                    " • ${BidiUtils.formatNumber(distanceKm, "en")} km to Makkah",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onOpenCityPicker()
                    },
                    modifier = Modifier.testTag("qibla_change_city_button")
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.change_city))
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isAligned) Color(0xFF1B8A5A).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = when {
                            !hasHardwareCompass -> "Fixed North Bearing"
                            isAligned -> "Aligned with Qibla"
                            hasSensorReading -> "Heading ${azimuthDeg.roundToInt()}°"
                            else -> "Hold phone flat"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAligned) Color(0xFF1B8A5A) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    val compassRotation = if (hasSensorReading) -azimuthDeg else 0f

                    drawCircle(
                        color = ringColor.copy(alpha = if (isAligned) 0.9f else 0.45f),
                        radius = radius,
                        style = Stroke(width = if (isAligned) 8.dp.toPx() else 4.dp.toPx())
                    )

                    // Rotate compass dial with North
                    rotate(degrees = compassRotation) {
                        for (deg in 0 until 360 step 15) {
                            val rad = Math.toRadians(deg.toDouble())
                            val isMajor = deg % 90 == 0
                            val innerR = radius * if (isMajor) 0.80f else 0.88f
                            val outerR = radius * 0.96f
                            drawLine(
                                color = if (deg == 0) Color(0xFFD32F2F) else ringColor.copy(alpha = if (isMajor) 0.7f else 0.3f),
                                start = Offset(
                                    center.x + (innerR * sin(rad)).toFloat(),
                                    center.y - (innerR * cos(rad)).toFloat()
                                ),
                                end = Offset(
                                    center.x + (outerR * sin(rad)).toFloat(),
                                    center.y - (outerR * cos(rad)).toFloat()
                                ),
                                strokeWidth = if (isMajor) 3.dp.toPx() else 1.5.dp.toPx()
                            )
                        }
                        val paint = android.graphics.Paint().apply {
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 13.sp.toPx()
                            isFakeBoldText = true
                            color = android.graphics.Color.argb(
                                230,
                                (labelTextColor.red * 255).toInt(),
                                (labelTextColor.green * 255).toInt(),
                                (labelTextColor.blue * 255).toInt()
                            )
                        }
                        val northPaint = android.graphics.Paint(paint).apply {
                            color = android.graphics.Color.rgb(211, 47, 47)
                        }
                        val labelR = radius * 0.68f
                        drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - labelR + 5.dp.toPx(), northPaint)
                        drawContext.canvas.nativeCanvas.drawText("S", center.x, center.y + labelR + 5.dp.toPx(), paint)
                        drawContext.canvas.nativeCanvas.drawText("E", center.x + labelR, center.y + 5.dp.toPx(), paint)
                        drawContext.canvas.nativeCanvas.drawText("W", center.x - labelR, center.y + 5.dp.toPx(), paint)
                    }

                    // Qibla Pointer Needle
                    rotate(degrees = relativeAngle) {
                        drawLine(
                            color = primaryColor,
                            start = Offset(center.x, center.y + radius * 0.16f),
                            end = Offset(center.x, center.y - radius * 0.74f),
                            strokeWidth = 7.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = goldColor,
                            radius = 12.dp.toPx(),
                            center = Offset(center.x, center.y - radius * 0.76f)
                        )
                    }
                    drawCircle(color = primaryColor, radius = 8.dp.toPx(), center = center)
                }
            }

            if (needsCalibration) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.qibla_calibrate_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = if (hasHardwareCompass) {
                        "$sensorSourceLabel • Declination ${if (declinationDeg >= 0) "+" else ""}${declinationDeg.roundToInt()}°"
                    } else {
                        stringResource(R.string.qibla_no_sensor_hint, qiblaBearingDeg.roundToInt())
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
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
                    Text(stringResource(R.string.prayer_schedule_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                    Text(stringResource(R.string.change_city))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.asr_madhab_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(
                    selected = !hanafiAsr,
                    onClick = { onToggleHanafiAsr(false) },
                    label = { Text(stringResource(R.string.madhab_standard)) },
                    modifier = Modifier.testTag("madhab_standard_chip")
                )
                FilterChip(
                    selected = hanafiAsr,
                    onClick = { onToggleHanafiAsr(true) },
                    label = { Text(stringResource(R.string.madhab_hanafi)) },
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
                        Text(stringResource(R.string.next_prayer_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(localizedPrayerName(prayerTimes.nextPrayerName), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                                text = localizedPrayerName(row.name),
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
