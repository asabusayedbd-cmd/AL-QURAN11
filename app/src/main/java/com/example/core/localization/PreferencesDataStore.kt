package com.example.core.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.noorDataStore by preferencesDataStore(name = "noor_al_quran_prefs")

enum class NoorThemeMode {
    SERENE_EMERALD,
    SOFT_SEPIA,
    MADINAH_GOLD_IVORY,
    ANDALUSIAN_ROSE,
    SLATE_DARK,
    AMOLED_BLACK,
    MIDNIGHT_INDIGO,
    DESERT_DUSK
}

enum class QuranScriptType {
    UTHMANI,
    INDOPAK
}

enum class ReaderViewMode {
    WORD_BY_WORD,
    MUSHAF
}

enum class ReaderCardStyle {
    CLASSIC_ELEVATED,
    CRISP_OUTLINED,
    MINIMAL_PAPER
}

enum class ArabicFontGroup(
    val titleEn: String,
    val titleBn: String
) {
    BANGLA_FONTS("Bangla Typography (Siliguri & More)", "বাংলা ফন্ট ও আকর্ষণীয় শিলিগুড়ি"),
    MADANI_UTHMANI("Madani & Uthmani Script", "মাদানী ও উসমানী লিপি"),
    BANGLADESHI_INDOPAK("Bangladeshi & IndoPak Fonts", "বাংলাদেশী ও ইন্দো-পাক ফন্ট"),
    CALLIGRAPHIC_MODERN("Calligraphic & Modern Fonts", "ক্যালিগ্রাফি ও আধুনিক ফন্ট")
}

enum class ArabicFontFamilyOption(
    val displayName: String,
    val subtitle: String,
    val group: ArabicFontGroup,
    val lineHeightMultiplier: Float = 1.72f,
    val arabicSample: String = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ"
) {
    AUTO(
        "Auto (Script Default)",
        "Amiri Quran (Uthmani) • Scheherazade (IndoPak)",
        ArabicFontGroup.MADANI_UTHMANI,
        1.72f
    ),
    AMIRI_QURAN(
        "Amiri Quran",
        "Authentic Madani Mushaf typography",
        ArabicFontGroup.MADANI_UTHMANI,
        1.75f
    ),
    AMIRI(
        "Amiri Classic",
        "Traditional Cairo Naskh",
        ArabicFontGroup.MADANI_UTHMANI,
        1.70f
    ),
    NOTO_NASKH(
        "Noto Naskh Arabic",
        "Crisp high-legibility modern Naskh",
        ArabicFontGroup.MADANI_UTHMANI,
        1.65f
    ),
    MARKAZI(
        "Markazi Text",
        "Refined classic editorial Naskh",
        ArabicFontGroup.MADANI_UTHMANI,
        1.65f
    ),
    IBM_PLEX_ARABIC(
        "IBM Plex Sans Arabic",
        "Balanced modern Madani & study clarity",
        ArabicFontGroup.MADANI_UTHMANI,
        1.60f
    ),
    SCHEHERAZADE(
        "Scheherazade New",
        "Classic Hafezi IndoPak & Uthmani clarity",
        ArabicFontGroup.BANGLADESHI_INDOPAK,
        1.76f
    ),
    LATEEF(
        "Lateef",
        "South Asian Bangladeshi & IndoPak script",
        ArabicFontGroup.BANGLADESHI_INDOPAK,
        1.78f
    ),
    NOTO_NASTALIQ_URDU(
        "Noto Nastaliq",
        "Authentic Lahore & Bangladeshi calligraphic style",
        ArabicFontGroup.BANGLADESHI_INDOPAK,
        2.25f
    ),
    GULZAR(
        "Gulzar IndoPak",
        "Ornate South Asian Nastaliq Mushaf script",
        ArabicFontGroup.BANGLADESHI_INDOPAK,
        2.05f
    ),
    ALKALAMI(
        "Alkalami",
        "Bold hand-crafted pen script with clear vowels",
        ArabicFontGroup.BANGLADESHI_INDOPAK,
        1.90f
    ),
    HARMATTAN(
        "Harmattan",
        "High-clarity South & West vowel positioning",
        ArabicFontGroup.BANGLADESHI_INDOPAK,
        1.70f
    ),
    MIRZA(
        "Mirza",
        "Flowing calligraphic Naskh display",
        ArabicFontGroup.BANGLADESHI_INDOPAK,
        1.72f
    ),
    REEM_KUFI(
        "Reem Kufi",
        "Geometric modern Kufi calligraphy",
        ArabicFontGroup.CALLIGRAPHIC_MODERN,
        1.62f
    ),
    NOTO_KUFI(
        "Noto Kufi Arabic",
        "Structured architectural Kufi script",
        ArabicFontGroup.CALLIGRAPHIC_MODERN,
        1.62f
    ),
    AREF_RUQAA(
        "Aref Ruqaa",
        "Traditional handwritten Ruq'ah",
        ArabicFontGroup.CALLIGRAPHIC_MODERN,
        1.75f
    ),
    CAIRO(
        "Cairo",
        "Clean contemporary Arabic",
        ArabicFontGroup.CALLIGRAPHIC_MODERN,
        1.58f
    ),
    HIND_SILIGURI(
        "Hind Siliguri (শিলিগুড়ি)",
        "আকর্ষণীয় শিলিগুড়ি বাংলা টাইপোগ্রাফি ও লিপি",
        ArabicFontGroup.BANGLA_FONTS,
        1.70f,
        "بِسْمِ ٱللَّهِ"
    ),
    NOTO_SANS_BENGALI(
        "Noto Sans Bengali (নোটো বাংলা)",
        "সাবলীল ও আধুনিক বাংলা ফন্ট",
        ArabicFontGroup.BANGLA_FONTS,
        1.68f,
        "بِسْمِ ٱللَّهِ"
    )
}

