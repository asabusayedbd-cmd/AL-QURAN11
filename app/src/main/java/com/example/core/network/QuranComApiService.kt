package com.example.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class VersesResponseDto(
    val verses: List<VerseDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class VerseDto(
    val id: Int,
    @Json(name = "verse_number") val verseNumber: Int,
    @Json(name = "verse_key") val verseKey: String,
    @Json(name = "juz_number") val juzNumber: Int = 1,
    @Json(name = "hizb_number") val hizbNumber: Int = 1,
    @Json(name = "page_number") val pageNumber: Int = 1,
    @Json(name = "text_uthmani") val textUthmani: String? = null,
    @Json(name = "text_indopak") val textIndopak: String? = null,
    @Json(name = "text_uthmani_tajweed") val textTajweed: String? = null,
    val words: List<WordDto> = emptyList(),
    val translations: List<TranslationDto> = emptyList(),
    val audio: VerseAudioDto? = null
)

@JsonClass(generateAdapter = true)
data class WordDto(
    val id: Long,
    val position: Int,
    @Json(name = "char_type_name") val charTypeName: String? = null,
    @Json(name = "text_uthmani") val textUthmani: String? = null,
    @Json(name = "text_indopak") val textIndopak: String? = null,
    @Json(name = "audio_url") val audioUrl: String? = null,
    val translation: WordTextDto? = null,
    val transliteration: WordTextDto? = null
)

@JsonClass(generateAdapter = true)
data class WordTextDto(
    val text: String? = null,
    @Json(name = "language_name") val languageName: String? = null
)

@JsonClass(generateAdapter = true)
data class TranslationDto(
    @Json(name = "resource_id") val resourceId: Int,
    val text: String,
    @Json(name = "resource_name") val resourceName: String? = null
)

@JsonClass(generateAdapter = true)
data class VerseAudioDto(
    val url: String? = null,
    val segments: List<List<Long>> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TafsirResponseDto(
    val tafsir: TafsirDetailDto? = null
)

@JsonClass(generateAdapter = true)
data class TafsirDetailDto(
    @Json(name = "resource_id") val resourceId: Int? = null,
    @Json(name = "resource_name") val resourceName: String? = null,
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class TranslationResourcesDto(
    val translations: List<TranslationResourceItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TranslationResourceItemDto(
    val id: Int,
    val name: String,
    @Json(name = "author_name") val authorName: String? = null,
    @Json(name = "language_name") val languageName: String? = null
)

@JsonClass(generateAdapter = true)
data class TafsirResourcesDto(
    val tafsirs: List<TafsirResourceItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TafsirResourceItemDto(
    val id: Int,
    val name: String,
    @Json(name = "author_name") val authorName: String? = null,
    @Json(name = "language_name") val languageName: String? = null
)

interface QuranComApiService {
    @GET("verses/by_chapter/{chapter_number}")
    suspend fun getVersesByChapter(
        @Path("chapter_number") chapterNumber: Int,
        @Query("language") language: String = "en",
        @Query("words") words: Boolean = true,
        @Query("word_fields") wordFields: String = "text_uthmani,text_indopak,char_type_name",
        @Query("fields") fields: String = "text_uthmani,text_indopak,text_uthmani_tajweed",
        @Query("translations") translations: String = "161,131",
        @Query("audio") audio: Int = 7,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): VersesResponseDto

    @GET("tafsirs/{tafsir_id}/by_ayah/{verse_key}")
    suspend fun getAyahTafsir(
        @Path("tafsir_id") tafsirId: Int,
        @Path("verse_key") verseKey: String
    ): TafsirResponseDto

    @GET("resources/translations")
    suspend fun getTranslationResources(
        @Query("language") language: String = "en"
    ): TranslationResourcesDto

    @GET("resources/tafsirs")
    suspend fun getTafsirResources(
        @Query("language") language: String = "en"
    ): TafsirResourcesDto
}
