package com.example.feature.ibadah

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.view.Surface as AndroidSurface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

@Immutable
data class QiblaSensorStatus(
    val azimuthDeg: Float = 0f,
    val cameraAzimuthDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val cameraPitchDeg: Float = 0f,
    val magneticFieldMicroTesla: Float = 0f,
    val hasSensorReading: Boolean = false,
    val hasHardwareCompass: Boolean = true,
    val needsCalibration: Boolean = false,
    val magneticInterferenceDetected: Boolean = false,
    val accuracyLevel: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val sensorSourceLabel: String = "Calibrating..."
)

@Immutable
data class QiblaGuidance(
    val relativeAngleDeg: Float,
    val signedTurnDeg: Float,
    val isAligned: Boolean
)

data class OrientationSample(
    val trueAzimuthDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float
)

@Immutable
data class SolarPosition(
    val azimuthDeg: Float,
    val altitudeDeg: Float,
    val isAboveHorizon: Boolean
)

@Immutable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

@Immutable
data class GlobeProjectionPoint(
    val x: Float,
    val y: Float,
    val isVisible: Boolean
)

object QiblaOrientationMath {
    private val CARDINALS = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    fun normalizeAngle360(deg: Float): Float {
        if (!deg.isFinite()) return 0f
        return ((deg % 360f) + 360f) % 360f
    }

    fun shortestSignedDelta(fromDeg: Float, toDeg: Float): Float {
        return ((toDeg - fromDeg + 540f) % 360f) - 180f
    }

    fun smoothAngleShortestArc(currentDeg: Float, targetDeg: Float, alpha: Float = 0.24f): Float {
        val delta = shortestSignedDelta(currentDeg, targetDeg)
        return normalizeAngle360(currentDeg + delta * alpha.coerceIn(0.05f, 1f))
    }

    fun advanceContinuousAngle(currentContinuousDeg: Float, targetDeg: Float): Float {
        val normalizedCurrent = normalizeAngle360(currentContinuousDeg)
        val delta = shortestSignedDelta(normalizedCurrent, normalizeAngle360(targetDeg))
        return currentContinuousDeg + delta
    }

    fun computeMagneticFieldMicroTesla(values: FloatArray): Float {
        if (values.size < 3) return 0f
        return sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
    }

    fun isMagneticInterference(microTesla: Float): Boolean {
        if (microTesla <= 0f) return false
        return microTesla < 18f || microTesla > 75f
    }

    fun computeQiblaGuidance(
        qiblaBearingDeg: Float,
        azimuthDeg: Float,
        hasSensorReading: Boolean,
        alignmentToleranceDeg: Float = 4.5f
    ): QiblaGuidance {
        val relative = if (hasSensorReading) {
            normalizeAngle360(qiblaBearingDeg - azimuthDeg)
        } else {
            normalizeAngle360(qiblaBearingDeg)
        }
        val signedTurn = ((relative + 540f) % 360f) - 180f
        val aligned = hasSensorReading && abs(signedTurn) <= alignmentToleranceDeg
        return QiblaGuidance(
            relativeAngleDeg = relative,
            signedTurnDeg = signedTurn,
            isAligned = aligned
        )
    }

    fun compassCardinalDirection(bearingDeg: Float): String {
        val normalized = normalizeAngle360(bearingDeg)
        val idx = (((normalized + 11.25f) % 360f) / 22.5f).toInt().coerceIn(0, CARDINALS.lastIndex)
        return CARDINALS[idx]
    }

    fun remapAxesForDisplayRotation(displayRotation: Int): Pair<Int, Int> = when (displayRotation) {
        AndroidSurface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        AndroidSurface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        AndroidSurface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }

    /**
     * Remaps axes for upright AR camera mode so the new +Y axis points along the rear camera
     * optical vector (-Z_device) while new +X/+Z align with screen right/up, preventing gimbal
     * lock and avoiding 180-degree azimuth inversion when holding the phone vertically.
     */
    fun remapAxesForUprightCamera(displayRotation: Int): Pair<Int, Int> = when (displayRotation) {
        AndroidSurface.ROTATION_90 -> SensorManager.AXIS_Z to SensorManager.AXIS_MINUS_X
        AndroidSurface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
        AndroidSurface.ROTATION_270 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
    }

    fun computeOrientationFromRotationMatrix(
        rawMatrix: FloatArray,
        displayRotation: Int,
        declinationDeg: Float,
        outRemapped: FloatArray,
        outOrientation: FloatArray
    ): OrientationSample = computeOrientationWithAxes(
        rawMatrix = rawMatrix,
        axes = remapAxesForDisplayRotation(displayRotation),
        declinationDeg = declinationDeg,
        outRemapped = outRemapped,
        outOrientation = outOrientation
    )

