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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
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
    private val duaSeedMutex = Mutex()
    private val surahMutexes = ConcurrentHashMap<Int, Mutex>()
    private val seedPrefs by lazy {
        context.getSharedPreferences("noor_seed_state", Context.MODE_PRIVATE)
    }

    @Volatile
    private var isFullySeeded: Boolean = false

    companion object {
        private const val KEY_SEED_VERSION = "seed_version"
        private const val CURRENT_SEED_VERSION = 6
        private const val EXPECTED_TOTAL_AYAHS = 6236
        // Keep cold Stage 1 tiny (17 Ayahs total) so Home & first frame render immediately
        private val PRIORITY_SURAH_IDS = listOf(1, 112, 113, 114)
    }

    private fun getSurahMutex(surahId: Int): Mutex =
        surahMutexes.computeIfAbsent(surahId) { Mutex() }

    suspend fun ensureDuasSeededNow(interChunkDelayMs: Long = 0L) {
        withContext(Dispatchers.IO) {
            duaSeedMutex.withLock {
                if (duaDao.countDuas() >= DuaSeederData.EXPECTED_MIN_DUA_COUNT) return@withLock
                seedDuasInChunks(interChunkDelayMs)
            }
        }
    }

    suspend fun seedIfNeeded(
        deferBulkDelayMs: Long = 0L,
        interSurahYieldMs: Long = 0L
    ) {
        if (isFullySeeded) return
        withContext(Dispatchers.IO) {
            seedMutex.withLock {
                if (isFullySeeded) return@withLock
                try {
                    val savedVersion = seedPrefs.getInt(KEY_SEED_VERSION, 0)
                    if (savedVersion >= CURRENT_SEED_VERSION && quranDao.getSurah(1) != null) {
                        isFullySeeded = true
                        return@withLock
                    }

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

                    // Stage 1: Seed 114 Surah catalog, translation/tafsir/Qari catalogs, & tiny priority Surahs (<40ms)
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

                    val needsCatalogUpgrade = savedVersion < CURRENT_SEED_VERSION ||
                        quranDao.countReciters() < 75 ||
                        quranDao.countTafsirSources() < 15 ||
                        quranDao.countTranslationSources() < 35

                    val catalogs = if (needsCatalogUpgrade) {
                        readStarterCatalogsStreaming()
                    } else {
                        null
                    }

                    if (catalogs != null) {
                        val existingTrMap = quranDao.getAllTranslationSources().associateBy { it.id }
                        val mergedTr = catalogs.translationSources.map { src ->
                            val prev = existingTrMap[src.id]
                            val actuallyDownloaded = when {
                                src.id == 20 -> true
                                prev != null && prev.isDownloaded -> quranDao.countTranslationsForSource(src.id) >= 6000
                                else -> false
                            }
                            when {
                                src.id == 20 -> src.copy(isDownloaded = true, isEnabled = prev?.isEnabled ?: true)
                                src.id == 131 -> src.copy(
                                    isDownloaded = actuallyDownloaded,
                                    isEnabled = if (prev != null && savedVersion >= 5) prev.isEnabled else false
                                )
                                prev != null -> src.copy(isDownloaded = actuallyDownloaded, isEnabled = prev.isEnabled)
                                else -> src.copy(isDownloaded = false, isEnabled = false)
                            }
                        }
                        val existingTafMap = quranDao.getAllTafsirSources().associateBy { it.id }
                        val mergedTaf = catalogs.tafsirSources.map { src ->
                            val prev = existingTafMap[src.id]
                            if (prev != null) src.copy(isDownloaded = prev.isDownloaded, isEnabled = prev.isEnabled) else src
                        }

                        database.withTransaction {
                            quranDao.insertTranslationSources(mergedTr)
                            quranDao.insertTafsirSources(mergedTaf)
                            quranDao.insertReciters(catalogs.reciters)
                        }
                    }

                    // Priority Surahs load immediately so Home & Al-Fatihah / Quls work right away
                    val expectedBySurah = surahEntities.associate { it.id to it.ayahCount }
                    for (sid in PRIORITY_SURAH_IDS) {
                        ensureSurahLoadedFromAssets(sid, expectedBySurah[sid], forceReplaceIfPartial = true)
                    }

                    // Defer heavy background seeding until initial UI frame + animations settle
                    if (deferBulkDelayMs > 0L) {
                        delay(deferBulkDelayMs)
                    }

                    if (!hasAllDuas) {
                        ensureDuasSeededNow(interChunkDelayMs = interSurahYieldMs)
                    }

                    // Stage 2: Batched background seeding for remaining Surahs (~450 ayahs per transaction)
                    val existingCountsBySurah = quranDao.getAyahCountsBySurah().associate { it.surahId to it.cnt }
                    val batchAyahs = ArrayList<AyahEntity>(520)
                    val batchWords = ArrayList<WordEntity>(6000)
                    val batchWordTrs = ArrayList<WordTranslationEntity>(6000)
                    val batchAyahTrs = ArrayList<AyahTranslationEntity>(520)
                    val batchAudio = ArrayList<AudioSegmentEntity>(520)
                    val partialReplaceIds = ArrayList<Int>(16)
                    val markDownloadedIds = ArrayList<Int>(16)

                    suspend fun flushBatch() {
                        if (batchAyahs.isEmpty() && markDownloadedIds.isEmpty()) return
                        database.withTransaction {
                            for (pid in partialReplaceIds) {
                                quranDao.deleteAyahsBySurah(pid)
                            }
                            if (batchAyahs.isNotEmpty()) quranDao.insertAyahs(batchAyahs)
                            if (batchWords.isNotEmpty()) quranDao.insertWords(batchWords)
                            if (batchWordTrs.isNotEmpty()) quranDao.insertWordTranslations(batchWordTrs)
                            if (batchAyahTrs.isNotEmpty()) quranDao.insertAyahTranslations(batchAyahTrs)
                            if (batchAudio.isNotEmpty()) quranDao.insertAudioSegments(batchAudio)
                            for (mid in markDownloadedIds) {
                                quranDao.markSurahDownloaded(mid, true)
                            }
                        }
                        batchAyahs.clear()
                        batchWords.clear()
                        batchWordTrs.clear()
                        batchAyahTrs.clear()
                        batchAudio.clear()
                        partialReplaceIds.clear()
                        markDownloadedIds.clear()
                        if (interSurahYieldMs > 0L) delay(interSurahYieldMs) else yield()
                    }

                    for (surah in surahEntities) {
                        if (surah.id in PRIORITY_SURAH_IDS) continue
                        if (savedVersion >= CURRENT_SEED_VERSION && !surah.isDownloaded) continue
                        val existingCount = existingCountsBySurah[surah.id] ?: 0
                        if (existingCount >= surah.ayahCount) {
                            if (!surah.isDownloaded) {
                                markDownloadedIds.add(surah.id)
                            }
                            continue
                        }
                        getSurahMutex(surah.id).withLock {
                            val assetPath = "quran_full/s${surah.id.toString().padStart(3, '0')}.qgz"
                            val parsed = parseCompressedSurah(assetPath, surah.id)
                            if (parsed.ayahs.isNotEmpty()) {
                                if (existingCount in 1 until parsed.ayahs.size) {
                                    partialReplaceIds.add(surah.id)
                                }
                                batchAyahs.addAll(parsed.ayahs)
                                batchWords.addAll(parsed.words)
                                batchWordTrs.addAll(parsed.wordTranslations)
                                batchAyahTrs.addAll(parsed.ayahTranslations)
                                batchAudio.addAll(parsed.audioSegments)
                                if (!surah.isDownloaded) {
                                    markDownloadedIds.add(surah.id)
                                }
                            }
                        }
                        if (batchAyahs.size >= 450) {
                            flushBatch()
                        }
                    }
                    flushBatch()

                    // Overlay rich starter Bangla/Urdu WBW, morphology roots, & Tafsirs using canonical IDs
                    if (catalogs != null) {
                        applyStarterEnrichmentOverlayStreaming(
                            rawTranslationSources = catalogs.translationSources,
                            rawTafsirSources = catalogs.tafsirSources,
                            reciters = catalogs.reciters
                        )
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
     * Loads a single Surah cleanly from bundled `assets/quran_full/sXXX.qgz` using a
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
                    if (surah?.isDownloaded != true) {
                        quranDao.markSurahDownloaded(sid, true)
                    }
                    return@withLock true
                }

                val assetPath = "quran_full/s${sid.toString().padStart(3, '0')}.qgz"
                val parsed = parseCompressedSurah(assetPath, sid)
                if (parsed.ayahs.isEmpty()) return@withLock false

                val alreadyMarkedDownloaded = surah?.isDownloaded == true
                database.withTransaction {
                    if (forceReplaceIfPartial && existingCount in 1 until parsed.ayahs.size) {
                        quranDao.deleteAyahsBySurah(sid)
                    }
                    quranDao.insertAyahs(parsed.ayahs)
                    if (parsed.words.isNotEmpty()) quranDao.insertWords(parsed.words)
                    if (parsed.wordTranslations.isNotEmpty()) quranDao.insertWordTranslations(parsed.wordTranslations)
                    if (parsed.ayahTranslations.isNotEmpty()) quranDao.insertAyahTranslations(parsed.ayahTranslations)
                    if (parsed.audioSegments.isNotEmpty()) quranDao.insertAudioSegments(parsed.audioSegments)
                    if (!alreadyMarkedDownloaded) {
                        quranDao.markSurahDownloaded(sid, true)
                    }
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

    private class StarterCatalogs(
        val translationSources: List<TranslationSourceEntity>,
        val tafsirSources: List<TafsirSourceEntity>,
        val reciters: List<AudioReciterEntity>
    )

    private fun readStarterCatalogsStreaming(): StarterCatalogs {
        val trSources = ArrayList<TranslationSourceEntity>(48)
        val tafSources = ArrayList<TafsirSourceEntity>(24)
        val reciters = ArrayList<AudioReciterEntity>(96)

        JsonReader(InputStreamReader(context.assets.open("quran_starter/quran_starter.json"), Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "translationSources" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            trSources.add(readTranslationSource(reader))
                        }
                        reader.endArray()
                    }
                    "tafsirSources" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            tafSources.add(readTafsirSource(reader))
                        }
                        reader.endArray()
                    }
                    "reciters" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reciters.add(readReciter(reader))
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        return StarterCatalogs(trSources, tafSources, reciters)
    }

    private fun readTranslationSource(reader: JsonReader): TranslationSourceEntity {
        var id = 0
        var languageCode = ""
        var languageName = ""
        var translatorName = ""
        var isDownloaded = false
        var isEnabled = false
        var fileSizeKb = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextInt()
                "languageCode" -> languageCode = reader.nextStringOrEmpty()
                "languageName" -> languageName = reader.nextStringOrEmpty()
                "translatorName" -> translatorName = reader.nextStringOrEmpty()
                "isDownloaded" -> isDownloaded = reader.nextBoolean()
                "isEnabled" -> isEnabled = reader.nextBoolean()
                "fileSizeKb" -> fileSizeKb = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return TranslationSourceEntity(id, languageCode, languageName, translatorName, isDownloaded, isEnabled, fileSizeKb)
    }

    private fun readTafsirSource(reader: JsonReader): TafsirSourceEntity {
        var id = 0
        var languageCode = ""
        var languageName = ""
        var tafsirName = ""
        var isDownloaded = false
        var isEnabled = false
        var fileSizeKb = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextInt()
                "languageCode" -> languageCode = reader.nextStringOrEmpty()
                "languageName" -> languageName = reader.nextStringOrEmpty()
                "tafsirName" -> tafsirName = reader.nextStringOrEmpty()
                "isDownloaded" -> isDownloaded = reader.nextBoolean()
                "isEnabled" -> isEnabled = reader.nextBoolean()
                "fileSizeKb" -> fileSizeKb = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return TafsirSourceEntity(id, languageCode, languageName, tafsirName, isDownloaded, isEnabled, fileSizeKb)
    }

    private fun readReciter(reader: JsonReader): AudioReciterEntity {
        var id = 0
        var name = ""
        var localizedName = ""
        var style = ""
        var baseUrl = ""
        var isDownloaded = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextInt()
                "name" -> name = reader.nextStringOrEmpty()
                "localizedName" -> localizedName = reader.nextStringOrEmpty()
                "style" -> style = reader.nextStringOrEmpty()
                "baseUrl" -> baseUrl = reader.nextStringOrEmpty()
                "isDownloaded" -> isDownloaded = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return AudioReciterEntity(id, name, localizedName, style, baseUrl, isDownloaded)
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

    private data class OverlayWord(
        val position: Int,
        val rootArabic: String,
        val lemmaArabic: String,
        val grammarTag: String,
        val startMs: Long,
        val endMs: Long,
        val meanings: Map<String, String>
    )

    private fun JsonReader.nextStringOrEmpty(): String {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            ""
        } else {
            nextString()
        }
    }

    private suspend fun applyStarterEnrichmentOverlayStreaming(
        rawTranslationSources: List<TranslationSourceEntity>,
        rawTafsirSources: List<TafsirSourceEntity>,
        reciters: List<AudioReciterEntity>
    ) {
        val translatorNamesById = rawTranslationSources.associate { it.id to (it.languageCode to it.translatorName) }
        val tafsirNamesById = rawTafsirSources.associate { it.id to (it.languageCode to it.tafsirName) }
        val extraTimestampReciters = reciters.filter { it.id in setOf(1, 9) }

        val updatedWords = ArrayList<WordEntity>(180)
        val extraWordTranslations = ArrayList<WordTranslationEntity>(360)
        val extraAyahTranslations = ArrayList<AyahTranslationEntity>(96)
        val extraAyahTafsirs = ArrayList<AyahTafsirEntity>(64)
        val extraAudioSegments = ArrayList<AudioSegmentEntity>(64)

        val parsedOverlays = ArrayList<OverlayAyahEntry>(40)

        JsonReader(InputStreamReader(context.assets.open("quran_starter/quran_starter.json"), Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() != "ayahs") {
                    reader.skipValue()
                    continue
                }
                reader.beginArray()
                while (reader.hasNext()) {
                    var verseKey = ""
                    val overlayWords = ArrayList<OverlayWord>(16)
                    val overlayTranslations = HashMap<Int, String>(4)
                    val overlayTafsirs = HashMap<Int, String>(4)

                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "verseKey" -> verseKey = reader.nextStringOrEmpty()
                            "words" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    var pos = 0
                                    var rootAr = ""
                                    var lemmaAr = ""
                                    var grammar = ""
                                    var startMs = 0L
                                    var endMs = 1000L
                                    val meanings = HashMap<String, String>(4)
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "position" -> pos = reader.nextInt()
                                            "rootArabic" -> rootAr = reader.nextStringOrEmpty()
                                            "lemmaArabic" -> lemmaAr = reader.nextStringOrEmpty()
                                            "grammarTag" -> grammar = reader.nextStringOrEmpty()
                                            "startMs" -> startMs = reader.nextLong()
                                            "endMs" -> endMs = reader.nextLong()
                                            "meanings" -> {
                                                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                                    reader.beginObject()
                                                    while (reader.hasNext()) {
                                                        val lang = reader.nextName()
                                                        meanings[lang] = reader.nextStringOrEmpty()
                                                    }
                                                    reader.endObject()
                                                } else {
                                                    reader.skipValue()
                                                }
                                            }
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    if (pos > 0) {
                                        overlayWords.add(OverlayWord(pos, rootAr, lemmaAr, grammar, startMs, endMs, meanings))
                                    }
                                }
                                reader.endArray()
                            }
                            "translations" -> {
                                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val sId = reader.nextName().toIntOrNull()
                                        val text = reader.nextStringOrEmpty()
                                        if (sId != null && text.isNotBlank()) overlayTranslations[sId] = text
                                    }
                                    reader.endObject()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            "tafsirs" -> {
                                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val sId = reader.nextName().toIntOrNull()
                                        val text = reader.nextStringOrEmpty()
                                        if (sId != null && text.isNotBlank()) overlayTafsirs[sId] = text
                                    }
                                    reader.endObject()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()

                    if (verseKey.isNotBlank()) {
                        parsedOverlays.add(OverlayAyahEntry(verseKey, overlayWords, overlayTranslations, overlayTafsirs))
                    }
                }
                reader.endArray()
            }
            reader.endObject()
        }

        if (parsedOverlays.isEmpty()) return

        val canonicalAyahsByKey = parsedOverlays.map { it.verseKey }
            .chunked(200)
            .flatMap { quranDao.getAyahsByKeys(it) }
            .associateBy { it.verseKey }
        val canonicalAyahIds = canonicalAyahsByKey.values.map { it.id }
        val canonicalWordsByAyah = canonicalAyahIds
            .chunked(200)
            .flatMap { quranDao.getWordsForAyahs(it) }
            .groupBy { it.ayahId }

        for (entry in parsedOverlays) {
            val canonicalAyah = canonicalAyahsByKey[entry.verseKey] ?: continue
            val canonicalWordsByPos = canonicalWordsByAyah[canonicalAyah.id].orEmpty().associateBy { it.position }
            val surahPadded = canonicalAyah.surahId.toString().padStart(3, '0')
            val ayahPadded = canonicalAyah.ayahNumber.toString().padStart(3, '0')

            val segJsonBuilder = StringBuilder("[")
            entry.words.forEachIndexed { idx, wObj ->
                val canonicalWord = canonicalWordsByPos[wObj.position]
                if (canonicalWord != null) {
                    if (wObj.rootArabic.isNotBlank() || wObj.lemmaArabic.isNotBlank() || wObj.grammarTag.isNotBlank()) {
                        updatedWords.add(
                            canonicalWord.copy(
                                rootArabic = wObj.rootArabic.ifBlank { canonicalWord.rootArabic },
                                lemmaArabic = wObj.lemmaArabic.ifBlank { canonicalWord.lemmaArabic },
                                grammarTagJson = wObj.grammarTag.ifBlank { canonicalWord.grammarTagJson }
                            )
                        )
                    }
                    wObj.meanings.forEach { (lang, text) ->
                        if (text.isNotBlank()) {
                            extraWordTranslations.add(
                                WordTranslationEntity(
                                    wordId = canonicalWord.id,
                                    languageCode = lang,
                                    meaningText = text
                                )
                            )
                        }
                    }
                }
                if (idx > 0) segJsonBuilder.append(',')
                segJsonBuilder.append("{\"position\":").append(wObj.position)
                    .append(",\"startMs\":").append(wObj.startMs)
                    .append(",\"endMs\":").append(wObj.endMs)
                    .append('}')
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

            entry.translations.forEach { (sourceId, text) ->
                val (langCode, translatorName) = translatorNamesById[sourceId] ?: ("en" to "Quran Translation")
                extraAyahTranslations.add(
                    AyahTranslationEntity(
                        sourceId = sourceId,
                        ayahId = canonicalAyah.id,
                        languageCode = langCode,
                        translatorName = translatorName,
                        translatedText = text
                    )
                )
            }

            entry.tafsirs.forEach { (sourceId, text) ->
                val (langCode, tafsirName) = tafsirNamesById[sourceId] ?: ("en" to "Tafsir")
                extraAyahTafsirs.add(
                    AyahTafsirEntity(
                        sourceId = sourceId,
                        ayahId = canonicalAyah.id,
                        languageCode = langCode,
                        tafsirName = tafsirName,
                        tafsirHtmlOrMarkdown = text
                    )
                )
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

    private data class OverlayAyahEntry(
        val verseKey: String,
        val words: List<OverlayWord>,
        val translations: Map<Int, String>,
        val tafsirs: Map<Int, String>
    )

    private suspend fun seedDuasInChunks(interChunkDelayMs: Long = 0L) {
        val bookmarkedIds = duaDao.getBookmarkedDuaIds().toHashSet()
        DuaSeederData.readInChunks(context, chunkSize = 90).forEach { chunk ->
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
            if (interChunkDelayMs > 0L) {
                delay(interChunkDelayMs)
            } else {
                yield()
            }
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
}
