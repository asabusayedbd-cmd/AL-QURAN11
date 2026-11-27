package com.example.core.di

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.audio.QuranAudioController
import com.example.core.database.NoorDatabase
import com.example.core.database.QuranDatabaseSeeder
import com.example.core.localization.LineTranslator
import com.example.core.localization.PreferencesRepository
import com.example.core.network.QuranComApiService
import com.example.core.network.QuranSyncRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val warmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val warmedUp = AtomicBoolean(false)

    val database: NoorDatabase by lazy { NoorDatabase.getInstance(appContext) }
    val seeder: QuranDatabaseSeeder by lazy { QuranDatabaseSeeder(appContext, database) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(appContext) }
    val pendingPlaybackJump = MutableStateFlow<Pair<Int, Int>?>(null)

    fun requestPlaybackJump(surahId: Int, ayahNumber: Int) {
        if (surahId in 1..114) {
            pendingPlaybackJump.value = surahId to ayahNumber.coerceAtLeast(1)
        }
    }

    fun clearPlaybackJump() {
        pendingPlaybackJump.value = null
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    val lineTranslator: LineTranslator by lazy {
        LineTranslator(appContext, okHttpClient)
    }

    val apiService: QuranComApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.quran.com/api/v4/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(QuranComApiService::class.java)
    }

    val quranSyncRepository: QuranSyncRepository by lazy {
        QuranSyncRepository(database, apiService, appContext)
    }

    val audioController: QuranAudioController by lazy {
        QuranAudioController(appContext, database, okHttpClient, preferencesRepository)
    }

    private var languageSyncJob: kotlinx.coroutines.Job? = null

    fun warmUpAsync() {
        if (!warmedUp.compareAndSet(false, true)) return
        warmScope.launch {
            runCatching { preferencesRepository.preferencesFlow.first() }
        }
        warmScope.launch {
            seeder.seedIfNeeded(deferBulkDelayMs = 1800L, interSurahYieldMs = 45L)
        }
        warmScope.launch {
            kotlinx.coroutines.delay(2200L)
            val fontResIds = intArrayOf(
                R.font.plus_jakarta_sans,
                R.font.amiri,
                R.font.noto_sans_bengali,
                R.font.noto_naskh_arabic,
                R.font.hind_siliguri,
                R.font.scheherazade_new,
                R.font.noto_nastaliq_urdu,
                R.font.gulzar,
                R.font.alkalami,
                R.font.ibm_plex_sans_arabic,
                R.font.noto_kufi_arabic
            )
            for (fontRes in fontResIds) {
                runCatching { ResourcesCompat.getFont(appContext, fontRes) }
            }
        }
    }

    fun syncLanguageContent(languageCode: String) {
        val normLang = languageCode.lowercase().trim()
        val primaryTrId = when (normLang) {
            "bn" -> 161
            "ur" -> 97
            "id" -> 33
            "tr" -> 77
            "fr" -> 31
            "es" -> 83
            "de" -> 27
            "ru" -> 45
            "hi" -> 122
            "fa" -> 29
            else -> 20
        }
        languageSyncJob?.cancel()
        languageSyncJob = warmScope.launch {
            runCatching {
                val quranDao = database.quranDao()
                quranDao.setTranslationEnabled(primaryTrId, true)

                val lastSurah = database.userLibraryDao().getReadingProgress()?.lastSurahId ?: 1
                val prioritySurahs = listOf(lastSurah, 1, 112, 113, 114).distinct()
                val source = quranDao.getAllTranslationSources().firstOrNull { it.id == primaryTrId }

                for (sid in prioritySurahs) {
                    val surah = quranDao.getSurah(sid)
                    if (surah == null || quranDao.countAyahsInSurah(sid) < surah.ayahCount) {
                        seeder.ensureSurahLoadedFromAssets(sid, surah?.ayahCount)
                    }
                    if (!normLang.equals("en", ignoreCase = true)) {
                        quranSyncRepository.ensureWbwForSurah(sid, normLang, lineTranslator)
                    }
                    if (primaryTrId != 20 && source != null) {
                        quranSyncRepository.ensureTranslationsForSurah(
                            surahId = sid,
                            enabledSources = listOf(source),
                            lineTranslator = lineTranslator
                        )
                    }
                }

                if (primaryTrId != 20 && quranDao.countTranslationsForSource(primaryTrId) < 6000) {
                    // Use direct Quran.com API download for full-Quran pack without blocking on 500+ sequential translate calls
                    quranSyncRepository.downloadTranslationPack(primaryTrId, lineTranslator = null)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