    fun computeCameraOrientationFromRotationMatrix(
        rawMatrix: FloatArray,
        displayRotation: Int,
        declinationDeg: Float,
        outRemapped: FloatArray,
        outOrientation: FloatArray
    ): OrientationSample = computeOrientationWithAxes(
        rawMatrix = rawMatrix,
        axes = remapAxesForUprightCamera(displayRotation),
        declinationDeg = declinationDeg,
        outRemapped = outRemapped,
        outOrientation = outOrientation
    )

    private fun computeOrientationWithAxes(
        rawMatrix: FloatArray,
        axes: Pair<Int, Int>,
        declinationDeg: Float,
        outRemapped: FloatArray,
        outOrientation: FloatArray
    ): OrientationSample {
        val targetMatrix = if (SensorManager.remapCoordinateSystem(rawMatrix, axes.first, axes.second, outRemapped)) {
            outRemapped
        } else {
            rawMatrix
        }
        SensorManager.getOrientation(targetMatrix, outOrientation)
        val magneticDeg = Math.toDegrees(outOrientation[0].toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(outOrientation[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(outOrientation[2].toDouble()).toFloat()
        return OrientationSample(
            trueAzimuthDeg = normalizeAngle360(magneticDeg + declinationDeg),
            pitchDeg = pitchDeg,
            rollDeg = rollDeg
        )
    }
}

object QiblaAstronomyMath {
    private const val KAABA_LAT = 21.4225
    private const val KAABA_LON = 39.8262

    fun computeSolarPosition(
        latitude: Double,
        longitude: Double,
        timeMillis: Long = System.currentTimeMillis()
    ): SolarPosition {
        val jd = timeMillis / 86_400_000.0 + 2440587.5
        val n = jd - 2451545.0
        val gRad = Math.toRadians((357.528 + 0.9856003 * n) % 360.0)
        val lDeg = (280.460 + 0.9856474 * n) % 360.0
        val lambdaRad = Math.toRadians(lDeg + 1.915 * sin(gRad) + 0.020 * sin(2.0 * gRad))
        val epsilonRad = Math.toRadians(23.439 - 0.0000004 * n)

        val sinDec = sin(epsilonRad) * sin(lambdaRad)
        val decRad = asin(sinDec.coerceIn(-1.0, 1.0))
        val raDeg = Math.toDegrees(atan2(cos(epsilonRad) * sin(lambdaRad), cos(lambdaRad)))

        val gmstHours = ((18.697374558 + 24.06570982441908 * n) % 24.0 + 24.0) % 24.0
        val lstDeg = gmstHours * 15.0 + longitude
        val haRad = Math.toRadians(lstDeg - raDeg)
        val latRad = Math.toRadians(latitude)

        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val altDeg = Math.toDegrees(asin(sinAlt.coerceIn(-1.0, 1.0))).toFloat()

        val azRad = atan2(
            -sin(haRad),
            tan(decRad) * cos(latRad) - sin(latRad) * cos(haRad)
        )
        val azDeg = QiblaOrientationMath.normalizeAngle360(Math.toDegrees(azRad).toFloat())
        return SolarPosition(
            azimuthDeg = azDeg,
            altitudeDeg = altDeg,
            isAboveHorizon = altDeg > -1.0f
        )
    }

    fun computeGreatCirclePathToKaaba(
        startLat: Double,
        startLon: Double,
        steps: Int = 24
    ): List<GeoPoint> {
        val count = steps.coerceAtLeast(2)
        val p0 = latLonToUnitVector(startLat, startLon)
        val p1 = latLonToUnitVector(KAABA_LAT, KAABA_LON)
        val dot = (p0[0] * p1[0] + p0[1] * p1[1] + p0[2] * p1[2]).coerceIn(-1.0, 1.0)
        val omega = acos(dot)
        val sinOmega = sin(omega)

        return (0..count).map { idx ->
            val t = idx.toDouble() / count.toDouble()
            val (w0, w1) = if (sinOmega < 1e-6) {
                (1.0 - t) to t
            } else {
                (sin((1.0 - t) * omega) / sinOmega) to (sin(t * omega) / sinOmega)
            }
            val x = w0 * p0[0] + w1 * p1[0]
            val y = w0 * p0[1] + w1 * p1[1]
            val z = w0 * p0[2] + w1 * p1[2]
            val norm = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-9)
            val lat = Math.toDegrees(asin((z / norm).coerceIn(-1.0, 1.0)))
            val lon = Math.toDegrees(atan2(y, x))
            GeoPoint(lat, lon)
        }
    }

    fun projectOrthographic(
        point: GeoPoint,
        centerLat: Double,
        centerLon: Double
    ): GlobeProjectionPoint {
        val phi = Math.toRadians(point.latitude)
        val phi0 = Math.toRadians(centerLat)
        val dLon = Math.toRadians(point.longitude - centerLon)
        val cosC = sin(phi0) * sin(phi) + cos(phi0) * cos(phi) * cos(dLon)
        val x = (cos(phi) * sin(dLon)).toFloat()
        val y = (cos(phi0) * sin(phi) - sin(phi0) * cos(phi) * cos(dLon)).toFloat()
        return GlobeProjectionPoint(x = x, y = -y, isVisible = cosC >= -0.02)
    }

    private fun latLonToUnitVector(latDeg: Double, lonDeg: Double): DoubleArray {
        val lat = Math.toRadians(latDeg)
        val lon = Math.toRadians(lonDeg)
        return doubleArrayOf(
            cos(lat) * cos(lon),
            cos(lat) * sin(lon),
            sin(lat)
        )
    }
}

fun fetchActiveCoarseLocation(
    context: Context,
    onResult: (latitude: Double, longitude: Double, altitudeMeters: Float) -> Unit
): CancellationSignal? {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    fun bestLastKnown(): Location? {
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers.mapNotNull { provider ->
            try {
                if (lm.isProviderEnabled(provider) || provider == LocationManager.PASSIVE_PROVIDER) {
                    lm.getLastKnownLocation(provider)
                } else null
            } catch (_: SecurityException) {
                null
            }
        }.maxByOrNull { it.time }
    }

    bestLastKnown()?.let { loc ->
        onResult(loc.latitude, loc.longitude, loc.altitude.toFloat())
    }

    val provider = when {
        runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
            LocationManager.NETWORK_PROVIDER
        runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
            LocationManager.GPS_PROVIDER
        else -> return null
    }

    val cancellationSignal = CancellationSignal()
    try {
        LocationManagerCompat.getCurrentLocation(
            lm,
            provider,
            cancellationSignal,
            ContextCompat.getMainExecutor(context)
        ) { loc ->
            if (loc != null) {
                onResult(loc.latitude, loc.longitude, loc.altitude.toFloat())
            }
        }
    } catch (_: SecurityException) {
        return null
    }
    return cancellationSignal
}

@Composable
fun rememberQiblaSensorState(
    latitude: Double,
    longitude: Double,
    altitudeMeters: Float = 0f
): QiblaSensorStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(QiblaSensorStatus()) }

