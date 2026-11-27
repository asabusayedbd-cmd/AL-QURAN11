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
    version = 4,
    exportSchema = false
)
abstract class NoorDatabase : RoomDatabase() {
    abstract fun quranDao(): QuranDao
    abstract fun userLibraryDao(): UserLibraryDao
    abstract fun duaDao(): DuaDao

    companion object {
        @Volatile
        private var INSTANCE: NoorDatabase? = null

        const val PREPOPULATED_ASSET_DB = "noor_al_quran_prepop.db"

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `words` ADD COLUMN `normalizedText` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_textUthmani` ON `words` (`textUthmani`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_normalizedText` ON `words` (`normalizedText`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ayahs_normalizedArabic` ON `ayahs` (`normalizedArabic`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ayahs_surahId_ayahNumber` ON `ayahs` (`surahId`, `ayahNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_ayahId_position` ON `words` (`ayahId`, `position`)")
            }
        }

        fun getInstance(context: Context): NoorDatabase {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val hasPrepopAsset = runCatching {
                    appCtx.assets.open(PREPOPULATED_ASSET_DB).use { true }
                }.getOrDefault(false)
                val builder = Room.databaseBuilder(
                    appCtx,
                    NoorDatabase::class.java,
                    "noor_al_quran.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                if (hasPrepopAsset) {
                    builder.createFromAsset(PREPOPULATED_ASSET_DB)
                }
                INSTANCE ?: builder.build().also { INSTANCE = it }
            }
        }
    }
}
