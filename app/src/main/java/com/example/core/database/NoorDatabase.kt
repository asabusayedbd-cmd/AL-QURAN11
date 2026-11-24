package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        HabitStreakEntity::class,
        DuaEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NoorDatabase : RoomDatabase() {
    abstract fun quranDao(): QuranDao
    abstract fun userLibraryDao(): UserLibraryDao
    abstract fun duaDao(): DuaDao

    companion object {
        @Volatile
        private var INSTANCE: NoorDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `duas` (
                        `id` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `arabicText` TEXT NOT NULL,
                        `transliteration` TEXT NOT NULL,
                        `translation` TEXT NOT NULL,
                        `reference` TEXT NOT NULL,
                        `virtueOrContext` TEXT NOT NULL,
                        `audioUrl` TEXT NOT NULL,
                        `isBookmarked` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_duas_category` ON `duas` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_duas_isBookmarked` ON `duas` (`isBookmarked`)")
            }
        }

        fun getInstance(context: Context): NoorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoorDatabase::class.java,
                    "noor_al_quran.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
