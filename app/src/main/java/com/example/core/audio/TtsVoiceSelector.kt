package com.example.core.audio

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Ensures device TextToSpeech for Quranic, Dua, and Tajweed pronunciation strictly
 * avoids female voices and selects an Arabic male voice whenever the engine exposes one.
 *
 * Note: "female" contains the substring "male", so male detection must explicitly exclude
 * female tokens instead of calling `.contains("male")`.
 */
object TtsVoiceSelector {

    private val FEMALE_NAME_MARKERS = listOf(
        "female", "woman", "girl", "lady", "fem",
        "-f-", "_f_", "#f#", "salma", "laila", "layla",
        "rana", "zainab", "fatima", "amira", "hoda", "sara",
        "nora", "mariam", "aisha", "ar-xa-x-arz", "ar-xa-x-ard"
    )

    private val MALE_NAME_MARKERS = listOf(
        "male", "man", "-m-", "_m_", "#m#",
        "tariq", "majed", "maged", "hamza", "hamed",
        "naayf", "zaid", "omar", "ali", "khalid", "ar-xa-x-arc", "ar-xa-x-are"
    )

    fun isLikelyFemaleVoiceName(rawName: String?): Boolean {
        if (rawName.isNullOrBlank()) return false
        val lower = rawName.lowercase(Locale.US)
        if (FEMALE_NAME_MARKERS.any { lower.contains(it) }) return true
        val tokens = lower.split('-', '_', '#', '.', ' ')
        return "f" in tokens || "fem" in tokens
    }

    fun isLikelyMaleVoiceName(rawName: String?): Boolean {
        if (rawName.isNullOrBlank() || isLikelyFemaleVoiceName(rawName)) return false
        val lower = rawName.lowercase(Locale.US)
        if (MALE_NAME_MARKERS.any { lower.contains(it) }) return true
        val tokens = lower.split('-', '_', '#', '.', ' ')
        return "m" in tokens || "male" in tokens
    }

    fun selectBestMaleArabicVoice(voices: Collection<Voice>?): Voice? {
        if (voices.isNullOrEmpty()) return null
        val arabicVoices = voices.filter { it.locale?.language.equals("ar", ignoreCase = true) }
        if (arabicVoices.isEmpty()) return null

        val nonFemaleArabic = arabicVoices.filterNot { isLikelyFemaleVoiceName(it.name) }
        if (nonFemaleArabic.isEmpty()) return null

        return nonFemaleArabic.firstOrNull { isLikelyMaleVoiceName(it.name) && !it.isNetworkConnectionRequired }
            ?: nonFemaleArabic.firstOrNull { isLikelyMaleVoiceName(it.name) }
            ?: nonFemaleArabic.firstOrNull { !it.isNetworkConnectionRequired }
            ?: nonFemaleArabic.firstOrNull()
    }

    fun configureMaleArabicTts(engine: TextToSpeech?) {
        if (engine == null) return
        runCatching { engine.setLanguage(Locale.forLanguageTag("ar")) }
        runCatching { engine.setPitch(0.82f) }
        runCatching {
            val selectedVoice = selectBestMaleArabicVoice(engine.voices)
            if (selectedVoice != null) {
                engine.voice = selectedVoice
            }
        }
    }
}
