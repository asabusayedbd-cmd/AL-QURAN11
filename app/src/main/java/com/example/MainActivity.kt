package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.audio.QuranPlaybackService
import com.example.core.di.AppContainer
import com.example.core.localization.NoorThemeMode
import com.example.core.perf.PerfTracker
import com.example.ui.navigation.NoorAppRoot
import com.example.ui.theme.NoorAlQuranTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : AppCompatActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        PerfTracker.markActivityCreated()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        appContainer = AppContainer.getInstance(applicationContext)
        appContainer.warmUpAsync()
        handlePlaybackNotificationIntent(intent)

        setContent {
            val themeAndLangFlow = remember(appContainer) {
                appContainer.preferencesRepository.preferencesFlow
                    .map { it.themeMode to it.uiLanguageCode }
                    .distinctUntilChanged()
            }
            val themeAndLang by themeAndLangFlow.collectAsStateWithLifecycle(
                initialValue = NoorThemeMode.SERENE_EMERALD to "en"
            )
            NoorAlQuranTheme(
                themeMode = themeAndLang.first,
                uiLanguageCode = themeAndLang.second
            ) {
                NoorAppRoot(
                    appContainer = appContainer,
                    uiLanguageCode = themeAndLang.second
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::appContainer.isInitialized) {
            handlePlaybackNotificationIntent(intent)
        }
    }

    private fun handlePlaybackNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(QuranPlaybackService.EXTRA_OPEN_CURRENT_PLAYBACK, false) == true) {
            intent.removeExtra(QuranPlaybackService.EXTRA_OPEN_CURRENT_PLAYBACK)
            val pb = appContainer.audioController.state.value
            if (pb.surahId in 1..114) {
                appContainer.requestPlaybackJump(pb.surahId, pb.ayahNumber.coerceAtLeast(1))
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing && ::appContainer.isInitialized) {
            val pb = appContainer.audioController.state.value
            if (!pb.isPlaying && !pb.isVisible) {
                appContainer.audioController.release()
            }
        }
        super.onDestroy()
    }
}
