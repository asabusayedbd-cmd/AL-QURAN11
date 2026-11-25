package com.example.feature.settings

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.localization.QuranScriptType
import com.example.ui.theme.LightTajweedPalette
import com.example.ui.theme.arabicScriptFont
import java.util.Locale

private enum class MakhrajZone(
    val titleEn: String,
    val titleAr: String,
    val subRegions: String,
    val descriptionEn: String,
    val letters: List<String>,
    val accentColor: Color
) {
    AL_JAWF(
        titleEn = "Al-Jawf (Oral & Throat Cavity)",
        titleAr = "الجَوْف",
        subRegions = "Empty space of mouth & throat",
        descriptionEn = "Open vocal tract producing elongated Madd vowels: Alif (after Fathah), Waw Sakinah (after Dammah), Ya Sakinah (after Kasrah).",
        letters = listOf("ا", "و", "ي"),
        accentColor = Color(0xFF00897B)
    ),
    AL_HALQ(
        titleEn = "Al-Halq (The Throat)",
        titleAr = "الحَلْق",
        subRegions = "Deep (أقصى) • Middle (وسط) • Upper (أدنى)",
        descriptionEn = "Three throat levels: Deepest throat (ء هـ), Middle pharynx (ع ح), and Upper throat near uvula (غ خ).",
        letters = listOf("ء", "ه", "ع", "ح", "غ", "خ"),
        accentColor = Color(0xFFE65100)
    ),
    AL_LISAN(
        titleEn = "Al-Lisan (The Tongue)",
        titleAr = "اللِّسَان",
        subRegions = "10 sub-points from root to tip & sides",
        descriptionEn = "Articulates 18 letters across root (ق ك), center (ج ش ي), sides (ض), edges (ل ن ر), alveolar (ط د ت / ص ز س), and tip with teeth (ظ ذ ث).",
        letters = listOf("ق", "ك", "ج", "ش", "ي", "ض", "ل", "ن", "ر", "ط", "د", "ت", "ص", "ز", "س", "ظ", "ذ", "ث"),
        accentColor = Color(0xFF1565C0)
    ),
    ASH_SHAFATAN(
        titleEn = "Ash-Shafatan (The Two Lips)",
        titleAr = "الشَّفَتَان",
        subRegions = "Inner lower lip + Both lips",
        descriptionEn = "Upper front teeth touch inner wet lower lip for (ف), while both lips meet or round for (ب م و).",
        letters = listOf("ف", "ب", "م", "و"),
        accentColor = Color(0xFF6A1B9A)
    ),
    AL_KHAYSHUM(
        titleEn = "Al-Khayshum (Nasal Passage)",
        titleAr = "الخَيْشُوم",
        subRegions = "Nasal cavity behind nose bridge",
        descriptionEn = "Produces Ghunnah (2-beat nasal resonance) accompanying Noon (ن) and Meem (م) in Mushaddah, Idgham, Ikhfa, and Iqlab.",
        letters = listOf("ن", "م"),
        accentColor = Color(0xFFC2185B)
    )
}

private data class ArabicLetterLesson(
    val letter: String,
    val nameEn: String,
    val nameAr: String,
    val makhrajZone: MakhrajZone,
    val specificPoint: String,
    val sifaat: String,
    val quranExample: String,
    val tipEn: String
)

