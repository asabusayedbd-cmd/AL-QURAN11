package com.example.feature.ibadah

import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.GeomagneticField
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.localization.BidiUtils
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class QiblaDisplayMode {
    COMPASS_2D,
    AR_CAMERA
}

@Composable
fun QiblaFinderOverlay(
    qiblaBearingDeg: Float,
    locationName: String,
    latitude: Double,
    longitude: Double,
    distanceKm: Int,
    uiLanguageCode: String,
    onDismiss: () -> Unit,
    onOpenCityPicker: () -> Unit,
    onUseGps: () -> Unit,
    altitudeMeters: Float = 0f
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(enabled = true) { onDismiss() }

        var displayMode by remember { mutableStateOf(QiblaDisplayMode.COMPASS_2D) }

        val sensorStatus = rememberQiblaSensorState(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters
        )

        val activeAzimuthDeg = if (displayMode == QiblaDisplayMode.AR_CAMERA) {
            sensorStatus.cameraAzimuthDeg
        } else {
            sensorStatus.azimuthDeg
        }

        val guidance = remember(qiblaBearingDeg, activeAzimuthDeg, sensorStatus.hasSensorReading) {
            QiblaOrientationMath.computeQiblaGuidance(
                qiblaBearingDeg = qiblaBearingDeg,
                azimuthDeg = activeAzimuthDeg,
                hasSensorReading = sensorStatus.hasSensorReading
            )
        }

        val haptics = LocalHapticFeedback.current
        var wasAligned by remember { mutableStateOf(false) }
        LaunchedEffect(guidance.isAligned) {
            if (guidance.isAligned && !wasAligned) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            wasAligned = guidance.isAligned
        }

        var clockMinuteTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000L)
                clockMinuteTickMs = System.currentTimeMillis()
            }
        }
        val solarPosition = remember(latitude, longitude, clockMinuteTickMs) {
            QiblaAstronomyMath.computeSolarPosition(latitude, longitude, clockMinuteTickMs)
        }

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

        val goldAccent = Color(0xFFF3CF82)
        val alignedEmerald = Color(0xFF28E094)
        val hudGreen by animateColorAsState(
            targetValue = if (guidance.isAligned) alignedEmerald else goldAccent,
            animationSpec = tween(220),
            label = "qiblaAccent"
        )

        var continuousDialDeg by remember { mutableFloatStateOf(0f) }
        var continuousNeedleDeg by remember { mutableFloatStateOf(guidance.relativeAngleDeg) }
        val targetDialDeg = if (sensorStatus.hasSensorReading) -sensorStatus.azimuthDeg else 0f
        LaunchedEffect(targetDialDeg) {
            continuousDialDeg = QiblaOrientationMath.advanceContinuousAngle(continuousDialDeg, targetDialDeg)
        }
        LaunchedEffect(guidance.relativeAngleDeg) {
            continuousNeedleDeg = QiblaOrientationMath.advanceContinuousAngle(continuousNeedleDeg, guidance.relativeAngleDeg)
        }

        val animatedDialDeg by animateFloatAsState(
            targetValue = continuousDialDeg,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
            label = "dialRot"
        )
        val animatedNeedleDeg by animateFloatAsState(
            targetValue = continuousNeedleDeg,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
            label = "needleRot"
        )

        val isLevel = abs(sensorStatus.pitchDeg) <= 18f && abs(sensorStatus.rollDeg) <= 18f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (guidance.isAligned) {
                            listOf(Color(0xFF052E24), Color(0xFF084233), Color(0xFF031B15))
                        } else {
                            listOf(Color(0xFF051F1A), Color(0xFF093129), Color(0xFF031512))
                        }
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("qibla_compass_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top HUD Overlay Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onOpenCityPicker()
                        },
                        shape = RoundedCornerShape(50),
                        color = Color(0x2EFFFFFF),
                        border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("qibla_change_city_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = goldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = locationName,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 190.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = onUseGps,
                            shape = CircleShape,
                            color = Color(0x26FFFFFF),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("qibla_gps_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = stringResource(R.string.gps_button),
                                    tint = goldAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("qibla_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = Color.White
                            )
                        }
                    }
                }

                // Segmented Mode Switcher (2D Compass <-> Live AR Camera)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0x22FFFFFF),
                    border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModeTogglePill(
                            label = stringResource(R.string.qibla_mode_compass),
                            icon = Icons.Default.Explore,
                            selected = displayMode == QiblaDisplayMode.COMPASS_2D,
                            accent = goldAccent,
                            onClick = { displayMode = QiblaDisplayMode.COMPASS_2D },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("qibla_mode_compass")
                        )
                        ModeTogglePill(
                            label = stringResource(R.string.qibla_mode_ar),
                            icon = Icons.Default.CameraAlt,
                            selected = displayMode == QiblaDisplayMode.AR_CAMERA,
                            accent = goldAccent,
                            onClick = { displayMode = QiblaDisplayMode.AR_CAMERA },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("qibla_mode_ar")
                        )
                    }
                }

                // Title & Turn Guidance HUD
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.qibla_compass_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    val cardinal = QiblaOrientationMath.compassCardinalDirection(qiblaBearingDeg)
                    Text(
                        text = stringResource(
                            R.string.qibla_bearing_fmt,
                            "$locationName ($cardinal)",
                            BidiUtils.formatNumber(qiblaBearingDeg.roundToInt(), uiLanguageCode)
                        ) + " • ${BidiUtils.formatNumber(distanceKm, uiLanguageCode)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFDCECE7),
                        textAlign = TextAlign.Center
                    )

                    val guidanceLabel = when {
                        !sensorStatus.hasHardwareCompass -> stringResource(R.string.qibla_fixed_bearing)
                        guidance.isAligned -> stringResource(R.string.qibla_aligned_status)
                        !sensorStatus.hasSensorReading -> stringResource(R.string.qibla_hold_flat)
                        guidance.signedTurnDeg > 0f -> stringResource(
                            R.string.qibla_turn_right_fmt,
                            abs(guidance.signedTurnDeg).roundToInt()
                        )
                        else -> stringResource(
                            R.string.qibla_turn_left_fmt,
                            abs(guidance.signedTurnDeg).roundToInt()
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (guidance.isAligned) alignedEmerald.copy(alpha = 0.22f) else Color(0x2BFFFFFF),
                        border = BorderStroke(
                            width = if (guidance.isAligned) 1.5.dp else 1.dp,
                            color = hudGreen.copy(alpha = if (guidance.isAligned) 0.95f else 0.45f)
                        ),
                        modifier = Modifier.testTag("qibla_alignment_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = hudGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = guidanceLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (guidance.isAligned) alignedEmerald else Color.White
                            )
                        }
                    }
                }

                // Active Mode Visualizer: Precision 2D Compass OR Live CameraX AR View
                AnimatedContent(
                    targetState = displayMode,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(180)) },
                    label = "qiblaModeContent"
                ) { mode ->
                    if (mode == QiblaDisplayMode.COMPASS_2D) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            QiblaCompassCanvasOverlay(
                                dialRotationDeg = animatedDialDeg,
                                needleRotationDeg = animatedNeedleDeg,
                                isAligned = guidance.isAligned,
                                pitchDeg = sensorStatus.pitchDeg,
                                rollDeg = sensorStatus.rollDeg,
                                isLevel = isLevel,
                                hasSensorReading = sensorStatus.hasSensorReading,
                                solarPosition = solarPosition,
                                accentColor = hudGreen,
                                goldAccent = goldAccent,
                                modifier = Modifier
                                    .size(308.dp)
                                    .padding(vertical = 4.dp)
                                    .testTag("qibla_compass_canvas")
                            )
                        }
                    } else {
                        QiblaArCameraOverlay(
                            qiblaBearingDeg = qiblaBearingDeg,
                            sensorStatus = sensorStatus,
                            solarPosition = solarPosition,
                            distanceKm = distanceKm,
                            uiLanguageCode = uiLanguageCode,
                            accentColor = hudGreen,
                            goldAccent = goldAccent
                        )
                    }
                }

                // Telemetry, Solar Cross-Check & Geodesic Globe Arc
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryChip(
                            label = stringResource(R.string.qibla_metric_heading),
                            value = if (sensorStatus.hasSensorReading) {
                                "${activeAzimuthDeg.roundToInt()}° ${QiblaOrientationMath.compassCardinalDirection(activeAzimuthDeg)}"
                            } else {
                                "--"
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryChip(
                            label = stringResource(R.string.qibla_metric_field),
                            value = if (sensorStatus.magneticFieldMicroTesla > 0f) {
                                "${sensorStatus.magneticFieldMicroTesla.roundToInt()} µT"
                            } else {
                                "--"
                            },
                            highlightWarning = sensorStatus.magneticInterferenceDetected,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryChip(
                            label = stringResource(R.string.qibla_metric_declination),
                            value = "${if (declinationDeg >= 0f) "+" else ""}${declinationDeg.roundToInt()}°",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Solar Cross-Check & Geodesic Globe Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x1EFFFFFF),
                        border = BorderStroke(0.8.dp, goldAccent.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QiblaGreatCircleGlobeCanvas(
                                latitude = latitude,
                                longitude = longitude,
                                goldAccent = goldAccent,
                                accentColor = hudGreen,
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("qibla_globe_canvas")
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = goldAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.qibla_globe_arc_label),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.testTag("qibla_sun_check_chip")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = null,
                                        tint = if (solarPosition.isAboveHorizon) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (solarPosition.isAboveHorizon) {
                                            stringResource(
                                                R.string.qibla_sun_check_fmt,
                                                solarPosition.azimuthDeg.roundToInt(),
                                                QiblaOrientationMath.compassCardinalDirection(solarPosition.azimuthDeg)
                                            )
                                        } else {
                                            stringResource(R.string.qibla_sun_below_horizon)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (solarPosition.isAboveHorizon) Color(0xFFFEF3C7) else Color(0xFFA7C7BF)
                                    )
                                }
                            }
                        }
                    }

                    if (sensorStatus.needsCalibration || sensorStatus.magneticInterferenceDetected) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x3DF59E0B),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.65f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnimatedFigureEightBadge(modifier = Modifier.size(44.dp, 26.dp))
                                Text(
                                    text = stringResource(R.string.qibla_calibrate_hint),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFEF3C7)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (sensorStatus.hasHardwareCompass) {
                                sensorStatus.sensorSourceLabel
                            } else {
                                stringResource(R.string.qibla_no_sensor_hint, qiblaBearingDeg.roundToInt())
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA7C7BF),
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onDismiss()
                                onOpenCityPicker()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.change_city), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = goldAccent,
                                contentColor = Color(0xFF06251F)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTogglePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) accent else Color.Transparent,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color(0xFF06251F) else Color(0xFFDCECE7),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFF06251F) else Color(0xFFDCECE7),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AnimatedFigureEightBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fig8")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fig8Phase"
    )
    val trackPath = remember { Path() }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width * 0.42f
        val ry = size.height * 0.36f
        trackPath.reset()
        val steps = 36
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (2.0 * Math.PI).toFloat()
            val x = cx + rx * sin(t)
            val y = cy + ry * sin(2f * t)
            if (i == 0) trackPath.moveTo(x, y) else trackPath.lineTo(x, y)
        }
        trackPath.close()
        drawPath(
            path = trackPath,
            color = Color(0xFFFCD34D).copy(alpha = 0.45f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        val dotX = cx + rx * sin(phase)
        val dotY = cy + ry * sin(2f * phase)
        drawCircle(
            color = Color(0xFFFDE68A),
            radius = 3.5.dp.toPx(),
            center = Offset(dotX, dotY)
        )
    }
}

