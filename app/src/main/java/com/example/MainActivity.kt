package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
