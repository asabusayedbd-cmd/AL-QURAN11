package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "surahs")
data class SurahEntity(
    @PrimaryKey val id: Int,
    val nameArabic: String,
    val nameTransliteration: String,
    val localizedNamesJson: String,
    val revelationPlace: String,
    val ayahCount: Int,
    val bismillahPre: Boolean,
    val startPage: Int,
    val startJuz: Int,
    val isDownloaded: Boolean = false
)

@Entity(
    tableName = "ayahs",
    foreignKeys = [
        ForeignKey(
            entity = SurahEntity::class,
            parentColumns = ["id"],
            childColumns = ["surahId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("surahId"),
        Index("verseKey", unique = true),
        Index("juzNumber"),
        Index("pageNumber")
    ]
)
data class AyahEntity(
    @PrimaryKey val id: Int,
    val surahId: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val textUthmani: String,
    val textIndopak: String,
    val textTajweedHtml: String,
    val normalizedArabic: String,
    val juzNumber: Int,
    val hizbNumber: Int,
    val pageNumber: Int,
    val sajdahType: String? = null
)

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = AyahEntity::class,
            parentColumns = ["id"],
            childColumns = ["ayahId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ayahId"), Index("rootArabic")]
)
data class WordEntity(
    @PrimaryKey val id: Long,
    val ayahId: Int,
    val position: Int,
    val textUthmani: String,
    val textIndopak: String,
    val transliteration: String,
    val rootArabic: String,
    val lemmaArabic: String,
    val grammarTagJson: String,
    val audioUrl: String? = null
)

@Entity(
    tableName = "word_translations",
    primaryKeys = ["wordId", "languageCode"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("languageCode")]
)
data class WordTranslationEntity(
    val wordId: Long,
    val languageCode: String,
    val meaningText: String
)

@Entity(tableName = "translation_sources")
data class TranslationSourceEntity(
    @PrimaryKey val id: Int,
    val languageCode: String,
    val languageName: String,
    val translatorName: String,
    val isDownloaded: Boolean,
    val isEnabled: Boolean,
    val fileSizeKb: Int
)

@Entity(
    tableName = "ayah_translations",
    primaryKeys = ["sourceId", "ayahId"],
    foreignKeys = [
        ForeignKey(
            entity = AyahEntity::class,
            parentColumns = ["id"],
            childColumns = ["ayahId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ayahId"), Index("languageCode")]
)
data class AyahTranslationEntity(
    val sourceId: Int,
    val ayahId: Int,
    val languageCode: String,
    val translatorName: String,
    val translatedText: String,
    val footnotesJson: String = "[]"
)

@Entity(tableName = "tafsir_sources")
data class TafsirSourceEntity(
    @PrimaryKey val id: Int,
    val languageCode: String,
    val languageName: String,
    val tafsirName: String,
    val isDownloaded: Boolean,
    val isEnabled: Boolean,
    val fileSizeKb: Int
)

@Entity(
    tableName = "ayah_tafsirs",
    primaryKeys = ["sourceId", "ayahId"],
    foreignKeys = [
        ForeignKey(
            entity = AyahEntity::class,
            parentColumns = ["id"],
            childColumns = ["ayahId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ayahId"), Index("languageCode")]
)
data class AyahTafsirEntity(
    val sourceId: Int,
    val ayahId: Int,
    val languageCode: String,
    val tafsirName: String,
    val tafsirHtmlOrMarkdown: String
)

@Entity(tableName = "audio_reciters")
data class AudioReciterEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val localizedName: String,
    val style: String,
    val baseUrl: String,
    val isDownloaded: Boolean
)

@Entity(
    tableName = "audio_segments",
    primaryKeys = ["reciterId", "ayahId"],
    indices = [Index("ayahId")]
)
data class AudioSegmentEntity(
    val reciterId: Int,
    val ayahId: Int,
    val audioUrl: String,
    val localFilePath: String? = null,
    val wordTimestampSegmentsJson: String
)

@Entity(tableName = "user_collections")
data class UserCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val createdAtEpochMs: Long
)

@Entity(tableName = "user_bookmarks", indices = [Index("verseKey"), Index("collectionId")])
data class UserBookmarkEntity(
    @PrimaryKey val id: String,
    val verseKey: String,
    val surahId: Int,
    val ayahNumber: Int,
    val collectionId: String,
    val label: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "user_notes", indices = [Index("verseKey")])
data class UserNoteEntity(
    @PrimaryKey val id: String,
    val verseKey: String,
    val surahId: Int,
    val ayahNumber: Int,
    val noteText: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val id: String = "main",
    val lastSurahId: Int = 1,
    val lastAyahNumber: Int = 1,
    val lastVerseKey: String = "1:1",
    val lastPageNumber: Int = 1,
    val lastJuzNumber: Int = 1,
    val dailyAyahGoal: Int = 20,
    val khatamTargetDays: Int = 30,
    val khatamCompletedAyahs: Int = 0,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "habit_streaks")
data class HabitStreakEntity(
    @PrimaryKey val dateKey: String,
    val ayahsReadCount: Int = 0,
    val minutesSpent: Int = 0,
    val tasbeehCount: Int = 0,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
