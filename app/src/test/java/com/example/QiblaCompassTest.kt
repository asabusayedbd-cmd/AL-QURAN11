package com.example

import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.feature.ibadah.GeoPoint
import com.example.feature.ibadah.QiblaAstronomyMath
import com.example.feature.ibadah.QiblaFinderOverlay
import com.example.feature.ibadah.QiblaOrientationMath
import com.example.ui.theme.NoorAlQuranTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QiblaCompassTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testShortestSignedDeltaAndContinuousAngleWrap() {
        assertEquals(2f, QiblaOrientationMath.shortestSignedDelta(359f, 1f), 0.01f)
        assertEquals(-2f, QiblaOrientationMath.shortestSignedDelta(1f, 359f), 0.01f)

        val steppedForward = QiblaOrientationMath.advanceContinuousAngle(358f, 3f)
        assertEquals(363f, steppedForward, 0.01f)

        val steppedBackward = QiblaOrientationMath.advanceContinuousAngle(2f, 357f)
        assertEquals(-3f, steppedBackward, 0.01f)

        val smoothed = QiblaOrientationMath.smoothAngleShortestArc(358f, 2f, alpha = 0.5f)
        assertEquals(0f, smoothed, 0.01f)
    }

    @Test
    fun testComputeQiblaGuidanceAndCardinals() {
        assertEquals("N", QiblaOrientationMath.compassCardinalDirection(0f))
        assertEquals("E", QiblaOrientationMath.compassCardinalDirection(90f))
        assertEquals("S", QiblaOrientationMath.compassCardinalDirection(180f))
        assertEquals("W", QiblaOrientationMath.compassCardinalDirection(270f))
        assertEquals("W", QiblaOrientationMath.compassCardinalDirection(277f))

        val aligned = QiblaOrientationMath.computeQiblaGuidance(
            qiblaBearingDeg = 277f,
            azimuthDeg = 274f,
            hasSensorReading = true
        )
        assertTrue(aligned.isAligned)
        assertEquals(3f, aligned.signedTurnDeg, 0.01f)

        val turnRight = QiblaOrientationMath.computeQiblaGuidance(
            qiblaBearingDeg = 277f,
            azimuthDeg = 240f,
            hasSensorReading = true
        )
        assertFalse(turnRight.isAligned)
        assertEquals(37f, turnRight.signedTurnDeg, 0.01f)

        val turnLeft = QiblaOrientationMath.computeQiblaGuidance(
            qiblaBearingDeg = 10f,
            azimuthDeg = 50f,
            hasSensorReading = true
        )
        assertFalse(turnLeft.isAligned)
        assertEquals(-40f, turnLeft.signedTurnDeg, 0.01f)

        val noSensor = QiblaOrientationMath.computeQiblaGuidance(
            qiblaBearingDeg = 277f,
            azimuthDeg = 120f,
            hasSensorReading = false
        )
        assertFalse(noSensor.isAligned)
        assertEquals(277f, noSensor.relativeAngleDeg, 0.01f)
    }

    @Test
    fun testMagneticFieldStrengthAndInterferenceDetection() {
        val normalField = QiblaOrientationMath.computeMagneticFieldMicroTesla(floatArrayOf(20f, 30f, 25f))
        assertEquals(43.87f, normalField, 0.1f)
        assertFalse(QiblaOrientationMath.isMagneticInterference(normalField))

        assertTrue(QiblaOrientationMath.isMagneticInterference(10f))
        assertTrue(QiblaOrientationMath.isMagneticInterference(95f))
        assertFalse(QiblaOrientationMath.isMagneticInterference(0f))
    }

    @Test
    fun testDisplayRotationAxisRemappingAndOrientation() {
        assertEquals(
            SensorManager.AXIS_X to SensorManager.AXIS_Y,
            QiblaOrientationMath.remapAxesForDisplayRotation(Surface.ROTATION_0)
        )
        assertEquals(
            SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X,
            QiblaOrientationMath.remapAxesForDisplayRotation(Surface.ROTATION_90)
        )
        assertEquals(
            SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y,
            QiblaOrientationMath.remapAxesForDisplayRotation(Surface.ROTATION_180)
        )
        assertEquals(
            SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X,
            QiblaOrientationMath.remapAxesForDisplayRotation(Surface.ROTATION_270)
        )

        val identity = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )
        val outRemapped = FloatArray(9)
        val outOrientation = FloatArray(3)

        val portrait = QiblaOrientationMath.computeOrientationFromRotationMatrix(
            rawMatrix = identity,
            displayRotation = Surface.ROTATION_0,
            declinationDeg = 5f,
            outRemapped = outRemapped,
            outOrientation = outOrientation
        )
        assertEquals(5f, portrait.trueAzimuthDeg, 0.1f)

        val landscape90 = QiblaOrientationMath.computeOrientationFromRotationMatrix(
            rawMatrix = identity,
            displayRotation = Surface.ROTATION_90,
            declinationDeg = 0f,
            outRemapped = outRemapped,
            outOrientation = outOrientation
        )
        assertEquals(90f, landscape90.trueAzimuthDeg, 0.5f)
    }

    @Test
    fun testUprightArCameraOrientationWithoutGimbalFlip() {
        assertEquals(
            SensorManager.AXIS_X to SensorManager.AXIS_Z,
            QiblaOrientationMath.remapAxesForUprightCamera(Surface.ROTATION_0)
        )
        assertEquals(
            SensorManager.AXIS_Z to SensorManager.AXIS_MINUS_X,
            QiblaOrientationMath.remapAxesForUprightCamera(Surface.ROTATION_90)
        )
        assertEquals(
            SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z,
            QiblaOrientationMath.remapAxesForUprightCamera(Surface.ROTATION_180)
        )
        assertEquals(
            SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X,
            QiblaOrientationMath.remapAxesForUprightCamera(Surface.ROTATION_270)
        )

        // Device held vertically facing North: device +X points East (+X_world), +Y points Up (+Z_world),
        // rear camera -Z points North (+Y_world) -> device +Z points South (-Y_world).
        val facingNorthUpright = floatArrayOf(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f
        )
        val outRemapped = FloatArray(9)
        val outOrientation = FloatArray(3)
        val camNorth = QiblaOrientationMath.computeCameraOrientationFromRotationMatrix(
            rawMatrix = facingNorthUpright,
            displayRotation = Surface.ROTATION_0,
            declinationDeg = 0f,
            outRemapped = outRemapped,
            outOrientation = outOrientation
        )
        assertEquals(0f, camNorth.trueAzimuthDeg, 1.0f)
        assertEquals(0f, camNorth.pitchDeg, 1.0f)

        // Device held vertically facing East: rear camera -Z points East (+X_world) -> +Z = (-1,0,0),
        // +X = South (0,-1,0), +Y = Up (0,0,1).
        val facingEastUpright = floatArrayOf(
            0f, 0f, -1f,
            -1f, 0f, 0f,
            0f, 1f, 0f
        )
        val camEast = QiblaOrientationMath.computeCameraOrientationFromRotationMatrix(
            rawMatrix = facingEastUpright,
            displayRotation = Surface.ROTATION_0,
            declinationDeg = 0f,
            outRemapped = outRemapped,
            outOrientation = outOrientation
        )
        assertEquals(90f, camEast.trueAzimuthDeg, 1.0f)
        assertEquals(0f, camEast.pitchDeg, 1.0f)

        // Landscape ROTATION_90 held vertically facing North: +X_dev = Up (0,0,1), +Y_dev = West (-1,0,0),
        // +Z_dev = South (0,-1,0).
        val facingNorthLandscape90 = floatArrayOf(
            0f, -1f, 0f,
            0f, 0f, -1f,
            1f, 0f, 0f
        )
        val camLandscapeNorth = QiblaOrientationMath.computeCameraOrientationFromRotationMatrix(
            rawMatrix = facingNorthLandscape90,
            displayRotation = Surface.ROTATION_90,
            declinationDeg = 0f,
            outRemapped = outRemapped,
            outOrientation = outOrientation
        )
        assertEquals(0f, camLandscapeNorth.trueAzimuthDeg, 1.0f)
        assertEquals(0f, camLandscapeNorth.pitchDeg, 1.0f)
    }

    @Test
    fun testSolarPositionAndGreatCircleGlobeProjection() {
        // UTC noon over Makkah (21.42N, 39.82E) -> Sun should be above horizon and roughly southerly/zenith
        val solar = QiblaAstronomyMath.computeSolarPosition(
            latitude = 23.8103,
            longitude = 90.4125,
            timeMillis = 1_710_050_400_000L // 2024-03-10 ~06:00 UTC (~12:00 PM Dhaka)
        )
        assertTrue(solar.azimuthDeg in 0f..360f)
        assertTrue(solar.isAboveHorizon)

        val arc = QiblaAstronomyMath.computeGreatCirclePathToKaaba(23.8103, 90.4125, steps = 16)
        assertEquals(17, arc.size)
        assertEquals(23.8103, arc.first().latitude, 0.01)
        assertEquals(90.4125, arc.first().longitude, 0.01)
        assertEquals(21.4225, arc.last().latitude, 0.01)
        assertEquals(39.8262, arc.last().longitude, 0.01)

        val center = arc[arc.size / 2]
        val proj = QiblaAstronomyMath.projectOrthographic(
            point = GeoPoint(21.4225, 39.8262),
            centerLat = center.latitude,
            centerLon = center.longitude
        )
        assertTrue(proj.isVisible)
        assertTrue(proj.x in -1f..1f && proj.y in -1f..1f)
    }

    @Test
    fun testQiblaFinderOverlayRendersArModeToggleAndDismisses() {
        var dismissed = false
        composeRule.setContent {
            NoorAlQuranTheme {
                QiblaFinderOverlay(
                    qiblaBearingDeg = 277.4f,
                    locationName = "Dhaka, Bangladesh",
                    latitude = 23.8103,
                    longitude = 90.4125,
                    distanceKm = 5180,
                    uiLanguageCode = "en",
                    altitudeMeters = 12f,
                    onDismiss = { dismissed = true },
                    onOpenCityPicker = {},
                    onUseGps = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("qibla_compass_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_mode_compass").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_mode_ar").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_compass_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_alignment_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_globe_canvas").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_sun_check_chip").performScrollTo().assertIsDisplayed()

        // Switch to AR Camera mode (permission dialog/prompt renders smoothly when CAMERA is ungranted)
        composeRule.onNodeWithTag("qibla_mode_ar").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("qibla_ar_camera_container").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_grant_camera_button").assertIsDisplayed()

        composeRule.onNodeWithTag("qibla_change_city_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_close_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }
}
