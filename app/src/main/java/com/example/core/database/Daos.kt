package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuranDao {
    @Query("SELECT * FROM surahs ORDER BY id ASC")
    fun observeAllSurahs(): Flow<List<SurahEntity>>

    @Query("SELECT * FROM surahs WHERE id = :surahId")
    suspend fun getSurah(surahId: Int): SurahEntity?

    @Query("SELECT * FROM surahs WHERE id = :surahId")
    fun observeSurah(surahId: Int): Flow<SurahEntity?>

    @Query("SELECT COUNT(*) FROM surahs")
    suspend fun countSurahs(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurahs(surahs: List<SurahEntity>)

    @Query("UPDATE surahs SET isDownloaded = :downloaded WHERE id = :surahId")
    suspend fun markSurahDownloaded(surahId: Int, downloaded: Boolean)

    @Query("SELECT id FROM surahs WHERE isDownloaded = 1 ORDER BY id ASC")
    suspend fun getDownloadedSurahIds(): List<Int>

    @Query("SELECT * FROM ayahs WHERE surahId = :surahId ORDER BY ayahNumber ASC")
    fun observeAyahsBySurah(surahId: Int): Flow<List<AyahEntity>>

    @Query("SELECT * FROM ayahs WHERE surahId = :surahId ORDER BY ayahNumber ASC")
    suspend fun getAyahsBySurah(surahId: Int): List<AyahEntity>

    @Query("SELECT * FROM ayahs WHERE juzNumber = :juzNumber ORDER BY surahId ASC, ayahNumber ASC")
    suspend fun getAyahsByJuz(juzNumber: Int): List<AyahEntity>

    @Query("SELECT * FROM ayahs WHERE pageNumber = :pageNumber ORDER BY surahId ASC, ayahNumber ASC")
    suspend fun getAyahsByPage(pageNumber: Int): List<AyahEntity>

    @Query("SELECT * FROM ayahs WHERE pageNumber = :pageNumber ORDER BY surahId ASC, ayahNumber ASC LIMIT 1")
    suspend fun getFirstAyahOnPage(pageNumber: Int): AyahEntity?

    @Query("SELECT * FROM ayahs WHERE verseKey = :verseKey LIMIT 1")
    suspend fun getAyahByKey(verseKey: String): AyahEntity?

    @Query("SELECT * FROM ayahs WHERE verseKey = :verseKey LIMIT 1")
    fun observeAyahByKey(verseKey: String): Flow<AyahEntity?>

    @Query("SELECT * FROM ayahs WHERE verseKey IN (:verseKeys)")
    suspend fun getAyahsByKeys(verseKeys: List<String>): List<AyahEntity>

    @Query("SELECT * FROM ayahs WHERE id IN (:ayahIds) ORDER BY surahId ASC, ayahNumber ASC")
    suspend fun getAyahsByIds(ayahIds: List<Int>): List<AyahEntity>

    @Query("SELECT * FROM ayahs WHERE normalizedArabic LIKE '%' || :normalizedQuery || '%' ORDER BY surahId ASC, ayahNumber ASC LIMIT 50")
    suspend fun searchAyahsByNormalizedArabic(normalizedQuery: String): List<AyahEntity>

    @Query("SELECT * FROM ayahs WHERE textUthmani LIKE '%' || :rawQuery || '%' ORDER BY surahId ASC, ayahNumber ASC LIMIT 50")
    suspend fun searchAyahsByUthmani(rawQuery: String): List<AyahEntity>

    @Query("SELECT DISTINCT ayahId FROM ayah_translations WHERE translatedText LIKE '%' || :rawQuery || '%' ORDER BY ayahId ASC LIMIT 60")
    suspend fun searchAyahIdsByTranslation(rawQuery: String): List<Int>

    @Query(
        """
        SELECT DISTINCT w.ayahId FROM words w
        LEFT JOIN word_translations wt ON wt.wordId = w.id
        WHERE w.transliteration LIKE '%' || :rawQuery || '%'
           OR w.normalizedText LIKE '%' || :normalizedQuery || '%'
           OR wt.meaningText LIKE '%' || :rawQuery || '%'
        ORDER BY w.ayahId ASC
        LIMIT 50
        """
    )
    suspend fun searchAyahIdsByWord(rawQuery: String, normalizedQuery: String): List<Int>

    @Query(
        """
        SELECT * FROM ayahs
        WHERE verseKey IN ('2:255', '2:282', '2:286', '3:190', '4:176', '24:35', '33:35', '59:22', '59:23', '59:24')
        ORDER BY surahId ASC, ayahNumber ASC
        """
    )
    suspend fun getLongAyahsInQuran(): List<AyahEntity>

    @Query(
        """
        SELECT DISTINCT a.* FROM ayahs a
        LEFT JOIN ayah_translations t ON t.ayahId = a.id
        LEFT JOIN words w ON w.ayahId = a.id
        LEFT JOIN word_translations wt ON wt.wordId = w.id
        WHERE a.verseKey = :exactQuery
           OR a.normalizedArabic LIKE '%' || :normalizedQuery || '%'
           OR a.textUthmani LIKE '%' || :rawQuery || '%'
           OR t.translatedText LIKE '%' || :rawQuery || '%'
           OR w.transliteration LIKE '%' || :rawQuery || '%'
           OR wt.meaningText LIKE '%' || :rawQuery || '%'
        ORDER BY a.surahId ASC, a.ayahNumber ASC
        LIMIT 60
        """
    )
    suspend fun searchAyahs(exactQuery: String, normalizedQuery: String, rawQuery: String): List<AyahEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAyahs(ayahs: List<AyahEntity>)

    @Query("SELECT * FROM words WHERE ayahId IN (:ayahIds) ORDER BY ayahId ASC, position ASC")
    fun observeWordsForAyahs(ayahIds: List<Int>): Flow<List<WordEntity>>

    @Query(
        """
        SELECT w.* FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE a.surahId = :surahId
        ORDER BY a.ayahNumber ASC, w.position ASC
        """
    )
    fun observeWordsBySurah(surahId: Int): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE ayahId IN (:ayahIds) ORDER BY ayahId ASC, position ASC")
    suspend fun getWordsForAyahs(ayahIds: List<Int>): List<WordEntity>

    @Query("SELECT * FROM words WHERE ayahId = :ayahId ORDER BY position ASC")
    suspend fun getWordsForAyah(ayahId: Int): List<WordEntity>

    @Query(
        """
        SELECT w.* FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE a.surahId = :surahId
        ORDER BY a.ayahNumber ASC, w.position ASC
        """
    )
    suspend fun getWordsBySurah(surahId: Int): List<WordEntity>

    @Query("UPDATE ayahs SET textIndopak = :textIndopak WHERE id = :ayahId")
    suspend fun updateAyahIndoPakText(ayahId: Int, textIndopak: String)

    @Query("UPDATE words SET textIndopak = :textIndopak WHERE id = :wordId")
    suspend fun updateWordIndoPakText(wordId: Long, textIndopak: String)

    @Query("SELECT COUNT(*) FROM words WHERE rootArabic = :rootArabic AND rootArabic != ''")
    suspend fun countWordsByRoot(rootArabic: String): Int

    @Query("SELECT COUNT(*) FROM words WHERE textUthmani = :wordArabic")
    suspend fun countWordsByArabic(wordArabic: String): Int

    @Query("SELECT COUNT(*) FROM words WHERE normalizedText = :normalizedText AND normalizedText != ''")
    suspend fun countWordsByNormalized(normalizedText: String): Int

    @Query("SELECT * FROM words WHERE rootArabic = :rootArabic AND rootArabic != '' ORDER BY ayahId ASC, position ASC LIMIT 30")
    suspend fun getWordsByRoot(rootArabic: String): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words WHERE normalizedText = ''")
    suspend fun countWordsWithoutNormalized(): Int

    @Query("SELECT * FROM words WHERE normalizedText = '' LIMIT :limit")
    suspend fun getWordsWithoutNormalizedBatch(limit: Int): List<WordEntity>

    @Query(
        """
        SELECT a.verseKey AS verseKey, a.surahId AS surahId, a.ayahNumber AS ayahNumber,
               a.textUthmani AS ayahArabic, w.textUthmani AS wordArabic, w.transliteration AS transliteration
        FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE w.rootArabic = :rootArabic AND w.rootArabic != ''
        ORDER BY a.surahId ASC, a.ayahNumber ASC, w.position ASC
        LIMIT 25
        """
    )
    suspend fun getRootOccurrences(rootArabic: String): List<WordOccurrenceProjection>

    @Query(
        """
        SELECT a.verseKey AS verseKey, a.surahId AS surahId, a.ayahNumber AS ayahNumber,
               a.textUthmani AS ayahArabic, w.textUthmani AS wordArabic, w.transliteration AS transliteration
        FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE w.textUthmani = :wordArabic
        ORDER BY a.surahId ASC, a.ayahNumber ASC, w.position ASC
        LIMIT 25
        """
    )
    suspend fun getWordOccurrencesByArabic(wordArabic: String): List<WordOccurrenceProjection>

    @Query(
        """
        SELECT a.verseKey AS verseKey, a.surahId AS surahId, a.ayahNumber AS ayahNumber,
               a.textUthmani AS ayahArabic, w.textUthmani AS wordArabic, w.transliteration AS transliteration
        FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE w.normalizedText = :normalizedText AND w.normalizedText != ''
        ORDER BY a.surahId ASC, a.ayahNumber ASC, w.position ASC
        LIMIT 25
        """
    )
    suspend fun getWordOccurrencesByNormalized(normalizedText: String): List<WordOccurrenceProjection>

    @Query(
        """
        SELECT w.textUthmani AS textUthmani, COUNT(*) AS occurrenceCount,
               MIN(a.surahId) AS sampleSurahId, MIN(a.ayahNumber) AS sampleAyahNumber,
               MIN(a.verseKey) AS sampleVerseKey
        FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE w.rootArabic = :rootArabic AND w.rootArabic != ''
        GROUP BY w.textUthmani
        ORDER BY occurrenceCount DESC
        LIMIT 12
        """
    )
    suspend fun getDistinctFormsByRoot(rootArabic: String): List<WordFormStatProjection>

    @Query(
        """
        SELECT w.textUthmani AS textUthmani, COUNT(*) AS occurrenceCount,
               MIN(a.surahId) AS sampleSurahId, MIN(a.ayahNumber) AS sampleAyahNumber,
               MIN(a.verseKey) AS sampleVerseKey
        FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE (w.normalizedText = :normalizedText OR w.normalizedText LIKE '%' || :normalizedStem || '%')
          AND w.normalizedText != ''
        GROUP BY w.textUthmani
        ORDER BY occurrenceCount DESC
        LIMIT 12
        """
    )
    suspend fun getDistinctFormsByNormalizedStem(normalizedText: String, normalizedStem: String): List<WordFormStatProjection>

    @Query(
        """
        SELECT w.id AS wordId, w.ayahId AS ayahId, a.surahId AS surahId, a.ayahNumber AS ayahNumber,
               a.verseKey AS verseKey, w.position AS position, w.textUthmani AS textUthmani,
               w.textIndopak AS textIndopak, w.transliteration AS transliteration,
               w.rootArabic AS rootArabic, w.lemmaArabic AS lemmaArabic, w.grammarTagJson AS grammarTagJson,
               COALESCE(MAX(CASE WHEN wt.languageCode = 'en' THEN wt.meaningText END), '') AS meaningEn,
               COALESCE(MAX(CASE WHEN wt.languageCode = 'bn' THEN wt.meaningText END), '') AS meaningBn,
               w.audioUrl AS audioUrl
        FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        LEFT JOIN word_translations wt ON wt.wordId = w.id
        WHERE a.surahId = :surahId
        GROUP BY w.id
        ORDER BY a.ayahNumber ASC, w.position ASC
        """
    )
    suspend fun getFlashcardWordsForSurah(surahId: Int): List<FlashcardWordProjection>

    @Query(
        """
        SELECT w.id AS wordId, w.ayahId AS ayahId, a.surahId AS surahId, a.ayahNumber AS ayahNumber,
               a.verseKey AS verseKey, w.position AS position, w.textUthmani AS textUthmani,
               w.textIndopak AS textIndopak, w.transliteration AS transliteration,
               w.rootArabic AS rootArabic, w.lemmaArabic AS lemmaArabic, w.grammarTagJson AS grammarTagJson,
               COALESCE(MAX(CASE WHEN wt.languageCode = 'en' THEN wt.meaningText END), '') AS meaningEn,
               COALESCE(MAX(CASE WHEN wt.languageCode = 'bn' THEN wt.meaningText END), '') AS meaningBn,
               w.audioUrl AS audioUrl
        FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        LEFT JOIN word_translations wt ON wt.wordId = w.id
        WHERE a.verseKey = :verseKey
        GROUP BY w.id
        ORDER BY w.position ASC
        """
    )
    suspend fun getFlashcardWordsForAyah(verseKey: String): List<FlashcardWordProjection>

    @Query(
        """
        SELECT w.id AS wordId, w.ayahId AS ayahId, a.surahId AS surahId, a.ayahNumber AS ayahNumber,
               a.verseKey AS verseKey, w.position AS position, w.textUthmani AS textUthmani,
               w.textIndopak AS textIndopak, w.transliteration AS transliteration,
               w.rootArabic AS rootArabic, w.lemmaArabic AS lemmaArabic, w.grammarTagJson AS grammarTagJson,
               COALESCE(MAX(CASE WHEN wt.languageCode = 'en' THEN wt.meaningText END), '') AS meaningEn,
               COALESCE(MAX(CASE WHEN wt.languageCode = 'bn' THEN wt.meaningText END), '') AS meaningBn,
               w.audioUrl AS audioUrl
        FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        LEFT JOIN word_translations wt ON wt.wordId = w.id
        WHERE a.verseKey IN (:verseKeys)
        GROUP BY w.id
        ORDER BY a.surahId ASC, a.ayahNumber ASC, w.position ASC
        """
    )
    suspend fun getFlashcardWordsForVerseKeys(verseKeys: List<String>): List<FlashcardWordProjection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Query("DELETE FROM words WHERE ayahId IN (:ayahIds) AND id NOT IN (:validWordIds)")
    suspend fun deleteObsoleteWords(ayahIds: List<Int>, validWordIds: List<Long>)

    @Query("SELECT * FROM word_translations WHERE wordId IN (:wordIds)")
    fun observeWordTranslations(wordIds: List<Long>): Flow<List<WordTranslationEntity>>

    @Query(
        """
        SELECT wt.* FROM word_translations wt
        INNER JOIN words w ON w.id = wt.wordId
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE a.surahId = :surahId
        """
    )
    fun observeWordTranslationsBySurah(surahId: Int): Flow<List<WordTranslationEntity>>

    @Query("SELECT * FROM word_translations WHERE wordId IN (:wordIds)")
    suspend fun getWordTranslationsForWords(wordIds: List<Long>): List<WordTranslationEntity>

    @Query("SELECT * FROM word_translations WHERE wordId = :wordId")
    suspend fun getWordTranslations(wordId: Long): List<WordTranslationEntity>

    @Query(
        """
        SELECT COUNT(*) FROM word_translations wt
        INNER JOIN words w ON w.id = wt.wordId
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE a.surahId = :surahId AND wt.languageCode = :languageCode
        """
    )
    suspend fun countWordTranslationsForSurah(surahId: Int, languageCode: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordTranslations(translations: List<WordTranslationEntity>)

    @Query("SELECT COUNT(*) FROM translation_sources")
    suspend fun countTranslationSources(): Int

    @Query("SELECT * FROM translation_sources")
    suspend fun getAllTranslationSources(): List<TranslationSourceEntity>

    @Query("SELECT * FROM translation_sources ORDER BY id ASC")
    fun observeTranslationSources(): Flow<List<TranslationSourceEntity>>

    @Query("SELECT * FROM translation_sources WHERE isEnabled = 1 ORDER BY id ASC")
    fun observeEnabledTranslationSources(): Flow<List<TranslationSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslationSources(sources: List<TranslationSourceEntity>)

    @Query("UPDATE translation_sources SET isEnabled = :enabled WHERE id = :sourceId")
    suspend fun setTranslationEnabled(sourceId: Int, enabled: Boolean)

    @Query("UPDATE translation_sources SET isDownloaded = :downloaded WHERE id = :sourceId")
    suspend fun setTranslationDownloaded(sourceId: Int, downloaded: Boolean)

    @Query("SELECT * FROM ayah_translations WHERE ayahId IN (:ayahIds)")
    fun observeAyahTranslations(ayahIds: List<Int>): Flow<List<AyahTranslationEntity>>

    @Query(
        """
        SELECT t.* FROM ayah_translations t
        INNER JOIN ayahs a ON a.id = t.ayahId
        WHERE a.surahId = :surahId
        """
    )
    fun observeAyahTranslationsBySurah(surahId: Int): Flow<List<AyahTranslationEntity>>

    @Query("SELECT * FROM ayah_translations WHERE ayahId IN (:ayahIds)")
    suspend fun getTranslationsForAyahs(ayahIds: List<Int>): List<AyahTranslationEntity>

    @Query("SELECT * FROM ayah_translations WHERE ayahId = :ayahId")
    suspend fun getTranslationsForAyah(ayahId: Int): List<AyahTranslationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAyahTranslations(translations: List<AyahTranslationEntity>)

    @Query("SELECT COUNT(*) FROM tafsir_sources")
    suspend fun countTafsirSources(): Int

    @Query("SELECT * FROM tafsir_sources")
    suspend fun getAllTafsirSources(): List<TafsirSourceEntity>

    @Query("SELECT * FROM tafsir_sources ORDER BY id ASC")
    fun observeTafsirSources(): Flow<List<TafsirSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTafsirSources(sources: List<TafsirSourceEntity>)

    @Query("UPDATE tafsir_sources SET isDownloaded = :downloaded, isEnabled = :enabled WHERE id = :sourceId")
    suspend fun updateTafsirSourceStatus(sourceId: Int, downloaded: Boolean, enabled: Boolean)

    @Query("SELECT * FROM ayah_tafsirs WHERE ayahId = :ayahId")
    fun observeTafsirsForAyah(ayahId: Int): Flow<List<AyahTafsirEntity>>

    @Query(
        """
        SELECT t.* FROM ayah_tafsirs t
        INNER JOIN ayahs a ON a.id = t.ayahId
        WHERE a.surahId = :surahId AND t.sourceId = :sourceId
        """
    )
    fun observeTafsirsForSurahBySource(surahId: Int, sourceId: Int): Flow<List<AyahTafsirEntity>>

    @Query("SELECT * FROM ayah_tafsirs WHERE ayahId = :ayahId")
    suspend fun getTafsirsForAyah(ayahId: Int): List<AyahTafsirEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAyahTafsirs(tafsirs: List<AyahTafsirEntity>)

    @Query("SELECT COUNT(*) FROM audio_reciters")
    suspend fun countReciters(): Int

    @Query("SELECT * FROM audio_reciters WHERE id = :reciterId LIMIT 1")
    suspend fun getReciter(reciterId: Int): AudioReciterEntity?

    @Query("SELECT * FROM audio_reciters ORDER BY id ASC")
    fun observeReciters(): Flow<List<AudioReciterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReciters(reciters: List<AudioReciterEntity>)

    @Query("DELETE FROM audio_reciters WHERE id = :reciterId")
    suspend fun deleteReciterById(reciterId: Int)

    @Query("SELECT * FROM audio_segments WHERE reciterId = :reciterId AND ayahId IN (:ayahIds)")
    fun observeAudioSegments(reciterId: Int, ayahIds: List<Int>): Flow<List<AudioSegmentEntity>>

    @Query("SELECT * FROM audio_segments WHERE reciterId = :reciterId AND ayahId = :ayahId LIMIT 1")
    suspend fun getAudioSegment(reciterId: Int, ayahId: Int): AudioSegmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioSegments(segments: List<AudioSegmentEntity>)

    @Query("DELETE FROM ayahs WHERE surahId = :surahId")
    suspend fun deleteAyahsBySurah(surahId: Int)

    @Query("DELETE FROM words WHERE ayahId IN (SELECT id FROM ayahs WHERE surahId = :surahId)")
    suspend fun deleteWordsBySurah(surahId: Int)

    @Query("DELETE FROM word_translations WHERE wordId IN (SELECT w.id FROM words w INNER JOIN ayahs a ON a.id = w.ayahId WHERE a.surahId = :surahId)")
    suspend fun deleteWordTranslationsBySurah(surahId: Int)

    @Query("DELETE FROM ayah_translations WHERE ayahId IN (SELECT id FROM ayahs WHERE surahId = :surahId)")
    suspend fun deleteAyahTranslationsBySurah(surahId: Int)

    @Query("DELETE FROM ayah_tafsirs WHERE ayahId IN (SELECT id FROM ayahs WHERE surahId = :surahId)")
    suspend fun deleteAyahTafsirsBySurah(surahId: Int)

    @Query("DELETE FROM audio_segments WHERE ayahId IN (SELECT id FROM ayahs WHERE surahId = :surahId)")
    suspend fun deleteAudioSegmentsBySurah(surahId: Int)

    @Query("DELETE FROM ayah_translations WHERE sourceId = :sourceId")
    suspend fun deleteTranslationsBySource(sourceId: Int)

    @Query("DELETE FROM ayah_tafsirs WHERE sourceId = :sourceId")
    suspend fun deleteTafsirsBySource(sourceId: Int)

    @Query("SELECT COUNT(*) FROM ayahs WHERE surahId = :surahId")
    suspend fun countAyahsInSurah(surahId: Int): Int

    @Query("SELECT surahId, COUNT(*) AS cnt FROM ayahs GROUP BY surahId")
    suspend fun getAyahCountsBySurah(): List<SurahAyahCountProjection>

    @Query("SELECT COUNT(*) FROM ayahs")
    suspend fun countAllAyahs(): Int

    @Query("DELETE FROM ayahs WHERE id > 6236 OR (id IN (2255, 2201, 3008) AND surahId IN (2, 3))")
    suspend fun deleteLegacyStarterAyahs()

    @Query("SELECT COUNT(*) FROM ayah_translations WHERE sourceId = :sourceId")
    suspend fun countTranslationsForSource(sourceId: Int): Int

    @Query(
        """
        SELECT COUNT(*) FROM ayah_translations t
        INNER JOIN ayahs a ON a.id = t.ayahId
        WHERE a.surahId = :surahId AND t.sourceId = :sourceId
        """
    )
    suspend fun countTranslationsForSurahBySource(surahId: Int, sourceId: Int): Int

    @Query(
        """
        SELECT COUNT(*) FROM words w
        INNER JOIN ayahs a ON a.id = w.ayahId
        WHERE a.surahId = :surahId
        """
    )
    suspend fun countWordsInSurah(surahId: Int): Int

    @Query("SELECT COUNT(*) FROM ayah_tafsirs WHERE sourceId = :sourceId")
    suspend fun countTafsirsForSource(sourceId: Int): Int
}

data class SurahAyahCountProjection(
    val surahId: Int,
    val cnt: Int
)

data class WordOccurrenceProjection(
    val verseKey: String,
    val surahId: Int,
    val ayahNumber: Int,
    val ayahArabic: String,
    val wordArabic: String,
    val transliteration: String
)

data class WordFormStatProjection(
    val textUthmani: String,
    val occurrenceCount: Int,
    val sampleSurahId: Int,
    val sampleAyahNumber: Int,
    val sampleVerseKey: String
)

data class FlashcardWordProjection(
    val wordId: Long,
    val ayahId: Int,
    val surahId: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val position: Int,
    val textUthmani: String,
    val textIndopak: String,
    val transliteration: String,
    val rootArabic: String,
    val lemmaArabic: String,
    val grammarTagJson: String,
    val meaningEn: String,
    val meaningBn: String,
    val audioUrl: String?
)

@Dao
interface UserLibraryDao {
    @Query("SELECT * FROM user_collections ORDER BY createdAtEpochMs ASC")
    fun observeCollections(): Flow<List<UserCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: UserCollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<UserCollectionEntity>)

    @Query("DELETE FROM user_collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: String)

    @Query("SELECT * FROM user_bookmarks ORDER BY updatedAtEpochMs DESC")
    fun observeBookmarks(): Flow<List<UserBookmarkEntity>>

    @Query("SELECT verseKey FROM user_bookmarks WHERE surahId = :surahId")
    fun observeBookmarkedKeysForSurah(surahId: Int): Flow<List<String>>

    @Query("SELECT * FROM user_bookmarks WHERE verseKey = :verseKey LIMIT 1")
    suspend fun getBookmarkByVerseKey(verseKey: String): UserBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: UserBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<UserBookmarkEntity>)

    @Query("DELETE FROM user_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: String)

    @Query("DELETE FROM user_bookmarks WHERE verseKey = :verseKey")
    suspend fun deleteBookmarkByVerseKey(verseKey: String)

    @Query("SELECT * FROM user_notes ORDER BY updatedAtEpochMs DESC")
    fun observeNotes(): Flow<List<UserNoteEntity>>

    @Query("SELECT verseKey FROM user_notes WHERE surahId = :surahId")
    fun observeNoteKeysForSurah(surahId: Int): Flow<List<String>>

    @Query("SELECT * FROM user_notes WHERE verseKey = :verseKey LIMIT 1")
    fun observeNoteForVerse(verseKey: String): Flow<UserNoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: UserNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<UserNoteEntity>)

    @Query("DELETE FROM user_notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: String)

    @Query("SELECT * FROM reading_progress WHERE id = 'main' LIMIT 1")
    fun observeReadingProgress(): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE id = 'main' LIMIT 1")
    suspend fun getReadingProgress(): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadingProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM habit_streaks ORDER BY dateKey DESC LIMIT 30")
    fun observeRecentStreaks(): Flow<List<HabitStreakEntity>>

    @Query("SELECT * FROM habit_streaks WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getStreakByDate(dateKey: String): HabitStreakEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreak(streak: HabitStreakEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreaks(streaks: List<HabitStreakEntity>)
}

@Dao
interface DuaDao {
    @Query("SELECT * FROM duas ORDER BY id ASC")
    fun observeAllDuas(): Flow<List<DuaEntity>>

    @Query("SELECT * FROM duas WHERE category = :category ORDER BY id ASC")
    fun observeDuasByCategory(category: String): Flow<List<DuaEntity>>

    @Query("SELECT * FROM duas WHERE id = :duaId LIMIT 1")
    suspend fun getDua(duaId: Int): DuaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDuas(duas: List<DuaEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDuasIfAbsent(duas: List<DuaEntity>)

    @Query("UPDATE duas SET isBookmarked = :isBookmarked WHERE id = :duaId")
    suspend fun setDuaBookmarked(duaId: Int, isBookmarked: Boolean)

    @Query("SELECT id FROM duas WHERE isBookmarked = 1")
    suspend fun getBookmarkedDuaIds(): List<Int>

    @Query(
        """
        SELECT * FROM duas
        WHERE title LIKE '%' || :query || '%'
           OR translation LIKE '%' || :query || '%'
           OR transliteration LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
           OR reference LIKE '%' || :query || '%'
           OR arabicText LIKE '%' || :query || '%'
        ORDER BY id ASC
        LIMIT 30
        """
    )
    suspend fun searchDuas(query: String): List<DuaEntity>

    @Query("SELECT COUNT(*) FROM duas")
    suspend fun countDuas(): Int
}

