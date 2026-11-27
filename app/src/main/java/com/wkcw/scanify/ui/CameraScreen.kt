package com.wkcw.scanify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Corners are already mapped into the visible preview area, in 0..1 coordinates. */
private data class ScanGuide(
    val corners: List<PointF>,
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
    onCaptured: (Bitmap) -> Unit,
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
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var scanGuide by remember { mutableStateOf<ScanGuide?>(null) }
    val mainExecutor = ContextCompat.getMainExecutor(context)

    fun takePicture(onError: () -> Unit = {}) {
        haptic(CouiHapticEffect.Strength)
        imageCapture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    onCaptured(prepare(bmp, rotation))
                }

                override fun onError(e: ImageCaptureException) {
                    onError()
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
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val useCases = mutableListOf<UseCase>(preview, imageCapture)

            if (liveDetection) {
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val lastCheck = AtomicLong(0L)
                val stableHits = AtomicInteger(0)
                val previousCorners = AtomicReference<List<PointF>?>(null)
                val captured = AtomicBoolean(false)

                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val now = System.currentTimeMillis()
                        if (!captured.get() && now - lastCheck.get() >= 500) {
                            lastCheck.set(now)
                            val raw = image.toBitmap()
                            val frame = rotateFrame(raw, image.imageInfo.rotationDegrees)
                            val found = DocumentScanner.findDocument(frame)
                            val previewW = previewView.width.takeIf { it > 0 } ?: frame.width
                            val previewH = previewView.height.takeIf { it > 0 } ?: frame.height
                            val scale = maxOf(
                                previewW.toFloat() / frame.width,
                                previewH.toFloat() / frame.height,
                            )
                            val visibleW = previewW / scale
                            val visibleH = previewH / scale
                            val offsetX = (frame.width - visibleW) / 2f
                            val offsetY = (frame.height - visibleH) / 2f
                            val normalized = found?.map {
                                PointF(
                                    (it.x - offsetX) / visibleW,
                                    (it.y - offsetY) / visibleH,
                                )
                            }
                            if (normalized == null) {
                                stableHits.set(0)
                                previousCorners.set(null)
                                mainExecutor.execute { scanGuide = null }
                            } else {
                                val inside = normalized.all {
                                    it.x in 0.04f..0.96f && it.y in 0.04f..0.96f
                                }
                                val goodFit = inside && quadArea(normalized) >= 0.35f
                                val stable = previousCorners.get()?.zip(normalized)
                                    ?.all { (a, b) -> hypot(a.x - b.x, a.y - b.y) < 0.025f } == true
                                stableHits.set(
                                    if (!goodFit) 0 else if (stable) stableHits.get() + 1 else 1,
                                )
                                previousCorners.set(normalized)
                                val locked = goodFit && stableHits.get() >= 3

                                mainExecutor.execute {
                                    scanGuide = ScanGuide(normalized, locked)
                                }
                                if (autoCapture && locked && captured.compareAndSet(false, true)) {
                                    mainExecutor.execute { takePicture(onError = { captured.set(false) }) }
                                }
                            }
                        }
                    } finally {
                        image.close()
                    }
                }
                useCases.add(analysis)
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                *useCases.toTypedArray(),
            )
        }, mainExecutor)
        onDispose { future.get().unbindAll() }
    }

    val readyColor = COUITheme.colorScheme.primary
    // Viewfinder overlays sit over the live camera feed, so dark scrim and light ink stay legible.
    val cameraScrim = Color.Black.copy(alpha = 0.56f)
    val cameraDeck = Color.Black.copy(alpha = 0.84f)
    val cameraPill = Color.Black.copy(alpha = 0.52f)
    val cameraControlFill = Color.White.copy(alpha = 0.16f)
    val shutterDescription = stringResource(R.string.camera_shutter)

    Scaffold(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())

            Canvas(Modifier.fillMaxSize()) {
                val left = size.width * 0.07f
                val top = size.height * 0.14f
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
                    val lineColor = if (guide.locked) readyColor else Color.White.copy(alpha = 0.88f)
                    for (i in corners.indices) {
                        drawLine(
                            lineColor, corners[i], corners[(i + 1) % corners.size],
                            strokeWidth = 5f, cap = StrokeCap.Round,
                        )
                    }
                    corners.forEach {
                        drawCircle(Color.White, 13f, it)
                        drawCircle(lineColor, 7f, it)
                    }
                }
            }

            val locked = scanGuide?.locked == true

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
                            .background(if (locked) readyColor else Color.White.copy(alpha = 0.6f)),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = when {
                            locked -> stringResource(R.string.camera_status_ready)
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
                            .size(78.dp)
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
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.88f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (locked) readyColor else Color.White),
                            )
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
