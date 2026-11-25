package com.example.core.ibadah

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.roundToInt

data class PrayerTimesSummary(
    val fajr: String,
    val imsak: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val sunset: String,
    val maghrib: String,
    val isha: String,
    val tahajjud: String,
    val midnight: String,
    val nextPrayerName: String,
    val nextPrayerTime: String,
    val countdownText: String
)

data class CityLocationPreset(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val banglaName: String = "",
    val region: String = "",
    val aliases: String = "",
    val utcOffsetHours: Double? = null
) {
    fun matchesQuery(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return name.contains(q, ignoreCase = true) ||
            banglaName.contains(q, ignoreCase = true) ||
            region.contains(q, ignoreCase = true) ||
            aliases.contains(q, ignoreCase = true)
    }
}

object PrayerTimeCalculator {

    private const val KAABA_LAT = 21.4225
    private const val KAABA_LON = 39.8262
    private const val EARTH_RADIUS_KM = 6371.0

    val WORLD_CITIES = listOf(
        CityLocationPreset("Makkah, Saudi Arabia", 21.4225, 39.8262, "মক্কা, সৌদি আরব", "Saudi Arabia", "Mecca", 3.0),
        CityLocationPreset("Madinah, Saudi Arabia", 24.4672, 39.6111, "মদীনা, সৌদি আরব", "Saudi Arabia", "Medina", 3.0),
        CityLocationPreset("Riyadh, Saudi Arabia", 24.7136, 46.6753, "রিয়াদ, সৌদি আরব", "Saudi Arabia", "", 3.0),
        CityLocationPreset("Jeddah, Saudi Arabia", 21.4858, 39.1925, "জেদ্দা, সৌদি আরব", "Saudi Arabia", "", 3.0),
        CityLocationPreset("Cairo, Egypt", 30.0444, 31.2357, "কায়রো, মিশর", "Egypt", "", 2.0),
        CityLocationPreset("Istanbul, Turkey", 41.0082, 28.9784, "ইস্তানবুল, তুরস্ক", "Turkey", "", 3.0),
        CityLocationPreset("Ankara, Turkey", 39.9334, 32.8597, "আঙ্কারা, তুরস্ক", "Turkey", "", 3.0),
        CityLocationPreset("Jakarta, Indonesia", -6.2088, 106.8456, "জাকার্তা, ইন্দোনেশিয়া", "Indonesia", "", 7.0),
        CityLocationPreset("Surabaya, Indonesia", -7.2575, 112.7521, "সুরাবায়া, ইন্দোনেশিয়া", "Indonesia", "", 7.0),
        CityLocationPreset("Kuala Lumpur, Malaysia", 3.1390, 101.6869, "কুয়ালালামপুর, মালয়েশিয়া", "Malaysia", "", 8.0),
        CityLocationPreset("Karachi, Pakistan", 24.8607, 67.0011, "করাচি, পাকিস্তান", "Pakistan", "", 5.0),
        CityLocationPreset("Lahore, Pakistan", 31.5204, 74.3587, "লাহোর, পাকিস্তান", "Pakistan", "", 5.0),
        CityLocationPreset("Islamabad, Pakistan", 33.6844, 73.0479, "ইসলামাবাদ, পাকিস্তান", "Pakistan", "", 5.0),
        CityLocationPreset("Kolkata, India", 22.5726, 88.3639, "কলকাতা, ভারত", "India", "Calcutta West Bengal", 5.5),
        CityLocationPreset("Delhi, India", 28.7041, 77.1025, "দিল্লি, ভারত", "India", "New Delhi", 5.5),
        CityLocationPreset("Mumbai, India", 19.0760, 72.8777, "মুম্বাই, ভারত", "India", "", 5.5),
        CityLocationPreset("Hyderabad, India", 17.3850, 78.4867, "হায়দ্রাবাদ, ভারত", "India", "", 5.5),
        CityLocationPreset("Dubai, UAE", 25.2048, 55.2708, "দুবাই, সংযুক্ত আরব আমিরাত", "UAE", "", 4.0),
        CityLocationPreset("Abu Dhabi, UAE", 24.4539, 54.3773, "আবু ধাবি", "UAE", "", 4.0),
        CityLocationPreset("Doha, Qatar", 25.2854, 51.5310, "দোহা, কাতার", "Qatar", "", 3.0),
        CityLocationPreset("Kuwait City, Kuwait", 29.3759, 47.9774, "কুয়েত সিটি", "Kuwait", "", 3.0),
        CityLocationPreset("Muscat, Oman", 23.5880, 58.3829, "মাস্কাট, ওমান", "Oman", "", 4.0),
        CityLocationPreset("London, UK", 51.5074, -0.1278, "লন্ডন, যুক্তরাজ্য", "UK", "", 0.0),
        CityLocationPreset("Birmingham, UK", 52.4862, -1.8904, "বার্মিংহাম, যুক্তরাজ্য", "UK", "", 0.0),
        CityLocationPreset("Paris, France", 48.8566, 2.3522, "প্যারিস, ফ্রান্স", "France", "", 1.0),
        CityLocationPreset("Berlin, Germany", 52.5200, 13.4050, "বার্লিন, জার্মানি", "Germany", "", 1.0),
        CityLocationPreset("New York, USA", 40.7128, -74.0060, "নিউ ইয়র্ক, যুক্তরাষ্ট্র", "USA", "", -5.0),
        CityLocationPreset("Chicago, USA", 41.8781, -87.6298, "শিকাগো, যুক্তরাষ্ট্র", "USA", "", -6.0),
        CityLocationPreset("Toronto, Canada", 43.6532, -79.3832, "টরন্টো, কানাডা", "Canada", "", -5.0),
        CityLocationPreset("Sydney, Australia", -33.8688, 151.2093, "সিডনি, অস্ট্রেলিয়া", "Australia", "", 10.0),
        CityLocationPreset("Tokyo, Japan", 35.6762, 139.6503, "টোকিও, জাপান", "Japan", "", 9.0),
        CityLocationPreset("Singapore", 1.3521, 103.8198, "সিঙ্গাপুর", "Singapore", "", 8.0)
    )

