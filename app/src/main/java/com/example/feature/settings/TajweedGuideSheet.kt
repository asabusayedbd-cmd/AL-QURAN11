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
import com.example.ui.theme.banglaSafeBodyStyle
import java.util.Locale

private enum class MakhrajZone(
    val titleEn: String,
    val titleBn: String,
    val titleAr: String,
    val subRegions: String,
    val subRegionsBn: String,
    val descriptionEn: String,
    val descriptionBn: String,
    val letters: List<String>,
    val accentColor: Color
) {
    AL_JAWF(
        titleEn = "Al-Jawf (Oral & Throat Cavity)",
        titleBn = "আল-জাওফ (মুখ ও কণ্ঠের শূন্যস্থান)",
        titleAr = "الجَوْف",
        subRegions = "Empty space of mouth & throat",
        subRegionsBn = "মুখগহ্বর ও কণ্ঠনালীর শূন্যস্থান",
        descriptionEn = "Open vocal tract producing elongated Madd vowels: Alif (after Fathah), Waw Sakinah (after Dammah), Ya Sakinah (after Kasrah).",
        descriptionBn = "মুখ ও গলার ভেতরের শূন্যস্থান থেকে ৩টি মাদ্দের হরফ উচ্চারিত হয়: ফাতহার পর আলিফ, দাম্মাহর পর ওয়াও সাকিন ও কাসরাহর পর ইয়া সাকিন।",
        letters = listOf("ا", "و", "ي"),
        accentColor = Color(0xFF00897B)
    ),
    AL_HALQ(
        titleEn = "Al-Halq (The Throat)",
        titleBn = "আল-হালক (কণ্ঠনালী)",
        titleAr = "الحَلْق",
        subRegions = "Deep (أقصى) • Middle (وسط) • Upper (أدنى)",
        subRegionsBn = "নিম্ন কণ্ঠ (أقصى) • মধ্য কণ্ঠ (وسط) • ঊর্ধ্ব কণ্ঠ (أدنى)",
        descriptionEn = "Three throat levels: Deepest throat (ء هـ), Middle pharynx (ع ح), and Upper throat near uvula (غ خ).",
        descriptionBn = "কণ্ঠনালীর ৩টি স্তর: কণ্ঠনালীর শেষভাগ (ء هـ), মধ্যভাগ (ع ح) এবং মুখের নিকটবর্তী ঊর্ধ্বভাগ (غ خ)।",
        letters = listOf("ء", "ه", "ع", "ح", "غ", "خ"),
        accentColor = Color(0xFFE65100)
    ),
    AL_LISAN(
        titleEn = "Al-Lisan (The Tongue)",
        titleBn = "আল-লিসান (জিহ্বা)",
        titleAr = "اللِّسَان",
        subRegions = "10 sub-points from root to tip & sides",
        subRegionsBn = "জিহ্বার গোড়া, মধ্যভাগ, কিনারা ও ডগাসহ ১০টি মাখরাজ",
        descriptionEn = "Articulates 18 letters across root (ق ك), center (ج ش ي), sides (ض), edges (ل ن ر), alveolar (ط د ت / ص ز س), and tip with teeth (ظ ذ ث).",
        descriptionBn = "জিহ্বা থেকে ১৮টি হরফ উচ্চারিত হয়: গোড়া (ق ك), মধ্যভাগ (ج ش ي), পাশ (ض), কিনারা ও ডগা (ل ن ر), মাড়ি (ط د ت / ص ز س) এবং সামনের দাঁতের প্রান্ত (ظ ذ ث)।",
        letters = listOf("ق", "ك", "ج", "ش", "ي", "ض", "ل", "ن", "ر", "ط", "د", "ت", "ص", "ز", "س", "ظ", "ذ", "ث"),
        accentColor = Color(0xFF1565C0)
    ),
    ASH_SHAFATAN(
        titleEn = "Ash-Shafatan (The Two Lips)",
        titleBn = "আশ-শাফাতান (দুই ঠোঁট)",
        titleAr = "الشَّفَتَان",
        subRegions = "Inner lower lip + Both lips",
        subRegionsBn = "নিচের ঠোঁটের পেট ও উভয় ঠোঁট",
        descriptionEn = "Upper front teeth touch inner wet lower lip for (ف), while both lips meet or round for (ب م و).",
        descriptionBn = "উপরের সামনের দাঁতের প্রান্ত নিচের ঠোঁটের ভেতরের অংশে লেগে (ف) এবং দুই ঠোঁটের সাহায্যে (ب م و) উচ্চারিত হয়।",
        letters = listOf("ف", "ب", "م", "و"),
        accentColor = Color(0xFF6A1B9A)
    ),
    AL_KHAYSHUM(
        titleEn = "Al-Khayshum (Nasal Passage)",
        titleBn = "আল-খাইশূম (নাকের বাঁশি)",
        titleAr = "الخَيْشُوم",
        subRegions = "Nasal cavity behind nose bridge",
        subRegionsBn = "নাসিকা গহ্বর (গুন্নাহ উচ্চারণের স্থান)",
        descriptionEn = "Produces Ghunnah (2-beat nasal resonance) accompanying Noon (ن) and Meem (م) in Mushaddah, Idgham, Ikhfa, and Iqlab.",
        descriptionBn = "নাকের বাঁশি থেকে নূন (ن) ও মীম (م)-এর গুন্নাহ (২ আলিফ পরিমাণ নাকে সুর করে উচ্চারণ) মুশাদ্দাদ, ইদগাম, ইখফা ও ইকলাবে বের হয়।",
        letters = listOf("ن", "م"),
        accentColor = Color(0xFFC2185B)
    )
}

