package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.audio.QuranPlaybackService
import com.example.core.di.AppContainer
import com.example.core.localization.UserPreferences
import com.example.ui.navigation.NoorAppRoot
import com.example.ui.theme.NoorAlQuranTheme

class MainActivity : AppCompatActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        appContainer = AppContainer.getInstance(applicationContext)
        appContainer.warmUpAsync()
        handlePlaybackNotificationIntent(intent)

        setContent {
            val prefs by appContainer.preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            NoorAlQuranTheme(
                themeMode = prefs.themeMode,
                uiLanguageCode = prefs.uiLanguageCode
            ) {
                NoorAppRoot(
                    appContainer = appContainer,
                    uiLanguageCode = prefs.uiLanguageCode
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