    val CITY_PRESETS: List<CityLocationPreset> = BangladeshLocations.ALL_PLACES + WORLD_CITIES

    fun searchLocations(query: String, divisionFilter: String? = null): List<CityLocationPreset> {
        val trimmed = query.trim()
        val pool = if (divisionFilter.isNullOrBlank() || divisionFilter == "All") {
            CITY_PRESETS
        } else if (divisionFilter == "Global") {
            WORLD_CITIES
        } else {
            CITY_PRESETS.filter { it.region.contains(divisionFilter, ignoreCase = true) }
        }
        if (trimmed.isEmpty()) return pool
        return pool.filter { it.matchesQuery(trimmed) }
    }

    fun resolveUtcOffsetForLocation(locationName: String, latitude: Double, longitude: Double): Double? {
        val exact = CITY_PRESETS.firstOrNull {
            it.name.equals(locationName, ignoreCase = true)
        }?.utcOffsetHours
        if (exact != null) return exact
        // Approximate Bangladesh bounding box when GPS detects inside Bangladesh
        if (latitude in 20.5..26.7 && longitude in 88.0..92.7) {
            return 6.0
        }
        return null
    }

    fun calculateQiblaBearing(latitude: Double, longitude: Double): Float {
        val phiK = Math.toRadians(KAABA_LAT)
        val lambdaK = Math.toRadians(KAABA_LON)
        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude)
        val deltaLambda = lambdaK - lambda

