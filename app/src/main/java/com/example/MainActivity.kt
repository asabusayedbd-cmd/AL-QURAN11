package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.core.di.AppContainer
import com.example.core.localization.UserPreferences
import com.example.ui.navigation.NoorAppRoot
import com.example.ui.theme.NoorAlQuranTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        appContainer = AppContainer(applicationContext)

        lifecycleScope.launch {
            appContainer.seeder.seedIfNeeded()
        }

        setContent {
            val prefs by appContainer.preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            LaunchedEffect(Unit) {
                appContainer.seeder.seedIfNeeded()
            }
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
        if (::appContainer.isInitialized) {
            appContainer.audioController.release()
        }
        super.onDestroy()
    }
}
