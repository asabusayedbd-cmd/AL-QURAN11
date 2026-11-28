package com.example.feature.ibadah

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
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
import kotlin.math.abs
import kotlin.math.sqrt

@Immutable
data class QiblaSensorStatus(
    val azimuthDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val magneticFieldMicroTesla: Float = 0f,
    val hasSensorReading: Boolean = false,
    val hasHardwareCompass: Boolean = true,
    val needsCalibration: Boolean = false,
    val magneticInterferenceDetected: Boolean = false,
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

    /**
     * Advances a cumulative (unbounded) angle smoothly along the shortest rotational arc toward [targetDeg],
     * preventing Compose Canvas rotations from spinning 359° -> 0° backwards.
     */
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

    fun computeOrientationFromRotationMatrix(
        rawMatrix: FloatArray,
        displayRotation: Int,
        declinationDeg: Float,
        outRemapped: FloatArray,
        outOrientation: FloatArray
    ): OrientationSample {
        val (axisX, axisY) = remapAxesForDisplayRotation(displayRotation)
        val targetMatrix = if (SensorManager.remapCoordinateSystem(rawMatrix, axisX, axisY, outRemapped)) {
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

@Composable
fun rememberQiblaSensorState(
    latitude: Double,
    longitude: Double
): QiblaSensorStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(QiblaSensorStatus()) }

    val declinationDeg = remember(latitude, longitude) {
        runCatching {
            GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                0f,
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
                val inclinationMatrix = FloatArray(9)
                val orientation = FloatArray(3)
                val gravity = FloatArray(3)
                val geomagnetic = FloatArray(3)
                var hasGravity = false
                var hasMagnetic = false
                var receivedRotationVector = false
                var smoothedAzimuth = 0f
                var smoothedPitch = 0f
                var smoothedRoll = 0f
                var latestFieldUt = 0f
                var initializedSmoothing = false
                var lowAccuracy = false

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
                    val sample = QiblaOrientationMath.computeOrientationFromRotationMatrix(
                        rawMatrix = rawMatrix,
                        displayRotation = currentDisplayRotation(),
                        declinationDeg = declinationDeg,
                        outRemapped = remappedRotation,
                        outOrientation = orientation
                    )
                    if (!initializedSmoothing) {
                        smoothedAzimuth = sample.trueAzimuthDeg
                        smoothedPitch = sample.pitchDeg
                        smoothedRoll = sample.rollDeg
                        initializedSmoothing = true
                    } else {
                        smoothedAzimuth = QiblaOrientationMath.smoothAngleShortestArc(
                            smoothedAzimuth,
                            sample.trueAzimuthDeg,
                            alpha = 0.24f
                        )
                        smoothedPitch += (sample.pitchDeg - smoothedPitch) * 0.22f
                        smoothedRoll += (sample.rollDeg - smoothedRoll) * 0.22f
                    }
                    status = QiblaSensorStatus(
                        azimuthDeg = smoothedAzimuth,
                        pitchDeg = smoothedPitch,
                        rollDeg = smoothedRoll,
                        magneticFieldMicroTesla = latestFieldUt,
                        hasSensorReading = true,
                        hasHardwareCompass = true,
                        needsCalibration = lowAccuracy,
                        magneticInterferenceDetected = QiblaOrientationMath.isMagneticInterference(latestFieldUt),
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
                        lowAccuracy = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
                        status = status.copy(needsCalibration = lowAccuracy)
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
