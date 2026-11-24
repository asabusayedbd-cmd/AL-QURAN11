package com.example.core.database

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.example.core.localization.BidiUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

class QuranDatabaseSeeder(
    private val context: Context,
    private val database: NoorDatabase
) {
    private val quranDao = database.quranDao()
    private val userLibraryDao = database.userLibraryDao()
    private val duaDao = database.duaDao()
    private val seedMutex = Mutex()
    private val surahMutexes = ConcurrentHashMap<Int, Mutex>()
    private val seedPrefs by lazy {
        context.getSharedPreferences("noor_seed_state", Context.MODE_PRIVATE)
    }

    @Volatile
    private var isFullySeeded: Boolean = false

    companion object {
        private const val KEY_SEED_VERSION = "seed_version"
        private const val CURRENT_SEED_VERSION = 5
        private const val EXPECTED_TOTAL_AYAHS = 6236
        private val PRIORITY_SURAH_IDS = listOf(1, 2, 3, 103, 108, 112, 113, 114)
    }

    private fun getSurahMutex(surahId: Int): Mutex =
        surahMutexes.computeIfAbsent(surahId) { Mutex() }

    suspend fun seedIfNeeded() {
        if (isFullySeeded) return
        withContext(Dispatchers.IO) {
            seedMutex.withLock {
                if (isFullySeeded) return@withLock
                try {
                    val savedVersion = seedPrefs.getInt(KEY_SEED_VERSION, 0)
                    val currentDuaCount = duaDao.countDuas()
                    val hasAllDuas = currentDuaCount >= DuaSeederData.EXPECTED_MIN_DUA_COUNT
                    val hasAllSurahs = quranDao.countSurahs() >= 114
                    val hasAllAyahs = quranDao.countAllAyahs() >= EXPECTED_TOTAL_AYAHS

                    if (savedVersion >= CURRENT_SEED_VERSION &&
                        hasAllSurahs &&
                        hasAllAyahs &&
                        hasAllDuas
                    ) {
                        isFullySeeded = true
                        return@withLock
                    }

                    // Stage 1: Seed 114 Surah catalog, translation/tafsir/Qari catalogs, & user defaults (<30ms)
                    val surahEntities = readSurahCatalogFromAssets(
                        markAllDownloaded = savedVersion < CURRENT_SEED_VERSION
                    )
                    if (!hasAllSurahs || savedVersion < CURRENT_SEED_VERSION) {
                        val existingById = if (hasAllSurahs) {
                            (1..114).mapNotNull { id -> quranDao.getSurah(id) }.associateBy { it.id }
                        } else {
                            emptyMap()
                        }
                        val toInsert = if (existingById.isEmpty() || savedVersion < CURRENT_SEED_VERSION) {
                            surahEntities
                        } else {
                            surahEntities.map { fresh ->
                                val prev = existingById[fresh.id]
                                if (prev != null) fresh.copy(isDownloaded = prev.isDownloaded) else fresh
                            }
                        }
                        database.withTransaction {
                            if (savedVersion in 1 until CURRENT_SEED_VERSION) {
                                quranDao.deleteLegacyStarterAyahs()
                            }
                            quranDao.insertSurahs(toInsert)
                            ensureDefaultUserLibraryState()
                        }
                    } else {
                        ensureDefaultUserLibraryState()
                    }

                    val starterRoot = readStarterCatalogIfNeeded(savedVersion)
                    if (starterRoot != null) {
                        val rawTrSources = parseTranslationSources(starterRoot.getJSONArray("translationSources"))
                        val rawTafSources = parseTafsirSources(starterRoot.getJSONArray("tafsirSources"))
                        val reciters = parseReciters(starterRoot.getJSONArray("reciters"))

                        val existingTrMap = quranDao.getAllTranslationSources().associateBy { it.id }
                        val mergedTr = rawTrSources.map { src ->
                            val prev = existingTrMap[src.id]
                            when {
                                prev != null && savedVersion >= CURRENT_SEED_VERSION ->
                                    src.copy(isDownloaded = prev.isDownloaded, isEnabled = prev.isEnabled)
                                prev != null && src.id == 20 ->
                                    src.copy(isDownloaded = true, isEnabled = true)
                                prev != null && src.id == 131 ->
                                    src.copy(isDownloaded = false, isEnabled = false)
                                prev != null ->
                                    src.copy(isDownloaded = prev.isDownloaded, isEnabled = prev.isEnabled)
                                else -> src
                            }
                        }
                        val existingTafMap = quranDao.getAllTafsirSources().associateBy { it.id }
                        val mergedTaf = rawTafSources.map { src ->
                            val prev = existingTafMap[src.id]
                            if (prev != null) src.copy(isDownloaded = prev.isDownloaded, isEnabled = prev.isEnabled) else src
                        }

                        database.withTransaction {
                            quranDao.insertTranslationSources(mergedTr)
                            quranDao.insertTafsirSources(mergedTaf)
                            quranDao.insertReciters(reciters)
                        }

                        // Priority Surahs load immediately so Home & Juz Amma work right away
                        val expectedBySurah = surahEntities.associate { it.id to it.ayahCount }
                        for (sid in PRIORITY_SURAH_IDS) {
                            ensureSurahLoadedFromAssets(sid, expectedBySurah[sid], forceReplaceIfPartial = true)
                        }

                        // Overlay rich starter Bangla/Urdu WBW, morphology roots, & Tafsirs using canonical IDs
                        applyStarterEnrichmentOverlay(starterRoot, rawTrSources, rawTafSources, reciters)
                    }

                    if (!hasAllDuas) {
                        seedDuasInChunks()
                    }

                    // Stage 2: Progressively stream & insert remaining Surahs 1..114 from compressed assets
                    val expectedBySurah = surahEntities.associate { it.id to it.ayahCount }
                    for (surah in surahEntities) {
                        if (savedVersion >= CURRENT_SEED_VERSION && !surah.isDownloaded) continue
                        ensureSurahLoadedFromAssets(
                            surahId = surah.id,
                            expectedAyahCount = expectedBySurah[surah.id],
                            forceReplaceIfPartial = true
                        )
                        yield()
                    }

                    // Persist seed completion version only after full dataset validation
                    val finalSurahs = quranDao.countSurahs()
                    val finalAyahs = quranDao.countAllAyahs()
                    val finalDuas = duaDao.countDuas()
                    if (finalSurahs >= 114 &&
                        finalAyahs >= EXPECTED_TOTAL_AYAHS &&
                        finalDuas >= DuaSeederData.EXPECTED_MIN_DUA_COUNT
                    ) {
                        seedPrefs.edit { putInt(KEY_SEED_VERSION, CURRENT_SEED_VERSION) }
                        isFullySeeded = true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("QuranSeeder", "Failed seeding Quran data", e)
                }
            }
        }
    }

    /**
     * Loads a single Surah cleanly from bundled `assets/quran_full/sXXX.json.gz` using a
     * streaming JsonReader so memory footprint remains tiny and UI screens never block on full DB seeding.
     */
    suspend fun ensureSurahLoadedFromAssets(
        surahId: Int,
        expectedAyahCount: Int? = null,
        forceReplaceIfPartial: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val sid = surahId.coerceIn(1, 114)
        getSurahMutex(sid).withLock {
            try {
                // Ensure Surah catalog row exists even if Reader opened immediately at cold start
                var surah = quranDao.getSurah(sid)
                if (surah == null) {
                    val catalog = readSurahCatalogFromAssets(markAllDownloaded = true)
                    database.withTransaction {
                        quranDao.insertSurahs(catalog)
                    }
                    surah = quranDao.getSurah(sid)
                }
                val targetCount = expectedAyahCount ?: surah?.ayahCount ?: 0
                val existingCount = quranDao.countAyahsInSurah(sid)
                if (targetCount > 0 && existingCount >= targetCount) {
                    if (surah?.isDownloaded == false) {
                        quranDao.markSurahDownloaded(sid, true)
                    }
                    return@withLock true
                }

                val assetPath = "quran_full/s${sid.toString().padStart(3, '0')}.qgz"
                val parsed = parseCompressedSurah(assetPath, sid)
                if (parsed.ayahs.isEmpty()) return@withLock false

                database.withTransaction {
                    if (forceReplaceIfPartial && existingCount in 1 until parsed.ayahs.size) {
                        quranDao.deleteAyahsBySurah(sid)
                    }
                    quranDao.insertAyahs(parsed.ayahs)
                    if (parsed.words.isNotEmpty()) quranDao.insertWords(parsed.words)
                    if (parsed.wordTranslations.isNotEmpty()) quranDao.insertWordTranslations(parsed.wordTranslations)
                    if (parsed.ayahTranslations.isNotEmpty()) quranDao.insertAyahTranslations(parsed.ayahTranslations)
                    if (parsed.audioSegments.isNotEmpty()) quranDao.insertAudioSegments(parsed.audioSegments)
                    quranDao.markSurahDownloaded(sid, true)
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("QuranSeeder", "Failed loading bundled Surah $sid", e)
                false
            }
        }
    }

    private fun readSurahCatalogFromAssets(markAllDownloaded: Boolean): List<SurahEntity> {
        val surahsJson = context.assets.open("quran_starter/surahs_index.json")
            .bufferedReader().use { it.readText() }
        val surahsArray = JSONArray(surahsJson)
        val initialStarterDownloaded = setOf(1, 103, 108, 112, 113, 114)
        val surahEntities = ArrayList<SurahEntity>(surahsArray.length())
        for (i in 0 until surahsArray.length()) {
            val obj = surahsArray.getJSONObject(i)
            val id = obj.getInt("id")
            surahEntities.add(
                SurahEntity(
                    id = id,
                    nameArabic = obj.getString("nameArabic"),
                    nameTransliteration = obj.getString("nameTransliteration"),
                    localizedNamesJson = obj.getJSONObject("localizedNames").toString(),
                    revelationPlace = obj.getString("revelationPlace"),
                    ayahCount = obj.getInt("ayahCount"),
                    bismillahPre = obj.getBoolean("bismillahPre"),
                    startPage = obj.getInt("startPage"),
                    startJuz = obj.getInt("startJuz"),
                    isDownloaded = if (markAllDownloaded) true else id in initialStarterDownloaded
                )
            )
        }
        return surahEntities
    }

    private suspend fun readStarterCatalogIfNeeded(savedVersion: Int): JSONObject? {
        val needsCatalogUpgrade = savedVersion < CURRENT_SEED_VERSION ||
            quranDao.countReciters() < 75 ||
            quranDao.countTafsirSources() < 15 ||
            quranDao.countTranslationSources() < 35
        if (!needsCatalogUpgrade) return null
        val starterJson = context.assets.open("quran_starter/quran_starter.json")
            .bufferedReader().use { it.readText() }
        return JSONObject(starterJson)
    }

    private class ParsedSurahData(
        val ayahs: List<AyahEntity>,
        val words: List<WordEntity>,
        val wordTranslations: List<WordTranslationEntity>,
        val ayahTranslations: List<AyahTranslationEntity>,
        val audioSegments: List<AudioSegmentEntity>
    )

    private fun parseCompressedSurah(assetPath: String, surahId: Int): ParsedSurahData {
        val ayahs = ArrayList<AyahEntity>(64)
        val words = ArrayList<WordEntity>(512)
        val wordTranslations = ArrayList<WordTranslationEntity>(512)
        val ayahTranslations = ArrayList<AyahTranslationEntity>(64)
        val audioSegments = ArrayList<AudioSegmentEntity>(64)

        val surahPadded = surahId.toString().padStart(3, '0')
        val alafasyBaseUrl = "https://everyayah.com/data/Alafasy_128kbps/"

        GZIPInputStream(context.assets.open(assetPath)).use { gz ->
            JsonReader(InputStreamReader(gz, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    var id = 0
                    var ayahNumber = 0
                    var verseKey = ""
                    var textUthmani = ""
                    var textIndopak = ""
                    var textTajweed = ""
                    var juz = 1
                    var hizb = 1
                    var page = 1
                    var trEn = ""
                    val rawWords = ArrayList<RawCompactWord>(12)
                    val segJsonBuilder = StringBuilder("[")
                    var segCount = 0

                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "id" -> id = reader.nextInt()
                            "n" -> ayahNumber = reader.nextInt()
                            "k" -> verseKey = reader.nextStringOrEmpty()
                            "u" -> textUthmani = reader.nextStringOrEmpty()
                            "i" -> textIndopak = reader.nextStringOrEmpty()
                            "t" -> textTajweed = reader.nextStringOrEmpty()
                            "j" -> juz = reader.nextInt()
                            "h" -> hizb = reader.nextInt()
                            "p" -> page = reader.nextInt()
                            "tr" -> trEn = reader.nextStringOrEmpty()
                            "w" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    reader.beginArray()
                                    val wId = if (reader.hasNext()) reader.nextLong() else 0L
                                    val pos = if (reader.hasNext()) reader.nextInt() else 1
                                    val wUth = if (reader.hasNext()) reader.nextStringOrEmpty() else ""
                                    val wIndo = if (reader.hasNext()) reader.nextStringOrEmpty() else ""
                                    val wTr = if (reader.hasNext()) reader.nextStringOrEmpty() else ""
                                    val wEn = if (reader.hasNext()) reader.nextStringOrEmpty() else ""
                                    while (reader.hasNext()) reader.skipValue()
                                    reader.endArray()
                                    rawWords.add(RawCompactWord(wId, pos, wUth, wIndo, wTr, wEn))
                                }
                                reader.endArray()
                            }
                            "seg" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    reader.beginArray()
                                    val sPos = if (reader.hasNext()) reader.nextInt() else 1
                                    val startMs = if (reader.hasNext()) reader.nextLong() else 0L
                                    val endMs = if (reader.hasNext()) reader.nextLong() else 0L
                                    while (reader.hasNext()) reader.skipValue()
                                    reader.endArray()
                                    if (segCount > 0) segJsonBuilder.append(',')
                                    segJsonBuilder.append("{\"position\":").append(sPos)
                                        .append(",\"startMs\":").append(startMs)
                                        .append(",\"endMs\":").append(endMs)
                                        .append('}')
                                    segCount++
                                }
                                reader.endArray()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    segJsonBuilder.append(']')

                    if (id > 0 && ayahNumber > 0) {
                        ayahs.add(
                            AyahEntity(
                                id = id,
                                surahId = surahId,
                                ayahNumber = ayahNumber,
                                verseKey = verseKey,
                                textUthmani = textUthmani,
                                textIndopak = textIndopak.ifBlank { textUthmani },
                                textTajweedHtml = textTajweed.ifBlank { textUthmani },
                                normalizedArabic = BidiUtils.normalizeArabicForSearch(textUthmani),
                                juzNumber = juz,
                                hizbNumber = hizb,
                                pageNumber = page,
                                sajdahType = null
                            )
                        )

                        val ayahPadded = ayahNumber.toString().padStart(3, '0')
                        for (w in rawWords) {
                            val posPadded = w.position.toString().padStart(3, '0')
                            words.add(
                                WordEntity(
                                    id = w.id,
                                    ayahId = id,
                                    position = w.position,
                                    textUthmani = w.textUthmani,
                                    textIndopak = w.textIndopak.ifBlank { w.textUthmani },
                                    transliteration = w.transliteration,
                                    rootArabic = "",
                                    lemmaArabic = "",
                                    grammarTagJson = "",
                                    audioUrl = "https://audio.qurancdn.com/wbw/${surahPadded}_${ayahPadded}_${posPadded}.mp3"
                                )
                            )
                            if (w.enMeaning.isNotBlank()) {
                                wordTranslations.add(
                                    WordTranslationEntity(
                                        wordId = w.id,
                                        languageCode = "en",
                                        meaningText = w.enMeaning
                                    )
                                )
                            }
                        }

                        if (trEn.isNotBlank()) {
                            ayahTranslations.add(
                                AyahTranslationEntity(
                                    sourceId = 20,
                                    ayahId = id,
                                    languageCode = "en",
                                    translatorName = "Saheeh International",
                                    translatedText = trEn
                                )
                            )
                        }

                        audioSegments.add(
                            AudioSegmentEntity(
                                reciterId = 7,
                                ayahId = id,
                                audioUrl = "${alafasyBaseUrl}${surahPadded}${ayahPadded}.mp3",
                                localFilePath = null,
                                wordTimestampSegmentsJson = segJsonBuilder.toString()
                            )
                        )
                    }
                }
                reader.endArray()
            }
        }

        return ParsedSurahData(ayahs, words, wordTranslations, ayahTranslations, audioSegments)
    }

    private data class RawCompactWord(
        val id: Long,
        val position: Int,
        val textUthmani: String,
        val textIndopak: String,
        val transliteration: String,
        val enMeaning: String
    )

    private fun JsonReader.nextStringOrEmpty(): String {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            ""
        } else {
            nextString()
        }
    }

    private suspend fun applyStarterEnrichmentOverlay(
        starterRoot: JSONObject,
        rawTranslationSources: List<TranslationSourceEntity>,
        rawTafsirSources: List<TafsirSourceEntity>,
        reciters: List<AudioReciterEntity>
    ) {
        val ayahObjects = starterRoot.optJSONArray("ayahs") ?: return
        val translatorNamesById = rawTranslationSources.associate { it.id to (it.languageCode to it.translatorName) }
        val tafsirNamesById = rawTafsirSources.associate { it.id to (it.languageCode to it.tafsirName) }
        val extraTimestampReciters = reciters.filter { it.id in setOf(1, 9) }

        val updatedWords = ArrayList<WordEntity>(180)
        val extraWordTranslations = ArrayList<WordTranslationEntity>(360)
        val extraAyahTranslations = ArrayList<AyahTranslationEntity>(96)
        val extraAyahTafsirs = ArrayList<AyahTafsirEntity>(64)
        val extraAudioSegments = ArrayList<AudioSegmentEntity>(64)

        for (i in 0 until ayahObjects.length()) {
            val obj = ayahObjects.getJSONObject(i)
            val verseKey = obj.getString("verseKey")
            val canonicalAyah = quranDao.getAyahByKey(verseKey) ?: continue
            val canonicalWordsByPos = quranDao.getWordsForAyah(canonicalAyah.id).associateBy { it.position }

            val surahPadded = canonicalAyah.surahId.toString().padStart(3, '0')
            val ayahPadded = canonicalAyah.ayahNumber.toString().padStart(3, '0')

            val wordsArray = obj.optJSONArray("words")
            val segJsonBuilder = StringBuilder("[")
            if (wordsArray != null) {
                for (w in 0 until wordsArray.length()) {
                    val wObj = wordsArray.getJSONObject(w)
                    val pos = wObj.getInt("position")
                    val canonicalWord = canonicalWordsByPos[pos]
                    if (canonicalWord != null) {
                        val rootAr = wObj.optString("rootArabic")
                        val lemmaAr = wObj.optString("lemmaArabic")
                        val grammar = wObj.optString("grammarTag")
                        if (rootAr.isNotBlank() || lemmaAr.isNotBlank() || grammar.isNotBlank()) {
                            updatedWords.add(
                                canonicalWord.copy(
                                    rootArabic = rootAr.ifBlank { canonicalWord.rootArabic },
                                    lemmaArabic = lemmaAr.ifBlank { canonicalWord.lemmaArabic },
                                    grammarTagJson = grammar.ifBlank { canonicalWord.grammarTagJson }
                                )
                            )
                        }

                        val meanings = wObj.optJSONObject("meanings")
                        if (meanings != null) {
                            val keys = meanings.keys()
                            while (keys.hasNext()) {
                                val lang = keys.next()
                                extraWordTranslations.add(
                                    WordTranslationEntity(
                                        wordId = canonicalWord.id,
                                        languageCode = lang,
                                        meaningText = meanings.getString(lang)
                                    )
                                )
                            }
                        }
                    }
                    if (w > 0) segJsonBuilder.append(',')
                    segJsonBuilder.append("{\"position\":").append(pos)
                        .append(",\"startMs\":").append(wObj.optLong("startMs", 0L))
                        .append(",\"endMs\":").append(wObj.optLong("endMs", 1000L))
                        .append('}')
                }
            }
            segJsonBuilder.append(']')
            val segJson = segJsonBuilder.toString()

            for (reciter in extraTimestampReciters) {
                extraAudioSegments.add(
                    AudioSegmentEntity(
                        reciterId = reciter.id,
                        ayahId = canonicalAyah.id,
                        audioUrl = "${reciter.baseUrl}${surahPadded}${ayahPadded}.mp3",
                        localFilePath = null,
                        wordTimestampSegmentsJson = segJson
                    )
                )
            }

            val translationsObj = obj.optJSONObject("translations")
            if (translationsObj != null) {
                val keys = translationsObj.keys()
                while (keys.hasNext()) {
                    val sourceIdStr = keys.next()
                    val sourceId = sourceIdStr.toIntOrNull() ?: continue
                    val (langCode, translatorName) = translatorNamesById[sourceId] ?: ("en" to "Quran Translation")
                    extraAyahTranslations.add(
                        AyahTranslationEntity(
                            sourceId = sourceId,
                            ayahId = canonicalAyah.id,
                            languageCode = langCode,
                            translatorName = translatorName,
                            translatedText = translationsObj.getString(sourceIdStr)
                        )
                    )
                }
            }

            val tafsirsObj = obj.optJSONObject("tafsirs")
            if (tafsirsObj != null) {
                val keys = tafsirsObj.keys()
                while (keys.hasNext()) {
                    val sourceIdStr = keys.next()
                    val sourceId = sourceIdStr.toIntOrNull() ?: continue
                    val (langCode, tafsirName) = tafsirNamesById[sourceId] ?: ("en" to "Tafsir")
                    extraAyahTafsirs.add(
                        AyahTafsirEntity(
                            sourceId = sourceId,
                            ayahId = canonicalAyah.id,
                            languageCode = langCode,
                            tafsirName = tafsirName,
                            tafsirHtmlOrMarkdown = tafsirsObj.getString(sourceIdStr)
                        )
                    )
                }
            }
        }

        database.withTransaction {
            if (updatedWords.isNotEmpty()) quranDao.insertWords(updatedWords)
            if (extraWordTranslations.isNotEmpty()) quranDao.insertWordTranslations(extraWordTranslations)
            if (extraAyahTranslations.isNotEmpty()) quranDao.insertAyahTranslations(extraAyahTranslations)
            if (extraAyahTafsirs.isNotEmpty()) quranDao.insertAyahTafsirs(extraAyahTafsirs)
            if (extraAudioSegments.isNotEmpty()) quranDao.insertAudioSegments(extraAudioSegments)
        }
    }

    private suspend fun seedDuasInChunks() {
        val bookmarkedIds = duaDao.getBookmarkedDuaIds().toHashSet()
        DuaSeederData.readInChunks(context, chunkSize = 35).forEach { chunk ->
            val merged = if (bookmarkedIds.isEmpty()) {
                chunk
            } else {
                chunk.map { dua ->
                    if (dua.id in bookmarkedIds) dua.copy(isBookmarked = true) else dua
                }
            }
            database.withTransaction {
                duaDao.insertDuas(merged)
            }
            yield()
        }
    }

    private suspend fun ensureDefaultUserLibraryState() {
        val now = System.currentTimeMillis()
        userLibraryDao.insertCollections(
            listOf(
                UserCollectionEntity(id = "favorites", name = "Favorites", colorHex = "#0F5C4D", createdAtEpochMs = now),
                UserCollectionEntity(id = "hifz", name = "Hifz", colorHex = "#C89D54", createdAtEpochMs = now),
                UserCollectionEntity(id = "comfort", name = "Comfort & Dua", colorHex = "#2A7B6F", createdAtEpochMs = now)
            )
        )
        if (userLibraryDao.getReadingProgress() == null) {
            userLibraryDao.upsertReadingProgress(
                ReadingProgressEntity(
                    id = "main",
                    lastSurahId = 1,
                    lastAyahNumber = 1,
                    lastVerseKey = "1:1",
                    lastPageNumber = 1,
                    lastJuzNumber = 1,
                    dailyAyahGoal = 20,
                    khatamTargetDays = 30,
                    khatamCompletedAyahs = 7,
                    updatedAtEpochMs = now
                )
            )
        }
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        if (userLibraryDao.getStreakByDate(todayKey) == null) {
            userLibraryDao.upsertStreak(
                HabitStreakEntity(
                    dateKey = todayKey,
                    ayahsReadCount = 7,
                    minutesSpent = 6,
                    tasbeehCount = 33,
                    updatedAtEpochMs = now
                )
            )
        }
    }

    private fun parseTranslationSources(array: JSONArray): List<TranslationSourceEntity> {
        return (0 until array.length()).map { idx ->
            val obj = array.getJSONObject(idx)
            TranslationSourceEntity(
                id = obj.getInt("id"),
                languageCode = obj.getString("languageCode"),
                languageName = obj.getString("languageName"),
                translatorName = obj.getString("translatorName"),
                isDownloaded = obj.getBoolean("isDownloaded"),
                isEnabled = obj.getBoolean("isEnabled"),
                fileSizeKb = obj.getInt("fileSizeKb")
            )
        }
    }

    private fun parseTafsirSources(array: JSONArray): List<TafsirSourceEntity> {
        return (0 until array.length()).map { idx ->
            val obj = array.getJSONObject(idx)
            TafsirSourceEntity(
                id = obj.getInt("id"),
                languageCode = obj.getString("languageCode"),
                languageName = obj.getString("languageName"),
                tafsirName = obj.getString("tafsirName"),
                isDownloaded = obj.getBoolean("isDownloaded"),
                isEnabled = obj.getBoolean("isEnabled"),
                fileSizeKb = obj.getInt("fileSizeKb")
            )
        }
    }

    private fun parseReciters(array: JSONArray): List<AudioReciterEntity> {
        return (0 until array.length()).map { idx ->
            val obj = array.getJSONObject(idx)
            AudioReciterEntity(
                id = obj.getInt("id"),
                name = obj.getString("name"),
                localizedName = obj.getString("localizedName"),
                style = obj.getString("style"),
                baseUrl = obj.getString("baseUrl"),
                isDownloaded = obj.getBoolean("isDownloaded")
            )
        }
    }
}