private val ARABIC_29_LETTERS = listOf(
    ArabicLetterLesson("ا", "Alif", "ألف", MakhrajZone.AL_JAWF, "Empty oral & throat cavity", "Jahr • Rakhawah • Istifal", "قَالَ", "Keep mouth open vertically without nasalizing."),
    ArabicLetterLesson("ب", "Ba", "باء", MakhrajZone.ASH_SHAFATAN, "Wet part of both lips closing", "Jahr • Shiddah • Qalqalah", "بِسْمِ", "Firm lip closure; bounce cleanly on Sukoon (Qalqalah)."),
    ArabicLetterLesson("ت", "Ta", "تاء", MakhrajZone.AL_LISAN, "Tongue tip against roots of upper incisors", "Hams • Shiddah • Istifal", "تَبَّتْ", "Light breath release (Hams) on Sukoon; never heavy."),
    ArabicLetterLesson("ث", "Tha", "ثاء", MakhrajZone.AL_LISAN, "Tongue tip touching bottom edge of upper incisors", "Hams • Rakhawah • Istifal", "ثُمَّ", "Soft interdental airflow; distinct from 's'."),
    ArabicLetterLesson("ج", "Jeem", "جيم", MakhrajZone.AL_LISAN, "Middle tongue firmly touches hard palate", "Jahr • Shiddah • Qalqalah", "جَعَلَ", "Full mid-tongue contact without leaking air like French 'j'."),
    ArabicLetterLesson("ح", "Ha (Haa)", "حاء", MakhrajZone.AL_HALQ, "Middle throat (epiglottis constriction)", "Hams • Rakhawah • Istifal", "حَمْدُ", "Pure smooth breathy whisper from mid-throat."),
    ArabicLetterLesson("خ", "Kha", "خاء", MakhrajZone.AL_HALQ, "Upper throat (closest to mouth)", "Hams • Rakhawah • Isti'la", "خَيْرٌ", "Heavy elevated back-tongue sound without excessive scraping."),
    ArabicLetterLesson("د", "Dal", "دال", MakhrajZone.AL_LISAN, "Tongue tip against upper incisor gum ridge", "Jahr • Shiddah • Qalqalah", "قَدْ", "Clear light stop; echoes with Qalqalah when silent."),
    ArabicLetterLesson("ذ", "Dhal", "ذال", MakhrajZone.AL_LISAN, "Tongue tip against edge of upper incisors", "Jahr • Rakhawah • Istifal", "ذَٰلِكَ", "Voiced interdental buzz; keep back tongue down."),
    ArabicLetterLesson("ر", "Ra", "راء", MakhrajZone.AL_LISAN, "Top edge of tongue tip near upper gum ridge", "Jahr • Tawassut • Takrir", "رَبِّ", "Heavy with Fathah/Dammah, light with Kasrah; avoid over-trilling."),
    ArabicLetterLesson("ز", "Zay", "زاي", MakhrajZone.AL_LISAN, "Tongue tip near inner lower incisors", "Jahr • Rakhawah • Safeer", "زَكَاةً", "Strong voiced whistling sibilant (Safeer)."),
    ArabicLetterLesson("س", "Seen", "سين", MakhrajZone.AL_LISAN, "Tongue tip near inner lower incisors", "Hams • Rakhawah • Safeer", "سَلَامٌ", "Crisp light whistling 's'; never lift back of tongue."),
    ArabicLetterLesson("ش", "Sheen", "شين", MakhrajZone.AL_LISAN, "Middle tongue raised toward hard palate", "Hams • Rakhawah • Tafashshi", "شَكَرَ", "Air spreads softly inside the mouth (Tafashshi)."),
    ArabicLetterLesson("ص", "Sad", "صاد", MakhrajZone.AL_LISAN, "Tip near lower incisors + elevated back tongue", "Hams • Isti'la • Itbaq • Safeer", "صِرَاطَ", "Heavy cupped tongue creating deep whistling resonance."),
    ArabicLetterLesson("ض", "Dad", "ضاد", MakhrajZone.AL_LISAN, "Side edge(s) of tongue against upper molars", "Jahr • Rakhawah • Istitalah", "ضَالِّينَ", "Unique Arabic letter; elongate side-tongue pressure smoothly."),
    ArabicLetterLesson("ط", "Ta (Heavy)", "طاء", MakhrajZone.AL_LISAN, "Tip against upper gums + full tongue elevation", "Jahr • Shiddah • Isti'la • Qalqalah", "طَيِّبًا", "Strongest heavy letter; zero whisper air; bold Qalqalah."),
    ArabicLetterLesson("ظ", "Dha (Heavy)", "ظاء", MakhrajZone.AL_LISAN, "Tongue tip at upper teeth edge + raised back tongue", "Jahr • Rakhawah • Isti'la • Itbaq", "ظُلُمَاتٍ", "Heavy interdental counterpart to ذ."),
    ArabicLetterLesson("ع", "Ayn", "عين", MakhrajZone.AL_HALQ, "Middle throat gentle constriction", "Jahr • Tawassut • Istifal", "عَالَمِينَ", "Clear voiced pharyngeal tone; flows smoothly without snapping."),
    ArabicLetterLesson("غ", "Ghayn", "غين", MakhrajZone.AL_HALQ, "Upper throat (root of tongue near uvula)", "Jahr • Rakhawah • Isti'la", "غَيْرِ", "Heavy voiced upper-throat articulation."),
    ArabicLetterLesson("ف", "Fa", "فاء", MakhrajZone.ASH_SHAFATAN, "Edge of upper incisors on inner lower lip", "Hams • Rakhawah • Istifal", "فِيهِ", "Gentle continuous air over inner lower lip."),
    ArabicLetterLesson("ق", "Qaf", "قاف", MakhrajZone.AL_LISAN, "Deepest back of tongue touching soft palate/uvula", "Jahr • Shiddah • Isti'la • Qalqalah", "قُلْ", "Deep heavy stop with Qalqalah echo on Sukoon."),
    ArabicLetterLesson("ك", "Kaf", "كاف", MakhrajZone.AL_LISAN, "Back of tongue slightly forward of Qaf", "Hams • Shiddah • Istifal", "كِتَابٌ", "Light stop followed by gentle Hams breath puff on Sukoon."),
    ArabicLetterLesson("ل", "Lam", "لام", MakhrajZone.AL_LISAN, "Front edges of tongue touching upper gum ridge", "Jahr • Tawassut • Inhiraf", "لِلَّهِ", "Always light except in Allah's name after Fathah or Dammah."),
    ArabicLetterLesson("م", "Meem", "ميم", MakhrajZone.ASH_SHAFATAN, "Dry outer lips closing + nasal Khayshum", "Jahr • Tawassut • Ghunnah", "مَالِكِ", "Close lips gently; holds nasal Ghunnah when doubled."),
    ArabicLetterLesson("ن", "Noon", "نون", MakhrajZone.AL_LISAN, "Tongue tip on upper gum + nasal Khayshum", "Jahr • Tawassut • Ghunnah", "نَعْبُدُ", "Oral contact paired with nasal resonance in Ghunnah/Ikhfa."),
    ArabicLetterLesson("ه", "Ha (Light)", "هاء", MakhrajZone.AL_HALQ, "Deepest throat (vocal cords)", "Hams • Rakhawah • Istifal", "هُدًى", "Deep chesty exhale from the lowest throat; keep light."),
    ArabicLetterLesson("و", "Waw", "واو", MakhrajZone.ASH_SHAFATAN, "Rounding both lips leaving small opening", "Jahr • Rakhawah • Leen", "وَلَا", "Round lips forward cleanly without pressing teeth on lip."),
    ArabicLetterLesson("ي", "Ya", "ياء", MakhrajZone.AL_LISAN, "Middle tongue raised toward palate (non-Madd)", "Jahr • Rakhawah • Leen", "يَوْمِ", "Distinct middle-tongue glide without nasalization."),
    ArabicLetterLesson("ء", "Hamzah", "همزة", MakhrajZone.AL_HALQ, "Deepest throat (glottal closure & release)", "Jahr • Shiddah • Istifal", "إِيَّاكَ", "Crisp clean glottal stop without dragging or Qalqalah.")
)

