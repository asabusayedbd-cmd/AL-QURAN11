package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SurahEntity::class,
        AyahEntity::class,
        WordEntity::class,
        WordTranslationEntity::class,
        TranslationSourceEntity::class,
        AyahTranslationEntity::class,
        TafsirSourceEntity::class,
        AyahTafsirEntity::class,
        AudioReciterEntity::class,
        AudioSegmentEntity::class,
        UserCollectionEntity::class,
        UserBookmarkEntity::class,
        UserNoteEntity::class,
        ReadingProgressEntity::class,
        HabitStreakEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NoorDatabase : RoomDatabase() {
    abstract fun quranDao(): QuranDao
    abstract fun userLibraryDao(): UserLibraryDao

    companion object {
        @Volatile
        private var INSTANCE: NoorDatabase? = null

        fun getInstance(context: Context): NoorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoorDatabase::class.java,
                    "noor_al_quran.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
