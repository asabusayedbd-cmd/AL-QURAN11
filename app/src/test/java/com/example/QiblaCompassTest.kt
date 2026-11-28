package com.example

import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    fun testQiblaFinderOverlayRendersAndDismisses() {
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
                    onDismiss = { dismissed = true },
                    onOpenCityPicker = {},
                    onUseGps = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("qibla_compass_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_compass_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_alignment_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_change_city_button").assertIsDisplayed()
        composeRule.onNodeWithTag("qibla_close_button").assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }
}