enum class MushafEditionStyle(
    val titleEn: String,
    val titleBn: String,
    val linesBadge: String,
    val lightPaperHex: Long,
    val darkPaperHex: Long,
    val borderHex: Long
) {
    MADANI_15_LINE(
        "Madani 15-Line",
        "মাদানী ১৫-লাইন",
        "15 Lines",
        0xFFFCF7EC,
        0xFF171E19,
        0xFF0F5C4D
    ),
    BANGLADESHI_HAFEZI(
        "Bangladeshi Hafezi",
        "বাংলাদেশী হাফেজী",
        "Hafezi",
        0xFFFDF8EB,
        0xFF1B1B15,
        0xFF8B6914
    ),
    KOLKATA_INDOPAK(
        "Kolkata IndoPak",
        "কলকাতা ইন্দো-পাক",
        "13 Lines",
        0xFFFAF6E8,
        0xFF191D19,
        0xFF1F6A52
    ),
    OTTOMAN_ROYAL(
        "Royal Ottoman",
        "রয়াল উসমানী",
        "Gold Trim",
        0xFFFFFAF0,
        0xFF1C1712,
        0xFFC59B27
    ),
    MAGHRIBI_CLASSIC(
        "Maghribi Classic",
        "মাগরিবী ক্লাসিক",
        "Classic",
        0xFFF6EFE2,
        0xFF1A1614,
        0xFF7A4E2D
    )
}