private data class TajweedRuleInfo(
    val title: String,
    val arabicName: String,
    val category: String,
    val beats: String,
    val exampleArabic: String,
    val description: String,
    val color: Color
)

private val TAJWEED_RULES = listOf(
    TajweedRuleInfo("Ghunnah (Nasality)", "الغُنَّة", "Noon & Meem Mushaddah", "2 Beats", "إِنَّ • ثُمَّ", "Hold steady nasal resonance in Al-Khayshum whenever نّ or مّ carries Shaddah.", LightTajweedPalette.ghunnah),
    TajweedRuleInfo("Idgham with Ghunnah", "إِدْغَام بِغُنَّة", "Noon Sakinah / Tanween", "2 Beats", "مَن يَقُولُ", "Merge Noon Sakinah or Tanween smoothly into (ي ن م و) while preserving nasal tone.", LightTajweedPalette.idgham),
    TajweedRuleInfo("Idgham without Ghunnah", "إِدْغَام بِغَيْرِ غُنَّة", "Noon Sakinah / Tanween", "Direct Merge", "مِن رَّبِّهِمْ", "Complete merging into ل or ر with zero nasal resonance.", LightTajweedPalette.idgham),
    TajweedRuleInfo("Ikhfa Haqiqi (Hiding)", "الإِخْفَاء الحَقِيقِي", "Noon Sakinah / Tanween", "2 Beats", "مِن قَبْلِ", "Hover the tongue near the next letter among 15 letters with nasal resonance.", LightTajweedPalette.ikhfa),
    TajweedRuleInfo("Iqlab (Conversion)", "الإِقْلَاب", "Noon Sakinah / Tanween", "2 Beats", "مِنۢ بَعْدِ", "Convert Noon Sakinah or Tanween into a light closed-lip Meem with Ghunnah before ب.", LightTajweedPalette.iqlab),
    TajweedRuleInfo("Izhar Halqi (Clarity)", "الإِظْهَار الحَلْقِي", "Noon Sakinah / Tanween", "Clear Stop", "مِنْ خَوْفٍ", "Pronounce Noon clearly without extra Ghunnah before the 6 throat letters (ء ه ع ح غ خ).", Color(0xFF0277BD)),
    TajweedRuleInfo("Meem Sakinah Rules", "أَحْكَام المِيم السَّاكِنَة", "Meem Sakinah", "0 / 2 Beats", "تَرْمِيهِم بِحِجَارَةٍ", "Ikhfa Shafawi before ب (2 beats), Idgham Shafawi before م (2 beats), and clear Izhar before all other 26 letters.", LightTajweedPalette.ikhfa),
    TajweedRuleInfo("Qalqalah (Echo)", "القَلْقَلَة", "Echoing Stops", "Instant Bounce", "قُلْ هُوَ اللَّهُ أَحَدٌ", "Echoing release on letters (ق ط ب ج د) when carrying Sukoon—stronger at verse pauses.", LightTajweedPalette.qalqalah),
    TajweedRuleInfo("Madd (Prolongation)", "المَدّ", "Elongation Rules", "2 / 4-5 / 6 Beats", "وَلَا الضَّالِّينَ", "Natural Madd (2 beats), Connected Muttasil (4-5), Separated Munfasil (4-5), and Compulsory Lazim (6 beats).", LightTajweedPalette.madd),
    TajweedRuleInfo("Tafkheem & Tarqeeq", "التَّفْخِيم وَالتَّرْقِيق", "Heaviness & Lightness", "Vocal Resonance", "خَصَّ ضَغْطٍ قِظْ", "Seven letters (خ ص ض غ ط ق ظ) are always heavy; Ra (ر) & Lam in 'Allah' vary by preceding vowel.", Color(0xFF5D4037)),
    TajweedRuleInfo("Lam Shamsiyyah & Qamariyyah", "اللَّام الشَّمْسِيَّة وَالقَمَرِيَّة", "Definite Article ال", "Assimilation", "الشَّمْسُ • القَمَرُ", "Solar Lam assimilates silently into 14 sun letters; Lunar Lam is clearly articulated before 14 moon letters.", Color(0xFF2E7D32))
)