        val y = sin(deltaLambda) * cos(phiK)
        val x = cos(phi) * sin(phiK) - sin(phi) * cos(phiK) * cos(deltaLambda)
        val bearingDeg = Math.toDegrees(atan2(y, x))
        return ((bearingDeg + 360.0) % 360.0).toFloat()
    }

    fun calculateDistanceToKaabaKm(latitude: Double, longitude: Double): Int {
        val dLat = Math.toRadians(KAABA_LAT - latitude)
        val dLon = Math.toRadians(KAABA_LON - longitude)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(KAABA_LAT)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt((1.0 - a).coerceAtLeast(0.0)))
        return (EARTH_RADIUS_KM * c).roundToInt()
    }

    fun calculateDailyTimes(
        latitude: Double,
        longitude: Double,
        calendar: Calendar = Calendar.getInstance(),
        hanafiAsr: Boolean = false,
        angleMethod: Int = 0, // 0: MWL / Karachi (18 deg), 1: ISNA (15 deg), 2: Egyptian (19.5 deg)
        timeZoneOffsetHours: Double? = null
    ): PrayerTimesSummary {
        val tzHours = timeZoneOffsetHours
            ?: (TimeZone.getDefault().getOffset(calendar.timeInMillis) / 3600000.0)
        val julian = julianDay(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        ) - longitude / (15.0 * 24.0)

        val d = julian - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
        val e = 23.439 - 0.00000036 * d

        val ra = atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0
        val eqTime = q / 15.0 - fixHour(ra)
        val decl = asinDeg(sinDeg(e) * sinDeg(l))

        val transit = 12.0 + tzHours - longitude / 15.0 - eqTime

        val fajrAngle = when (angleMethod) {
            1 -> 15.0
            2 -> 19.5
            else -> 18.0
        }
        val ishaAngle = when (angleMethod) {
            1 -> 15.0
            2 -> 17.5
            else -> 18.0
        }

        val fajr = transit - hourAngle(fajrAngle, latitude, decl)
        val imsak = fajr - (10.0 / 60.0) // 10 mins before Fajr
        val sunrise = transit - hourAngle(0.833, latitude, decl)
        val dhuhr = transit + (2.0 / 60.0) // +2 min safety offset
        val asrFactor = if (hanafiAsr) 2.0 else 1.0
        val asrAltDeg = Math.toDegrees(atan2(1.0, asrFactor + tan(Math.toRadians(kotlin.math.abs(latitude - decl)))))
        val asr = transit + hourAngle(-asrAltDeg, latitude, decl)
        val sunset = transit + hourAngle(0.833, latitude, decl)
        val maghrib = sunset + (3.0 / 60.0) // +3 min safety after sunset
        val isha = transit + hourAngle(ishaAngle, latitude, decl)

        // Tahajjud (last third of night) & Midnight
        val nightDurationHours = (24.0 - sunset + fajr) % 24.0
        val midnightDecimal = fixHour(sunset + nightDurationHours / 2.0)
        val tahajjudDecimal = fixHour(fajr - (nightDurationHours / 3.0))

        val nowDecimal = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60.0 + calendar.get(Calendar.SECOND) / 3600.0
        
        val ordered = listOf(
            "Fajr" to fajr,
            "Sunrise" to sunrise,
            "Dhuhr" to dhuhr,
            "Asr" to asr,
            "Maghrib" to maghrib,
            "Isha" to isha
        )

        val nextEntry = ordered.firstOrNull { it.second > nowDecimal } ?: ("Fajr" to (fajr + 24.0))
        val diffHours = nextEntry.second - nowDecimal
        val diffMinTotal = (diffHours * 60.0).roundToInt()
        val dh = diffMinTotal / 60
        val dm = diffMinTotal % 60
        val countdownStr = if (dh > 0) "in ${dh}h ${dm}m" else "in ${dm}m"

        return PrayerTimesSummary(
            fajr = formatTime(fajr),
            imsak = formatTime(imsak),
            sunrise = formatTime(sunrise),
            dhuhr = formatTime(dhuhr),
            asr = formatTime(asr),
            sunset = formatTime(sunset),
            maghrib = formatTime(maghrib),
            isha = formatTime(isha),
            tahajjud = formatTime(tahajjudDecimal),
            midnight = formatTime(midnightDecimal),
            nextPrayerName = nextEntry.first,
            nextPrayerTime = formatTime(nextEntry.second % 24.0),
            countdownText = countdownStr
        )
    }

    private fun hourAngle(angleBelowHorizon: Double, latitude: Double, decl: Double): Double {
        val term = (-sinDeg(angleBelowHorizon) - sinDeg(latitude) * sinDeg(decl)) /
            (cosDeg(latitude) * cosDeg(decl))
        val clamped = term.coerceIn(-0.999, 0.999)
        return Math.toDegrees(acos(clamped)) / 15.0
    }

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun formatTime(hoursDecimal: Double): String {
        val normalized = fixHour(hoursDecimal + 0.5 / 60.0)
        val hour24 = floor(normalized).toInt().coerceIn(0, 23)
        val minute = floor((normalized - hour24) * 60.0).toInt().coerceIn(0, 59)
        val amPm = if (hour24 >= 12) "PM" else "AM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "$hour12:${minute.toString().padStart(2, '0')} $amPm"
    }

    private fun fixAngle(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
    private fun fixHour(hr: Double): Double = ((hr % 24.0) + 24.0) % 24.0
    private fun sinDeg(deg: Double): Double = sin(Math.toRadians(deg))
    private fun cosDeg(deg: Double): Double = cos(Math.toRadians(deg))
    private fun asinDeg(x: Double): Double = Math.toDegrees(asin(x.coerceIn(-1.0, 1.0)))
    private fun atan2Deg(y: Double, x: Double): Double = Math.toDegrees(atan2(y, x))
}
