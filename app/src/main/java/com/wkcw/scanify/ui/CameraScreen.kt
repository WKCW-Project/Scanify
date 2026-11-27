package com.wkcw.scanify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.wkcw.scanify.ProcessingOverlay
import com.wkcw.scanify.R
import com.wkcw.scanify.scanner.DocumentScanner
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Scan
import io.github.suqi8.coui.kmp.icon.extended.ScreenCapture
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

private const val ANALYSIS_INTERVAL_MS = 150L
private const val CAMERA_WARMUP_MS = 650L
private const val LOCK_REQUIRED_HITS = 7
private const val MAX_MISSED_FRAMES = 2
private const val STABILITY_MAX_DELTA = 0.022f
private const val SMOOTHING_ALPHA = 0.42f

/** Corners are already mapped into the visible preview area, in 0..1 coordinates. */
private data class ScanGuide(
    val corners: List<PointF>,
    val lockProgress: Float,
    val locked: Boolean,
)

@Composable
fun CameraScreen(
    pageCount: Int,
    autoDetect: Boolean,
    autoCapture: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onToggleAutoCapture: () -> Unit,
    onCaptured: (Bitmap, List<PointF>?) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = rememberCouiHaptic()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        CouiPageScaffold(
            title = stringResource(R.string.app_name),
            modifier = modifier,
            onBack = onBack,
            topBarStyle = CouiTopBarStyle.Small,
        ) { scope ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scope.contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.camera_permission_title),
                    subtitle = stringResource(R.string.camera_permission_summary),
                    actionText = stringResource(R.string.camera_permission_grant),
                    onActionClick = { launcher.launch(Manifest.permission.CAMERA) },
                    illustration = {
                        Icon(
                            imageVector = COUIIcons.Scan,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = COUITheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
        return
    }

    val lifecycleOwner = context as LifecycleOwner
    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val imageCapture = remember(resolutionSelector) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val cameraRef = remember { AtomicReference<Camera?>(null) }
    val captureInFlight = remember { AtomicBoolean(false) }
    val latestFrameCorners = remember { AtomicReference<List<PointF>?>(null) }
    var scanGuide by remember { mutableStateOf<ScanGuide?>(null) }
    var focusTap by remember { mutableStateOf<Offset?>(null) }
    val mainExecutor = ContextCompat.getMainExecutor(context)

    LaunchedEffect(busy) {
        if (!busy) {
            captureInFlight.set(false)
        }
    }

    LaunchedEffect(focusTap) {
        if (focusTap != null) {
            delay(750)
            focusTap = null
        }
    }

    fun takePicture(hintNormalizedCorners: List<PointF>? = latestFrameCorners.get()) {
        if (!captureInFlight.compareAndSet(false, true)) return
        haptic(CouiHapticEffect.Strength)
        imageCapture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    onCaptured(prepare(bmp, rotation), hintNormalizedCorners)
                }

                override fun onError(e: ImageCaptureException) {
                    captureInFlight.set(false)
                    e.printStackTrace()
                }
            },
        )
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    val liveDetection = autoDetect || autoCapture
    DisposableEffect(liveDetection, autoCapture) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val useCases = mutableListOf<UseCase>(preview, imageCapture)

            if (liveDetection) {
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(resolutionSelector)
                    .build()
                val startTime = System.currentTimeMillis()
                val lastCheck = AtomicLong(0L)
                val stableHits = AtomicInteger(0)
                val missedFrames = AtomicInteger(0)
                val smoothedCorners = AtomicReference<List<PointF>?>(null)
                val previousCorners = AtomicReference<List<PointF>?>(null)

                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val now = System.currentTimeMillis()
                        if (
                            !captureInFlight.get() &&
                            now - startTime >= CAMERA_WARMUP_MS &&
                            now - lastCheck.get() >= ANALYSIS_INTERVAL_MS
                        ) {
                            lastCheck.set(now)
                            val raw = image.toBitmap()
                            val frame = rotateFrame(raw, image.imageInfo.rotationDegrees)
                            if (frame !== raw) raw.recycle()
                            val frameW = frame.width
                            val frameH = frame.height
                            val found = DocumentScanner.findDocument(frame, fast = true)
                            frame.recycle()
                            val previewW = previewView.width.takeIf { it > 0 } ?: frameW
                            val previewH = previewView.height.takeIf { it > 0 } ?: frameH
                            val scale = maxOf(
                                previewW.toFloat() / frameW,
                                previewH.toFloat() / frameH,
                            )
                            val visibleW = previewW / scale
                            val visibleH = previewH / scale
                            val offsetX = (frameW - visibleW) / 2f
                            val offsetY = (frameH - visibleH) / 2f
                            val normalized = found?.map {
                                PointF(
                                    (it.x - offsetX) / visibleW,
                                    (it.y - offsetY) / visibleH,
                                )
                            }
                            if (normalized == null) {
                                val misses = missedFrames.incrementAndGet()
                                if (misses > MAX_MISSED_FRAMES) {
                                    stableHits.set(0)
                                    previousCorners.set(null)
                                    smoothedCorners.set(null)
                                    latestFrameCorners.set(null)
                                    mainExecutor.execute { scanGuide = null }
                                }
                            } else {
                                missedFrames.set(0)
                                val smoothed = smoothCorners(smoothedCorners.get(), normalized)
                                smoothedCorners.set(smoothed)

                                // Also store corners normalized to the full sensor frame as a
                                // fallback for photo auto-crop.
                                latestFrameCorners.set(
                                    found.map { PointF(it.x / frameW, it.y / frameH) },
                                )

                                val inside = smoothed.all {
                                    it.x in 0.03f..0.97f && it.y in 0.09f..0.83f
                                }
                                val cx = smoothed.sumOf { it.x.toDouble() }.toFloat() / 4f
                                val cy = smoothed.sumOf { it.y.toDouble() }.toFloat() / 4f
                                val centered = cx in 0.18f..0.82f && cy in 0.22f..0.68f
                                val area = quadArea(smoothed)
                                val goodFit = inside && centered && area in 0.18f..0.82f
                                val prev = previousCorners.get()
                                val stable = prev != null && prev.zip(smoothed)
                                    .all { (a, b) -> hypot(a.x - b.x, a.y - b.y) < STABILITY_MAX_DELTA }
                                val hits = when {
                                    !goodFit -> 0
                                    stable -> (stableHits.get() + 1).coerceAtMost(LOCK_REQUIRED_HITS)
                                    else -> 1
                                }
                                stableHits.set(hits)
                                previousCorners.set(smoothed)
                                val progress = (hits.toFloat() / LOCK_REQUIRED_HITS).coerceIn(0f, 1f)
                                val locked = goodFit && hits >= LOCK_REQUIRED_HITS

                                mainExecutor.execute {
                                    scanGuide = ScanGuide(smoothed, progress, locked)
                                }
                                if (autoCapture && locked && !captureInFlight.get()) {
                                    val fallback = latestFrameCorners.get()
                                    mainExecutor.execute { takePicture(fallback) }
                                }
                            }
                        }
                    } finally {
                        image.close()
                    }
                }
                useCases.add(analysis)
            } else {
                latestFrameCorners.set(null)
                scanGuide = null
            }

            provider.unbindAll()
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                *useCases.toTypedArray(),
            )
            cameraRef.set(boundCamera)
        }, mainExecutor)
        onDispose {
            cameraRef.set(null)
            future.get().unbindAll()
        }
    }

    val readyColor = COUITheme.colorScheme.primary
    // Viewfinder overlays sit over the live camera feed, so dark scrim and light ink stay legible.
    val cameraScrim = Color.Black.copy(alpha = 0.50f)
    val cameraDeck = Color.Black.copy(alpha = 0.84f)
    val cameraPill = Color.Black.copy(alpha = 0.52f)
    val cameraControlFill = Color.White.copy(alpha = 0.16f)
    val shutterDescription = stringResource(R.string.camera_shutter)
    val animatedProgress by animateFloatAsState(
        targetValue = scanGuide?.lockProgress ?: 0f,
        animationSpec = tween(durationMillis = 120),
        label = "lock_progress",
    )

    Scaffold(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            focusTap = offset
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                            ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                            cameraRef.get()?.cameraControl?.startFocusAndMetering(action)
                        }
                    },
            ) {
                val left = size.width * 0.06f
                val top = size.height * 0.13f
                val right = size.width - left
                val bottom = size.height * 0.77f

                drawRect(cameraScrim, Offset(0f, 0f), Size(size.width, top))
                drawRect(cameraScrim, Offset(0f, bottom), Size(size.width, size.height - bottom))
                drawRect(cameraScrim, Offset(0f, top), Size(left, bottom - top))
                drawRect(cameraScrim, Offset(right, top), Size(size.width - right, bottom - top))

                fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
                    val len = 38f
                    drawLine(
                        Color.White, Offset(x, y), Offset(x + dx * len, y),
                        strokeWidth = 6f, cap = StrokeCap.Round,
                    )
                    drawLine(
                        Color.White, Offset(x, y), Offset(x, y + dy * len),
                        strokeWidth = 6f, cap = StrokeCap.Round,
                    )
                }
                bracket(left, top, 1f, 1f)
                bracket(right, top, -1f, 1f)
                bracket(right, bottom, -1f, -1f)
                bracket(left, bottom, 1f, -1f)

                val guide = scanGuide
                if (guide != null) {
                    val corners = guide.corners.map {
                        Offset(it.x * size.width, it.y * size.height)
                    }
                    val activeColor = if (guide.locked || guide.lockProgress >= 0.4f) {
                        readyColor
                    } else {
                        Color.White.copy(alpha = 0.92f)
                    }
                    val fillPath = Path().apply {
                        moveTo(corners[0].x, corners[0].y)
                        for (i in 1 until corners.size) {
                            lineTo(corners[i].x, corners[i].y)
                        }
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        color = activeColor.copy(alpha = 0.10f + 0.16f * animatedProgress),
                    )
                    for (i in corners.indices) {
                        drawLine(
                            activeColor,
                            corners[i],
                            corners[(i + 1) % corners.size],
                            strokeWidth = if (guide.locked) 7f else 5.5f,
                            cap = StrokeCap.Round,
                        )
                    }
                    corners.forEach {
                        drawCircle(Color.White, 13f, it)
                        drawCircle(activeColor, 7.5f, it)
                    }
                }

                focusTap?.let { tap ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 44f,
                        center = tap,
                        style = Stroke(width = 3.5f),
                    )
                }
            }

            val locked = scanGuide?.locked == true
            val detecting = scanGuide != null && !locked

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(cameraPill),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = COUIIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(cameraPill)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    locked -> readyColor
                                    detecting -> readyColor.copy(alpha = 0.75f)
                                    else -> Color.White.copy(alpha = 0.6f)
                                },
                            ),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = when {
                            locked -> stringResource(R.string.camera_status_ready)
                            detecting && autoCapture -> stringResource(R.string.camera_status_detecting)
                            autoCapture -> stringResource(R.string.camera_status_auto)
                            else -> stringResource(R.string.camera_status_manual)
                        },
                        style = COUITheme.textStyles.buttonM,
                        color = Color.White,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(cameraPill),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = COUIIcons.Settings,
                            contentDescription = stringResource(R.string.common_settings),
                            tint = Color.White,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(cameraDeck)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when {
                        locked -> stringResource(R.string.camera_hint_ready)
                        detecting && autoCapture -> stringResource(R.string.camera_hint_holding)
                        autoCapture -> stringResource(R.string.camera_hint_auto_capture)
                        autoDetect -> stringResource(R.string.camera_hint_auto_detect)
                        else -> stringResource(R.string.camera_hint_manual)
                    },
                    style = COUITheme.textStyles.bodyS,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(CircleShape)
                            .background(cameraControlFill)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.camera_pages_badge, pageCount),
                            style = COUITheme.textStyles.buttonM,
                            color = Color.White,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(82.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (autoCapture && animatedProgress > 0.02f) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawArc(
                                    color = readyColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .semantics {
                                    contentDescription = shutterDescription
                                    role = Role.Button
                                }
                                .clickable(enabled = !busy) { takePicture() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.88f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (locked || detecting) readyColor else Color.White),
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (autoCapture) readyColor else cameraControlFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = {
                                haptic(CouiHapticEffect.Switch)
                                onToggleAutoCapture()
                            },
                        ) {
                            Icon(
                                imageVector = COUIIcons.ScreenCapture,
                                contentDescription = stringResource(R.string.camera_toggle_auto),
                                tint = if (autoCapture) COUITheme.colorScheme.onPrimary else Color.White,
                            )
                        }
                    }
                }
            }

            ProcessingOverlay(show = busy)
        }
    }
}