private data class ArabicLetterLesson(
    val letter: String,
    val nameEn: String,
    val nameBn: String,
    val nameAr: String,
    val makhrajZone: MakhrajZone,
    val specificPoint: String,
    val specificPointBn: String,
    val sifaat: String,
    val sifaatBn: String,
    val quranExample: String,
    val tipEn: String,
    val tipBn: String
)

private val ARABIC_29_LETTERS = listOf(
    ArabicLetterLesson("ا", "Alif", "আলিফ", "ألف", MakhrajZone.AL_JAWF, "Empty oral & throat cavity", "মুখ ও কণ্ঠের শূন্যস্থান", "Jahr • Rakhawah • Istifal", "জাহর • রাখওয়াহ • ইস্তিফাল (পাতলা)", "قَالَ", "Keep mouth open vertically without nasalizing.", "নাকে না নিয়ে মুখ লম্বালম্বি খুলে টানুন।"),
    ArabicLetterLesson("ب", "Ba", "বা", "باء", MakhrajZone.ASH_SHAFATAN, "Wet part of both lips closing", "দুই ঠোঁটের ভেজা অংশ মিলিয়ে", "Jahr • Shiddah • Qalqalah", "জাহর • শিদ্দাহ • কল্কলাহ", "بِسْمِ", "Firm lip closure; bounce cleanly on Sukoon (Qalqalah).", "সুকুন থাকলে দুই ঠোঁট মিলিয়ে স্পষ্ট কল্কলাহ করুন।"),
    ArabicLetterLesson("ت", "Ta", "তা", "تاء", MakhrajZone.AL_LISAN, "Tongue tip against roots of upper incisors", "জিহ্বার ডগা উপরের সামনের ২ দাঁতের গোড়ায়", "Hams • Shiddah • Istifal", "হামস • শিদ্দাহ • পাতলা", "تَبَّتْ", "Light breath release (Hams) on Sukoon; never heavy.", "সুকুনে হালকা বাতাস ছাড়ুন; মোটা করবেন না।"),
    ArabicLetterLesson("ث", "Tha", "ছা", "ثاء", MakhrajZone.AL_LISAN, "Tongue tip touching bottom edge of upper incisors", "জিহ্বার ডগা উপরের দাঁতের আগায়", "Hams • Rakhawah • Istifal", "হামস • রাখওয়াহ • পাতলা", "ثُمَّ", "Soft interdental airflow; distinct from 's'.", "দাঁতের আগায় জিহ্বা ছুঁয়ে নরমভাবে পড়ুন, 'স' নয়।"),
    ArabicLetterLesson("ج", "Jeem", "জীম", "جيم", MakhrajZone.AL_LISAN, "Middle tongue firmly touches hard palate", "জিহ্বার মধ্যভাগ উপরের তালুতে লাগিয়ে", "Jahr • Shiddah • Qalqalah", "জাহর • শিদ্দাহ • কল্কলাহ", "جَعَلَ", "Full mid-tongue contact without leaking air like French 'j'.", "জিহ্বার মাঝখান তালুতে দৃঢ়ভাবে লাগিয়ে বাতাস আটকে পড়ুন।"),
    ArabicLetterLesson("ح", "Ha (Haa)", "হা (বড় হা)", "حاء", MakhrajZone.AL_HALQ, "Middle throat (epiglottis constriction)", "কণ্ঠনালীর মধ্যভাগ", "Hams • Rakhawah • Istifal", "হামস • রাখওয়াহ • পাতলা", "حَمْدُ", "Pure smooth breathy whisper from mid-throat.", "গলার মাঝখান থেকে পরিষ্কার ঘর্ষণহীন বাতাসসহ উচ্চারণ করুন।"),
    ArabicLetterLesson("خ", "Kha", "খা", "خاء", MakhrajZone.AL_HALQ, "Upper throat (closest to mouth)", "কণ্ঠনালীর উপরের অংশ (মুখের কাছে)", "Hams • Rakhawah • Isti'la", "হামস • রাখওয়াহ • ইস্তি‘লা (মোটা)", "خَيْرٌ", "Heavy elevated back-tongue sound without excessive scraping.", "অতিরিক্ত ঘষা না দিয়ে গলার উপরের অংশ থেকে মোটা করে পড়ুন।"),
    ArabicLetterLesson("د", "Dal", "দাল", "دال", MakhrajZone.AL_LISAN, "Tongue tip against upper incisor gum ridge", "জিহ্বার ডগা উপরের ২ দাঁতের গোড়ায়", "Jahr • Shiddah • Qalqalah", "জাহর • শিদ্দাহ • কল্কলাহ", "قَدْ", "Clear light stop; echoes with Qalqalah when silent.", "পাতলা ও স্পষ্ট; সুকুন থাকলে কল্কলাহ করে থামুন।"),
    ArabicLetterLesson("ذ", "Dhal", "যাল", "ذال", MakhrajZone.AL_LISAN, "Tongue tip against edge of upper incisors", "জিহ্বার ডগা উপরের দাঁতের আগায়", "Jahr • Rakhawah • Istifal", "জাহর • রাখওয়াহ • পাতলা", "ذَٰلِكَ", "Voiced interdental buzz; keep back tongue down.", "জিহ্বা নরমভাবে দাঁতের আগায় ছুঁয়ে ধ্বনিসহ উচ্চারণ করুন।"),
    ArabicLetterLesson("ر", "Ra", "রা", "راء", MakhrajZone.AL_LISAN, "Top edge of tongue tip near upper gum ridge", "জিহ্বার ডগার পিঠ উপরের মাড়িতে", "Jahr • Tawassut • Takrir", "জাহর • তাওয়াসসুত • তাকরীর", "رَبِّ", "Heavy with Fathah/Dammah, light with Kasrah; avoid over-trilling.", "যবর/পেশে মোটা, যেরে পাতলা; জিহ্বা বেশি কাঁপাবেন না।"),
    ArabicLetterLesson("ز", "Zay", "যা", "زاي", MakhrajZone.AL_LISAN, "Tongue tip near inner lower incisors", "জিহ্বার ডগা নিচের দাঁতের ভেতরের পাড়িতে", "Jahr • Rakhawah • Safeer", "জাহর • রাখওয়াহ • সাফীর (শিস)", "زَكَاةً", "Strong voiced whistling sibilant (Safeer).", "স্পষ্ট ধ্বনি ও মৃদু শিসসহ উচ্চারণ করুন।"),
    ArabicLetterLesson("س", "Seen", "সীন", "سين", MakhrajZone.AL_LISAN, "Tongue tip near inner lower incisors", "জিহ্বার ডগা নিচের দাঁতের ভেতরে", "Hams • Rakhawah • Safeer", "হামস • রাখওয়াহ • সাফীর (পাতলা)", "سَلَامٌ", "Crisp light whistling 's'; never lift back of tongue.", "পাতলা ও তীক্ষ্ণ শিস; জিহ্বার গোড়া তুলবেন না।"),
    ArabicLetterLesson("ش", "Sheen", "শীন", "شين", MakhrajZone.AL_LISAN, "Middle tongue raised toward hard palate", "জিহ্বার মধ্যভাগ তালুর দিকে তুলে", "Hams • Rakhawah • Tafashshi", "হামস • রাখওয়াহ • তাফাশশী", "شَكَرَ", "Air spreads softly inside the mouth (Tafashshi).", "মুখের ভেতরে বাতাস ছড়িয়ে নরমভাবে পড়ুন।"),
    ArabicLetterLesson("ص", "Sad", "সোয়াদ", "صاد", MakhrajZone.AL_LISAN, "Tip near lower incisors + elevated back tongue", "নিচের দাঁতের কাছে ডগা ও জিহ্বার গোড়া উঁচু", "Hams • Isti'la • Itbaq • Safeer", "হামস • ইস্তি‘লা (মোটা) • ইতবাক • সাফীর", "صِرَاطَ", "Heavy cupped tongue creating deep whistling resonance.", "মুখ ভরে মোটা ও গম্ভীর শিসের আওয়াজ বের করুন।"),
    ArabicLetterLesson("ض", "Dad", "দোয়াদ", "ضاد", MakhrajZone.AL_LISAN, "Side edge(s) of tongue against upper molars", "জিহ্বার পাশ উপরের মাড়ির দাঁতের গোড়ায়", "Jahr • Rakhawah • Istitalah", "জাহর • রাখওয়াহ • ইস্তিতালাহ (মোটা)", "ضَالِّينَ", "Unique Arabic letter; elongate side-tongue pressure smoothly.", "মাড়ির দাঁতে জিহ্বার কিনারা চেপে মোটা ও দীর্ঘ আওয়াজে পড়ুন।"),
    ArabicLetterLesson("ط", "Ta (Heavy)", "তোয়া", "طاء", MakhrajZone.AL_LISAN, "Tip against upper gums + full tongue elevation", "জিহ্বার ডগা উপরের মাড়িতে + পুরো জিহ্বা উঁচু", "Jahr • Shiddah • Isti'la • Qalqalah", "জাহর • শিদ্দাহ • মোটা • কল্কলাহ", "طَيِّبًا", "Strongest heavy letter; zero whisper air; bold Qalqalah.", "সবচেয়ে শক্তিশালী মোটা হরফ; বাতাস ছাড়বেন না; সুকুনে কল্কলাহ করুন।"),
    ArabicLetterLesson("ظ", "Dha (Heavy)", "যোয়া", "ظاء", MakhrajZone.AL_LISAN, "Tongue tip at upper teeth edge + raised back tongue", "জিহ্বার ডগা উপরের দাঁতের আগায় + জিহ্বার গোড়া উঁচু", "Jahr • Rakhawah • Isti'la • Itbaq", "জাহর • রাখওয়াহ • মোটা • ইতবাক", "ظُلُمَاتٍ", "Heavy interdental counterpart to ذ.", "ذ-এর মতোই অবস্থান, তবে মুখ ভরে মোটা আওয়াজে পড়ুন।"),
    ArabicLetterLesson("ع", "Ayn", "আইন", "عين", MakhrajZone.AL_HALQ, "Middle throat gentle constriction", "কণ্ঠনালীর মধ্যভাগ চেপে", "Jahr • Tawassut • Istifal", "জাহর • তাওয়াসসুত • পাতলা", "عَالَمِينَ", "Clear voiced pharyngeal tone; flows smoothly without snapping.", "মধ্য-কণ্ঠ থেকে গভীর ও মসৃণভাবে উচ্চারণ করুন।"),
    ArabicLetterLesson("غ", "Ghayn", "গাইন", "غين", MakhrajZone.AL_HALQ, "Upper throat (root of tongue near uvula)", "কণ্ঠনালীর উপরের অংশ", "Jahr • Rakhawah • Isti'la", "জাহর • রাখওয়াহ • মোটা", "غَيْرِ", "Heavy voiced upper-throat articulation.", "গলার উপরের অংশ থেকে মোটা ও ধ্বনিসহ পড়ুন।"),
    ArabicLetterLesson("ف", "Fa", "ফা", "فاء", MakhrajZone.ASH_SHAFATAN, "Edge of upper incisors on inner lower lip", "উপরের সামনের দাঁত নিচের ঠোঁটের ভেতরে", "Hams • Rakhawah • Istifal", "হামস • রাখওয়াহ • পাতলা", "فِيهِ", "Gentle continuous air over inner lower lip.", "নিচের ঠোঁটের ভেজা অংশে দাঁত লাগিয়ে কোমল বাতাস ছাড়ুন।"),
    ArabicLetterLesson("ق", "Qaf", "ক্বফ", "قاف", MakhrajZone.AL_LISAN, "Deepest back of tongue touching soft palate/uvula", "জিহ্বার একেবারে গোড়া উপরের নরম তালুতে", "Jahr • Shiddah • Isti'la • Qalqalah", "জাহর • শিদ্দাহ • মোটা • কল্কলাহ", "قُلْ", "Deep heavy stop with Qalqalah echo on Sukoon.", "জিহ্বার গোড়া থেকে ভারী ও গভীর ধ্বনি; সুকুনে কল্কলাহ করুন।"),
    ArabicLetterLesson("ك", "Kaf", "কাফ", "كاف", MakhrajZone.AL_LISAN, "Back of tongue slightly forward of Qaf", "ক্বফ-এর একটু সামনে জিহ্বার গোড়া ও তালু", "Hams • Shiddah • Istifal", "হামস • শিদ্দাহ • পাতলা", "كِتَابٌ", "Light stop followed by gentle Hams breath puff on Sukoon.", "পাতলা ও স্পষ্ট; সুকুন থাকলে শেষে হালকা বাতাস বের হবে।"),
    ArabicLetterLesson("ل", "Lam", "লাম", "لام", MakhrajZone.AL_LISAN, "Front edges of tongue touching upper gum ridge", "জিহ্বার ডগার কিনারা উপরের মাড়িতে", "Jahr • Tawassut • Inhiraf", "জাহর • তাওয়াসসুত • ইনহিরাফ", "لِلَّهِ", "Always light except in Allah's name after Fathah or Dammah.", "সাধারণত পাতলা; শুধু 'আল্লাহ' শব্দের আগে যবর/পেশ থাকলে মোটা হয়।"),
    ArabicLetterLesson("م", "Meem", "মীম", "ميم", MakhrajZone.ASH_SHAFATAN, "Dry outer lips closing + nasal Khayshum", "দুই ঠোঁটের শুকনো অংশ মিলিয়ে + নাকের বাঁশি", "Jahr • Tawassut • Ghunnah", "জাহর • তাওয়াসসুত • গুন্নাহ", "مَالِكِ", "Close lips gently; holds nasal Ghunnah when doubled.", "ঠোঁট আলতো বন্ধ করে পড়ুন; তাশদীদে ২ আলিফ গুন্নাহ করুন।"),
    ArabicLetterLesson("ن", "Noon", "নূন", "نون", MakhrajZone.AL_LISAN, "Tongue tip on upper gum + nasal Khayshum", "জিহ্বার ডগা উপরের মাড়িতে + নাকের বাঁশি", "Jahr • Tawassut • Ghunnah", "জাহর • তাওয়াসসুত • গুন্নাহ", "نَعْبُدُ", "Oral contact paired with nasal resonance in Ghunnah/Ikhfa.", "গুন্নাহ ও ইখফায় নাকের বাঁশি থেকে ধ্বনি বের করুন।"),
    ArabicLetterLesson("ه", "Ha (Light)", "হা (ছোট হা)", "هاء", MakhrajZone.AL_HALQ, "Deepest throat (vocal cords)", "কণ্ঠনালীর একেবারে শেষভাগ (বুক সংলগ্ন)", "Hams • Rakhawah • Istifal", "হামস • রাখওয়াহ • পাতলা", "هُدًى", "Deep chesty exhale from the lowest throat; keep light.", "কণ্ঠের গভীর থেকে বাতাসসহ হালকাভাবে বের করুন।"),
    ArabicLetterLesson("و", "Waw", "ওয়াও", "واو", MakhrajZone.ASH_SHAFATAN, "Rounding both lips leaving small opening", "দুই ঠোঁট গোল করে সামান্য ফাঁক রেখে", "Jahr • Rakhawah • Leen", "জাহর • রাখওয়াহ • লীন", "وَلَا", "Round lips forward cleanly without pressing teeth on lip.", "ঠোঁট গোল করে পরিষ্কার উচ্চারণ করুন, দাঁত ঠোঁটে লাগাবেন না।"),
    ArabicLetterLesson("ي", "Ya", "ইয়া", "ياء", MakhrajZone.AL_LISAN, "Middle tongue raised toward palate (non-Madd)", "জিহ্বার মধ্যভাগ তালুর দিকে তুলে", "Jahr • Rakhawah • Leen", "জাহর • রাখওয়াহ • লীন", "يَوْمِ", "Distinct middle-tongue glide without nasalization.", "নাকের সাহায্য ছাড়াই জিহ্বার মাঝখান থেকে স্পষ্ট পড়ুন।"),
    ArabicLetterLesson("ء", "Hamzah", "হামযাহ", "همزة", MakhrajZone.AL_HALQ, "Deepest throat (glottal closure & release)", "কণ্ঠনালীর শেষভাগ থেকে হঠাৎ ঝটকায়", "Jahr • Shiddah • Istifal", "জাহর • শিদ্দাহ • পাতলা", "إِيَّاكَ", "Crisp clean glottal stop without dragging or Qalqalah.", "পরিষ্কার ও দৃঢ় কণ্ঠ-থামা উচ্চারণ; কল্কলাহ বা টান দেবেন না।")
)

