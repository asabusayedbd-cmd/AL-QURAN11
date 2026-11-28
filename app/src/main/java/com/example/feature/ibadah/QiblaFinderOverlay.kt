package com.example.feature.ibadah

import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.GeomagneticField
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    onUseGps: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(enabled = true) { onDismiss() }

        val sensorStatus = rememberQiblaSensorState(latitude = latitude, longitude = longitude)
        val guidance = remember(qiblaBearingDeg, sensorStatus.azimuthDeg, sensorStatus.hasSensorReading) {
            QiblaOrientationMath.computeQiblaGuidance(
                qiblaBearingDeg = qiblaBearingDeg,
                azimuthDeg = sensorStatus.azimuthDeg,
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
                verticalArrangement = Arrangement.SpaceBetween
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
                                modifier = Modifier.widthIn(max = 200.dp)
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
                            modifier = Modifier.size(48.dp)
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

                Spacer(modifier = Modifier.height(8.dp))

                // Title & Turn Guidance HUD
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

                // Precision Compass UI Overlay Canvas
                QiblaCompassCanvasOverlay(
                    dialRotationDeg = animatedDialDeg,
                    needleRotationDeg = animatedNeedleDeg,
                    isAligned = guidance.isAligned,
                    pitchDeg = sensorStatus.pitchDeg,
                    rollDeg = sensorStatus.rollDeg,
                    isLevel = isLevel,
                    hasSensorReading = sensorStatus.hasSensorReading,
                    accentColor = hudGreen,
                    goldAccent = goldAccent,
                    modifier = Modifier
                        .size(308.dp)
                        .padding(vertical = 8.dp)
                        .testTag("qibla_compass_canvas")
                )

                // Telemetry & Calibration Footer
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
                                "${sensorStatus.azimuthDeg.roundToInt()}° ${QiblaOrientationMath.compassCardinalDirection(sensorStatus.azimuthDeg)}"
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFFCD34D),
                                    modifier = Modifier.size(20.dp)
                                )
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
    modifier: Modifier = Modifier
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
    val kaabaLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.rgb(243, 207, 130)
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
        kaabaLabelPaint.textSize = 10.sp.toPx()

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

        // Rotating Compass Rose & Degree Ring
        rotate(degrees = dialRotationDeg) {
            // Subtle Rub el Hizb 8-point star watermark
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

            // 72 precision ticks (every 5°)
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
            // Directional sector beam
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

            // Tapered Qibla Needle
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

            // Kaaba Symbol Badge at tip
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
            // Golden Kiswah horizontal band
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