data class UserPreferences(
    val uiLanguageCode: String = "en",
    val wbwLanguageCode: String = "en",
    val activeTafsirSourceId: Int = 164,
    val activeReciterId: Int = 7,
    val themeMode: NoorThemeMode = NoorThemeMode.SERENE_EMERALD,
    val readerCardStyle: ReaderCardStyle = ReaderCardStyle.CLASSIC_ELEVATED,
    val mushafStyle: MushafEditionStyle = MushafEditionStyle.MADANI_15_LINE,
    val scriptType: QuranScriptType = QuranScriptType.UTHMANI,
    val arabicFontFamily: ArabicFontFamilyOption = ArabicFontFamilyOption.AUTO,
    val readerViewMode: ReaderViewMode = ReaderViewMode.WORD_BY_WORD,
    val showWordByWord: Boolean = true,
    val showTranslation: Boolean = true,
    val showInlineTafsir: Boolean = false,
    val tajweedColorsEnabled: Boolean = true,
    val showTransliteration: Boolean = true,
    val showFootnotes: Boolean = true,
    val arabicFontSizeSp: Float = 28f,
    val wbwFontSizeSp: Float = 13f,
    val translationFontSizeSp: Float = 16f,
    val locationName: String = "Dhaka, Bangladesh",
    val latitude: Double = 23.8103,
    val longitude: Double = 90.4125,
    val audioPlaybackSpeed: Float = 1.0f,
    val audioRepeatPerAyah: Int = 1,
    val audioPauseSeconds: Int = 0,
    val audioWordByWordMode: Boolean = false,
    val autoPlayNextSurah: Boolean = true,
    val autoPlayWordOnInspect: Boolean = false,
    val hanafiAsr: Boolean = false,
    val lastRecitedSurahId: Int = 0,
    val lastRecitedAyahNumber: Int = 0,
    val lastRecitedReciterId: Int = 7,
    val lastRecitedPositionMs: Long = 0L
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val WBW_LANGUAGE = stringPreferencesKey("wbw_language")
        val ACTIVE_TAFSIR_ID = intPreferencesKey("active_tafsir_id")
        val ACTIVE_RECITER_ID = intPreferencesKey("active_reciter_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READER_CARD_STYLE = stringPreferencesKey("reader_card_style")
        val MUSHAF_STYLE = stringPreferencesKey("mushaf_style")
        val SCRIPT_TYPE = stringPreferencesKey("script_type")
        val ARABIC_FONT_FAMILY = stringPreferencesKey("arabic_font_family")
        val READER_VIEW_MODE = stringPreferencesKey("reader_view_mode")
        val SHOW_WORD_BY_WORD = booleanPreferencesKey("show_word_by_word")
        val SHOW_TRANSLATION = booleanPreferencesKey("show_translation")
        val SHOW_INLINE_TAFSIR = booleanPreferencesKey("show_inline_tafsir")
        val TAJWEED_COLORS = booleanPreferencesKey("tajweed_colors")
        val SHOW_TRANSLITERATION = booleanPreferencesKey("show_transliteration")
        val SHOW_FOOTNOTES = booleanPreferencesKey("show_footnotes")
        val ARABIC_FONT_SIZE = floatPreferencesKey("arabic_font_size")
        val WBW_FONT_SIZE = floatPreferencesKey("wbw_font_size")
        val TRANSLATION_FONT_SIZE = floatPreferencesKey("translation_font_size")
        val LOCATION_NAME = stringPreferencesKey("location_name")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val AUDIO_SPEED = floatPreferencesKey("audio_speed")
        val AUDIO_REPEAT_AYAH = intPreferencesKey("audio_repeat_ayah")
        val AUDIO_PAUSE_SEC = intPreferencesKey("audio_pause_sec")
        val AUDIO_WBW_MODE = booleanPreferencesKey("audio_wbw_mode")
        val AUTO_NEXT_SURAH = booleanPreferencesKey("auto_next_surah")
        val AUTO_PLAY_WORD_ON_INSPECT = booleanPreferencesKey("auto_play_word_on_inspect")
        val HANAFI_ASR = booleanPreferencesKey("hanafi_asr")
        val LAST_RECITED_SURAH_ID = intPreferencesKey("last_recited_surah_id")
        val LAST_RECITED_AYAH_NUMBER = intPreferencesKey("last_recited_ayah_number")
        val LAST_RECITED_RECITER_ID = intPreferencesKey("last_recited_reciter_id")
        val LAST_RECITED_POS_MS = longPreferencesKey("last_recited_pos_ms")
    }

    val preferencesFlow: Flow<UserPreferences> = context.noorDataStore.data.map { prefs ->
        val savedActiveReciter = prefs[Keys.ACTIVE_RECITER_ID] ?: 7
        val safeActiveReciter = if (savedActiveReciter == 75) 7 else savedActiveReciter
        val savedLastReciter = prefs[Keys.LAST_RECITED_RECITER_ID] ?: safeActiveReciter
        val safeLastReciter = if (savedLastReciter == 75) 7 else savedLastReciter
        UserPreferences(
            uiLanguageCode = prefs[Keys.UI_LANGUAGE] ?: "en",
            wbwLanguageCode = prefs[Keys.WBW_LANGUAGE] ?: "en",
            activeTafsirSourceId = prefs[Keys.ACTIVE_TAFSIR_ID] ?: 164,
            activeReciterId = safeActiveReciter,
            themeMode = enumValues<NoorThemeMode>().firstOrNull { it.name == prefs[Keys.THEME_MODE] }
                ?: NoorThemeMode.SERENE_EMERALD,
            readerCardStyle = enumValues<ReaderCardStyle>().firstOrNull { it.name == prefs[Keys.READER_CARD_STYLE] }
                ?: ReaderCardStyle.CLASSIC_ELEVATED,
            mushafStyle = enumValues<MushafEditionStyle>().firstOrNull { it.name == prefs[Keys.MUSHAF_STYLE] }
                ?: MushafEditionStyle.MADANI_15_LINE,
            scriptType = enumValues<QuranScriptType>().firstOrNull { it.name == prefs[Keys.SCRIPT_TYPE] }
                ?: QuranScriptType.UTHMANI,
            arabicFontFamily = enumValues<ArabicFontFamilyOption>().firstOrNull { it.name == prefs[Keys.ARABIC_FONT_FAMILY] }
                ?: ArabicFontFamilyOption.AUTO,
            readerViewMode = enumValues<ReaderViewMode>().firstOrNull { it.name == prefs[Keys.READER_VIEW_MODE] }
                ?: ReaderViewMode.WORD_BY_WORD,
            showWordByWord = prefs[Keys.SHOW_WORD_BY_WORD] ?: true,
            showTranslation = prefs[Keys.SHOW_TRANSLATION] ?: true,
            showInlineTafsir = prefs[Keys.SHOW_INLINE_TAFSIR] ?: false,
            tajweedColorsEnabled = prefs[Keys.TAJWEED_COLORS] ?: true,
            showTransliteration = prefs[Keys.SHOW_TRANSLITERATION] ?: true,
            showFootnotes = prefs[Keys.SHOW_FOOTNOTES] ?: true,
            arabicFontSizeSp = prefs[Keys.ARABIC_FONT_SIZE] ?: 28f,
            wbwFontSizeSp = prefs[Keys.WBW_FONT_SIZE] ?: 13f,
            translationFontSizeSp = prefs[Keys.TRANSLATION_FONT_SIZE] ?: 16f,
            locationName = prefs[Keys.LOCATION_NAME] ?: "Dhaka, Bangladesh",
            latitude = prefs[Keys.LATITUDE] ?: 23.8103,
            longitude = prefs[Keys.LONGITUDE] ?: 90.4125,
            audioPlaybackSpeed = prefs[Keys.AUDIO_SPEED] ?: 1.0f,
            audioRepeatPerAyah = prefs[Keys.AUDIO_REPEAT_AYAH] ?: 1,
            audioPauseSeconds = prefs[Keys.AUDIO_PAUSE_SEC] ?: 0,
            audioWordByWordMode = prefs[Keys.AUDIO_WBW_MODE] ?: false,
            autoPlayNextSurah = prefs[Keys.AUTO_NEXT_SURAH] ?: true,
            autoPlayWordOnInspect = prefs[Keys.AUTO_PLAY_WORD_ON_INSPECT] ?: false,
            hanafiAsr = prefs[Keys.HANAFI_ASR] ?: false,
            lastRecitedSurahId = prefs[Keys.LAST_RECITED_SURAH_ID] ?: 0,
            lastRecitedAyahNumber = prefs[Keys.LAST_RECITED_AYAH_NUMBER] ?: 0,
            lastRecitedReciterId = safeLastReciter,
            lastRecitedPositionMs = prefs[Keys.LAST_RECITED_POS_MS] ?: 0L
        )
    }

    suspend fun setUiLanguage(languageCode: String) {
        val defaultTafsirId = when (languageCode.lowercase().trim()) {
            "bn" -> 164
            "ar" -> 16
            "ur" -> 160
            "ru" -> 170
            else -> 169
        }
        context.noorDataStore.edit {
            it[Keys.UI_LANGUAGE] = languageCode
            it[Keys.WBW_LANGUAGE] = languageCode
            it[Keys.ACTIVE_TAFSIR_ID] = defaultTafsirId
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (!currentTags.equals(languageCode, ignoreCase = true)) {
                val appLocales = LocaleListCompat.forLanguageTags(languageCode)
                AppCompatDelegate.setApplicationLocales(appLocales)
            }
        }
    }

    suspend fun setWbwLanguage(languageCode: String) {
        context.noorDataStore.edit { it[Keys.WBW_LANGUAGE] = languageCode }
    }

    suspend fun setActiveTafsirSourceId(id: Int) {
        context.noorDataStore.edit { it[Keys.ACTIVE_TAFSIR_ID] = id }
    }

    suspend fun setActiveReciterId(id: Int) {
        val safeId = if (id == 75) 7 else id
        context.noorDataStore.edit { it[Keys.ACTIVE_RECITER_ID] = safeId }
    }

    suspend fun setThemeMode(mode: NoorThemeMode) {
        context.noorDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setReaderCardStyle(style: ReaderCardStyle) {
        context.noorDataStore.edit { it[Keys.READER_CARD_STYLE] = style.name }
    }

    suspend fun setMushafStyle(style: MushafEditionStyle) {
        context.noorDataStore.edit { it[Keys.MUSHAF_STYLE] = style.name }
    }

    suspend fun setScriptType(script: QuranScriptType) {
        context.noorDataStore.edit { it[Keys.SCRIPT_TYPE] = script.name }
    }

    suspend fun setArabicFontFamily(fontFamily: ArabicFontFamilyOption) {
        context.noorDataStore.edit { it[Keys.ARABIC_FONT_FAMILY] = fontFamily.name }
    }

    suspend fun setReaderViewMode(mode: ReaderViewMode) {
        context.noorDataStore.edit { it[Keys.READER_VIEW_MODE] = mode.name }
    }

    suspend fun setShowWordByWord(show: Boolean) {
        context.noorDataStore.edit { it[Keys.SHOW_WORD_BY_WORD] = show }
    }

    suspend fun setShowTranslation(show: Boolean) {
        context.noorDataStore.edit { it[Keys.SHOW_TRANSLATION] = show }
    }

    suspend fun setShowInlineTafsir(show: Boolean) {
        context.noorDataStore.edit { it[Keys.SHOW_INLINE_TAFSIR] = show }
    }

    suspend fun setTajweedEnabled(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.TAJWEED_COLORS] = enabled }
    }

    suspend fun setShowTransliteration(show: Boolean) {
        context.noorDataStore.edit { it[Keys.SHOW_TRANSLITERATION] = show }
    }

    suspend fun setArabicFontSize(sizeSp: Float) {
        context.noorDataStore.edit { it[Keys.ARABIC_FONT_SIZE] = sizeSp.coerceIn(20f, 46f) }
    }

    suspend fun setWbwFontSize(sizeSp: Float) {
        context.noorDataStore.edit { it[Keys.WBW_FONT_SIZE] = sizeSp.coerceIn(10f, 22f) }
    }

    suspend fun setTranslationFontSize(sizeSp: Float) {
        context.noorDataStore.edit { it[Keys.TRANSLATION_FONT_SIZE] = sizeSp.coerceIn(12f, 26f) }
    }

    suspend fun setLocation(name: String, latitude: Double, longitude: Double) {
        context.noorDataStore.edit {
            it[Keys.LOCATION_NAME] = name
            it[Keys.LATITUDE] = latitude
            it[Keys.LONGITUDE] = longitude
        }
    }

    suspend fun setAudioPlaybackSpeed(speed: Float) {
        context.noorDataStore.edit { it[Keys.AUDIO_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setAudioRepeatPerAyah(repeatCount: Int) {
        context.noorDataStore.edit { it[Keys.AUDIO_REPEAT_AYAH] = repeatCount.coerceAtLeast(0) }
    }

    suspend fun setAudioPauseSeconds(seconds: Int) {
        context.noorDataStore.edit { it[Keys.AUDIO_PAUSE_SEC] = seconds.coerceIn(0, 15) }
    }

    suspend fun setAudioWordByWordMode(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.AUDIO_WBW_MODE] = enabled }
    }

    suspend fun setAutoPlayNextSurah(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.AUTO_NEXT_SURAH] = enabled }
    }

    suspend fun setAutoPlayWordOnInspect(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.AUTO_PLAY_WORD_ON_INSPECT] = enabled }
    }

    suspend fun setHanafiAsr(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.HANAFI_ASR] = enabled }
    }

    suspend fun setLastRecited(surahId: Int, ayahNumber: Int, reciterId: Int, positionMs: Long) {
        if (surahId !in 1..114 || ayahNumber <= 0) return
        val safeReciter = if (reciterId == 75) 7 else reciterId
        context.noorDataStore.edit {
            it[Keys.LAST_RECITED_SURAH_ID] = surahId
            it[Keys.LAST_RECITED_AYAH_NUMBER] = ayahNumber
            it[Keys.LAST_RECITED_RECITER_ID] = safeReciter
            it[Keys.LAST_RECITED_POS_MS] = positionMs.coerceAtLeast(0L)
        }
    }
}