private data class WaqfSignInfo(
    val symbol: String,
    val nameEn: String,
    val nameAr: String,
    val ruling: String,
    val color: Color,
    val exampleRef: String,
    val descriptionEn: String
)

private val WAQF_SIGNS = listOf(
    WaqfSignInfo("مـ", "Compulsory Stop", "الوقف اللازم", "MUST STOP", Color(0xFFC62828), "Surah 6:36", "Stopping here is mandatory because continuing would merge two unrelated clauses and distort meaning."),
    WaqfSignInfo("لا", "Prohibited Stop", "الوقف الممنوع", "DO NOT STOP", Color(0xFFD84315), "Surah 16:32", "Do not pause here intentionally—the grammatical phrase continues. If out of breath, step back 1-2 words and join."),
    WaqfSignInfo("ج", "Permissible Stop", "الوقف الجائز", "STOP OR CONTINUE", Color(0xFF00838F), "Surah 2:10", "Stopping and continuing carry equal grammatical completeness; either choice is fully sound."),
    WaqfSignInfo("قلى", "Stop Preferred", "الوقف أولى", "STOP BETTER", Color(0xFF1565C0), "Surah 2:6", "Continuing is permissible, yet pausing at this mark completes the intended meaning better."),
    WaqfSignInfo("صلى", "Continue Preferred", "الوصل أولى", "CONTINUE BETTER", Color(0xFF2E7D32), "Surah 2:14", "Stopping is permissible if needed, though continuing smoothly preserves the connected thought better."),
    WaqfSignInfo("∴ ∴", "Embracing Pause", "وقف المعانقة", "STOP AT ONE ONLY", Color(0xFF6A1B9A), "Surah 2:2", "Appears in pairs (ذَٰلِكَ الْكِتَابُ لَا رَيْبَ ۛ فِيهِ ۛ). You may stop at one mark, never both."),
    WaqfSignInfo("س", "Saktah (Silent Pause)", "السَّكْتَة", "PAUSE NO BREATH", Color(0xFFE65100), "Surah 18:1 / 36:52", "Pause briefly for ~1–2 beats in complete silence WITHOUT taking a fresh breath, then resume."),
    WaqfSignInfo("۩", "Sajdah Tilawah", "سجدة التلاوة", "PROSTRATION", Color(0xFF00695C), "Surah 96:19", "Marks a verse of prostration in Al-Quran. Perform one brief prostration upon reciting or listening.")
)

