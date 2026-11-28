package com.example.feature.ibadah

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.core.localization.BidiUtils
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun QiblaArCameraOverlay(
    qiblaBearingDeg: Float,
    sensorStatus: QiblaSensorStatus,
    solarPosition: SolarPosition,
    distanceKm: Int,
    uiLanguageCode: String,
    accentColor: Color,
    goldAccent: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraBindError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val deltaYaw = remember(qiblaBearingDeg, sensorStatus.cameraAzimuthDeg, sensorStatus.hasSensorReading) {
        if (sensorStatus.hasSensorReading) {
            QiblaOrientationMath.shortestSignedDelta(sensorStatus.cameraAzimuthDeg, qiblaBearingDeg)
        } else {
            0f
        }
    }
    val smoothDeltaYaw by animateFloatAsState(
        targetValue = deltaYaw,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "arYaw"
    )
    val smoothPitch by animateFloatAsState(
        targetValue = sensorStatus.cameraPitchDeg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "arPitch"
    )
    val isArAligned = sensorStatus.hasSensorReading && abs(deltaYaw) <= 4.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF041914))
            .testTag("qibla_ar_camera_container"),
        contentAlignment = Alignment.Center
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = goldAccent.copy(alpha = 0.16f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = goldAccent,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.qibla_camera_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.qibla_camera_permission_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCDE5DF),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = goldAccent,
                        contentColor = Color(0xFF06251F)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("qibla_grant_camera_button")
                ) {
                    Text(
                        text = stringResource(R.string.qibla_camera_grant_btn),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            val previewView = remember(context) {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            DisposableEffect(lifecycleOwner, previewView) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val executor = ContextCompat.getMainExecutor(context)
                var boundProvider: ProcessCameraProvider? = null
                val listener = Runnable {
                    runCatching {
                        val provider = cameraProviderFuture.get()
                        boundProvider = provider
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview
                        )
                    }.onFailure { err ->
                        cameraBindError = err.localizedMessage ?: "Camera unavailable"
                    }
                }
                cameraProviderFuture.addListener(listener, executor)
                onDispose {
                    runCatching { boundProvider?.unbindAll() }
                }
            }

            if (cameraBindError == null) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Translucent emerald vignette for high HUD legibility over live video
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x88031814),
                                Color(0x28031814),
                                Color(0x99031814)
                            )
                        )
                    )
            )

            // AR Reticle + Floating Kaaba Beacon + Sun Overlay Canvas
            val halfFovDeg = 30f
            Canvas(modifier = Modifier.fillMaxSize()) {
                val horizonY = (size.height / 2f + (smoothPitch / 45f) * (size.height * 0.25f))
                    .coerceIn(size.height * 0.22f, size.height * 0.78f)

                // Subtle artificial horizon line
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(24.dp.toPx(), horizonY),
                    end = Offset(size.width - 24.dp.toPx(), horizonY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )

                // Center aiming crosshair
                val reticleColor = if (isArAligned) accentColor else Color.White.copy(alpha = 0.72f)
                drawCircle(
                    color = reticleColor,
                    radius = if (isArAligned) 24.dp.toPx() else 18.dp.toPx(),
                    center = Offset(center.x, horizonY),
                    style = Stroke(width = if (isArAligned) 3.dp.toPx() else 1.5.dp.toPx())
                )

                // Solar marker in AR horizon when visible in camera FOV
                if (solarPosition.isAboveHorizon && sensorStatus.hasSensorReading) {
                    val sunDelta = QiblaOrientationMath.shortestSignedDelta(
                        sensorStatus.cameraAzimuthDeg,
                        solarPosition.azimuthDeg
                    )
                    if (abs(sunDelta) <= halfFovDeg) {
                        val sunX = center.x + (sunDelta / halfFovDeg) * (size.width / 2f)
                        val sunY = (horizonY - (solarPosition.altitudeDeg / 45f) * (size.height * 0.24f))
                            .coerceIn(36.dp.toPx(), size.height - 36.dp.toPx())
                        drawCircle(
                            color = Color(0x4DFBBF24),
                            radius = 16.dp.toPx(),
                            center = Offset(sunX, sunY)
                        )
                        drawCircle(
                            color = Color(0xFFFBBF24),
                            radius = 7.dp.toPx(),
                            center = Offset(sunX, sunY)
                        )
                    }
                }

                // Floating Qibla Target Beacon inside Camera FOV
                if (sensorStatus.hasSensorReading && abs(smoothDeltaYaw) <= halfFovDeg + 6f) {
                    val targetX = (center.x + (smoothDeltaYaw / halfFovDeg) * (size.width / 2f))
                        .coerceIn(34.dp.toPx(), size.width - 34.dp.toPx())

                    // Vertical Qibla beam
                    drawLine(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                (if (isArAligned) accentColor else goldAccent).copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(targetX, 18.dp.toPx()),
                        end = Offset(targetX, size.height - 18.dp.toPx()),
                        strokeWidth = if (isArAligned) 4.dp.toPx() else 2.5.dp.toPx()
                    )

                    val kaabaSize = 30.dp.toPx()
                    drawCircle(
                        color = (if (isArAligned) accentColor else goldAccent).copy(alpha = 0.28f),
                        radius = 28.dp.toPx(),
                        center = Offset(targetX, horizonY)
                    )
                    drawRoundRect(
                        color = Color(0xFF101314),
                        topLeft = Offset(targetX - kaabaSize / 2f, horizonY - kaabaSize / 2f),
                        size = Size(kaabaSize, kaabaSize),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (isArAligned) accentColor else goldAccent,
                        topLeft = Offset(targetX - kaabaSize / 2f, horizonY - kaabaSize / 2f),
                        size = Size(kaabaSize, kaabaSize),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawLine(
                        color = goldAccent,
                        start = Offset(targetX - kaabaSize / 2f + 3.dp.toPx(), horizonY - 3.dp.toPx()),
                        end = Offset(targetX + kaabaSize / 2f - 3.dp.toPx(), horizonY - 3.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }

            // Top AR Status Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xAA041E18),
                border = BorderStroke(1.dp, (if (isArAligned) accentColor else goldAccent).copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = if (isArAligned) accentColor else goldAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (sensorStatus.hasSensorReading) {
                            "${BidiUtils.formatNumber(distanceKm, uiLanguageCode)} km • ${qiblaBearingDeg.roundToInt()}°"
                        } else {
                            stringResource(R.string.qibla_ar_hold_upright)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Off-screen Directional Chevron when Qibla is outside camera FOV
            if (sensorStatus.hasSensorReading && abs(smoothDeltaYaw) > halfFovDeg) {
                val turnRight = smoothDeltaYaw > 0f
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xCC06261E),
                    border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .align(if (turnRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!turnRight) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = goldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "${abs(smoothDeltaYaw).roundToInt()}°",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = goldAccent
                        )
                        if (turnRight) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = goldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Sun Cross-Check Chip inside AR View
            if (solarPosition.isAboveHorizon) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0x99041E18),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.qibla_sun_check_fmt,
                                solarPosition.azimuthDeg.roundToInt(),
                                QiblaOrientationMath.compassCardinalDirection(solarPosition.azimuthDeg)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFEF3C7)
                        )
                    }
                }
            }
        }
    }
}
