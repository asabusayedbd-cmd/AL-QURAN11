package com.example.core.localization

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class TranslationLanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String
)

class LineTranslator(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val loadedLangs = ConcurrentHashMap.newKeySet<String>()
    private val fileMutex = Mutex()
    private val cacheDir = File(context.filesDir, "line_tr_cache")

    companion object {
        val SUPPORTED_LANGUAGES: List<TranslationLanguageOption> = listOf(
            TranslationLanguageOption("bn", "বাংলা", "Bangla (Bangladesh)"),
            TranslationLanguageOption("en", "English", "English"),
            TranslationLanguageOption("ar", "العربية", "Arabic"),
            TranslationLanguageOption("ur", "اردو", "Urdu"),
            TranslationLanguageOption("hi", "हिन्दी", "Hindi"),
            TranslationLanguageOption("id", "Bahasa Indonesia", "Indonesian"),
            TranslationLanguageOption("ms", "Bahasa Melayu", "Malay"),
            TranslationLanguageOption("tr", "Türkçe", "Turkish"),
            TranslationLanguageOption("fr", "Français", "French"),
            TranslationLanguageOption("es", "Español", "Spanish"),
            TranslationLanguageOption("de", "Deutsch", "German"),
            TranslationLanguageOption("ru", "Русский", "Russian"),
            TranslationLanguageOption("fa", "فارسی", "Persian"),
            TranslationLanguageOption("ta", "தமிழ்", "Tamil"),
            TranslationLanguageOption("ml", "മലയാളം", "Malayalam"),
            TranslationLanguageOption("zh-CN", "中文", "Chinese"),
            TranslationLanguageOption("ja", "日本語", "Japanese"),
            TranslationLanguageOption("ko", "한국어", "Korean"),
            TranslationLanguageOption("pt", "Português", "Portuguese"),
            TranslationLanguageOption("it", "Italiano", "Italian"),
            TranslationLanguageOption("nl", "Nederlands", "Dutch"),
            TranslationLanguageOption("sw", "Kiswahili", "Swahili"),
            TranslationLanguageOption("bs", "Bosanski", "Bosnian"),
            TranslationLanguageOption("sq", "Shqip", "Albanian"),
            TranslationLanguageOption("so", "Soomaali", "Somali"),
            TranslationLanguageOption("uz", "Oʻzbekcha", "Uzbek"),
            TranslationLanguageOption("th", "ไทย", "Thai"),
            TranslationLanguageOption("vi", "Tiếng Việt", "Vietnamese"),
            TranslationLanguageOption("tl", "Filipino", "Filipino"),
            TranslationLanguageOption("az", "Azərbaycan", "Azerbaijani"),
            TranslationLanguageOption("kk", "Қазақша", "Kazakh"),
            TranslationLanguageOption("ps", "پښتو", "Pashto")
        )

        fun languageLabel(code: String): String {
            return SUPPORTED_LANGUAGES.firstOrNull {
                it.code.equals(code, ignoreCase = true)
            }?.let { "${it.nativeName} (${it.englishName})" }
                ?: code.uppercase()
        }

        internal fun parseGoogleTranslateJson(body: String): String? {
            return try {
                val root = JSONArray(body)
                val clauses = root.optJSONArray(0) ?: return null
                val sb = StringBuilder()
                for (i in 0 until clauses.length()) {
                    val clause = clauses.optJSONArray(i) ?: continue
                    val piece = clause.optString(0)
                    if (!piece.isNullOrEmpty() && piece != "null") {
                        sb.append(piece)
                    }
                }
                sb.toString().trim().takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun cacheKey(targetLang: String, text: String): String {
        val norm = targetLang.trim().lowercase()
        val digest = text.trim().hashCode().toString(16) + "_" + text.length
        return "$norm|$digest|${text.trim().take(48)}"
    }

    private suspend fun ensureDiskCacheLoaded(targetLang: String) {
        val lang = targetLang.trim().lowercase()
        if (loadedLangs.contains(lang)) return
        fileMutex.withLock {
            if (loadedLangs.contains(lang)) return@withLock
            val file = File(cacheDir, "$lang.json")
            if (file.exists() && file.length() > 0L) {
                runCatching {
                    val json = JSONObject(file.readText())
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        memoryCache[k] = json.getString(k)
                    }
                }
            }
            loadedLangs.add(lang)
        }
    }

    private suspend fun persistCacheEntry(targetLang: String, key: String, translated: String) {
        val lang = targetLang.trim().lowercase()
        memoryCache[key] = translated
        fileMutex.withLock {
            runCatching {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val file = File(cacheDir, "$lang.json")
                val json = if (file.exists() && file.length() > 0L) {
                    runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
                } else {
                    JSONObject()
                }
                json.put(key, translated)
                file.writeText(json.toString())
            }
        }
    }

    fun getCachedTranslation(text: String, targetLang: String): String? {
        if (text.isBlank()) return null
        return memoryCache[cacheKey(targetLang, text)]
    }

    suspend fun translateLine(text: String, targetLang: String): Result<String> = withContext(Dispatchers.IO) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return@withContext Result.success("")

        ensureDiskCacheLoaded(targetLang)
        val key = cacheKey(targetLang, cleaned)
        memoryCache[key]?.let { return@withContext Result.success(it) }

        try {
            val translated = translateChunkedOverNetwork(cleaned, targetLang)
            persistCacheEntry(targetLang, key, translated)
            Result.success(translated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("LineTranslator", "Translation to $targetLang failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun translateLinesJoined(
        lines: List<String>,
        targetLang: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext Result.success(emptyList())
        ensureDiskCacheLoaded(targetLang)

        val results = MutableList<String?>(lines.size) { idx ->
            val src = lines[idx].trim()
            if (src.isBlank()) "" else memoryCache[cacheKey(targetLang, src)]
        }
        val missingIndices = results.indices.filter { results[it] == null }
        if (missingIndices.isEmpty()) {
            return@withContext Result.success(results.map { it.orEmpty() })
        }

        try {
            // Join missing lines with a unique delimiter unlikely to mutate
            val delimiter = "\n§§§\n"
            val missingTexts = missingIndices.map { lines[it].trim() }
            val batches = mutableListOf<List<Int>>()
            var currentBatch = mutableListOf<Int>()
            var currentChars = 0
            for ((pos, idx) in missingIndices.withIndex()) {
                val len = missingTexts[pos].length + delimiter.length
                if (currentBatch.isNotEmpty() && currentChars + len > 1400) {
                    batches.add(currentBatch)
                    currentBatch = mutableListOf()
                    currentChars = 0
                }
                currentBatch.add(idx)
                currentChars += len
            }
            if (currentBatch.isNotEmpty()) batches.add(currentBatch)

            for (batch in batches) {
                val payload = batch.joinToString(delimiter) { lines[it].trim() }
                val rawTranslated = requestSingleTranslation(payload, targetLang)
                val split = rawTranslated.split(Regex("\\s*§{2,4}\\s*"))
                if (split.size == batch.size) {
                    batch.forEachIndexed { bIdx, origIdx ->
                        val src = lines[origIdx].trim()
                        val tr = split[bIdx].trim()
                        results[origIdx] = tr
                        persistCacheEntry(targetLang, cacheKey(targetLang, src), tr)
                    }
                } else {
                    // Fallback to individual line calls if delimiter split drifted
                    for (origIdx in batch) {
                        val src = lines[origIdx].trim()
                        val tr = translateChunkedOverNetwork(src, targetLang)
                        results[origIdx] = tr
                        persistCacheEntry(targetLang, cacheKey(targetLang, src), tr)
                    }
                }
            }
            Result.success(results.map { it.orEmpty() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun translateChunkedOverNetwork(text: String, targetLang: String): String {
        if (text.length <= 1500) {
            return requestSingleTranslation(text, targetLang)
        }
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (remaining.length <= 1500) {
                chunks.add(remaining)
                break
            }
            val cut = remaining.lastIndexOf('\n', 1500)
                .takeIf { it > 400 }
                ?: remaining.lastIndexOf(". ", 1500).takeIf { it > 400 }
                ?: remaining.lastIndexOf(' ', 1500).takeIf { it > 400 }
                ?: 1500
            chunks.add(remaining.substring(0, cut).trim())
            remaining = remaining.substring(cut).trimStart()
        }
        return chunks.joinToString("\n\n") { requestSingleTranslation(it, targetLang) }
    }

    private fun requestSingleTranslation(text: String, targetLang: String): String {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val tl = URLEncoder.encode(targetLang.trim(), "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t&q=$encoded"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseGoogleTranslateJson(body) ?: error("Empty translation response")
        }
    }
}