private data class TajweedRuleInfo(
    val title: String,
    val titleBn: String,
    val arabicName: String,
    val category: String,
    val categoryBn: String,
    val beats: String,
    val beatsBn: String,
    val exampleArabic: String,
    val description: String,
    val descriptionBn: String,
    val color: Color
)

private val TAJWEED_RULES = listOf(
    TajweedRuleInfo("Ghunnah (Nasality)", "গুন্নাহ (নাকে ধ্বনি)", "الغُنَّة", "Noon & Meem Mushaddah", "নূন ও মীম মুশাদ্দাদ", "2 Beats", "২ হারাকাত (১ আলিফ)", "إِنَّ • ثُمَّ", "Hold steady nasal resonance in Al-Khayshum whenever نّ or مّ carries Shaddah.", "নূন (نّ) বা মীম (مّ)-এ তাশদীদ থাকলে নাকের বাঁশিতে ১ আলিফ পরিমাণ গুন্নাহ করে পড়া ওয়াজিব।", LightTajweedPalette.ghunnah),
    TajweedRuleInfo("Idgham with Ghunnah", "ইদগাম বা-গুন্নাহ", "إِدْغَام بِغُنَّة", "Noon Sakinah / Tanween", "নূন সাকিন ও তানভীন", "2 Beats", "২ হারাকাত", "مَن يَقُولُ", "Merge Noon Sakinah or Tanween smoothly into (ي ن م و) while preserving nasal tone.", "নূন সাকিন বা তানভীনের পর (ي ن م و) এলে গুন্নাহসহ পরবর্তী হরফে মিলিয়ে পড়তে হয়।", LightTajweedPalette.idgham),
    TajweedRuleInfo("Idgham without Ghunnah", "ইদগাম বিলা গুন্নাহ", "إِدْغَام بِغَيْرِ غُنَّة", "Noon Sakinah / Tanween", "নূন সাকিন ও তানভীন", "Direct Merge", "সরাসরি মিলন", "مِن رَّبِّهِمْ", "Complete merging into ل or ر with zero nasal resonance.", "নূন সাকিন বা তানভীনের পর (ل র) এলে কোনো গুন্নাহ ছাড়াই পুরোপুরি মিলিয়ে পড়তে হয়।", LightTajweedPalette.idgham),
    TajweedRuleInfo("Ikhfa Haqiqi (Hiding)", "ইখফায়ে হাকীকী", "الإِخْفَاء الحَقِيقِي", "Noon Sakinah / Tanween", "নূন সাকিন ও তানভীন", "2 Beats", "২ হারাকাত", "مِن قَبْلِ", "Hover the tongue near the next letter among 15 letters with nasal resonance.", "ইখফার ১৫টি হরফের কোনো একটি পরে এলে নূনকে গোপন রেখে নাকে গুন্নাহসহ উচ্চারণ করতে হয়।", LightTajweedPalette.ikhfa),
    TajweedRuleInfo("Iqlab (Conversion)", "ইকলাব (পরিবর্তন)", "الإِقْلَاب", "Noon Sakinah / Tanween", "নূন সাকিন ও তানভীন", "2 Beats", "২ হারাকাত", "مِنۢ بَعْدِ", "Convert Noon Sakinah or Tanween into a light closed-lip Meem with Ghunnah before ب.", "নূন সাকিন বা তানভীনের পর (ب) এলে নূনকে মীম দ্বারা পরিবর্তন করে গুন্নাহসহ পড়তে হয়।", LightTajweedPalette.iqlab),
    TajweedRuleInfo("Izhar Halqi (Clarity)", "ইযহারে হালকী (স্পষ্ট)", "الإِظْهَار الحَلْقِي", "Noon Sakinah / Tanween", "নূন সাকিন ও তানভীন", "Clear Stop", "স্পষ্ট উচ্চারণ", "مِنْ خَوْفٍ", "Pronounce Noon clearly without extra Ghunnah before the 6 throat letters (ء ه ع ح غ خ).", "হালকের ৬টি হরফ (ء ه ع ح غ خ) পরে এলে নূনকে কোনো অতিরিক্ত গুন্নাহ ছাড়াই স্পষ্ট পড়তে হয়।", Color(0xFF0277BD)),
    TajweedRuleInfo("Meem Sakinah Rules", "মীম সাকিনের বিধান", "أَحْكَام المِيم السَّاكِنَة", "Meem Sakinah", "মীম সাকিন", "0 / 2 Beats", "০ / ২ হারাকাত", "تَرْمِيهِم بِحِجَارَةٍ", "Ikhfa Shafawi before ب (2 beats), Idgham Shafawi before م (2 beats), and clear Izhar before all other 26 letters.", "মীম সাকিনের পর ب এলে ইখফা, م এলে ইদগাম (গুন্নাহসহ) এবং বাকি ২৬ হরফে স্পষ্ট ইযহার শাফাবী হয়।", LightTajweedPalette.ikhfa),
    TajweedRuleInfo("Qalqalah (Echo)", "কল্কলাহ (প্রতিধ্বনি)", "القَلْقَلَة", "Echoing Stops", "প্রতিধ্বনির হরফ", "Instant Bounce", "তাৎক্ষণিক ধাক্কা", "قُلْ هُوَ اللَّهُ أَحَدٌ", "Echoing release on letters (ق ط ب ج د) when carrying Sukoon—stronger at verse pauses.", "(ق ط ب ج د) এই ৫টি হরফে সুকুন বা ওয়াকফ হলে উচ্চারণে প্রতিধ্বনি বা ধাক্কা দিয়ে পড়তে হয়।", LightTajweedPalette.qalqalah),
    TajweedRuleInfo("Madd (Prolongation)", "মাদ্দ (টেনে পড়া)", "المَدّ", "Elongation Rules", "দীর্ঘ উচ্চারণ", "2 / 4-5 / 6 Beats", "১ / ২-৩ / ৪ আলিফ", "وَلَا الضَّالِّينَ", "Natural Madd (2 beats), Connected Muttasil (4-5), Separated Munfasil (4-5), and Compulsory Lazim (6 beats).", "মাদ্দে আসলী (১ আলিফ), মুত্তাসিল ও মুনফাসিল (৩-৪ আলিফ) এবং মাদ্দে লাযিম (৪ আলিফ বা ৬ হারাকাত) টেনে পড়া হয়।", LightTajweedPalette.madd),
    TajweedRuleInfo("Tafkheem & Tarqeeq", "তাফখীম ও তারকীক", "التَّفْخِيم وَالتَّرْقِيق", "Heaviness & Lightness", "মোটা ও পাতলা", "Vocal Resonance", "স্বর-গাম্ভীর্য", "خَصَّ ضَغْطٍ قِظْ", "Seven letters (خ ص ض غ ط ق ظ) are always heavy; Ra (ر) & Lam in 'Allah' vary by preceding vowel.", "৭টি হরফ (خ ص ض غ ط ق ظ) সর্বদা মোটা হয়; র (ر) ও 'আল্লাহ' শব্দের লাম পূর্ববর্তী হারাকাত অনুযায়ী মোটা বা পাতলা হয়।", Color(0xFF5D4037)),
    TajweedRuleInfo("Lam Shamsiyyah & Qamariyyah", "লামে শামসিয়াহ ও কামারিয়াহ", "اللَّام الشَّمْسِيَّة وَالقَمَرِيَّة", "Definite Article ال", "আলিফ-লাম", "Assimilation", "লীন/স্পষ্ট", "الشَّمْسُ • القَمَرُ", "Solar Lam assimilates silently into 14 sun letters; Lunar Lam is clearly articulated before 14 moon letters.", "১৪টি শামসী হরফে লাম উচ্চারিত না হয়ে পরবর্তী হরফে মিলে যায়; ১৪টি কামারী হরফে লাম স্পষ্ট উচ্চারিত হয়।", Color(0xFF2E7D32))
)

