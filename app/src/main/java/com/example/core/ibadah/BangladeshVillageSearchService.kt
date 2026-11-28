package com.example.core.ibadah

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class BangladeshVillageSearchResult(
    val places: List<CityLocationPreset>,
    val isFromLiveSearch: Boolean,
    val hadNetworkError: Boolean
)

/**
 * Local SharedPreferences store for any user-selected online village, GPS-resolved village,
 * or custom village pin in Bangladesh so saved villages remain 100% available offline.
 */
object SavedBangladeshLocationsStore {
    private const val PREFS_NAME = "noor_saved_bd_villages"
    private const val KEY_PLACES_JSON = "saved_places_v1"
    private const val MAX_SAVED_PLACES = 40

    fun getSavedLocations(context: Context): List<CityLocationPreset> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLACES_JSON, null)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return parseSavedJson(raw)
    }

    fun saveLocation(context: Context, preset: CityLocationPreset): List<CityLocationPreset> {
        val current = getSavedLocations(context).toMutableList()
        current.removeAll { it.name.equals(preset.name, ignoreCase = true) }
        val normalized = preset.copy(
            utcOffsetHours = preset.utcOffsetHours ?: if (PrayerTimeCalculator.isInsideBangladesh(preset.latitude, preset.longitude)) 6.0 else null,
            sourceLabel = preset.sourceLabel.ifBlank { "Saved" }
        )
        current.add(0, normalized)
        val trimmed = current.take(MAX_SAVED_PLACES)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLACES_JSON, serializeSavedJson(trimmed))
            .apply()
        return trimmed
    }

    internal fun parseSavedJson(raw: String): List<CityLocationPreset> = runCatching {
        val arr = JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isEmpty()) continue
                val lat = obj.optDouble("lat", Double.NaN)
                val lon = obj.optDouble("lon", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite()) continue
                val utcOffset = if (obj.has("utc") && !obj.isNull("utc")) obj.optDouble("utc") else null
                add(
                    CityLocationPreset(
                        name = name,
                        latitude = lat,
                        longitude = lon,
                        banglaName = obj.optString("bn"),
                        region = obj.optString("region", "Bangladesh"),
                        aliases = obj.optString("aliases"),
                        utcOffsetHours = utcOffset,
                        sourceLabel = obj.optString("source", "Saved")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    internal fun serializeSavedJson(items: List<CityLocationPreset>): String {
        val arr = JSONArray()
        items.forEach { p ->
            val obj = JSONObject().apply {
                put("name", p.name)
                put("lat", p.latitude)
                put("lon", p.longitude)
                put("bn", p.banglaName)
                put("region", p.region)
                put("aliases", p.aliases)
                if (p.utcOffsetHours != null) put("utc", p.utcOffsetHours)
                put("source", p.sourceLabel)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}

/**
 * Searches all Bangladesh villages, unions, mouzas, wards, and upazilas online using
 * OpenStreetMap Nominatim (bounded strictly to countrycodes=bd) with Android Geocoder fallback,
 * plus reverse-geocoding for GPS village resolution.
 */
object BangladeshVillageSearchService {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .build()
    }

    private val queryCache = ConcurrentHashMap<String, List<CityLocationPreset>>()

    suspend fun searchVillagesOnline(
        context: Context,
        query: String,
        divisionHint: String? = null,
        districtHint: String? = null
    ): BangladeshVillageSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) {
            return@withContext BangladeshVillageSearchResult(
                places = emptyList(),
                isFromLiveSearch = false,
                hadNetworkError = false
            )
        }

        val scopedQuery = buildScopedSearchQuery(cleanQuery, divisionHint, districtHint)
        val cacheKey = scopedQuery.lowercase(Locale.ROOT)
        queryCache[cacheKey]?.let { cached ->
            return@withContext BangladeshVillageSearchResult(
                places = cached,
                isFromLiveSearch = true,
                hadNetworkError = false
            )
        }

        // Primary: OpenStreetMap Nominatim restricted to Bangladesh (countrycodes=bd)
        val nominatimAttempt = runCatching {
            val encodedQ = Uri.encode(scopedQuery)
            val url = "https://nominatim.openstreetmap.org/search" +
                "?q=$encodedQ&countrycodes=bd&format=jsonv2&addressdetails=1&limit=25&accept-language=bn,en"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "NoorAlQuran-Android/1.0 (PrayerTimes Village Setup)")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                parseNominatimSearchJson(body)
            }
        }

        val nominatimHits = nominatimAttempt.getOrNull().orEmpty()
        if (nominatimHits.isNotEmpty()) {
            queryCache[cacheKey] = nominatimHits
            return@withContext BangladeshVillageSearchResult(
                places = nominatimHits,
                isFromLiveSearch = true,
                hadNetworkError = false
            )
        }

        // Secondary fallback: Android platform Geocoder (Bangladesh)
        val geocoderHits = geocodeNameBangladesh(context, "$scopedQuery, Bangladesh")
        if (geocoderHits.isNotEmpty()) {
            queryCache[cacheKey] = geocoderHits
            return@withContext BangladeshVillageSearchResult(
                places = geocoderHits,
                isFromLiveSearch = true,
                hadNetworkError = false
            )
        }

        BangladeshVillageSearchResult(
            places = emptyList(),
            isFromLiveSearch = nominatimAttempt.isSuccess,
            hadNetworkError = nominatimAttempt.isFailure
        )
    }

    suspend fun reverseGeocodeBangladeshPlace(
        context: Context,
        latitude: Double,
        longitude: Double
    ): CityLocationPreset = withContext(Dispatchers.IO) {
        // Offline nearest fallback first
        val nearest = PrayerTimeCalculator.findNearestBangladeshPlace(latitude, longitude)
        val offlineFallbackName = if (nearest != null) {
            val cleanBase = nearest.first.name.removeSuffix(", Bangladesh").trim()
            if (nearest.second < 4.0) "$cleanBase, Bangladesh" else "Near $cleanBase, Bangladesh"
        } else {
            String.format(Locale.US, "BD %.3f, %.3f", latitude, longitude)
        }

        // 1. Try Android platform Geocoder first (respects device locale & avoids unnecessary HTTP calls)
        val platformAddress = runCatching { reverseWithPlatformGeocoder(context, latitude, longitude) }.getOrNull()
        if (platformAddress != null) {
            return@withContext platformAddress
        }

        // 2. Fallback to Nominatim reverse if inside Bangladesh
        val nominatimReverse = runCatching {
            val url = String.format(
                Locale.US,
                "https://nominatim.openstreetmap.org/reverse?lat=%.6f&lon=%.6f&zoom=16&format=jsonv2&addressdetails=1&accept-language=bn,en",
                latitude,
                longitude
            )
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "NoorAlQuran-Android/1.0 (PrayerTimes GPS Reverse)")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else parseNominatimReverseJson(resp.body?.string().orEmpty(), latitude, longitude)
            }
        }.getOrNull()

        if (nominatimReverse != null) {
            return@withContext nominatimReverse
        }

        CityLocationPreset(
            name = offlineFallbackName,
            latitude = latitude,
            longitude = longitude,
            banglaName = nearest?.first?.banglaName.orEmpty(),
            region = nearest?.first?.region ?: "Bangladesh",
            aliases = "GPS",
            utcOffsetHours = PrayerTimeCalculator.resolveUtcOffsetForLocation(offlineFallbackName, latitude, longitude) ?: 6.0,
            sourceLabel = "GPS"
        )
    }

    internal fun buildScopedSearchQuery(
        query: String,
        divisionHint: String?,
        districtHint: String?
    ): String {
        val q = query.trim()
        val parts = mutableListOf(q)
        if (!districtHint.isNullOrBlank() && districtHint != "All" && !q.contains(districtHint, ignoreCase = true)) {
            parts.add(districtHint)
        } else if (!divisionHint.isNullOrBlank() &&
            divisionHint !in setOf("All", "Global", "Saved") &&
            !q.contains(divisionHint, ignoreCase = true)
        ) {
            parts.add(divisionHint)
        }
        return parts.joinToString(", ")
    }

    internal fun parseNominatimSearchJson(rawJson: String): List<CityLocationPreset> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            val seen = HashSet<String>()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val preset = parseNominatimObject(obj) ?: continue
                    if (seen.add(preset.name.lowercase(Locale.ROOT))) {
                        add(preset)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun parseNominatimReverseJson(
        rawJson: String,
        fallbackLat: Double,
        fallbackLon: Double
    ): CityLocationPreset? {
        if (rawJson.isBlank()) return null
        return runCatching {
            val obj = JSONObject(rawJson)
            parseNominatimObject(obj, fallbackLat, fallbackLon)
        }.getOrNull()
    }

    private fun parseNominatimObject(
        obj: JSONObject,
        fallbackLat: Double? = null,
        fallbackLon: Double? = null
    ): CityLocationPreset? {
        val addressType = obj.optString("addresstype")
        if (addressType == "country") return null

        val lat = obj.optString("lat").toDoubleOrNull() ?: fallbackLat ?: return null
        val lon = obj.optString("lon").toDoubleOrNull() ?: fallbackLon ?: return null
        if (!PrayerTimeCalculator.isInsideBangladesh(lat, lon)) return null

        val addr = obj.optJSONObject("address")
        val primaryPlace = firstNonBlank(
            addr?.optString("village"),
            addr?.optString("hamlet"),
            addr?.optString("isolated_dwelling"),
            addr?.optString("suburb"),
            addr?.optString("neighbourhood"),
            addr?.optString("quarter"),
            addr?.optString("town"),
            addr?.optString("municipality"),
            addr?.optString("city_district"),
            obj.optString("name")
        )
        if (primaryPlace.isBlank()) return null

        val upazila = firstNonBlank(
            addr?.optString("county"),
            addr?.optString("subdistrict")
        ).cleanAdminSuffix()

        val district = firstNonBlank(
            addr?.optString("state_district"),
            addr?.optString("district"),
            addr?.optString("city")
        ).cleanAdminSuffix()

        val division = firstNonBlank(addr?.optString("state")).ifBlank { "Bangladesh" }

        val labelParts = linkedSetOf<String>().apply {
            add(primaryPlace)
            if (upazila.isNotBlank() && !upazila.equals(primaryPlace, ignoreCase = true)) add(upazila)
            if (district.isNotBlank() && !district.equals(primaryPlace, ignoreCase = true) && !district.equals(upazila, ignoreCase = true)) {
                add(district)
            }
        }
        val formattedName = labelParts.joinToString(", ") + ", Bangladesh"
        val regionText = buildString {
            if (upazila.isNotBlank() && district.isNotBlank()) {
                append("$upazila, $district • ")
            } else if (district.isNotBlank()) {
                append("$district • ")
            }
            append(division)
        }
        val typeLabel = when (addressType) {
            "village", "hamlet", "isolated_dwelling", "farm", "allotments" -> "Village"
            "suburb", "neighbourhood", "quarter", "residential" -> "Area"
            "town", "municipality" -> "Union / Town"
            else -> "Bangladesh Place"
        }

        return CityLocationPreset(
            name = formattedName,
            latitude = lat,
            longitude = lon,
            banglaName = "",
            region = regionText,
            aliases = obj.optString("display_name"),
            utcOffsetHours = 6.0,
            sourceLabel = typeLabel
        )
    }

    private suspend fun geocodeNameBangladesh(
        context: Context,
        query: String
    ): List<CityLocationPreset> {
        if (!Geocoder.isPresent()) return emptyList()
        val addresses = runCatching { queryPlatformGeocoder(context, query, 12) }.getOrDefault(emptyList())
        return addresses.mapNotNull { addr ->
            addressToPreset(addr, fallbackLabel = "Village")
        }.distinctBy { it.name.lowercase(Locale.ROOT) }
    }

    private suspend fun reverseWithPlatformGeocoder(
        context: Context,
        latitude: Double,
        longitude: Double
    ): CityLocationPreset? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(results: MutableList<Address>) {
                        if (cont.isActive) cont.resume(results)
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(emptyList())
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
        }
        return addresses.firstOrNull()?.let { addressToPreset(it, fallbackLabel = "GPS", overrideLat = latitude, overrideLon = longitude) }
    }

    private suspend fun queryPlatformGeocoder(
        context: Context,
        query: String,
        maxResults: Int
    ): List<Address> {
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(query, maxResults, object : Geocoder.GeocodeListener {
                    override fun onGeocode(results: MutableList<Address>) {
                        if (cont.isActive) cont.resume(results)
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(emptyList())
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, maxResults).orEmpty()
        }
    }

    private fun addressToPreset(
        addr: Address,
        fallbackLabel: String,
        overrideLat: Double? = null,
        overrideLon: Double? = null
    ): CityLocationPreset? {
        val lat = overrideLat ?: addr.latitude
        val lon = overrideLon ?: addr.longitude
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (!PrayerTimeCalculator.isInsideBangladesh(lat, lon)) return null

        val primary = firstNonBlank(addr.subLocality, addr.featureName, addr.locality)
        val subAdmin = addr.subAdminArea.orEmpty().cleanAdminSuffix()
        val admin = addr.adminArea.orEmpty()
        val parts = linkedSetOf<String>()
        if (primary.isNotBlank()) parts.add(primary)
        if (subAdmin.isNotBlank()) parts.add(subAdmin)
        if (parts.isEmpty() && admin.isNotBlank()) parts.add(admin)
        if (parts.isEmpty()) return null

        val name = parts.joinToString(", ") + ", Bangladesh"
        val region = listOfNotNull(
            subAdmin.takeIf { it.isNotBlank() },
            admin.takeIf { it.isNotBlank() }
        ).joinToString(" • ").ifBlank { "Bangladesh" }

        return CityLocationPreset(
            name = name,
            latitude = lat,
            longitude = lon,
            region = region,
            aliases = listOfNotNull(addr.subLocality, addr.locality, addr.subAdminArea, addr.adminArea).joinToString(" "),
            utcOffsetHours = 6.0,
            sourceLabel = fallbackLabel
        )
    }

    private fun firstNonBlank(vararg candidates: String?): String {
        return candidates.firstOrNull { !it.isNullOrBlank() && it != "null" }?.trim().orEmpty()
    }

    private fun String.cleanAdminSuffix(): String {
        return this
            .replace(" Upazila", "", ignoreCase = true)
            .replace(" District", "", ignoreCase = true)
            .replace(" Zila", "", ignoreCase = true)
            .replace(" উপজেলা", "")
            .replace(" জেলা", "")
            .trim()
    }
}
