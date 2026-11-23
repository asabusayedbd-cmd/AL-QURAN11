package com.example.core.database

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.core.localization.BidiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuranDatabaseSeeder(
    private val context: Context,
    private val database: NoorDatabase
) {
    private val quranDao = database.quranDao()
    private val userLibraryDao = database.userLibraryDao()

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val alreadyHasCoreText = quranDao.countSurahs() >= 114 && quranDao.getAyahByKey("1:1") != null
            val needsCatalogUpgrade = quranDao.countReciters() < 50 ||
                quranDao.countTafsirSources() < 15 ||
                quranDao.countTranslationSources() < 12

            if (alreadyHasCoreText && !needsCatalogUpgrade) {
                ensureDefaultUserLibraryState()
                return@withContext
            }

            val surahsJson = context.assets.open("quran_starter/surahs_index.json")
                .bufferedReader().use { it.readText() }
            val starterJson = context.assets.open("quran_starter/quran_starter.json")
                .bufferedReader().use { it.readText() }

            val starterRoot = JSONObject(starterJson)
            val rawTranslationSources = parseTranslationSources(starterRoot.getJSONArray("translationSources"))
            val rawTafsirSources = parseTafsirSources(starterRoot.getJSONArray("tafsirSources"))
            val reciters = parseReciters(starterRoot.getJSONArray("reciters"))

            if (alreadyHasCoreText) {
                val existingTrMap = quranDao.getAllTranslationSources().associateBy { it.id }
                val mergedTr = rawTranslationSources.map { src ->
                    val prev = existingTrMap[src.id]
                    if (prev != null) src.copy(isDownloaded = prev.isDownloaded, isEnabled = prev.isEnabled) else src
                }
                val existingTafsirMap = quranDao.getAllTafsirSources().associateBy { it.id }
                val mergedTafsir = rawTafsirSources.map { src ->
                    val prev = existingTafsirMap[src.id]
                    if (prev != null) src.copy(isDownloaded = prev.isDownloaded, isEnabled = prev.isEnabled) else src
                }
                database.withTransaction {
                    quranDao.insertTranslationSources(mergedTr)
                    quranDao.insertTafsirSources(mergedTafsir)
                    quranDao.insertReciters(reciters)
                }
                ensureDefaultUserLibraryState()
                return@withContext
            }

            val ayahObjects = starterRoot.getJSONArray("ayahs")
            val downloadedSurahs = mutableSetOf<Int>()
            for (i in 0 until ayahObjects.length()) {
                val surahId = ayahObjects.getJSONObject(i).getInt("surahId")
                // Surahs fully included offline in starter bundle: 1, 103, 108, 112, 113, 114
                if (surahId in setOf(1, 103, 108, 112, 113, 114)) {
                    downloadedSurahs.add(surahId)
                }
            }

            val surahsArray = JSONArray(surahsJson)
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
                        isDownloaded = id in downloadedSurahs
                    )
                )
            }

            val translationSources = rawTranslationSources
            val tafsirSources = rawTafsirSources

            val ayahs = mutableListOf<AyahEntity>()
            val words = mutableListOf<WordEntity>()
            val wordTranslations = mutableListOf<WordTranslationEntity>()
            val ayahTranslations = mutableListOf<AyahTranslationEntity>()
            val ayahTafsirs = mutableListOf<AyahTafsirEntity>()
            val audioSegments = mutableListOf<AudioSegmentEntity>()

            val translatorNamesById = translationSources.associate { it.id to (it.languageCode to it.translatorName) }
            val tafsirNamesById = tafsirSources.associate { it.id to (it.languageCode to it.tafsirName) }
            val coreTimestampReciters = reciters.filter { it.id in setOf(1, 7, 9) }

            for (i in 0 until ayahObjects.length()) {
                val obj = ayahObjects.getJSONObject(i)
                val ayahId = obj.getInt("id")
                val surahId = obj.getInt("surahId")
                val ayahNumber = obj.getInt("ayahNumber")
                val textUthmani = obj.getString("textUthmani")
                ayahs.add(
                    AyahEntity(
                        id = ayahId,
                        surahId = surahId,
                        ayahNumber = ayahNumber,
                        verseKey = obj.getString("verseKey"),
                        textUthmani = textUthmani,
                        textIndopak = obj.getString("textIndopak"),
                        textTajweedHtml = obj.getString("textTajweedHtml"),
                        normalizedArabic = BidiUtils.normalizeArabicForSearch(textUthmani),
                        juzNumber = obj.getInt("juzNumber"),
                        hizbNumber = obj.getInt("hizbNumber"),
                        pageNumber = obj.getInt("pageNumber"),
                        sajdahType = obj.optString("sajdahType").takeIf { it.isNotBlank() && it != "null" }
                    )
                )

                val wordsArray = obj.optJSONArray("words") ?: JSONArray()
                val segmentArray = JSONArray()
                for (w in 0 until wordsArray.length()) {
                    val wObj = wordsArray.getJSONObject(w)
                    val wordId = wObj.getLong("id")
                    val pos = wObj.getInt("position")
                    val surahPadded = surahId.toString().padStart(3, '0')
                    val ayahPadded = ayahNumber.toString().padStart(3, '0')
                    val posPadded = pos.toString().padStart(3, '0')
                    words.add(
                        WordEntity(
                            id = wordId,
                            ayahId = ayahId,
                            position = pos,
                            textUthmani = wObj.getString("textUthmani"),
                            textIndopak = wObj.getString("textIndopak"),
                            transliteration = wObj.getString("transliteration"),
                            rootArabic = wObj.optString("rootArabic"),
                            lemmaArabic = wObj.optString("lemmaArabic"),
                            grammarTagJson = wObj.optString("grammarTag"),
                            audioUrl = "https://audio.qurancdn.com/wbw/${surahPadded}_${ayahPadded}_${posPadded}.mp3"
                        )
                    )

                    val meanings = wObj.optJSONObject("meanings")
                    if (meanings != null) {
                        val keys = meanings.keys()
                        while (keys.hasNext()) {
                            val lang = keys.next()
                            wordTranslations.add(
                                WordTranslationEntity(
                                    wordId = wordId,
                                    languageCode = lang,
                                    meaningText = meanings.getString(lang)
                                )
                            )
                        }
                    }

                    val segObj = JSONObject()
                    segObj.put("position", pos)
                    segObj.put("startMs", wObj.optLong("startMs", 0L))
                    segObj.put("endMs", wObj.optLong("endMs", 1000L))
                    segmentArray.put(segObj)
                }

                val surahPadded = surahId.toString().padStart(3, '0')
                val ayahPadded = ayahNumber.toString().padStart(3, '0')
                for (reciter in coreTimestampReciters) {
                    audioSegments.add(
                        AudioSegmentEntity(
                            reciterId = reciter.id,
                            ayahId = ayahId,
                            audioUrl = "${reciter.baseUrl}${surahPadded}${ayahPadded}.mp3",
                            localFilePath = null,
                            wordTimestampSegmentsJson = segmentArray.toString()
                        )
                    )
                }

                val translationsObj = obj.optJSONObject("translations")
                if (translationsObj != null) {
                    val keys = translationsObj.keys()
                    while (keys.hasNext()) {
                        val sourceIdStr = keys.next()
                        val sourceId = sourceIdStr.toIntOrNull() ?: continue
                        val (langCode, translatorName) = translatorNamesById[sourceId] ?: ("en" to "Verified Translation")
                        ayahTranslations.add(
                            AyahTranslationEntity(
                                sourceId = sourceId,
                                ayahId = ayahId,
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
                        ayahTafsirs.add(
                            AyahTafsirEntity(
                                sourceId = sourceId,
                                ayahId = ayahId,
                                languageCode = langCode,
                                tafsirName = tafsirName,
                                tafsirHtmlOrMarkdown = tafsirsObj.getString(sourceIdStr)
                            )
                        )
                    }
                }
            }

            database.withTransaction {
                quranDao.insertSurahs(surahEntities)
                quranDao.insertTranslationSources(translationSources)
                quranDao.insertTafsirSources(tafsirSources)
                quranDao.insertReciters(reciters)
                quranDao.insertAyahs(ayahs)
                quranDao.insertWords(words)
                quranDao.insertWordTranslations(wordTranslations)
                quranDao.insertAyahTranslations(ayahTranslations)
                quranDao.insertAyahTafsirs(ayahTafsirs)
                quranDao.insertAudioSegments(audioSegments)
            }

            ensureDefaultUserLibraryState()
        } catch (e: Exception) {
            Log.e("QuranSeeder", "Failed seeding starter Quran data", e)
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
