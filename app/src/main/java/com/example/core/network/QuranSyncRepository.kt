package com.example.core.network

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.core.database.AyahEntity
import com.example.core.database.AyahTafsirEntity
import com.example.core.database.AyahTranslationEntity
import com.example.core.database.AudioSegmentEntity
import com.example.core.database.NoorDatabase
import com.example.core.database.TranslationSourceEntity
import com.example.core.database.WordEntity
import com.example.core.database.WordTranslationEntity
import com.example.core.localization.BidiUtils
import com.example.core.localization.LineTranslator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class QuranSyncRepository(
    private val database: NoorDatabase,
    private val apiService: QuranComApiService,
    private val context: Context? = null
) {
    private val quranDao = database.quranDao()
    private val wbwSyncMutexes = ConcurrentHashMap<String, Mutex>()
    private val trSyncMutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun downloadSurahForOffline(
        surahId: Int,
        translationIds: List<Int>? = null,
        wbwLanguages: List<String> = listOf("bn", "en"),
        reciterId: Int = 7
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val surah = quranDao.getSurah(surahId) ?: return@withContext Result.failure(
                IllegalArgumentException("Unknown Surah $surahId")
            )
            val allTranslationSources = quranDao.getAllTranslationSources()
            val translationLangById = allTranslationSources.associate { it.id to it.languageCode }
            val resolvedTranslationIds = translationIds?.takeIf { it.isNotEmpty() }
                ?: allTranslationSources
                    .filter { it.isEnabled }
                    .map { it.id }
                    .ifEmpty { listOf(20, 161) }

            val reciter = quranDao.getReciter(reciterId)
            val totalPages = ((surah.ayahCount - 1) / 50) + 1
            val translationsCsv = resolvedTranslationIds.distinct().joinToString(",")
            val primaryWbwLang = wbwLanguages.firstOrNull()?.takeIf { it.isNotBlank() } ?: "en"

            // Fetch multi-page Surahs concurrently in bounded batches for fast loading
            val pageResponses = coroutineScope {
                (1..totalPages).chunked(3).flatMap { batch ->
                    batch.map { page ->
                        async {
                            apiService.getVersesByChapter(
                                chapterNumber = surahId,
                                language = primaryWbwLang,
                                translations = translationsCsv,
                                audio = 7, // Reliable Quran.com v4 word-timestamp recitation ID
                                page = page,
                                perPage = 50
                            )
                        }
                    }.awaitAll()
                }
            }

            val ayahsToSave = mutableListOf<AyahEntity>()
            val wordsToSave = mutableListOf<WordEntity>()
            val wordTranslationsToSave = mutableListOf<WordTranslationEntity>()
            val ayahTranslationsToSave = mutableListOf<AyahTranslationEntity>()
            val segmentsToSave = mutableListOf<AudioSegmentEntity>()

            for (response in pageResponses) {
                for (v in response.verses) {
                    val onlyWords = v.words.filter { w ->
                        val type = w.charTypeName?.lowercase()
                        val text = w.textUthmani.orEmpty().trim()
                        type != "end" &&
                            !(type == "pause" && w.translation?.text.isNullOrBlank()) &&
                            text.isNotBlank() &&
                            !VERSE_END_MARKER_REGEX.matches(text)
                    }

                    val uthmani = v.textUthmani?.takeIf { it.isNotBlank() }
                        ?: onlyWords.joinToString(" ") { it.textUthmani.orEmpty() }
                    val indopak = v.textIndopak?.takeIf { it.isNotBlank() }
                        ?: onlyWords.joinToString(" ") { it.textIndopak ?: it.textUthmani.orEmpty() }.ifBlank { uthmani }
                    val rawTajweed = v.textTajweed?.let { stripEndSpan(it) }.takeIf { !it.isNullOrBlank() } ?: uthmani

                    ayahsToSave.add(
                        AyahEntity(
                            id = v.id,
                            surahId = surahId,
                            ayahNumber = v.verseNumber,
                            verseKey = v.verseKey,
                            textUthmani = uthmani,
                            textIndopak = indopak,
                            textTajweedHtml = rawTajweed,
                            normalizedArabic = BidiUtils.normalizeArabicForSearch(uthmani),
                            juzNumber = v.juzNumber,
                            hizbNumber = v.hizbNumber,
                            pageNumber = v.pageNumber
                        )
                    )

                    for (w in onlyWords) {
                        val wordTextUthmani = w.textUthmani.orEmpty().trim()
                        wordsToSave.add(
                            WordEntity(
                                id = w.id,
                                ayahId = v.id,
                                position = w.position,
                                textUthmani = wordTextUthmani,
                                textIndopak = w.textIndopak?.takeIf { it.isNotBlank() } ?: wordTextUthmani,
                                transliteration = cleanHtml(w.transliteration?.text.orEmpty()),
                                rootArabic = "",
                                lemmaArabic = "",
                                grammarTagJson = "",
                                audioUrl = w.audioUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                    if (url.startsWith("http")) url else "https://audio.qurancdn.com/$url"
                                }
                            )
                        )
                        val meaning = cleanHtml(w.translation?.text.orEmpty())
                        if (meaning.isNotBlank()) {
                            wordTranslationsToSave.add(
                                WordTranslationEntity(
                                    wordId = w.id,
                                    languageCode = primaryWbwLang,
                                    meaningText = meaning
                                )
                            )
                        }
                    }

                    for (tr in v.translations) {
                        val langCode = translationLangById[tr.resourceId] ?: inferTranslationLang(tr.resourceId)
                        ayahTranslationsToSave.add(
                            AyahTranslationEntity(
                                sourceId = tr.resourceId,
                                ayahId = v.id,
                                languageCode = langCode,
                                translatorName = tr.resourceName ?: "Quran Translation",
                                translatedText = cleanHtml(tr.text)
                            )
                        )
                    }

                    val segJson = JSONArray().apply {
                        v.audio?.segments?.forEach { seg ->
                            if (seg.size >= 3) {
                                put(
                                    JSONObject().apply {
                                        put("position", seg[0].toInt())
                                        put("startMs", seg[1])
                                        put("endMs", seg[2])
                                    }
                                )
                            }
                        }
                    }.toString()

                    val alafasyUrl = v.audio?.url?.takeIf { it.isNotBlank() }?.let { raw ->
                        if (raw.startsWith("http")) raw else "https://verses.quran.com/$raw"
                    } ?: buildFallbackAudioUrl(null, 7, surahId, v.verseNumber)

                    segmentsToSave.add(
                        AudioSegmentEntity(
                            reciterId = 7,
                            ayahId = v.id,
                            audioUrl = alafasyUrl,
                            localFilePath = null,
                            wordTimestampSegmentsJson = segJson
                        )
                    )
                    if (reciterId != 7) {
                        segmentsToSave.add(
                            AudioSegmentEntity(
                                reciterId = reciterId,
                                ayahId = v.id,
                                audioUrl = buildFallbackAudioUrl(reciter?.baseUrl, reciterId, surahId, v.verseNumber),
                                localFilePath = null,
                                wordTimestampSegmentsJson = segJson
                            )
                        )
                    }
                }
            }

            if (ayahsToSave.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No verses returned for Surah $surahId"))
            }

            database.withTransaction {
                quranDao.insertAyahs(ayahsToSave)

                // Match existing words by (ayahId, position) in batched queries so starter metadata
                // and multilingual word translations survive cleanly without N+1 database calls.
                val ayahIds = ayahsToSave.map { it.id }
                val existingWords = ayahIds.chunked(500).flatMap { quranDao.getWordsForAyahs(it) }
                val existingByPos = existingWords.associateBy { it.ayahId to it.position }

                val changedOldIds = wordsToSave.mapNotNull { w ->
                    val old = existingByPos[w.ayahId to w.position]
                    if (old != null && old.id != w.id) old.id else null
                }
                val oldTranslationsByWordId = if (changedOldIds.isNotEmpty()) {
                    changedOldIds.chunked(500)
                        .flatMap { quranDao.getWordTranslationsForWords(it) }
                        .groupBy { it.wordId }
                } else {
                    emptyMap()
                }
                val migratedOldTranslations = mutableListOf<WordTranslationEntity>()

                val mergedWords = wordsToSave.map { w ->
                    val old = existingByPos[w.ayahId to w.position]
                    if (old != null) {
                        if (old.id != w.id) {
                            oldTranslationsByWordId[old.id]?.forEach { tr ->
                                migratedOldTranslations.add(tr.copy(wordId = w.id))
                            }
                        }
                        w.copy(
                            rootArabic = old.rootArabic.ifBlank { w.rootArabic },
                            lemmaArabic = old.lemmaArabic.ifBlank { w.lemmaArabic },
                            grammarTagJson = old.grammarTagJson.ifBlank { w.grammarTagJson },
                            transliteration = w.transliteration.ifBlank { old.transliteration }
                        )
                    } else w
                }

                quranDao.insertWords(mergedWords)
                if (mergedWords.isNotEmpty()) {
                    ayahsToSave.map { it.id }.chunked(500).forEach { ayahChunk ->
                        val validIds = mergedWords.filter { it.ayahId in ayahChunk }.map { it.id }
                        if (validIds.isNotEmpty()) {
                            quranDao.deleteObsoleteWords(ayahChunk, validIds)
                        }
                    }
                }
                val combinedWordTrs = (migratedOldTranslations + wordTranslationsToSave)
                if (combinedWordTrs.isNotEmpty()) {
                    quranDao.insertWordTranslations(combinedWordTrs)
                }
                if (ayahTranslationsToSave.isNotEmpty()) {
                    quranDao.insertAyahTranslations(ayahTranslationsToSave)
                }
                if (segmentsToSave.isNotEmpty()) {
                    quranDao.insertAudioSegments(segmentsToSave)
                }
                quranDao.markSurahDownloaded(surahId, ayahsToSave.size >= surah.ayahCount)
            }
            Result.success(ayahsToSave.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("QuranSync", "Failed downloading Surah $surahId", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAyahTafsirIfMissing(
        ayahId: Int,
        verseKey: String,
        tafsirId: Int,
        languageCode: String,
        tafsirName: String
    ): Result<AyahTafsirEntity> = withContext(Dispatchers.IO) {
        try {
            val existing = quranDao.getTafsirsForAyah(ayahId).firstOrNull { it.sourceId == tafsirId }
            if (existing != null) return@withContext Result.success(existing)

            val response = apiService.getAyahTafsir(tafsirId, verseKey)
            val html = response.tafsir?.text?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(IllegalStateException("Tafsir not available on API for $verseKey"))
            val entity = AyahTafsirEntity(
                sourceId = tafsirId,
                ayahId = ayahId,
                languageCode = languageCode,
                tafsirName = response.tafsir.resourceName ?: tafsirName,
                tafsirHtmlOrMarkdown = html
            )
            quranDao.insertAyahTafsirs(listOf(entity))
            quranDao.updateTafsirSourceStatus(tafsirId, downloaded = true, enabled = true)
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("QuranSync", "Failed fetching Tafsir $tafsirId for $verseKey", e)
            Result.failure(e)
        }
    }

    suspend fun refreshRemoteTafsirCatalog(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val existingMap = quranDao.getAllTafsirSources().associateBy { it.id }
            val remote = apiService.getTafsirResources("en").tafsirs
            val entities = remote.map { item ->
                val prev = existingMap[item.id]
                val langCode = mapLanguageNameToCode(item.languageName)
                com.example.core.database.TafsirSourceEntity(
                    id = item.id,
                    languageCode = langCode,
                    languageName = item.languageName ?: langCode.uppercase(),
                    tafsirName = item.name,
                    isDownloaded = prev?.isDownloaded ?: false,
                    isEnabled = prev?.isEnabled ?: false,
                    fileSizeKb = prev?.fileSizeKb ?: 7500
                )
            }
            if (entities.isNotEmpty()) {
                quranDao.insertTafsirSources(entities)
            }
            Result.success(entities.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("QuranSync", "Could not refresh remote tafsir catalog", e)
            Result.failure(e)
        }
    }

    suspend fun ensureWbwForSurah(
        surahId: Int,
        wbwLanguageCode: String,
        lineTranslator: LineTranslator? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val lang = wbwLanguageCode.trim().lowercase()
        if (lang.isBlank() || lang == "en") return@withContext Result.success(0)

        val mutex = wbwSyncMutexes.computeIfAbsent("$surahId:$lang") { Mutex() }
        mutex.withLock {
            try {
                val surah = quranDao.getSurah(surahId) ?: return@withLock Result.success(0)
                val totalWords = quranDao.countWordsInSurah(surahId)
                if (totalWords == 0) return@withLock Result.success(0)
                val existingLangCount = quranDao.countWordTranslationsForSurah(surahId, lang)
                if (existingLangCount >= (totalWords * 0.92f).toInt()) {
                    return@withLock Result.success(0)
                }

                val ayahs = quranDao.getAyahsBySurah(surahId)
                val ayahIds = ayahs.map { it.id }
                val ayahByVerseKey = ayahs.associateBy { it.verseKey }
                val localWords = ayahIds.chunked(500).flatMap { quranDao.getWordsForAyahs(it) }
                if (localWords.isEmpty()) return@withLock Result.success(0)
                val wordByAyahPos = localWords.associateBy { it.ayahId to it.position }

                var insertedCount = 0
                val totalPages = ((surah.ayahCount - 1) / 50) + 1
                val apiMeanings = ArrayList<WordTranslationEntity>(localWords.size)

                runCatching {
                    val pages = coroutineScope {
                        (1..totalPages).chunked(3).flatMap { batch ->
                            batch.map { page ->
                                async {
                                    apiService.getVersesByChapter(
                                        chapterNumber = surahId,
                                        language = lang,
                                        words = true,
                                        translations = "",
                                        audio = 0,
                                        page = page,
                                        perPage = 50
                                    )
                                }
                            }.awaitAll()
                        }
                    }
                    for (resp in pages) {
                        for (v in resp.verses) {
                            val localAyah = ayahByVerseKey[v.verseKey] ?: continue
                            for (w in v.words) {
                                val meaning = cleanHtml(w.translation?.text.orEmpty())
                                if (meaning.isBlank()) continue
                                val matchedWord = wordByAyahPos[localAyah.id to w.position] ?: continue
                                apiMeanings.add(
                                    WordTranslationEntity(
                                        wordId = matchedWord.id,
                                        languageCode = lang,
                                        meaningText = meaning
                                    )
                                )
                            }
                        }
                    }
                }

                if (apiMeanings.isNotEmpty()) {
                    quranDao.insertWordTranslations(apiMeanings)
                    insertedCount += apiMeanings.size
                }

                // Fill any remaining words without this WBW language via batch LineTranslator from English
                if (lineTranslator != null) {
                    val allWordTrs = localWords.map { it.id }
                        .chunked(500)
                        .flatMap { quranDao.getWordTranslationsForWords(it) }
                        .groupBy { it.wordId }

                    val missingWordToEnglish = ArrayList<Pair<Long, String>>()
                    for (w in localWords) {
                        val trs = allWordTrs[w.id].orEmpty()
                        val hasLang = trs.any { it.languageCode.equals(lang, ignoreCase = true) && it.meaningText.isNotBlank() }
                        if (!hasLang) {
                            val enMeaning = trs.firstOrNull { it.languageCode.equals("en", ignoreCase = true) }?.meaningText
                                ?: w.transliteration
                            if (enMeaning.isNotBlank()) {
                                missingWordToEnglish.add(w.id to enMeaning.trim())
                            }
                        }
                    }

                    if (missingWordToEnglish.isNotEmpty()) {
                        val distinctEn = missingWordToEnglish.map { it.second }.distinct()
                        val translatedMap = HashMap<String, String>(distinctEn.size)
                        for (chunk in distinctEn.chunked(40)) {
                            val res = lineTranslator.translateLinesJoined(chunk, lang)
                            if (res.isSuccess) {
                                val out = res.getOrThrow()
                                chunk.forEachIndexed { idx, srcText ->
                                    val tr = out.getOrNull(idx).orEmpty().trim()
                                    if (tr.isNotBlank()) translatedMap[srcText] = tr
                                }
                            }
                        }
                        if (translatedMap.isNotEmpty()) {
                            val fallbackEntities = missingWordToEnglish.mapNotNull { (wordId, enText) ->
                                translatedMap[enText]?.let { trText ->
                                    WordTranslationEntity(
                                        wordId = wordId,
                                        languageCode = lang,
                                        meaningText = trText
                                    )
                                }
                            }
                            if (fallbackEntities.isNotEmpty()) {
                                quranDao.insertWordTranslations(fallbackEntities)
                                insertedCount += fallbackEntities.size
                            }
                        }
                    }
                }

                Result.success(insertedCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("QuranSync", "Failed ensuring WBW '$lang' for Surah $surahId", e)
                Result.failure(e)
            }
        }
    }

    suspend fun ensureTranslationsForSurah(
        surahId: Int,
        enabledSources: List<TranslationSourceEntity>,
        lineTranslator: LineTranslator? = null
    ) = withContext(Dispatchers.IO) {
        val surah = quranDao.getSurah(surahId) ?: return@withContext
        val targets = enabledSources.filter { it.id != 20 }
        if (targets.isEmpty()) return@withContext

        for (source in targets) {
            val mutex = trSyncMutexes.computeIfAbsent("$surahId:${source.id}") { Mutex() }
            mutex.withLock {
                try {
                    val existingCount = quranDao.countTranslationsForSurahBySource(surahId, source.id)
                    if (existingCount >= surah.ayahCount) return@withLock

                    syncSurahTranslationSource(surahId, surah.ayahCount, source, lineTranslator)
                    if (quranDao.countTranslationsForSource(source.id) >= 6000) {
                        quranDao.setTranslationDownloaded(source.id, true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("QuranSync", "Failed ensuring translation ${source.id} for Surah $surahId", e)
                }
            }
        }
    }

    private suspend fun syncSurahTranslationSource(
        surahId: Int,
        ayahCount: Int,
        source: TranslationSourceEntity,
        lineTranslator: LineTranslator?
    ): Int {
        val ayahs = quranDao.getAyahsBySurah(surahId)
        if (ayahs.isEmpty()) return 0
        val ayahByVerseKey = ayahs.associateBy { it.verseKey }
        val totalPages = ((ayahCount - 1) / 50) + 1
        var saved = 0

        val apiEntities = ArrayList<AyahTranslationEntity>(ayahCount)
        runCatching {
            val pages = coroutineScope {
                (1..totalPages).chunked(3).flatMap { batch ->
                    batch.map { page ->
                        async {
                            apiService.getVersesByChapter(
                                chapterNumber = surahId,
                                language = source.languageCode,
                                words = false,
                                fields = "verse_key",
                                translations = source.id.toString(),
                                audio = 0,
                                page = page,
                                perPage = 50
                            )
                        }
                    }.awaitAll()
                }
            }
            for (resp in pages) {
                for (v in resp.verses) {
                    val localAyah = ayahByVerseKey[v.verseKey] ?: continue
                    val tr = v.translations.firstOrNull { it.resourceId == source.id }
                        ?: v.translations.firstOrNull()
                        ?: continue
                    val cleanText = cleanHtml(tr.text)
                    if (cleanText.isNotBlank()) {
                        apiEntities.add(
                            AyahTranslationEntity(
                                sourceId = source.id,
                                ayahId = localAyah.id,
                                languageCode = source.languageCode,
                                translatorName = tr.resourceName?.takeIf { it.isNotBlank() } ?: source.translatorName,
                                translatedText = cleanText
                            )
                        )
                    }
                }
            }
        }

        if (apiEntities.isNotEmpty()) {
            quranDao.insertAyahTranslations(apiEntities)
            saved += apiEntities.size
        }

        // Fallback to translating from bundled English (sourceId = 20) if API was unreachable or returned partial verses
        if (lineTranslator != null) {
            val allTrsForSurah = ayahs.map { it.id }
                .chunked(500)
                .flatMap { quranDao.getTranslationsForAyahs(it) }
                .groupBy { it.ayahId }

            val missingPairs = ArrayList<Pair<Int, String>>()
            for (ayah in ayahs) {
                val trs = allTrsForSurah[ayah.id].orEmpty()
                val alreadyHasTarget = trs.any { it.sourceId == source.id && it.translatedText.isNotBlank() }
                if (!alreadyHasTarget) {
                    val enText = trs.firstOrNull { it.sourceId == 20 }?.translatedText
                        ?: trs.firstOrNull { it.languageCode.equals("en", ignoreCase = true) }?.translatedText
                    if (!enText.isNullOrBlank()) {
                        missingPairs.add(ayah.id to enText)
                    }
                }
            }

            if (missingPairs.isNotEmpty()) {
                val targetLang = source.languageCode.ifBlank { inferTranslationLang(source.id) }
                val translatedEntities = ArrayList<AyahTranslationEntity>(missingPairs.size)
                for (chunk in missingPairs.chunked(12)) {
                    val res = lineTranslator.translateLinesJoined(chunk.map { it.second }, targetLang)
                    if (res.isSuccess) {
                        val out = res.getOrThrow()
                        chunk.forEachIndexed { idx, (ayahId, _) ->
                            val trText = out.getOrNull(idx).orEmpty().trim()
                            if (trText.isNotBlank()) {
                                translatedEntities.add(
                                    AyahTranslationEntity(
                                        sourceId = source.id,
                                        ayahId = ayahId,
                                        languageCode = targetLang,
                                        translatorName = source.translatorName,
                                        translatedText = trText
                                    )
                                )
                            }
                        }
                    }
                }
                if (translatedEntities.isNotEmpty()) {
                    quranDao.insertAyahTranslations(translatedEntities)
                    saved += translatedEntities.size
                }
            }
        }
        return saved
    }

    suspend fun downloadTranslationPack(
        sourceId: Int,
        lineTranslator: LineTranslator? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (sourceId == 20) {
                quranDao.setTranslationDownloaded(20, true)
                quranDao.setTranslationEnabled(20, true)
                return@withContext Result.success(Unit)
            }
            val source = quranDao.getAllTranslationSources().firstOrNull { it.id == sourceId }
                ?: TranslationSourceEntity(
                    id = sourceId,
                    languageCode = inferTranslationLang(sourceId),
                    languageName = inferTranslationLang(sourceId).uppercase(),
                    translatorName = "Quran Translation",
                    isDownloaded = false,
                    isEnabled = true,
                    fileSizeKb = 1500
                )

            var syncedAyahs = 0
            for (surahId in 1..114) {
                val surah = quranDao.getSurah(surahId) ?: continue
                if (quranDao.countAyahsInSurah(surahId) == 0) continue
                val existing = quranDao.countTranslationsForSurahBySource(surahId, sourceId)
                if (existing >= surah.ayahCount) continue
                syncedAyahs += syncSurahTranslationSource(surahId, surah.ayahCount, source, lineTranslator)
            }

            val totalCount = quranDao.countTranslationsForSource(sourceId)
            val isComplete = totalCount >= 6000
            quranDao.setTranslationDownloaded(sourceId, isComplete)

            if (isComplete || syncedAyahs > 0 || totalCount > 0) {
                quranDao.setTranslationEnabled(sourceId, true)
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Could not download translation offline. Check connection."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("QuranSync", "Failed downloading translation pack $sourceId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSurahOffline(surahId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context?.let { ctx ->
                val audioRoot = File(ctx.filesDir, "audio")
                val s = surahId.toString().padStart(3, '0')
                if (audioRoot.exists()) {
                    audioRoot.listFiles()?.forEach { qariDir ->
                        if (qariDir.isDirectory) {
                            qariDir.listFiles { f -> f.name.startsWith(s) }?.forEach { it.delete() }
                        }
                    }
                }
            }
            database.withTransaction {
                quranDao.deleteAudioSegmentsBySurah(surahId)
                quranDao.deleteAyahTafsirsBySurah(surahId)
                quranDao.deleteAyahTranslationsBySurah(surahId)
                quranDao.deleteWordTranslationsBySurah(surahId)
                quranDao.deleteWordsBySurah(surahId)
                quranDao.deleteAyahsBySurah(surahId)
                quranDao.markSurahDownloaded(surahId, false)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("QuranSync", "Failed to delete Surah $surahId offline data", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAllDownloadedSurahs(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            context?.let { ctx ->
                val audioRoot = File(ctx.filesDir, "audio")
                audioRoot.deleteRecursively()
            }
            val downloadedIds = quranDao.getDownloadedSurahIds()
            var count = 0
            database.withTransaction {
                for (id in downloadedIds) {
                    quranDao.deleteAudioSegmentsBySurah(id)
                    quranDao.deleteAyahTafsirsBySurah(id)
                    quranDao.deleteAyahTranslationsBySurah(id)
                    quranDao.deleteWordTranslationsBySurah(id)
                    quranDao.deleteWordsBySurah(id)
                    quranDao.deleteAyahsBySurah(id)
                    quranDao.markSurahDownloaded(id, false)
                    count++
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.e("QuranSync", "Failed to delete all downloaded surahs", e)
            Result.failure(e)
        }
    }

    suspend fun deleteTranslationPack(sourceId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                quranDao.deleteTranslationsBySource(sourceId)
                quranDao.setTranslationDownloaded(sourceId, false)
                quranDao.setTranslationEnabled(sourceId, false)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("QuranSync", "Failed to delete translation pack $sourceId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteTafsirPack(sourceId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                quranDao.deleteTafsirsBySource(sourceId)
                quranDao.updateTafsirSourceStatus(sourceId, downloaded = false, enabled = false)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("QuranSync", "Failed to delete tafsir pack $sourceId", e)
            Result.failure(e)
        }
    }

    private fun inferTranslationLang(resourceId: Int): String = when (resourceId) {
        161, 162, 163 -> "bn"
        97, 54, 158, 234 -> "ur"
        33, 134 -> "id"
        77, 52, 210 -> "tr"
        31, 136 -> "fr"
        83 -> "es"
        122 -> "hi"
        27, 208 -> "de"
        39 -> "ms"
        45, 79 -> "ru"
        29, 135 -> "fa"
        133 -> "ta"
        80 -> "ml"
        56 -> "zh"
        35 -> "ja"
        219 -> "ko"
        231 -> "sw"
        126 -> "bs"
        89 -> "sq"
        46 -> "so"
        153 -> "it"
        43 -> "pt"
        144 -> "nl"
        101 -> "uz"
        else -> "en"
    }

    private fun mapLanguageNameToCode(languageName: String?): String {
        return when (languageName?.lowercase()?.trim()) {
            "bengali", "bangla" -> "bn"
            "arabic" -> "ar"
            "english" -> "en"
            "urdu" -> "ur"
            "indonesian" -> "id"
            "turkish" -> "tr"
            "russian" -> "ru"
            "kurdish" -> "ku"
            "french" -> "fr"
            "spanish" -> "es"
            "hindi" -> "hi"
            "malay" -> "ms"
            "german" -> "de"
            "persian", "farsi" -> "fa"
            "tamil" -> "ta"
            "malayalam" -> "ml"
            "chinese" -> "zh"
            "japanese" -> "ja"
            "korean" -> "ko"
            "swahili" -> "sw"
            "bosnian" -> "bs"
            "albanian" -> "sq"
            "somali" -> "so"
            "italian" -> "it"
            "portuguese" -> "pt"
            "dutch" -> "nl"
            "uzbek" -> "uz"
            else -> languageName?.take(2)?.lowercase() ?: "en"
        }
    }

    private fun buildFallbackAudioUrl(baseUrl: String?, reciterId: Int, surahId: Int, ayahNumber: Int): String {
        val resolvedBase = baseUrl?.takeIf { it.isNotBlank() } ?: when (reciterId) {
            1 -> "https://everyayah.com/data/Husary_128kbps/"
            9 -> "https://everyayah.com/data/Minshawy_Murattal_128kbps/"
            else -> "https://everyayah.com/data/Alafasy_128kbps/"
        }
        val normalizedBase = if (resolvedBase.endsWith("/")) resolvedBase else "$resolvedBase/"
        val s = surahId.toString().padStart(3, '0')
        val a = ayahNumber.toString().padStart(3, '0')
        return "${normalizedBase}${s}${a}.mp3"
    }

    private fun stripEndSpan(raw: String): String {
        return END_SPAN_REGEX.replace(raw, "").trim()
    }

    private fun cleanHtml(raw: String): String {
        return CLEAN_HTML_SUP_REGEX.replace(raw, "")
            .let { CLEAN_HTML_TAG_REGEX.replace(it, "") }
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rsquo;", "’")
            .replace("&lsquo;", "‘")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .trim()
    }

    companion object {
        private val END_SPAN_REGEX = Regex("<span\\s+class=[\"']?end[\"']?[^>]*>.*?</span>", RegexOption.IGNORE_CASE)
        private val VERSE_END_MARKER_REGEX = Regex("^[\\u0660-\\u06690-9\\uFD3E\\uFD3F]+$")
        private val CLEAN_HTML_SUP_REGEX = Regex("<sup[^>]*>.*?</sup>", RegexOption.DOT_MATCHES_ALL)
        private val CLEAN_HTML_TAG_REGEX = Regex("<[^>]+>")
    }
}
