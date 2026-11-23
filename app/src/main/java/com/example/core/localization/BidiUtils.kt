package com.example.core.localization

import android.os.Build
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Calendar
import java.util.Date
import java.util.Locale

object BidiUtils {

    private val ARABIC_DIACRITICS_REGEX = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val ARABIC_ALEF_VARIANTS_REGEX = Regex("[\\u0622\\u0623\\u0625\\u0671]")

    /**
     * Wraps inline Arabic text with Unicode Bidi isolation marks so punctuation,
     * brackets, and verse numbers do not flip when embedded inside LTR Bangla or English sentences.
     */
    fun wrapArabicInline(arabicText: String, uiLocale: Locale = Locale.getDefault()): String {
        val formatter = BidiFormatter.getInstance(uiLocale)
        return formatter.unicodeWrap(arabicText, TextDirectionHeuristicsCompat.RTL)
    }

    /**
     * Wraps a verse key like "2:255" safely in RTL/LTR contexts so digits and colon never reverse.
     */
    fun wrapVerseKey(verseKey: String, uiLocale: Locale = Locale.getDefault()): String {
        val formatter = BidiFormatter.getInstance(uiLocale)
        return formatter.unicodeWrap(verseKey, TextDirectionHeuristicsCompat.LTR)
    }

    /**
     * Strips tashkeel/harakat and normalizes alef variants for fast harakat-insensitive Arabic search.
     */
    fun normalizeArabicForSearch(input: String): String {
        val noDiacritics = ARABIC_DIACRITICS_REGEX.replace(input, "")
        val normalizedAlef = ARABIC_ALEF_VARIANTS_REGEX.replace(noDiacritics, "ا")
        return normalizedAlef
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .trim()
    }

    /**
     * Formats integers into localized digits for Bangla (bn), Arabic (ar), Urdu (ur), or Latin.
     */
    fun formatNumber(value: Int, languageCode: String): String {
        val str = value.toString()
        val zeroChar: Char = when (languageCode.lowercase()) {
            "bn" -> '\u09E6' // ০
            "ar" -> '\u0660' // ٠
            "ur", "fa" -> '\u06F0' // ۰
            else -> return str
        }
        return buildString(str.length) {
            for (ch in str) {
                if (ch in '0'..'9') {
                    append((zeroChar.code + (ch - '0')).toChar())
                } else {
                    append(ch)
                }
            }
        }
    }

    /**
     * Formats ornate Quranic Ayah end marker (e.g. ﴿١﴾ or ﴿১﴾).
     */
    fun formatAyahMarker(ayahNumber: Int, useArabicDigits: Boolean = true, languageCode: String = "ar"): String {
        val digits = if (useArabicDigits) {
            formatNumber(ayahNumber, "ar")
        } else {
            formatNumber(ayahNumber, languageCode)
        }
        return "\uFD3F$digits\uFD3E"
    }

    fun extractLocalizedSurahName(localizedNamesJson: String, languageCode: String): String {
        return try {
            val obj = JSONObject(localizedNamesJson)
            obj.optString(languageCode).takeIf { it.isNotBlank() }
                ?: obj.optString("en").takeIf { it.isNotBlank() }
                ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun formatGregorianAndHijriDate(languageCode: String): Pair<String, String> {
        val locale = Locale.forLanguageTag(languageCode)
        val gregorian = SimpleDateFormat("EEE, d MMM yyyy", locale).format(Date())
        val hijri = computeHijriDateString(languageCode)
        return gregorian to hijri
    }

    fun computeHijriDateString(languageCode: String): String {
        val monthNamesEn = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Ula", "Jumada al-Thani", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )
        val monthNamesBn = listOf(
            "মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি",
            "জুমাদাল উলা", "জুমাদাস সানি", "রজব", "শাবান",
            "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ"
        )
        val monthNamesAr = listOf(
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر",
            "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
            "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
        )

        val (day, monthIdx, year) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hijrah = HijrahChronology.INSTANCE.date(LocalDate.now())
            Triple(
                hijrah.get(ChronoField.DAY_OF_MONTH),
                hijrah.get(ChronoField.MONTH_OF_YEAR) - 1,
                hijrah.get(ChronoField.YEAR_OF_ERA)
            )
        } else {
            kuwaitiFallbackHijri()
        }

        val monthName = when (languageCode.lowercase()) {
            "bn" -> monthNamesBn.getOrElse(monthIdx) { monthNamesEn[0] }
            "ar" -> monthNamesAr.getOrElse(monthIdx) { monthNamesEn[0] }
            else -> monthNamesEn.getOrElse(monthIdx) { monthNamesEn[0] }
        }
        val dayStr = formatNumber(day, languageCode)
        val yearStr = formatNumber(year, languageCode)
        val suffix = when (languageCode.lowercase()) {
            "bn" -> "হি."
            "ar" -> "هـ"
            else -> "AH"
        }
        return "$dayStr $monthName $yearStr $suffix"
    }

    private fun kuwaitiFallbackHijri(): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance()
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)
        val m = if (gMonth < 3) gMonth + 12 else gMonth
        val y = if (gMonth < 3) gYear - 1 else gYear
        val jd = (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + gDay - 1524
        val l = jd - 1948440 + 10632
        val n = (l - 1) / 10631
        val l2 = l - 10631 * n + 354
        val j = ((10985 - l2) / 5316) * ((50 * l2) / 17719) + (l2 / 5670) * ((43 * l2) / 15238)
        val l3 = l2 - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val hMonth = ((24 * l3) / 709).coerceIn(1, 12)
        val hDay = (l3 - (709 * hMonth) / 24).coerceIn(1, 30)
        val hYear = 30 * n + j - 30
        return Triple(hDay, hMonth - 1, hYear)
    }
}
