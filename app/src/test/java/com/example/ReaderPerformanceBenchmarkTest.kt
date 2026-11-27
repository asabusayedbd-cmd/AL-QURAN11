package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.audio.QuranAudioController
import com.example.core.database.NoorDatabase
import com.example.core.database.QuranDatabaseSeeder
import com.example.core.localization.LineTranslator
import com.example.core.localization.PreferencesRepository
import com.example.core.localization.ReaderViewMode
import com.example.core.network.QuranComApiService
import com.example.core.network.QuranSyncRepository
import com.example.core.perf.PerfTracker
import com.example.feature.reader.ReaderScreen
import com.example.feature.reader.ReaderViewModel
import com.example.ui.components.InspirationSourceKind
import com.example.ui.components.StartupInspiration
import com.example.ui.components.StartupInspirationCatalog
import com.example.ui.components.StartupWelcomeOverlay
import com.example.ui.theme.NoorAlQuranTheme
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReaderPerformanceBenchmarkTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: NoorDatabase
    private lateinit var prefsRepo: PreferencesRepository
    private lateinit var seeder: QuranDatabaseSeeder
    private val networkCallsCount = AtomicInteger(0)

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.databaseBuilder(app, NoorDatabase::class.java, "perf_bench_${System.nanoTime()}.db")
            .allowMainThreadQueries()
            .build()
        prefsRepo = PreferencesRepository(app)
        seeder = QuranDatabaseSeeder(app, db)
        networkCallsCount.set(0)
        PerfTracker.resetForTest()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun buildSyncRepo(simulatedLatencyMs: Long): Pair<QuranSyncRepository, OkHttpClient> {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                networkCallsCount.incrementAndGet()
                if (simulatedLatencyMs > 0) {
                    Thread.sleep(simulatedLatencyMs)
                }
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("/tafsirs/") -> """{"tafsir":{"text":"<p>Sample tafsir</p>"}}"""
                    else -> """{"verses":[]}"""
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val api = Retrofit.Builder()
            .baseUrl("https://api.quran.com/api/v4/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(QuranComApiService::class.java)
        return QuranSyncRepository(db, api, app) to client
    }

    @Test
    fun measureSurahTapToFirstVisibleContentPostOptimization() = runBlocking {
        val tSeed0 = System.nanoTime()
        seeder.ensureSurahLoadedFromAssets(1, 7)
        val stage1Ms = (System.nanoTime() - tSeed0) / 1_000_000.0

        val (syncRepo, okHttp) = buildSyncRepo(simulatedLatencyMs = 250L)
        val audioController = QuranAudioController(app, db, okHttp, prefsRepo)
        val lineTranslator = LineTranslator(app, okHttp)

        prefsRepo.setReaderViewMode(ReaderViewMode.WORD_BY_WORD)
        prefsRepo.setWbwLanguage("bn")
        prefsRepo.setShowInlineTafsir(true)

        PerfTracker.markSurahTapped(2)
        val tapNs = System.nanoTime()
        val vm = ReaderViewModel(
            initialSurahId = 2,
            database = db,
            preferencesRepository = prefsRepo,
            syncRepository = syncRepo,
            audioController = audioController,
            seeder = seeder,
            lineTranslator = lineTranslator
        )

        var stateEmissions = 0
        var firstNonEmptyStateMs = -1.0
        var networkCallsAtFirstVisibleTrace = -1
        val collectorJob = launch(Dispatchers.Default) {
            vm.uiState.collect { st ->
                stateEmissions++
                if (firstNonEmptyStateMs < 0 && st.ayahs.isNotEmpty()) {
                    firstNonEmptyStateMs = (System.nanoTime() - tapNs) / 1_000_000.0
                }
            }
        }
        val traceObserverJob = launch(Dispatchers.Default) {
            PerfTracker.lastTrace.collect { tr ->
                if (tr?.firstVisibleContentMs != null && networkCallsAtFirstVisibleTrace < 0) {
                    networkCallsAtFirstVisibleTrace = networkCallsCount.get()
                }
            }
        }

        composeRule.setContent {
            NoorAlQuranTheme {
                ReaderScreen(
                    surahId = 2,
                    targetAyah = 1,
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("ayah_card_2:1").fetchSemanticsNodes().isNotEmpty()
        }
        val firstVisibleMs = (System.nanoTime() - tapNs) / 1_000_000.0

        // Allow deferred optional sync to begin AFTER first visible render
        delay(350L)
        val networkCallsAfterRender = networkCallsCount.get()
        val trace = PerfTracker.lastTrace.value

        collectorJob.cancel()
        traceObserverJob.cancel()
        audioController.release()

        println(
            String.format(
                "PERF_POST_OPT stage1Ms=%.2f firstNonEmptyStateMs=%.2f firstVisibleMs=%.2f " +
                    "stateEmissionsBeforeVisible=%d networkCallsAtFirstVisible=%d networkCallsAfterRender=%d " +
                    "traceNavMs=%.2f traceLocalMs=%.2f traceFirstVisibleMs=%.2f traceOptionalSyncStartMs=%.2f mushafSkipped=%s",
                stage1Ms,
                firstNonEmptyStateMs,
                firstVisibleMs,
                trace?.stateEmissionsBeforeVisible ?: stateEmissions,
                networkCallsAtFirstVisibleTrace,
                networkCallsAfterRender,
                trace?.navToReaderMs ?: -1.0,
                trace?.localDataReadyMs ?: -1.0,
                trace?.firstVisibleContentMs ?: -1.0,
                trace?.optionalSyncStartMs ?: -1.0,
                trace?.mushafGroupingSkipped.toString()
            )
        )

        // Verify optional network sync does NOT run before first visible reader content!
        assertEquals(0, networkCallsAtFirstVisibleTrace)
        assertTrue("Expected optional sync to run after render", networkCallsAfterRender > 0)
        assertNotNull(trace)
        assertTrue("Expected mushafGroupingSkipped in WBW mode", trace!!.mushafGroupingSkipped)
        assertNotNull(trace.optionalSyncStartMs)
        assertTrue(
            "Optional sync (${trace.optionalSyncStartMs}ms) must start after first visible (${trace.firstVisibleContentMs}ms)",
            trace.optionalSyncStartMs!! >= trace.firstVisibleContentMs!!
        )
        assertTrue(
            "Expected no top-bar or empty-state Download Surah button flash",
            composeRule.onAllNodesWithTag("download_current_surah_button").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("reader_download_surah_button").fetchSemanticsNodes().isEmpty()
        )
        composeRule.waitUntil(timeoutMillis = 10_000) {
            vm.uiState.value.ayahs.size == 286
        }
    }

    @Test
    fun measurePrepopulatedAssetDbSurah2ColdOpenAndNoDownloadFlash() = runBlocking {
        val prepopName = "prepop_bench_${System.nanoTime()}.db"
        val prepopDb = Room.databaseBuilder(app, NoorDatabase::class.java, prepopName)
            .createFromAsset(NoorDatabase.PREPOPULATED_ASSET_DB)
            .addMigrations(
                NoorDatabase.MIGRATION_1_2,
                NoorDatabase.MIGRATION_2_3,
                NoorDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

        try {
            val prepopSeeder = QuranDatabaseSeeder(app, prepopDb)
            val tSeed0 = System.nanoTime()
            prepopSeeder.seedIfNeeded()
            val seedFastPathMs = (System.nanoTime() - tSeed0) / 1_000_000.0

            val (syncRepo, okHttp) = buildSyncRepo(simulatedLatencyMs = 200L)
            val audioController = QuranAudioController(app, prepopDb, okHttp, prefsRepo)
            val lineTranslator = LineTranslator(app, okHttp)

            prefsRepo.setReaderViewMode(ReaderViewMode.WORD_BY_WORD)
            prefsRepo.setWbwLanguage("en")
            prefsRepo.setShowInlineTafsir(false)

            PerfTracker.resetForTest()
            PerfTracker.markSurahTapped(2)
            val tapNs = System.nanoTime()

            val vm = ReaderViewModel(
                initialSurahId = 2,
                database = prepopDb,
                preferencesRepository = prefsRepo,
                syncRepository = syncRepo,
                audioController = audioController,
                seeder = prepopSeeder,
                lineTranslator = lineTranslator
            )

            var downloadFlashDetected = false
            val collectorJob = launch(Dispatchers.Default) {
                vm.uiState.collect { st ->
                    if (!st.isLoadingSurah && st.ayahs.isEmpty() && st.surah != null) {
                        downloadFlashDetected = true
                    }
                    if (st.surah != null && !st.surah.isDownloaded) {
                        downloadFlashDetected = true
                    }
                }
            }

            composeRule.setContent {
                NoorAlQuranTheme {
                    ReaderScreen(
                        surahId = 2,
                        targetAyah = 1,
                        viewModel = vm,
                        onNavigateBack = {}
                    )
                }
            }

            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("ayah_card_2:1").fetchSemanticsNodes().isNotEmpty()
            }
            val firstVisibleMs = (System.nanoTime() - tapNs) / 1_000_000.0

            composeRule.waitUntil(timeoutMillis = 15_000) {
                vm.uiState.value.ayahs.size == 286
            }
            val fullExpandMs = (System.nanoTime() - tapNs) / 1_000_000.0

            assertFalse("Download Surah button must never flash on Surah open", downloadFlashDetected)
            assertTrue(composeRule.onAllNodesWithTag("download_current_surah_button").fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithTag("reader_download_surah_button").fetchSemanticsNodes().isEmpty())
            assertEquals(0, networkCallsCount.get())

            println(
                String.format(
                    "PERF_PREPOP_ASSET seedFastPathMs=%.2f firstVisibleMs=%.2f fullExpand286Ms=%.2f",
                    seedFastPathMs,
                    firstVisibleMs,
                    fullExpandMs
                )
            )

            collectorJob.cancel()
            audioController.release()
        } finally {
            prepopDb.close()
            app.getDatabasePath(prepopName).delete()
        }
    }

    @Test
    fun startupWelcomeOverlayShowsLogoAndInspirationWithReferenceAndDismissesImmediately() {
        var dismissed = false
        val sample = StartupInspiration(
            kind = InspirationSourceKind.SAHIH_HADITH,
            arabicText = "خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ",
            textEn = "The best among you are those who learn the Quran and teach it.",
            textBn = "তোমাদের মধ্যে সর্বোত্তম সেই ব্যক্তি, যে নিজে কুরআন শেখে এবং অন্যকে শেখায়।",
            referenceEn = "Sahih al-Bukhari 5027",
            referenceBn = "সহীহ বুখারী ৫০২৭"
        )

        composeRule.setContent {
            NoorAlQuranTheme {
                StartupWelcomeOverlay(
                    visible = !dismissed,
                    uiLanguageCode = "en",
                    inspiration = sample,
                    autoDismissDelayMs = 0L,
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("startup_welcome_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("startup_app_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("startup_inspiration_card").assertIsDisplayed()
        composeRule.onNodeWithText("Sahih al-Bukhari 5027").assertIsDisplayed()
        composeRule.onNodeWithTag("startup_continue_button").performScrollTo().performClick()
        assertTrue(dismissed)

        // Verify catalog items all have Arabic, EN/BN translations, and verified references
        assertTrue(StartupInspirationCatalog.items.size >= 5)
        StartupInspirationCatalog.items.forEach { item ->
            assertFalse(item.arabicText.isBlank())
            assertFalse(item.textEn.isBlank())
            assertFalse(item.textBn.isBlank())
            assertFalse(item.referenceEn.isBlank())
            assertFalse(item.referenceBn.isBlank())
        }
    }
}