/** Exponentially smooth corners between frames to eliminate hand-tremor jitter. */
private fun smoothCorners(previous: List<PointF>?, current: List<PointF>): List<PointF> {
    if (previous == null || previous.size != current.size) return current
    val jump = previous.zip(current).maxOf { (a, b) -> hypot(a.x - b.x, a.y - b.y) }
    // Snap immediately when the document moves to a completely new region.
    if (jump > 0.22f) return current
    return previous.zip(current).map { (a, b) ->
        PointF(
            a.x + (b.x - a.x) * SMOOTHING_ALPHA,
            a.y + (b.y - a.y) * SMOOTHING_ALPHA,
        )
    }
}

/** Apply the sensor rotation to an analysis frame before detecting document corners. */
private fun rotateFrame(src: Bitmap, rotation: Int): Bitmap {
    if (rotation % 360 == 0) return src
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

/** Normalized area of a four-point quad, used to reject tiny false detections. */
private fun quadArea(points: List<PointF>): Float {
    var sum = 0f
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        sum += a.x * b.y - b.x * a.y
    }
    return abs(sum) / 2f
}

/** Rotate according to the sensor orientation and downscale to keep memory usage low. */
private fun prepare(src: Bitmap, rotation: Int, maxSide: Int = 2400): Bitmap {
    val scale = maxSide.toFloat() / max(src.width, src.height)
    val m = Matrix().apply {
        postRotate(rotation.toFloat())
        if (scale < 1f) postScale(scale, scale)
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}
