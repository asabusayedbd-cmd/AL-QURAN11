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

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
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

    fun warmUpAsync() {
        if (!warmedUp.compareAndSet(false, true)) return
        warmScope.launch {
            runCatching { preferencesRepository.preferencesFlow.first() }
        }
        warmScope.launch {
            seeder.seedIfNeeded(deferBulkDelayMs = 1100L, interSurahYieldMs = 15L)
        }
        warmScope.launch {
            kotlinx.coroutines.delay(1800L)
            val fontResIds = intArrayOf(
                R.font.plus_jakarta_sans,
                R.font.amiri,
                R.font.noto_sans_bengali,
                R.font.noto_naskh_arabic,
                R.font.hind_siliguri,
                R.font.scheherazade_new
            )
            for (fontRes in fontResIds) {
                runCatching { ResourcesCompat.getFont(appContext, fontRes) }
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