private data class HarakatOption(val label: String, val mark: String, val arabicName: String)

private val HARAKAT_OPTIONS = listOf(
    HarakatOption("Base", "", "مجرد"),
    HarakatOption("Fathah", "\u064E", "فَتْحَة"),
    HarakatOption("Kasrah", "\u0650", "كَسْرَة"),
    HarakatOption("Dammah", "\u064F", "ضَمَّة"),
    HarakatOption("Sukoon", "\u0652", "سُكُون"),
    HarakatOption("Tanween", "\u064B", "تَنْوِين"),
    HarakatOption("Shaddah", "\u0651\u064E", "شَدَّة")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TajweedGuideSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedZone by remember { mutableStateOf(MakhrajZone.AL_HALQ) }
    var selectedLetter by remember { mutableStateOf(ARABIC_29_LETTERS.first()) }
    var selectedHarakat by remember { mutableStateOf(HARAKAT_OPTIONS.first()) }

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.forLanguageTag("ar")
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

    fun speakArabic(text: String, rate: Float = 1.0f) {
        if (ttsReady) {
            tts.setSpeechRate(rate)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tajweed_${System.nanoTime()}")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.tajweed_academy_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                listOf(
                    stringResource(R.string.tajweed_tab_makharij),
                    stringResource(R.string.tajweed_tab_letters),
                    stringResource(R.string.tajweed_tab_rules),
                    stringResource(R.string.tajweed_tab_waqf)
                ).forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("tajweed_academy_tab_$idx")
                    )
                }
            }

            when (selectedTab) {
                0 -> MakharijVisualizerTab(
                    selectedZone = selectedZone,
                    onSelectZone = { selectedZone = it },
                    onSpeakLetter = { speakArabic(it, 0.85f) }
                )
                1 -> Arabic29LettersTab(
                    selectedLetter = selectedLetter,
                    selectedHarakat = selectedHarakat,
                    onSelectLetter = { selectedLetter = it },
                    onSelectHarakat = { selectedHarakat = it },
                    onSpeak = { text, rate -> speakArabic(text, rate) }
                )
                2 -> TajweedRulesTab()
                else -> WaqfSignsTab()
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakharijVisualizerTab(
    selectedZone: MakhrajZone,
    onSelectZone: (MakhrajZone) -> Unit,
    onSpeakLetter: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Interactive Vocal Tract (مخارج الحروف)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    VocalTractCanvas(
                        activeZone = selectedZone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MakhrajZone.entries.forEach { zone ->
                            FilterChip(
                                selected = selectedZone == zone,
                                onClick = { onSelectZone(zone) },
                                label = { Text("${zone.titleAr} • ${zone.titleEn.substringBefore(" (")}") },
                                modifier = Modifier.testTag("makhraj_zone_${zone.name}")
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = selectedZone.accentColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, selectedZone.accentColor.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedZone.titleEn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = selectedZone.accentColor,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = selectedZone.titleAr,
                            fontFamily = arabicScriptFont(QuranScriptType.UTHMANI),
                            fontSize = 22.sp,
                            color = selectedZone.accentColor
                        )
                    }
                    Text(
                        text = selectedZone.subRegions,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectedZone.descriptionEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    Text(
                        text = "Tap any letter below to hear its pronunciation:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedZone.letters.forEach { letter ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, selectedZone.accentColor.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .size(width = 50.dp, height = 48.dp)
                                    .clickable { onSpeakLetter(letter) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = letter,
                                        fontFamily = arabicScriptFont(QuranScriptType.UTHMANI),
                                        fontSize = 24.sp,
                                        color = selectedZone.accentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VocalTractCanvas(
    activeZone: MakhrajZone,
    modifier: Modifier = Modifier
) {
    val baseLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val tongueFill = Color(0xFFE57373).copy(alpha = 0.25f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Background rounded schematic frame
        drawRoundRect(
            color = surfaceBg,
            cornerRadius = CornerRadius(24f, 24f),
            size = Size(w, h)
        )

        // Outer head/palate contour (facing left)
        val palatePath = Path().apply {
            moveTo(w * 0.16f, h * 0.42f) // upper lip outer
            cubicTo(w * 0.24f, h * 0.34f, w * 0.42f, h * 0.28f, w * 0.58f, h * 0.30f) // hard + soft palate
            cubicTo(w * 0.72f, h * 0.33f, w * 0.78f, h * 0.50f, w * 0.78f, h * 0.92f) // back pharyngeal wall
        }
        drawPath(palatePath, color = baseLineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

        // Nasal cavity (Al-Khayshum)
        val nasalColor = if (activeZone == MakhrajZone.AL_KHAYSHUM) activeZone.accentColor else baseLineColor.copy(alpha = 0.3f)
        val nasalPath = Path().apply {
            moveTo(w * 0.18f, h * 0.26f)
            cubicTo(w * 0.35f, h * 0.12f, w * 0.62f, h * 0.14f, w * 0.70f, h * 0.34f)
            lineTo(w * 0.56f, h * 0.29f)
            cubicTo(w * 0.42f, h * 0.24f, w * 0.28f, h * 0.27f, w * 0.18f, h * 0.26f)
            close()
        }
        drawPath(
            nasalPath,
            color = if (activeZone == MakhrajZone.AL_KHAYSHUM) nasalColor.copy(alpha = 0.32f) else nasalColor.copy(alpha = 0.1f)
        )
        drawPath(nasalPath, color = nasalColor, style = Stroke(width = if (activeZone == MakhrajZone.AL_KHAYSHUM) 5f else 2.5f))

        // Throat channel (Al-Halq)
        val throatActive = activeZone == MakhrajZone.AL_HALQ
        drawRoundRect(
            color = if (throatActive) activeZone.accentColor.copy(alpha = 0.28f) else baseLineColor.copy(alpha = 0.08f),
            topLeft = Offset(w * 0.62f, h * 0.42f),
            size = Size(w * 0.14f, h * 0.48f),
            cornerRadius = CornerRadius(18f, 18f)
        )
        if (throatActive) {
            listOf(0.50f, 0.66f, 0.82f).forEach { yFrac ->
                drawCircle(
                    color = activeZone.accentColor,
                    radius = 9f,
                    center = Offset(w * 0.69f, h * yFrac)
                )
            }
        }

        // Oral empty cavity glow (Al-Jawf)
        if (activeZone == MakhrajZone.AL_JAWF) {
            drawOval(
                color = activeZone.accentColor.copy(alpha = 0.26f),
                topLeft = Offset(w * 0.26f, h * 0.32f),
                size = Size(w * 0.38f, h * 0.28f)
            )
            drawOval(
                color = activeZone.accentColor,
                topLeft = Offset(w * 0.26f, h * 0.32f),
                size = Size(w * 0.38f, h * 0.28f),
                style = Stroke(width = 4f)
            )
        }

        // Tongue silhouette (Al-Lisan)
        val tongueActive = activeZone == MakhrajZone.AL_LISAN
        val tonguePath = Path().apply {
            moveTo(w * 0.22f, h * 0.62f)
            cubicTo(w * 0.32f, h * 0.50f, w * 0.52f, h * 0.48f, w * 0.62f, h * 0.68f)
            lineTo(w * 0.62f, h * 0.86f)
            lineTo(w * 0.24f, h * 0.78f)
            close()
        }
        drawPath(
            tonguePath,
            color = if (tongueActive) activeZone.accentColor.copy(alpha = 0.30f) else tongueFill
        )
        drawPath(
            tonguePath,
            color = if (tongueActive) activeZone.accentColor else baseLineColor,
            style = Stroke(width = if (tongueActive) 5f else 3f, cap = StrokeCap.Round)
        )
        if (tongueActive) {
            listOf(Offset(w * 0.25f, h * 0.60f), Offset(w * 0.42f, h * 0.53f), Offset(w * 0.56f, h * 0.57f)).forEach { pt ->
                drawCircle(color = activeZone.accentColor, radius = 8f, center = pt)
            }
        }

        // Upper & lower teeth
        drawRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(w * 0.19f, h * 0.43f),
            size = Size(w * 0.03f, h * 0.08f)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(w * 0.19f, h * 0.66f),
            size = Size(w * 0.03f, h * 0.08f)
        )

        // Two lips (Ash-Shafatan)
        val lipsActive = activeZone == MakhrajZone.ASH_SHAFATAN
        val lipColor = if (lipsActive) activeZone.accentColor else baseLineColor
        drawCircle(
            color = if (lipsActive) lipColor.copy(alpha = 0.38f) else lipColor.copy(alpha = 0.18f),
            radius = 16f,
            center = Offset(w * 0.15f, h * 0.47f)
        )
        drawCircle(
            color = if (lipsActive) lipColor.copy(alpha = 0.38f) else lipColor.copy(alpha = 0.18f),
            radius = 16f,
            center = Offset(w * 0.15f, h * 0.68f)
        )
        if (lipsActive) {
            drawCircle(color = lipColor, radius = 16f, center = Offset(w * 0.15f, h * 0.47f), style = Stroke(width = 4f))
            drawCircle(color = lipColor, radius = 16f, center = Offset(w * 0.15f, h * 0.68f), style = Stroke(width = 4f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Arabic29LettersTab(
    selectedLetter: ArabicLetterLesson,
    selectedHarakat: HarakatOption,
    onSelectLetter: (ArabicLetterLesson) -> Unit,
    onSelectHarakat: (HarakatOption) -> Unit,
    onSpeak: (String, Float) -> Unit
) {
    val drillForm = remember(selectedLetter, selectedHarakat) {
        if (selectedLetter.letter == "ا" && selectedHarakat.mark.isNotEmpty()) {
            "أ${selectedHarakat.mark}"
        } else {
            "${selectedLetter.letter}${selectedHarakat.mark}"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedLetter.nameEn} (${selectedLetter.nameAr})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedLetter.makhrajZone.titleAr} • ${selectedLetter.specificPoint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = selectedLetter.makhrajZone.accentColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = drillForm,
                                fontFamily = arabicScriptFont(QuranScriptType.UTHMANI),
                                fontSize = 42.sp,
                                color = selectedLetter.makhrajZone.accentColor,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HARAKAT_OPTIONS.forEach { harakat ->
                            FilterChip(
                                selected = selectedHarakat == harakat,
                                onClick = {
                                    onSelectHarakat(harakat)
                                    val spoken = if (selectedLetter.letter == "ا" && harakat.mark.isNotEmpty()) {
                                        "أ${harakat.mark}"
                                    } else {
                                        "${selectedLetter.letter}${harakat.mark}"
                                    }
                                    onSpeak(spoken, 0.9f)
                                },
                                label = { Text("${harakat.label} (${harakat.arabicName})") }
                            )
                        }
                    }

                    Text(
                        text = "Sifaat: ${selectedLetter.sifaat}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Quranic Practice Word: ${selectedLetter.quranExample} — ${selectedLetter.tipEn}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSpeak(drillForm, 0.95f) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("letter_speak_normal")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Listen Letter")
                        }
                        FilledTonalButton(
                            onClick = { onSpeak(selectedLetter.quranExample, 0.70f) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("letter_speak_example")
                        ) {
                            Icon(Icons.Default.SlowMotionVideo, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Slow Example")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "All 29 Arabic Alphabet Letters (Tap to Drill)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ARABIC_29_LETTERS.forEach { item ->
                    val isSelected = item.letter == selectedLetter.letter
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            item.makhrajZone.accentColor.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = if (isSelected) BorderStroke(1.5.dp, item.makhrajZone.accentColor) else null,
                        modifier = Modifier
                            .size(width = 62.dp, height = 62.dp)
                            .testTag("arabic_letter_${item.nameEn}")
                            .clickable {
                                onSelectLetter(item)
                                onSpeak(item.letter, 0.9f)
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = item.letter,
                                fontFamily = arabicScriptFont(QuranScriptType.UTHMANI),
                                fontSize = 24.sp,
                                color = if (isSelected) item.makhrajZone.accentColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.nameEn.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TajweedRulesTab() {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(TAJWEED_RULES) { rule ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = rule.color,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(16.dp)
                    ) {}
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rule.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = rule.arabicName,
                                fontFamily = arabicScriptFont(QuranScriptType.UTHMANI),
                                fontSize = 19.sp,
                                color = rule.color
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${rule.category} • ${rule.beats}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Example: ${rule.exampleArabic}",
                            fontFamily = arabicScriptFont(QuranScriptType.UTHMANI),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaqfSignsTab() {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(WAQF_SIGNS) { waqf ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = waqf.color.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, waqf.color.copy(alpha = 0.45f)),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = waqf.symbol,
                                fontFamily = arabicScriptFont(QuranScriptType.UTHMANI),
                                fontSize = 26.sp,
                                color = waqf.color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = waqf.nameEn,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = waqf.color.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = waqf.ruling,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = waqf.color,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${waqf.nameAr} • ${waqf.exampleRef}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = waqf.descriptionEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