@Composable
private fun QiblaGreatCircleGlobeCanvas(
    latitude: Double,
    longitude: Double,
    goldAccent: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val pathPoints = remember(latitude, longitude) {
        QiblaAstronomyMath.computeGreatCirclePathToKaaba(latitude, longitude, steps = 20)
    }
    val centerPoint = remember(pathPoints) {
        pathPoints[pathPoints.size / 2]
    }
    val projectedPoints = remember(pathPoints, centerPoint) {
        pathPoints.map { QiblaAstronomyMath.projectOrthographic(it, centerPoint.latitude, centerPoint.longitude) }
    }
    val arcPath = remember { Path() }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f - 3.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0E4D3E), Color(0xFF06251F), Color(0xFF031512)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = goldAccent.copy(alpha = 0.45f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        // Subtle equator & meridian crosshairs
        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 0.8.dp.toPx()
        )

        arcPath.reset()
        var started = false
        projectedPoints.forEach { pt ->
            val sx = center.x + pt.x * radius * 0.92f
            val sy = center.y + pt.y * radius * 0.92f
            if (pt.isVisible) {
                if (!started) {
                    arcPath.moveTo(sx, sy)
                    started = true
                } else {
                    arcPath.lineTo(sx, sy)
                }
            }
        }
        if (started) {
            drawPath(
                path = arcPath,
                color = goldAccent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        projectedPoints.firstOrNull()?.takeIf { it.isVisible }?.let { origin ->
            drawCircle(
                color = accentColor,
                radius = 3.2.dp.toPx(),
                center = Offset(center.x + origin.x * radius * 0.92f, center.y + origin.y * radius * 0.92f)
            )
        }
        projectedPoints.lastOrNull()?.takeIf { it.isVisible }?.let { kaaba ->
            val kx = center.x + kaaba.x * radius * 0.92f
            val ky = center.y + kaaba.y * radius * 0.92f
            drawCircle(
                color = goldAccent.copy(alpha = 0.35f),
                radius = 6.dp.toPx(),
                center = Offset(kx, ky)
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(kx, ky)
            )
        }
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlightWarning: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlightWarning) Color(0x38F59E0B) else Color(0x24FFFFFF),
        border = BorderStroke(
            0.8.dp,
            if (highlightWarning) Color(0xFFF59E0B).copy(alpha = 0.7f) else Color(0x30FFFFFF)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFBCE0D7),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (highlightWarning) Color(0xFFFCD34D) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QiblaCompassCanvasOverlay(
    dialRotationDeg: Float,
    needleRotationDeg: Float,
    isAligned: Boolean,
    pitchDeg: Float,
    rollDeg: Float,
    isLevel: Boolean,
    hasSensorReading: Boolean,
    accentColor: Color,
    goldAccent: Color,
    modifier: Modifier = Modifier,
    solarPosition: SolarPosition? = null
) {
    val cardinalPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.WHITE
        }
    }
    val northPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.rgb(255, 107, 107)
        }
    }
    val degreePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            color = android.graphics.Color.argb(185, 220, 236, 231)
        }
    }
    val reticlePath = remember { Path() }
    val needlePath = remember { Path() }

    Canvas(modifier = modifier) {
        val outerRadius = size.minDimension / 2f - 10.dp.toPx()
        val dialRadius = outerRadius * 0.90f

        cardinalPaint.textSize = 15.sp.toPx()
        northPaint.textSize = 16.sp.toPx()
        degreePaint.textSize = 10.sp.toPx()

        // Outer alignment glow & dial face
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isAligned) accentColor.copy(alpha = 0.24f) else goldAccent.copy(alpha = 0.12f),
                    Color(0xAA05201A),
                    Color(0xE6041713)
                ),
                center = center,
                radius = outerRadius
            ),
            radius = outerRadius,
            center = center
        )

        drawCircle(
            color = if (isAligned) accentColor else goldAccent.copy(alpha = 0.45f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = if (isAligned) 3.5.dp.toPx() else 1.8.dp.toPx())
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = dialRadius * 0.60f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // Fixed 12-o'clock device heading reticle
        val tipY = center.y - outerRadius + 2.dp.toPx()
        val baseY = center.y - outerRadius - 8.dp.toPx()
        val halfW = 6.dp.toPx()
        reticlePath.reset()
        reticlePath.moveTo(center.x, tipY)
        reticlePath.lineTo(center.x - halfW, baseY)
        reticlePath.lineTo(center.x + halfW, baseY)
        reticlePath.close()
        drawPath(path = reticlePath, color = if (isAligned) accentColor else goldAccent)

        // Rotating Compass Rose, Degree Ring & Solar Azimuth Cross-Check Marker
        rotate(degrees = dialRotationDeg) {
            val starSide = dialRadius * 0.56f
            val halfSide = starSide / 2f
            drawRect(
                color = goldAccent.copy(alpha = 0.10f),
                topLeft = Offset(center.x - halfSide, center.y - halfSide),
                size = Size(starSide, starSide),
                style = Stroke(width = 1.2.dp.toPx())
            )
            rotate(degrees = 45f) {
                drawRect(
                    color = goldAccent.copy(alpha = 0.10f),
                    topLeft = Offset(center.x - halfSide, center.y - halfSide),
                    size = Size(starSide, starSide),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            if (solarPosition != null && solarPosition.isAboveHorizon) {
                val sunRad = Math.toRadians(solarPosition.azimuthDeg.toDouble())
                val sunOuterR = dialRadius * 0.91f
                val sunPos = Offset(
                    x = center.x + (sunOuterR * sin(sunRad)).toFloat(),
                    y = center.y - (sunOuterR * cos(sunRad)).toFloat()
                )
                drawLine(
                    color = Color(0x66FBBF24),
                    start = center,
                    end = sunPos,
                    strokeWidth = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
                drawCircle(
                    color = Color(0x4DFBBF24),
                    radius = 10.dp.toPx(),
                    center = sunPos
                )
                drawCircle(
                    color = Color(0xFFFBBF24),
                    radius = 5.dp.toPx(),
                    center = sunPos
                )
            }

            for (deg in 0 until 360 step 5) {
                val rad = Math.toRadians(deg.toDouble())
                val isCardinal = deg % 90 == 0
                val isMajor30 = deg % 30 == 0
                val isMedium15 = deg % 15 == 0
                val innerR = when {
                    isCardinal -> dialRadius * 0.80f
                    isMajor30 -> dialRadius * 0.84f
                    isMedium15 -> dialRadius * 0.88f
                    else -> dialRadius * 0.92f
                }
                val tickColor = when {
                    deg == 0 -> Color(0xFFFF6B6B)
                    isCardinal -> goldAccent.copy(alpha = 0.9f)
                    isMajor30 -> Color.White.copy(alpha = 0.72f)
                    isMedium15 -> Color.White.copy(alpha = 0.42f)
                    else -> Color.White.copy(alpha = 0.22f)
                }
                drawLine(
                    color = tickColor,
                    start = Offset(
                        x = center.x + (innerR * sin(rad)).toFloat(),
                        y = center.y - (innerR * cos(rad)).toFloat()
                    ),
                    end = Offset(
                        x = center.x + (dialRadius * sin(rad)).toFloat(),
                        y = center.y - (dialRadius * cos(rad)).toFloat()
                    ),
                    strokeWidth = when {
                        isCardinal -> 2.6.dp.toPx()
                        isMajor30 -> 1.8.dp.toPx()
                        else -> 1.dp.toPx()
                    },
                    cap = StrokeCap.Round
                )

                if (isMajor30 && !isCardinal) {
                    val labelR = dialRadius * 0.72f
                    val lx = center.x + (labelR * sin(rad)).toFloat()
                    val ly = center.y - (labelR * cos(rad)).toFloat() + 3.5.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(deg.toString(), lx, ly, degreePaint)
                }
            }

            val cardinalR = dialRadius * 0.67f
            drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - cardinalR + 5.dp.toPx(), northPaint)
            drawContext.canvas.nativeCanvas.drawText("S", center.x, center.y + cardinalR + 5.dp.toPx(), cardinalPaint)
            drawContext.canvas.nativeCanvas.drawText("E", center.x + cardinalR, center.y + 5.dp.toPx(), cardinalPaint)
            drawContext.canvas.nativeCanvas.drawText("W", center.x - cardinalR, center.y + 5.dp.toPx(), cardinalPaint)
        }

        // Qibla Bearing Light Cone + Tapered Pointer + Kaaba Emblem
        rotate(degrees = needleRotationDeg) {
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isAligned) 0.34f else 0.20f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dialRadius
                ),
                startAngle = -102f,
                sweepAngle = 24f,
                useCenter = true,
                topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
                size = Size(dialRadius * 2f, dialRadius * 2f)
            )

            val needleTipY = center.y - dialRadius * 0.72f
            val needleBaseY = center.y + dialRadius * 0.14f
            val needleHalfW = 5.dp.toPx()
            needlePath.reset()
            needlePath.moveTo(center.x, needleTipY)
            needlePath.lineTo(center.x - needleHalfW, center.y)
            needlePath.lineTo(center.x, needleBaseY)
            needlePath.lineTo(center.x + needleHalfW, center.y)
            needlePath.close()
            drawPath(path = needlePath, color = accentColor)

            val kaabaCenterY = center.y - dialRadius * 0.84f
            val kaabaSize = 22.dp.toPx()
            drawCircle(
                color = if (isAligned) accentColor.copy(alpha = 0.35f) else goldAccent.copy(alpha = 0.22f),
                radius = 18.dp.toPx(),
                center = Offset(center.x, kaabaCenterY)
            )
            drawRoundRect(
                color = Color(0xFF101314),
                topLeft = Offset(center.x - kaabaSize / 2f, kaabaCenterY - kaabaSize / 2f),
                size = Size(kaabaSize, kaabaSize),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawRoundRect(
                color = goldAccent,
                topLeft = Offset(center.x - kaabaSize / 2f, kaabaCenterY - kaabaSize / 2f),
                size = Size(kaabaSize, kaabaSize),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawLine(
                color = goldAccent,
                start = Offset(center.x - kaabaSize / 2f + 2.dp.toPx(), kaabaCenterY - 2.dp.toPx()),
                end = Offset(center.x + kaabaSize / 2f - 2.dp.toPx(), kaabaCenterY - 2.dp.toPx()),
                strokeWidth = 2.4.dp.toPx()
            )
        }

        // Center Spirit-Level Tilt Reticle
        val levelRingR = 22.dp.toPx()
        drawCircle(
            color = Color(0xCC06241E),
            radius = levelRingR,
            center = center
        )
        drawCircle(
            color = if (isLevel) Color(0xFF28E094).copy(alpha = 0.7f) else Color(0xFFF59E0B).copy(alpha = 0.7f),
            radius = levelRingR,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
        if (hasSensorReading) {
            val maxTiltShift = levelRingR - 6.dp.toPx()
            val bubbleDx = ((rollDeg / 45f).coerceIn(-1f, 1f)) * maxTiltShift
            val bubbleDy = ((pitchDeg / 45f).coerceIn(-1f, 1f)) * maxTiltShift
            drawCircle(
                color = if (isLevel) Color(0xFF28E094) else Color(0xFFFBBF24),
                radius = 5.dp.toPx(),
                center = Offset(center.x + bubbleDx, center.y + bubbleDy)
            )
        }
        drawCircle(color = goldAccent, radius = 2.5.dp.toPx(), center = center)
    }
}