private data class WaqfSignInfo(
    val symbol: String,
    val nameEn: String,
    val nameBn: String,
    val nameAr: String,
    val ruling: String,
    val rulingBn: String,
    val color: Color,
    val exampleRef: String,
    val exampleRefBn: String,
    val descriptionEn: String,
    val descriptionBn: String
)

private val WAQF_SIGNS = listOf(
    WaqfSignInfo("مـ", "Compulsory Stop", "ওয়াকফে লাযিম (বাধ্যতামূলক বিরতি)", "الوقف اللازم", "MUST STOP", "অবশ্যই থামুন", Color(0xFFC62828), "Surah 6:36", "সূরা ৬:৩৬", "Stopping here is mandatory because continuing would merge two unrelated clauses and distort meaning.", "এখানে থামা বাধ্যতামূলক; না থামলে ভিন্ন দুটি বাক্য মিলে গিয়ে অর্থ বিকৃত হয়ে যেতে পারে।"),
    WaqfSignInfo("لا", "Prohibited Stop", "ওয়াকফে মামনূ‘ (থামা নিষেধ)", "الوقف الممنوع", "DO NOT STOP", "থামবেন না", Color(0xFFD84315), "Surah 16:32", "সূরা ১৬:৩২", "Do not pause here intentionally—the grammatical phrase continues. If out of breath, step back 1-2 words and join.", "ইচ্ছাকৃতভাবে এখানে থামবেন না, কারণ বাক্য অসম্পূর্ণ। শ্বাস ফুরালে ১-২ শব্দ পিছিয়ে মিলিয়ে পড়ুন।"),
    WaqfSignInfo("ج", "Permissible Stop", "ওয়াকফে জায়িয (বৈধ বিরতি)", "الوقف الجائز", "STOP OR CONTINUE", "থামা বা চলা সমান", Color(0xFF00838F), "Surah 2:10", "সূরা ২:১০", "Stopping and continuing carry equal grammatical completeness; either choice is fully sound.", "এখানে থামা এবং মিলিয়ে পড়া উভয়ই ব্যাকরণগতভাবে সম্পূর্ণ শুদ্ধ।"),
    WaqfSignInfo("قلى", "Stop Preferred", "আল-ওয়াকফু আওলা (থামা উত্তম)", "الوقف أولى", "STOP BETTER", "থামাই উত্তম", Color(0xFF1565C0), "Surah 2:6", "সূরা ২:৬", "Continuing is permissible, yet pausing at this mark completes the intended meaning better.", "মিলিয়ে পড়া বৈধ, তবে এখানে থামলে আয়াতের অর্থ অধিক সুন্দর ও পূর্ণাঙ্গ হয়।"),
    WaqfSignInfo("صلى", "Continue Preferred", "আল-ওয়াসলু আওলা (মিলিয়ে পড়া উত্তম)", "الوصل أولى", "CONTINUE BETTER", "মিলিয়ে পড়াই উত্তম", Color(0xFF2E7D32), "Surah 2:14", "সূরা ২:১৪", "Stopping is permissible if needed, though continuing smoothly preserves the connected thought better.", "প্রয়োজনে থামা যায়, তবে থেমে না গিয়ে মিলিয়ে পড়া উত্তম।"),
    WaqfSignInfo("∴ ∴", "Embracing Pause", "ওয়াকফে মু‘আনাকাহ", "وقف المعانقة", "STOP AT ONE ONLY", "যেকোনো একটিতে থামুন", Color(0xFF6A1B9A), "Surah 2:2", "সূরা ২:২", "Appears in pairs (ذَٰلِكَ الْكِتَابُ لَا رَيْبَ ۛ فِيهِ ۛ). You may stop at one mark, never both.", "জোড়ায় জোড়ায় আসে। দুটি চিহ্নের যেকোনো একটিতে থামতে পারবেন, দুটিতে নয়।"),
    WaqfSignInfo("س", "Saktah (Silent Pause)", "সাকতাহ (শ্বাস না নিয়ে বিরতি)", "السَّكْتَة", "PAUSE NO BREATH", "শ্বাস না নিয়ে নীরবতা", Color(0xFFE65100), "Surah 18:1 / 36:52", "সূরা ১৮:১ / ৩৬:৫২", "Pause briefly for ~1–2 beats in complete silence WITHOUT taking a fresh breath, then resume.", "নতুন শ্বাস না নিয়ে ১-২ হারাকাত পরিমাণ আওয়াজ বন্ধ রেখে পুনরায় পড়া শুরু করুন।"),
    WaqfSignInfo("۩", "Sajdah Tilawah", "সাজদায়ে তিলাওয়াত", "سجدة التلاوة", "PROSTRATION", "সিজদা ওয়াজিব", Color(0xFF00695C), "Surah 96:19", "সূরা ৯৬:১৯", "Marks a verse of prostration in Al-Quran. Perform one brief prostration upon reciting or listening.", "তিলাওয়াতের সিজদার আয়াত বোঝায়। তিলাওয়াত বা শ্রবণ করলে একটি তিলাওয়াতের সিজদা আদায় করুন।")
)

