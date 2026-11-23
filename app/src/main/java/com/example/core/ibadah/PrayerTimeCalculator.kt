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

data class PrayerTimesSummary(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val nextPrayerName: String,
    val nextPrayerTime: String
)

data class CityLocationPreset(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

object PrayerTimeCalculator {

    private const val KAABA_LAT = 21.4225
    private const val KAABA_LON = 39.8262

    val CITY_PRESETS = listOf(
        CityLocationPreset("Dhaka, Bangladesh", 23.8103, 90.4125),
        CityLocationPreset("Chattogram, Bangladesh", 22.3569, 91.7832),
        CityLocationPreset("Sylhet, Bangladesh", 24.8949, 91.8687),
        CityLocationPreset("Makkah, Saudi Arabia", 21.4225, 39.8262),
        CityLocationPreset("Madinah, Saudi Arabia", 24.4672, 39.6111),
        CityLocationPreset("Istanbul, Turkey", 41.0082, 28.9784),
        CityLocationPreset("Jakarta, Indonesia", -6.2088, 106.8456),
        CityLocationPreset("Kuala Lumpur, Malaysia", 3.1390, 101.6869),
        CityLocationPreset("Karachi, Pakistan", 24.8607, 67.0011),
        CityLocationPreset("London, UK", 51.5074, -0.1278),
        CityLocationPreset("New York, USA", 40.7128, -74.0060)
    )

    /**
     * Computes great-circle initial bearing from given coordinates to the Kaaba in Makkah (0..360 deg).
     */
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

    /**
     * Astronomical solar calculation using MWL/Karachi standard angles (18 deg Fajr, 17.5 deg Isha, Shafi'i/Hanafi Asr).
     */
    fun calculateDailyTimes(
        latitude: Double,
        longitude: Double,
        calendar: Calendar = Calendar.getInstance(),
        hanafiAsr: Boolean = false
    ): PrayerTimesSummary {
        val tzHours = TimeZone.getDefault().getOffset(calendar.timeInMillis) / 3600000.0
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
        val fajr = transit - hourAngle(18.0, latitude, decl)
        val sunrise = transit - hourAngle(0.833, latitude, decl)
        val dhuhr = transit + (2.0 / 60.0)
        val asrFactor = if (hanafiAsr) 2.0 else 1.0
        val asrAltDeg = Math.toDegrees(atan2(1.0, asrFactor + tan(Math.toRadians(kotlin.math.abs(latitude - decl)))))
        val asr = transit + hourAngle(-asrAltDeg, latitude, decl)
        val maghrib = transit + hourAngle(0.833, latitude, decl)
        val isha = transit + hourAngle(18.0, latitude, decl)

        val nowDecimal = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60.0
        val ordered = listOf(
            "Fajr" to fajr,
            "Dhuhr" to dhuhr,
            "Asr" to asr,
            "Maghrib" to maghrib,
            "Isha" to isha
        )
        val next = ordered.firstOrNull { it.second > nowDecimal } ?: ("Fajr" to fajr)

        return PrayerTimesSummary(
            fajr = formatTime(fajr),
            sunrise = formatTime(sunrise),
            dhuhr = formatTime(dhuhr),
            asr = formatTime(asr),
            maghrib = formatTime(maghrib),
            isha = formatTime(isha),
            nextPrayerName = next.first,
            nextPrayerTime = formatTime(next.second)
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
