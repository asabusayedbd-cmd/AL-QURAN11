package com.example.core.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

enum class ArabicFontFamilyOption(
    val displayName: String,
    val subtitle: String,
    val arabicSample: String = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ"
) {
    AUTO("Auto (Script Default)", "Amiri Quran (Uthmani) • Scheherazade (IndoPak)"),
    AMIRI_QURAN("Amiri Quran", "Authentic Madani Mushaf typography"),
    AMIRI("Amiri Classic", "Traditional Cairo Naskh"),
    SCHEHERAZADE("Scheherazade New", "Tall IndoPak & Uthmani clarity"),
    LATEEF("Lateef", "South Asian IndoPak calligraphic style"),
    NOTO_NASKH("Noto Naskh Arabic", "Crisp high-legibility modern Naskh"),
    HARMATTAN("Harmattan", "Maghribi & clear vowel positioning"),
    MARKAZI("Markazi Text", "Refined classic editorial Naskh"),
    MIRZA("Mirza", "Flowing calligraphic Naskh display"),
    REEM_KUFI("Reem Kufi", "Geometric modern Kufi calligraphy"),
    AREF_RUQAA("Aref Ruqaa", "Traditional handwritten Ruq'ah"),
    CAIRO("Cairo", "Clean contemporary Arabic")
}

data class UserPreferences(
    val uiLanguageCode: String = "en",
    val wbwLanguageCode: String = "en",
    val activeTafsirSourceId: Int = 164,
    val activeReciterId: Int = 7,
    val themeMode: NoorThemeMode = NoorThemeMode.SERENE_EMERALD,
    val readerCardStyle: ReaderCardStyle = ReaderCardStyle.CLASSIC_ELEVATED,
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
    val hanafiAsr: Boolean = false
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val WBW_LANGUAGE = stringPreferencesKey("wbw_language")
        val ACTIVE_TAFSIR_ID = intPreferencesKey("active_tafsir_id")
        val ACTIVE_RECITER_ID = intPreferencesKey("active_reciter_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READER_CARD_STYLE = stringPreferencesKey("reader_card_style")
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
    }

    val preferencesFlow: Flow<UserPreferences> = context.noorDataStore.data.map { prefs ->
        UserPreferences(
            uiLanguageCode = prefs[Keys.UI_LANGUAGE] ?: "en",
            wbwLanguageCode = prefs[Keys.WBW_LANGUAGE] ?: "en",
            activeTafsirSourceId = prefs[Keys.ACTIVE_TAFSIR_ID] ?: 164,
            activeReciterId = prefs[Keys.ACTIVE_RECITER_ID] ?: 7,
            themeMode = enumValues<NoorThemeMode>().firstOrNull { it.name == prefs[Keys.THEME_MODE] }
                ?: NoorThemeMode.SERENE_EMERALD,
            readerCardStyle = enumValues<ReaderCardStyle>().firstOrNull { it.name == prefs[Keys.READER_CARD_STYLE] }
                ?: ReaderCardStyle.CLASSIC_ELEVATED,
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
            hanafiAsr = prefs[Keys.HANAFI_ASR] ?: false
        )
    }

    suspend fun setUiLanguage(languageCode: String) {
        context.noorDataStore.edit { it[Keys.UI_LANGUAGE] = languageCode }
        val appLocales = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocales)
    }

    suspend fun setWbwLanguage(languageCode: String) {
        context.noorDataStore.edit { it[Keys.WBW_LANGUAGE] = languageCode }
    }

    suspend fun setActiveTafsirSourceId(id: Int) {
        context.noorDataStore.edit { it[Keys.ACTIVE_TAFSIR_ID] = id }
    }

    suspend fun setActiveReciterId(id: Int) {
        context.noorDataStore.edit { it[Keys.ACTIVE_RECITER_ID] = id }
    }

    suspend fun setThemeMode(mode: NoorThemeMode) {
        context.noorDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setReaderCardStyle(style: ReaderCardStyle) {
        context.noorDataStore.edit { it[Keys.READER_CARD_STYLE] = style.name }
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
}