private data class HarakatOption(val label: String, val labelBn: String, val mark: String, val arabicName: String)

private val HARAKAT_OPTIONS = listOf(
    HarakatOption("Base", "মূল", "", "مجرد"),
    HarakatOption("Fathah", "যবর", "\u064E", "فَتْحَة"),
    HarakatOption("Kasrah", "যের", "\u0650", "كَسْرَة"),
    HarakatOption("Dammah", "পেশ", "\u064F", "ضَمَّة"),
    HarakatOption("Sukoon", "সুকুন", "\u0652", "سُكُون"),
    HarakatOption("Tanween", "তানভীন", "\u064B", "تَنْوِين"),
    HarakatOption("Shaddah", "তাশদীদ", "\u0651\u064E", "شَدَّة")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TajweedGuideSheet(
    uiLanguageCode: String = "en",
    onDismiss: () -> Unit
) {
    val isBn = uiLanguageCode.equals("bn", ignoreCase = true)
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
                com.example.core.audio.TtsVoiceSelector.configureMaleArabicTts(engine)
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
            com.example.core.audio.TtsVoiceSelector.configureMaleArabicTts(tts)
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
                    isBn = isBn,
                    selectedZone = selectedZone,
                    onSelectZone = { selectedZone = it },
                    onSpeakLetter = { speakArabic(it, 0.85f) }
                )
                1 -> Arabic29LettersTab(
                    isBn = isBn,
                    selectedLetter = selectedLetter,
                    selectedHarakat = selectedHarakat,
                    onSelectLetter = { selectedLetter = it },
                    onSelectHarakat = { selectedHarakat = it },
                    onSpeak = { text, rate -> speakArabic(text, rate) }
                )
                2 -> TajweedRulesTab(isBn = isBn)
                else -> WaqfSignsTab(isBn = isBn)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakharijVisualizerTab(
    isBn: Boolean,
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
                        text = if (isBn) "ইন্টারেক্টিভ মাখরাজ মানচিত্র (مخارج الحروف)" else "Interactive Vocal Tract (مخارج الحروف)",
                        style = if (isBn) banglaSafeBodyStyle(16f, "bn") else MaterialTheme.typography.titleMedium,
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
                            val chipTitle = if (isBn) zone.titleBn.substringBefore(" (") else zone.titleEn.substringBefore(" (")
                            FilterChip(
                                selected = selectedZone == zone,
                                onClick = { onSelectZone(zone) },
                                label = { Text("${zone.titleAr} • $chipTitle") },
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
                            text = if (isBn) selectedZone.titleBn else selectedZone.titleEn,
                            style = if (isBn) banglaSafeBodyStyle(16f, "bn") else MaterialTheme.typography.titleMedium,
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
                        text = if (isBn) selectedZone.subRegionsBn else selectedZone.subRegions,
                        style = if (isBn) banglaSafeBodyStyle(13f, "bn") else MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isBn) selectedZone.descriptionBn else selectedZone.descriptionEn,
                        style = if (isBn) banglaSafeBodyStyle(14f, "bn") else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    Text(
                        text = if (isBn) "উচ্চারণ শুনতে নিচের যেকোনো হরফে ট্যাপ করুন:" else "Tap any letter below to hear its pronunciation:",
                        style = if (isBn) banglaSafeBodyStyle(12.5f, "bn") else MaterialTheme.typography.labelMedium,
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
    isBn: Boolean,
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
                                text = "${if (isBn) selectedLetter.nameBn else selectedLetter.nameEn} (${selectedLetter.nameAr})",
                                style = if (isBn) banglaSafeBodyStyle(16f, "bn") else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedLetter.makhrajZone.titleAr} • ${if (isBn) selectedLetter.specificPointBn else selectedLetter.specificPoint}",
                                style = if (isBn) banglaSafeBodyStyle(12.5f, "bn") else MaterialTheme.typography.bodySmall,
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
                                label = { Text("${if (isBn) harakat.labelBn else harakat.label} (${harakat.arabicName})") }
                            )
                        }
                    }

                    Text(
                        text = if (isBn) "সিফাত (গুণাবলী): ${selectedLetter.sifaatBn}" else "Sifaat: ${selectedLetter.sifaat}",
                        style = if (isBn) banglaSafeBodyStyle(13f, "bn") else MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = if (isBn) {
                            "কুরআনের অনুশীলন শব্দ: ${selectedLetter.quranExample} — ${selectedLetter.tipBn}"
                        } else {
                            "Quranic Practice Word: ${selectedLetter.quranExample} — ${selectedLetter.tipEn}"
                        },
                        style = if (isBn) banglaSafeBodyStyle(13f, "bn") else MaterialTheme.typography.bodySmall,
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
                            Text(if (isBn) "হরফটি শুনুন" else "Listen Letter")
                        }
                        FilledTonalButton(
                            onClick = { onSpeak(selectedLetter.quranExample, 0.70f) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("letter_speak_example")
                        ) {
                            Icon(Icons.Default.SlowMotionVideo, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isBn) "ধীর উদাহরণ" else "Slow Example")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (isBn) "আরবি ২৯টি হরফ (অনুশীলনের জন্য ট্যাপ করুন)" else "All 29 Arabic Alphabet Letters (Tap to Drill)",
                style = if (isBn) banglaSafeBodyStyle(14f, "bn") else MaterialTheme.typography.titleSmall,
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
                                text = if (isBn) item.nameBn.substringBefore(" ") else item.nameEn.substringBefore(" "),
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
private fun TajweedRulesTab(isBn: Boolean) {
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
                                text = if (isBn) rule.titleBn else rule.title,
                                style = if (isBn) banglaSafeBodyStyle(15.5f, "bn") else MaterialTheme.typography.titleMedium,
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
                                    text = if (isBn) "${rule.categoryBn} • ${rule.beatsBn}" else "${rule.category} • ${rule.beats}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isBn) rule.descriptionBn else rule.description,
                            style = if (isBn) banglaSafeBodyStyle(13f, "bn") else MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isBn) "উদাহরণ: ${rule.exampleArabic}" else "Example: ${rule.exampleArabic}",
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
private fun WaqfSignsTab(isBn: Boolean) {
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
                                text = if (isBn) waqf.nameBn else waqf.nameEn,
                                style = if (isBn) banglaSafeBodyStyle(15.5f, "bn") else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = waqf.color.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = if (isBn) waqf.rulingBn else waqf.ruling,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = waqf.color,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isBn) "${waqf.nameAr} • ${waqf.exampleRefBn}" else "${waqf.nameAr} • ${waqf.exampleRef}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isBn) waqf.descriptionBn else waqf.descriptionEn,
                            style = if (isBn) banglaSafeBodyStyle(13f, "bn") else MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