    val declinationDeg = remember(latitude, longitude, altitudeMeters) {
        runCatching {
            GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitudeMeters,
                System.currentTimeMillis()
            ).declination
        }.getOrDefault(0f)
    }

    DisposableEffect(context, declinationDeg) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (sensorManager == null || (rotationSensor == null && (accelSensor == null || magnetSensor == null))) {
            status = status.copy(
                hasHardwareCompass = false,
                sensorSourceLabel = "No magnetometer sensor on device"
            )
            onDispose { }
        } else {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val listener = object : SensorEventListener {
                val rawRotation = FloatArray(9)
                val remappedRotation = FloatArray(9)
                val orientation = FloatArray(3)
                val inclinationMatrix = FloatArray(9)
                val gravity = FloatArray(3)
                val geomagnetic = FloatArray(3)
                var hasGravity = false
                var hasMagnetic = false
                var receivedRotationVector = false
                var smoothedAzimuth = 0f
                var smoothedCameraAzimuth = 0f
                var smoothedPitch = 0f
                var smoothedRoll = 0f
                var smoothedCameraPitch = 0f
                var latestFieldUt = 0f
                var initializedSmoothing = false
                var lowAccuracy = false
                var currentAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

                @Suppress("DEPRECATION")
                private fun currentDisplayRotation(): Int {
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching { context.display.rotation }.getOrNull()
                            ?: windowManager?.defaultDisplay?.rotation
                            ?: AndroidSurface.ROTATION_0
                    } else {
                        windowManager?.defaultDisplay?.rotation ?: AndroidSurface.ROTATION_0
                    }
                }

                private fun publishReading(rawMatrix: FloatArray, sourceLabel: String) {
                    val displayRot = currentDisplayRotation()
                    val flatSample = QiblaOrientationMath.computeOrientationFromRotationMatrix(
                        rawMatrix = rawMatrix,
                        displayRotation = displayRot,
                        declinationDeg = declinationDeg,
                        outRemapped = remappedRotation,
                        outOrientation = orientation
                    )
                    val camSample = QiblaOrientationMath.computeCameraOrientationFromRotationMatrix(
                        rawMatrix = rawMatrix,
                        displayRotation = displayRot,
                        declinationDeg = declinationDeg,
                        outRemapped = remappedRotation,
                        outOrientation = orientation
                    )
                    if (!initializedSmoothing) {
                        smoothedAzimuth = flatSample.trueAzimuthDeg
                        smoothedCameraAzimuth = camSample.trueAzimuthDeg
                        smoothedPitch = flatSample.pitchDeg
                        smoothedRoll = flatSample.rollDeg
                        smoothedCameraPitch = camSample.pitchDeg
                        initializedSmoothing = true
                    } else {
                        smoothedAzimuth = QiblaOrientationMath.smoothAngleShortestArc(
                            smoothedAzimuth,
                            flatSample.trueAzimuthDeg,
                            alpha = 0.26f
                        )
                        smoothedCameraAzimuth = QiblaOrientationMath.smoothAngleShortestArc(
                            smoothedCameraAzimuth,
                            camSample.trueAzimuthDeg,
                            alpha = 0.26f
                        )
                        smoothedPitch += (flatSample.pitchDeg - smoothedPitch) * 0.24f
                        smoothedRoll += (flatSample.rollDeg - smoothedRoll) * 0.24f
                        smoothedCameraPitch += (camSample.pitchDeg - smoothedCameraPitch) * 0.24f
                    }
                    status = QiblaSensorStatus(
                        azimuthDeg = smoothedAzimuth,
                        cameraAzimuthDeg = smoothedCameraAzimuth,
                        pitchDeg = smoothedPitch,
                        rollDeg = smoothedRoll,
                        cameraPitchDeg = smoothedCameraPitch,
                        magneticFieldMicroTesla = latestFieldUt,
                        hasSensorReading = true,
                        hasHardwareCompass = true,
                        needsCalibration = lowAccuracy,
                        magneticInterferenceDetected = QiblaOrientationMath.isMagneticInterference(latestFieldUt),
                        accuracyLevel = currentAccuracy,
                        sensorSourceLabel = sourceLabel
                    )
                }

                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR,
                        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                            receivedRotationVector = true
                            SensorManager.getRotationMatrixFromVector(rawRotation, event.values)
                            publishReading(rawRotation, "Fusion Sensor • True North")
                        }
                        Sensor.TYPE_ACCELEROMETER -> {
                            val alpha = 0.80f
                            if (!hasGravity) {
                                System.arraycopy(event.values, 0, gravity, 0, 3)
                                hasGravity = true
                            } else {
                                gravity[0] = alpha * gravity[0] + (1f - alpha) * event.values[0]
                                gravity[1] = alpha * gravity[1] + (1f - alpha) * event.values[1]
                                gravity[2] = alpha * gravity[2] + (1f - alpha) * event.values[2]
                            }
                            if (!receivedRotationVector && hasMagnetic &&
                                SensorManager.getRotationMatrix(rawRotation, inclinationMatrix, gravity, geomagnetic)
                            ) {
                                publishReading(rawRotation, "Magnetic + Accel • True North")
                            }
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            latestFieldUt = QiblaOrientationMath.computeMagneticFieldMicroTesla(event.values)
                            val alpha = 0.80f
                            if (!hasMagnetic) {
                                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                                hasMagnetic = true
                            } else {
                                geomagnetic[0] = alpha * geomagnetic[0] + (1f - alpha) * event.values[0]
                                geomagnetic[1] = alpha * geomagnetic[1] + (1f - alpha) * event.values[1]
                                geomagnetic[2] = alpha * geomagnetic[2] + (1f - alpha) * event.values[2]
                            }
                            if (!receivedRotationVector && hasGravity &&
                                SensorManager.getRotationMatrix(rawRotation, inclinationMatrix, gravity, geomagnetic)
                            ) {
                                publishReading(rawRotation, "Magnetic + Accel • True North")
                            } else if (status.hasSensorReading) {
                                status = status.copy(
                                    magneticFieldMicroTesla = latestFieldUt,
                                    magneticInterferenceDetected = QiblaOrientationMath.isMagneticInterference(latestFieldUt)
                                )
                            }
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
                        sensor?.type == Sensor.TYPE_ROTATION_VECTOR ||
                        sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
                    ) {
                        currentAccuracy = accuracy
                        lowAccuracy = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
                        status = status.copy(
                            needsCalibration = lowAccuracy,
                            accuracyLevel = accuracy
                        )
                    }
                }
            }

            if (rotationSensor != null) {
                sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            }
            if (accelSensor != null) {
                sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            }
            if (magnetSensor != null) {
                sensorManager.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_GAME)
            }

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return status
}
