package com.example.core.di

import android.content.Context
import com.example.core.audio.QuranAudioController
import com.example.core.database.NoorDatabase
import com.example.core.database.QuranDatabaseSeeder
import com.example.core.localization.PreferencesRepository
import com.example.core.network.QuranComApiService
import com.example.core.network.QuranSyncRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: NoorDatabase by lazy { NoorDatabase.getInstance(appContext) }
    val seeder: QuranDatabaseSeeder by lazy { QuranDatabaseSeeder(appContext, database) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(appContext) }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
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
        QuranSyncRepository(database, apiService)
    }

    val audioController: QuranAudioController by lazy {
        QuranAudioController(appContext, database, okHttpClient, preferencesRepository)
    }
}
