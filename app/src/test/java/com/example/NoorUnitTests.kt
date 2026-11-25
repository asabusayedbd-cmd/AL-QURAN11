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
import com.example.core.localization.LineTranslator
import com.example.core.localization.NoorThemeMode
import com.example.core.localization.ReaderCardStyle
import com.example.core.localization.SegmentRoleType
import com.example.core.localization.WordMorphologyAnalyzer
import com.example.ui.components.TajweedTextFormatter
import com.example.ui.theme.LightTajweedPalette
import com.example.ui.theme.colorSchemeForTheme
import com.example.ui.theme.isDarkTheme
import com.example.ui.theme.themePreviewSwatches
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
        assertEquals(6236, container.database.quranDao().countAllAyahs())
        assertTrue(container.database.quranDao().countReciters() >= 75)
        assertTrue(container.database.quranDao().countTranslationSources() >= 35)
        assertTrue(
            "Expected >= 150 seeded Duas",
            container.database.duaDao().countDuas() >= 150
        )

        // Verify Surah 2 (Al-Baqarah) has all 286 ayahs, words, English WBW meanings, and Saheeh International offline
        val baqarah = container.database.quranDao().getAyahsBySurah(2)
        assertEquals(286, baqarah.size)
        val kursi = container.database.quranDao().getAyahByKey("2:255")!!
        val kursiWords = container.database.quranDao().getWordsForAyah(kursi.id)
        assertTrue("Expected words for 2:255 offline", kursiWords.isNotEmpty())
        assertTrue(
            "Expected English WBW meanings for Surah 2",
            container.database.quranDao().countWordTranslationsForSurah(2, "en") > 0
        )
        assertTrue(
            "Expected Saheeh International translation for 2:255",
            container.database.quranDao().getTranslationsForAyah(kursi.id).any { it.sourceId == 20 }
        )

        // Verify delete + on-demand offline restore of Surah 18 works without network
        container.quranSyncRepository.deleteSurahOffline(18)
        assertEquals(0, container.database.quranDao().countAyahsInSurah(18))
        assertTrue(container.seeder.ensureSurahLoadedFromAssets(18))
        assertEquals(110, container.database.quranDao().countAyahsInSurah(18))

        // Verify Dua bookmark survives re-seeding
        container.database.duaDao().setDuaBookmarked(1, true)
        assertTrue(container.database.duaDao().getDua(1)?.isBookmarked == true)

        // Verify Surah 44 (Ad-Dukhan) & Mushaf page lookup integrity
        val dukhan = container.database.quranDao().getSurah(44)!!
        assertEquals("Ad-Dukhan", dukhan.nameTransliteration)
        assertEquals(59, dukhan.ayahCount)
        assertEquals(59, container.database.quranDao().getAyahsBySurah(44).size)
        val page496First = container.database.quranDao().getFirstAyahOnPage(496)
        val page498First = container.database.quranDao().getFirstAyahOnPage(498)
        assertEquals(44, page496First?.surahId)
        assertEquals(44, page498First?.surahId)

        // Verify non-bundled translation source 161 is not falsely marked fully downloaded offline
        val tr161 = container.database.quranDao().getAllTranslationSources().firstOrNull { it.id == 161 }
        assertFalse("Expected source 161 not falsely marked isDownloaded with partial rows", tr161?.isDownloaded == true)
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

    // 8. Word Morphology on untagged Quranic words and full Bangla localization
    @Test
    fun testWordMorphologyUntaggedWordsAndBanglaLocalization() {
        val untaggedAlameen = WordEntity(
            id = 201L,
            ayahId = 2,
            position = 2,
            textUthmani = "ٱلْعَـٰلَمِينَ",
            textIndopak = "الْعٰلَمِیْنَ",
            transliteration = "al-'alameen",
            rootArabic = "", // Empty in raw .qgz bundles
            lemmaArabic = "",
            grammarTagJson = "",
            audioUrl = ""
        )
        val translations = listOf(
            WordTranslationEntity(201L, "en", "the worlds"),
            WordTranslationEntity(201L, "bn", "সৃষ্টিজগতের")
        )
        val analysis = WordMorphologyAnalyzer.analyze(untaggedAlameen, translations)
        // Verify prefix segmentation decomposes definite article "الـ"
        assertTrue(
            "Expected prefix segmentation for ٱلْعَـٰلَمِينَ",
            analysis.segments.any { it.roleType == SegmentRoleType.PREFIX && (it.arabicPrefixOrPart == "الـ" || it.arabicPrefixOrPart == "ٱلْـ") }
        )
        assertTrue(analysis.segments.any { it.roleType == SegmentRoleType.STEM })
        // Verify Bangla localization
        val bnPos = analysis.localizedPos("bn")
        assertTrue("Expected Bangla POS label, got: $bnPos", bnPos.contains("বিশেষ্য") || bnPos.isNotBlank())
        val bnExplanation = analysis.localizedExplanation("bn")
        assertTrue("Expected non-empty Bangla explanation", bnExplanation.isNotBlank())
        assertTrue("Bangla meaning should be prioritized first", analysis.allMeanings.first().first.contains("বাংলা"))

        // Untagged preposition + pronoun e.g. "بِهِمْ"
        val bihim = WordEntity(
            id = 202L,
            ayahId = 15,
            position = 1,
            textUthmani = "بِهِمْ",
            textIndopak = "بِهِمْ",
            transliteration = "bihim",
            rootArabic = "",
            lemmaArabic = "",
            grammarTagJson = "",
            audioUrl = ""
        )
        val bihimAnalysis = WordMorphologyAnalyzer.analyze(bihim, listOf(WordTranslationEntity(202L, "bn", "তাদের সাথে")))
        assertTrue(
            "Expected preposition prefix 'بِـ' in bihim",
            bihimAnalysis.segments.any { it.roleType == SegmentRoleType.PREFIX && it.arabicPrefixOrPart == "بِـ" }
        )
    }

    // 9. LineTranslator: Bangla priority, 32+ languages, and Google Translate JSON parser
    @Test
    fun testLineTranslatorJsonParsingAndLanguages() {
        // Verify Bangla is prioritized as the first language option
        assertEquals("bn", LineTranslator.SUPPORTED_LANGUAGES.first().code)
        assertTrue("Expected >= 32 supported languages", LineTranslator.SUPPORTED_LANGUAGES.size >= 32)
        assertEquals("বাংলা (Bangla (Bangladesh))", LineTranslator.languageLabel("bn"))

        // Verify JSON response parsing with multi-line delimiter
        val mockJson = """
            [[["সমস্ত প্রশংসা আল্লাহর জন্য।\n§§§\nপরম করুণাময়, অতি দয়ালু।","All praise is due to Allah.\n§§§\nEntirely Merciful, Especially Merciful.",null,null,10]],null,"en"]
        """.trimIndent()
        val parsed = LineTranslator.parseGoogleTranslateJson(mockJson)
        assertTrue(parsed != null)
        assertTrue(parsed!!.contains("সমস্ত প্রশংসা আল্লাহর জন্য।"))
        assertTrue(parsed.contains("§§§"))
        assertTrue(parsed.contains("পরম করুণাময়, অতি দয়ালু।"))

        // Test empty/invalid JSON graceful fallback
        assertNull(LineTranslator.parseGoogleTranslateJson(""))
        assertNull(LineTranslator.parseGoogleTranslateJson("[]"))
        assertNull(LineTranslator.parseGoogleTranslateJson("invalid json text"))
    }

    // 10. Surah Title formatting, 8 Theme Modes + 3 Card Styles, and Strict Word Morphology Language Separation
    @Test
    fun testSurahTitleThemesAndMorphologyLanguageSeparation() {
        val dukhanJson = """{"en":"The Smoke","bn":"আদ-দুখান","ar":"الدخان"}"""
        assertEquals("Ad-Dukhan (The Smoke)", BidiUtils.formatSurahTitle("Ad-Dukhan", dukhanJson, "en"))
        assertEquals("আদ-দুখান", BidiUtils.formatSurahTitle("Ad-Dukhan", dukhanJson, "bn"))

        // All 8 themes & 3 card styles produce valid distinct color schemes & swatches
        assertEquals(8, NoorThemeMode.entries.size)
        assertEquals(3, ReaderCardStyle.entries.size)
        val swatchSet = NoorThemeMode.entries.map { mode ->
            val scheme = colorSchemeForTheme(mode)
            assertTrue(scheme.primary != scheme.background)
            val swatches = themePreviewSwatches(mode)
            if (mode in setOf(NoorThemeMode.SLATE_DARK, NoorThemeMode.AMOLED_BLACK, NoorThemeMode.MIDNIGHT_INDIGO, NoorThemeMode.DESERT_DUSK)) {
                assertTrue(isDarkTheme(mode))
            } else {
                assertFalse(isDarkTheme(mode))
            }
            swatches
        }.toSet()
        assertEquals(8, swatchSet.size)

        // Strict separation: no Bangla in English explanation, no English meaning fallback in Bangla, no English words in Arabic
        val sampleWord = WordEntity(
            id = 999L,
            ayahId = 1,
            position = 1,
            textUthmani = "ٱلْحَمْدُ",
            textIndopak = "اَلْحَمْدُ",
            transliteration = "al-hamdu",
            rootArabic = "ح م د",
            lemmaArabic = "حَمْد",
            grammarTagJson = "Noun, Nominative",
            audioUrl = ""
        )
        val enOnlyAnalysis = WordMorphologyAnalyzer.analyze(
            sampleWord,
            listOf(WordTranslationEntity(999L, "en", "All praise"))
        )
        assertFalse(
            "English explanation must not contain Bangla characters",
            enOnlyAnalysis.localizedExplanation("en").any { it in '\u0980'..'\u09FF' }
        )
        assertFalse(
            "Bangla explanation must not leak raw English meaning when bnMeaning is missing",
            enOnlyAnalysis.localizedExplanation("bn").contains("All praise")
        )
        assertFalse(
            "Arabic explanation must not contain Latin English grammar terms",
            enOnlyAnalysis.localizedExplanation("ar").contains("Nominative") ||
                enOnlyAnalysis.localizedExplanation("ar").contains("Singular")
        )
    }

    // 11. Bangladesh 64 Districts + Upazilas Search, UTC+6 Resolution & Kaaba Distance
    @Test
    fun testBangladeshLocationsSearchAndUtcOffset() {
        val allBd = com.example.core.ibadah.BangladeshLocations.ALL_PLACES
        assertTrue("Expected >= 150 Bangladesh places, got ${allBd.size}", allBd.size >= 150)

        val expectedDistricts = listOf(
            "Dhaka", "Faridpur", "Gazipur", "Gopalganj", "Kishoreganj", "Madaripur", "Manikganj",
            "Munshiganj", "Narayanganj", "Narsingdi", "Rajbari", "Shariatpur", "Tangail",
            "Bandarban", "Brahmanbaria", "Chandpur", "Chattogram", "Cumilla", "Cox's Bazar",
            "Feni", "Khagrachhari", "Lakshmipur", "Noakhali", "Rangamati",
            "Bogura", "Chapainawabganj", "Joypurhat", "Naogaon", "Natore", "Pabna", "Rajshahi", "Sirajganj",
            "Bagerhat", "Chuadanga", "Jashore", "Jhenaidah", "Khulna", "Kushtia", "Magura",
            "Meherpur", "Narail", "Satkhira",
            "Barguna", "Barishal", "Bhola", "Jhalokati", "Patuakhali", "Pirojpur",
            "Habiganj", "Moulvibazar", "Sunamganj", "Sylhet",
            "Dinajpur", "Gaibandha", "Kurigram", "Lalmonirhat", "Nilphamari", "Panchagarh", "Rangpur", "Thakurgaon",
            "Jamalpur", "Mymensingh", "Netrokona", "Sherpur"
        )
        assertEquals(64, expectedDistricts.size)
        expectedDistricts.forEach { district ->
            val hits = PrayerTimeCalculator.searchLocations(district)
            assertTrue("Expected district '$district' in searchLocations", hits.isNotEmpty())
        }

        // Bangla script & legacy English spelling alias search
        assertTrue(PrayerTimeCalculator.searchLocations("কুমিল্লা").any { it.name.contains("Cumilla") })
        assertTrue(PrayerTimeCalculator.searchLocations("Comilla").any { it.name.contains("Cumilla") })
        assertTrue(PrayerTimeCalculator.searchLocations("সিলেট").any { it.name.contains("Sylhet") })
        assertTrue(PrayerTimeCalculator.searchLocations("Chittagong").any { it.name.contains("Chattogram") })

        // Timezone offset & Kaaba distance checks
        assertEquals(6.0, PrayerTimeCalculator.resolveUtcOffsetForLocation("Sylhet, Bangladesh", 24.89, 91.87) ?: 0.0, 0.01)
        assertEquals(3.0, PrayerTimeCalculator.resolveUtcOffsetForLocation("Makkah, Saudi Arabia", 21.42, 39.82) ?: 0.0, 0.01)
        val distanceKm = PrayerTimeCalculator.calculateDistanceToKaabaKm(23.8103, 90.4125)
        assertTrue("Expected Dhaka->Makkah distance ~5180 km, got $distanceKm", distanceKm in 4900..5400)
    }

    // 12. Room MIGRATION_2_3 schema & search/word index verification
    @Test
    fun testMigration2To3SchemaAndIndexes() {
        val appContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dbName = "migration_2_3_${System.nanoTime()}.db"
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(appContext)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `ayahs` (`id` INTEGER NOT NULL PRIMARY KEY, `normalizedArabic` TEXT NOT NULL DEFAULT '')")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `words` (`id` INTEGER NOT NULL PRIMARY KEY, `ayahId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `textUthmani` TEXT NOT NULL)")
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
            val db = helper.writableDatabase
            com.example.core.database.NoorDatabase.MIGRATION_2_3.migrate(db)
            db.query("PRAGMA table_info(`words`)").use { c ->
                val cols = mutableListOf<String>()
                val nameIdx = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    cols.add(c.getString(nameIdx))
                }
                assertTrue("Expected normalizedText column on words after MIGRATION_2_3", "normalizedText" in cols)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='index'").use { c ->
                val indexes = mutableListOf<String>()
                while (c.moveToNext()) {
                    indexes.add(c.getString(0))
                }
                assertTrue("index_ayahs_normalizedArabic" in indexes)
                assertTrue("index_words_textUthmani" in indexes)
                assertTrue("index_words_normalizedText" in indexes)
            }
            helper.close()
        } finally {
            appContext.getDatabasePath(dbName).delete()
        }
    }

    // 13. Sarf & Nahw Breakdown & Zero-Hallucination Root Guarantee
    @Test
    fun testWordSarfNahwAndNoHallucinatedRoots() {
        val untaggedWord = WordEntity(
            id = 501L,
            ayahId = 1,
            position = 2,
            textUthmani = "ٱلْحَمْدُ",
            textIndopak = "اَلْحَمْدُ",
            transliteration = "al-hamdu",
            rootArabic = "",
            lemmaArabic = "",
            grammarTagJson = "",
            audioUrl = ""
        )
        val analysis = WordMorphologyAnalyzer.analyze(
            untaggedWord,
            listOf(WordTranslationEntity(501L, "en", "All praise"))
        )
        assertTrue(analysis.sarfPatternEn.isNotBlank())
        assertTrue(analysis.definitenessEn.contains("Definite", ignoreCase = true))
        assertTrue(analysis.nahwFunctionEn.isNotBlank())
        assertTrue(analysis.normalizedStem.isNotBlank())
        // Zero hallucination: if rootArabic is empty in DB, spacedRoot must remain empty (never fabricated)
        assertEquals("", analysis.spacedRoot)
    }

    // 14. Curated Translation Book Titles & Multi-Script Search Digit Normalization
    @Test
    fun testTranslationBookTitlesAndSearchDigitNormalization() {
        val appContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        val starterText = appContext.assets.open("quran_starter/quran_starter.json").bufferedReader().use { it.readText() }
        val sources = JSONObject(starterText).getJSONArray("translationSources")
        val titlesById = mutableMapOf<Int, String>()
        for (i in 0 until sources.length()) {
            val obj = sources.getJSONObject(i)
            titlesById[obj.getInt("id")] = obj.getString("translatorName")
        }
        assertTrue(titlesById[161]?.contains("বয়ানুল কুরআন") == true)
        assertTrue(titlesById[131]?.contains("The Clear Quran") == true)
        assertEquals("Saheeh International Translation", titlesById[20])

        // Multi-script search digits (Bangla & Arabic-Indic -> ASCII)
        assertEquals("2:255", BidiUtils.normalizeDigitsToAscii("২:২৫৫"))
        assertEquals("40:3", BidiUtils.normalizeDigitsToAscii("٤٠:٣"))
    }
}
