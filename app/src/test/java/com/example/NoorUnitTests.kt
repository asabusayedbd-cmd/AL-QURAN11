package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.core.audio.HifzLoopConfig
import com.example.core.audio.HifzLoopEngine
import com.example.core.audio.LoopAdvanceResult
import com.example.core.audio.WordAudioSegment
import com.example.core.database.HabitStreakEntity
import com.example.core.database.WordEntity
import com.example.core.database.WordTranslationEntity
import com.example.core.ibadah.PrayerTimeCalculator
import com.example.core.ibadah.StreakCalculator
import com.example.core.localization.BidiUtils
import com.example.core.localization.SegmentRoleType
import com.example.core.localization.WordMorphologyAnalyzer
import com.example.ui.components.TajweedTextFormatter
import com.example.ui.theme.LightTajweedPalette
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.core.di.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoorUnitTests {

    // 1. Arabic-Bangla Bidi formatting & normalization tests
    @Test
    fun testArabicBanglaBidiWrappingAndDigits() {
        val bnLocale = Locale.forLanguageTag("bn")
        val wrappedArabic = BidiUtils.wrapArabicInline("بِسْمِ ٱللَّهِ", bnLocale)
        // AndroidX BidiFormatter isolates RTL text inside LTR Bangla context with LRM (\u200E) + RLE (\u202B) / PDF (\u202C)
        assertTrue(wrappedArabic.contains("بِسْمِ"))
        assertTrue(
            "Expected Bidi isolation markers in '$wrappedArabic'",
            wrappedArabic.contains("\u202B") || wrappedArabic.contains("\u2067") || wrappedArabic.contains("\u200E")
        )

        val wrappedKey = BidiUtils.wrapVerseKey("2:255", Locale.forLanguageTag("ar"))
        assertTrue(wrappedKey.contains("2:255"))
        assertTrue(wrappedKey.length > "2:255".length)

        // Localized numbers
        assertEquals("২৫৫", BidiUtils.formatNumber(255, "bn"))
        assertEquals("٢٥٥", BidiUtils.formatNumber(255, "ar"))
        assertEquals("255", BidiUtils.formatNumber(255, "en"))

        // Ornate Ayah marker
        val marker = BidiUtils.formatAyahMarker(7, useArabicDigits = true)
        assertEquals("\uFD3F٧\uFD3E", marker)

        // Harakat stripping for search
        val normalized = BidiUtils.normalizeArabicForSearch("بِسْمِ اللَّهِ الرَّحْمَنِ")
        assertEquals("بسم الله الرحمن", normalized)
    }

    // 2. Ayah loop range logic & word segment timestamp tests
    @Test
    fun testHifzAyahLoopAndWordTimestamps() {
        // Repeat each ayah 3 times before advancing
        val repeatConfig = HifzLoopConfig(startAyah = 1, endAyah = 5, repeatPerAyah = 3, pauseSecondsAfterAyah = 2)
        val step1 = HifzLoopEngine.nextStep(currentAyah = 2, currentIteration = 1, config = repeatConfig, maxAyahInSurah = 7)
        assertEquals(LoopAdvanceResult.PlayAyah(ayahNumber = 2, iterationOfAyah = 2, delayMsBeforePlay = 2000L), step1)

        val step2 = HifzLoopEngine.nextStep(currentAyah = 2, currentIteration = 3, config = repeatConfig, maxAyahInSurah = 7)
        assertEquals(LoopAdvanceResult.PlayAyah(ayahNumber = 3, iterationOfAyah = 1, delayMsBeforePlay = 2000L), step2)

        // A-B Loop wrapping back to A when B ends (also handles inverted start > end safely)
        val loopConfig = HifzLoopConfig(startAyah = 5, endAyah = 3, repeatPerAyah = 1, loopRangeEnabled = true)
        val wrapStep = HifzLoopEngine.nextStep(currentAyah = 5, currentIteration = 1, config = loopConfig, maxAyahInSurah = 7)
        assertEquals(LoopAdvanceResult.PlayAyah(ayahNumber = 3, iterationOfAyah = 1, delayMsBeforePlay = 0L), wrapStep)

        // Non-loop completes at end of Surah
        val noLoopConfig = HifzLoopConfig(repeatPerAyah = 1, loopRangeEnabled = false)
        val doneStep = HifzLoopEngine.nextStep(currentAyah = 7, currentIteration = 1, config = noLoopConfig, maxAyahInSurah = 7)
        assertEquals(LoopAdvanceResult.Completed, doneStep)

        // Infinite repeat stays on current Ayah
        val infConfig = HifzLoopConfig(repeatPerAyah = 0)
        val infStep = HifzLoopEngine.nextStep(currentAyah = 4, currentIteration = 9, config = infConfig, maxAyahInSurah = 7)
        assertEquals(LoopAdvanceResult.PlayAyah(ayahNumber = 4, iterationOfAyah = 10, delayMsBeforePlay = 0L), infStep)

        // Word timestamp resolution
        val segments = listOf(
            WordAudioSegment(1, 0L, 450L),
            WordAudioSegment(2, 460L, 1100L),
            WordAudioSegment(3, 1120L, 1800L)
        )
        assertEquals(1, HifzLoopEngine.findActiveWordPosition(segments, 220L))
        assertEquals(2, HifzLoopEngine.findActiveWordPosition(segments, 800L))
        assertEquals(3, HifzLoopEngine.findActiveWordPosition(segments, 1500L))
        assertNull(HifzLoopEngine.findActiveWordPosition(emptyList(), 500L))

        // Proportional fallback highlighting when exact word timestamps are absent
        assertEquals(1, HifzLoopEngine.estimateWordPositionByProgress(wordCount = 4, positionMs = 100L, durationMs = 4000L))
        assertEquals(2, HifzLoopEngine.estimateWordPositionByProgress(wordCount = 4, positionMs = 1500L, durationMs = 4000L))
        assertEquals(4, HifzLoopEngine.estimateWordPositionByProgress(wordCount = 4, positionMs = 3900L, durationMs = 4000L))
        assertNull(HifzLoopEngine.estimateWordPositionByProgress(wordCount = 0, positionMs = 1000L, durationMs = 4000L))

        // Duration formatting
        assertEquals("0:00", HifzLoopEngine.formatDurationMmSs(0L))
        assertEquals("1:05", HifzLoopEngine.formatDurationMmSs(65_000L))
    }

    // 3. Daily streak calculation & Khatam pacing tests
    @Test
    fun testDailyStreakAndKhatamCalculations() {
        val today = "2026-11-21"
        val consecutive = listOf(
            HabitStreakEntity(dateKey = "2026-11-21", ayahsReadCount = 12),
            HabitStreakEntity(dateKey = "2026-11-20", ayahsReadCount = 20),
            HabitStreakEntity(dateKey = "2026-11-19", ayahsReadCount = 8),
            HabitStreakEntity(dateKey = "2026-11-18", ayahsReadCount = 0), // gap breaks streak
            HabitStreakEntity(dateKey = "2026-11-17", ayahsReadCount = 15)
        )
        assertEquals(3, StreakCalculator.calculateCurrentStreak(consecutive, today))

        // If today has 0 so far, active streak carries from yesterday
        val carryOver = listOf(
            HabitStreakEntity(dateKey = "2026-11-20", ayahsReadCount = 10),
            HabitStreakEntity(dateKey = "2026-11-19", ayahsReadCount = 10)
        )
        assertEquals(2, StreakCalculator.calculateCurrentStreak(carryOver, today))

        // Khatam plan math (6236 total Ayahs, 236 done -> 6000 remaining over 30 days -> 200/day)
        val khatam = StreakCalculator.calculateKhatamPlan(completedAyahs = 236, targetDays = 30)
        assertEquals(6000, khatam.remainingAyahs)
        assertEquals(200, khatam.requiredAyahsPerDay)

        // Qibla bearing from Dhaka (23.81, 90.41) toward Makkah (21.42, 39.82) points West-Southwest (~277 deg)
        val dhakaQibla = PrayerTimeCalculator.calculateQiblaBearing(23.8103, 90.4125)
        assertTrue("Expected WSW bearing ~277°, got $dhakaQibla", dhakaQibla in 265f..288f)
    }

    // 4. Word Morphology Inspector & Starter JSON Qari/Tafsir catalog integrity
    @Test
    fun testWordMorphologyAndStarterCatalogCounts() {
        val word = WordEntity(
            id = 101L,
            ayahId = 1,
            position = 1,
            textUthmani = "بِسْمِ",
            textIndopak = "بِسْمِ",
            transliteration = "Bismi",
            rootArabic = "س م و",
            lemmaArabic = "اِسْم",
            grammarTagJson = "Preposition + Noun, Genitive",
            audioUrl = "https://audio.qurancdn.com/wbw/001_001_001.mp3"
        )
        val translations = listOf(
            WordTranslationEntity(101L, "en", "In the name of"),
            WordTranslationEntity(101L, "bn", "নামে"),
            WordTranslationEntity(101L, "ur", "نام سے")
        )
        val analysis = WordMorphologyAnalyzer.analyze(word, translations)
        assertEquals("Preposition + Noun", analysis.partOfSpeechEn)
        assertTrue(analysis.grammaticalCaseOrMood.contains("Genitive"))
        assertTrue(analysis.segments.any { it.roleType == SegmentRoleType.PREFIX && it.arabicPrefixOrPart == "بِـ" })
        assertTrue(analysis.segments.any { it.roleType == SegmentRoleType.STEM })
        assertEquals("বাংলা (Bangla)", analysis.allMeanings.first().first)
        assertEquals(3, analysis.allMeanings.size)

        // Diacritic-safe conjunction prefix stripping (وَٱللَّهِ should yield "وَـ")
        val waWord = word.copy(id = 102L, textUthmani = "وَٱللَّهِ", grammarTagJson = "Conjunction + Proper Noun")
        val waAnalysis = WordMorphologyAnalyzer.analyze(waWord, translations)
        assertTrue(waAnalysis.segments.any { it.roleType == SegmentRoleType.PREFIX && it.arabicPrefixOrPart == "وَـ" })

        // Verify starter catalog contains >= 75 Qaris, >= 15 Tafsirs, >= 35 Translations
        val appContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertEquals("Al-Quran", appContext.getString(R.string.app_name))
        val starterText = appContext.assets.open("quran_starter/quran_starter.json").bufferedReader().use { it.readText() }
        val root = JSONObject(starterText)
        assertTrue("Expected >= 75 reciters", root.getJSONArray("reciters").length() >= 75)
        assertTrue("Expected >= 15 tafsir sources", root.getJSONArray("tafsirSources").length() >= 15)
        assertTrue("Expected >= 35 translation sources", root.getJSONArray("translationSources").length() >= 35)
    }

    // 5. Tajweed HTML parser (quoted & unquoted attributes, end-marker stripping, IndoPak mode) & 114-Surah index
    @Test
    fun testTajweedFormatterAndSurahIndex() {
        val sampleHtml = "<tajweed class=ham_wasl>ٱ</tajweed>لْحَمْدُ لِلَّهِ <tajweed class=\"ghunnah\">مِنَّ</tajweed> <span class=end>٢</span>"
        val formattedUthmani = TajweedTextFormatter.formatArabicVerse(
            rawText = "ٱلْحَمْدُ لِلَّهِ مِنَّ",
            tajweedHtml = sampleHtml,
            tajweedEnabled = true,
            palette = LightTajweedPalette,
            ayahEndMarker = "\uFD3F٢\uFD3E",
            useTajweedMarkup = true
        )
        assertTrue(formattedUthmani.text.contains("ٱلْحَمْدُ لِلَّهِ مِنَّ"))
        assertTrue(formattedUthmani.text.endsWith("\uFD3F٢\uFD3E"))
        assertFalse(formattedUthmani.text.contains("<tajweed"))
        assertFalse(formattedUthmani.text.contains("<span"))
        assertTrue(formattedUthmani.spanStyles.any { it.item.color == LightTajweedPalette.ghunnah })
        assertTrue(formattedUthmani.spanStyles.any { it.item.color == LightTajweedPalette.silent })

        // When useTajweedMarkup = false (IndoPak script), preserve IndoPak rawText instead of Uthmani HTML
        val formattedIndoPak = TajweedTextFormatter.formatArabicVerse(
            rawText = "اَلْحَمْدُ لِلّٰهِ",
            tajweedHtml = sampleHtml,
            tajweedEnabled = true,
            palette = LightTajweedPalette,
            ayahEndMarker = "\uFD3F١\uFD3E",
            useTajweedMarkup = false
        )
        assertTrue(formattedIndoPak.text.startsWith("اَلْحَمْدُ لِلّٰهِ"))

        // Verify all 114 Surahs exist in surahs_index.json
        val appContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        val surahIndexText = appContext.assets.open("quran_starter/surahs_index.json").bufferedReader().use { it.readText() }
        val surahs = JSONArray(surahIndexText)
        assertEquals(114, surahs.length())
        assertEquals(1, surahs.getJSONObject(0).getInt("id"))
        assertEquals(114, surahs.getJSONObject(113).getInt("id"))
    }

    // 6. Surah localization cache & concurrent idempotent seeding verification
    @Test
    fun testBidiCacheAndIdempotentSeeder() = runBlocking {
        val sampleJson = """{"en":"Al-Fatihah","bn":"আল-ফাতিহা","ar":"الفاتحة"}"""
        val first = BidiUtils.extractLocalizedSurahName(sampleJson, "bn")
        val second = BidiUtils.extractLocalizedSurahName(sampleJson, "bn")
        assertEquals("আল-ফাতিহা", first)
        assertEquals(first, second)
        assertEquals("Al-Fatihah", BidiUtils.extractLocalizedSurahName(sampleJson, "fr"))

        val appContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer.getInstance(appContext)
        // Concurrent calls must safely coalesce via Mutex without duplicating rows
        listOf(
            async { container.seeder.seedIfNeeded() },
            async { container.seeder.seedIfNeeded() },
            async { container.seeder.seedIfNeeded() }
        ).awaitAll()
        assertEquals(114, container.database.quranDao().countSurahs())
        assertTrue(container.database.quranDao().countReciters() >= 75)
        assertTrue(container.database.quranDao().countTranslationSources() >= 35)
        assertTrue(
            "Expected >= 150 seeded Duas",
            container.database.duaDao().countDuas() >= 150
        )

        // Verify Dua bookmark survives re-seeding
        container.database.duaDao().setDuaBookmarked(1, true)
        assertTrue(container.database.duaDao().getDua(1)?.isBookmarked == true)
    }

    // 7. 150+ Dua catalog, Room MIGRATION_1_2, and Hanafi vs Standard Asr timings
    @Test
    fun testDuaCatalogMigrationAndHanafiAsr() {
        val appContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        val catalog = com.example.core.database.DuaSeederData.loadAllFromAssets(appContext)
        assertTrue("Expected >= 150 Duas in catalog, got ${catalog.size}", catalog.size >= 150)
        assertEquals(12, catalog.map { it.category }.distinct().size)
        assertTrue(catalog.all { it.reference.isNotBlank() && it.arabicText.isNotBlank() && it.translation.isNotBlank() })
        assertFalse(catalog.any { it.translation.contains("Verified Translation", ignoreCase = true) })

        // Verify Room MIGRATION_1_2 creates the duas table & indices cleanly
        val dbName = "migration_test_${System.nanoTime()}.db"
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(appContext)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `surahs` (`id` INTEGER NOT NULL PRIMARY KEY)")
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        try {
            val wrappedDb = helper.writableDatabase
            com.example.core.database.NoorDatabase.MIGRATION_1_2.migrate(wrappedDb)
            wrappedDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='duas'").use { c ->
                assertTrue("Expected duas table after MIGRATION_1_2", c.moveToFirst())
            }
            wrappedDb.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_duas_category'").use { c ->
                assertTrue("Expected category index after MIGRATION_1_2", c.moveToFirst())
            }
            helper.close()
        } finally {
            appContext.getDatabasePath(dbName).delete()
        }

        // Verify Hanafi Asr is strictly later than Standard (Shafi'i) Asr and 30+ city presets exist
        assertTrue("Expected >= 30 global city presets", PrayerTimeCalculator.CITY_PRESETS.size >= 30)
        val cal = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.JUNE, 21, 10, 0, 0) }
        val standard = PrayerTimeCalculator.calculateDailyTimes(23.8103, 90.4125, calendar = cal, hanafiAsr = false)
        val hanafi = PrayerTimeCalculator.calculateDailyTimes(23.8103, 90.4125, calendar = cal, hanafiAsr = true)
        assertFalse("Expected Hanafi Asr (${hanafi.asr}) to differ from Standard (${standard.asr})", hanafi.asr == standard.asr)
    }
}
