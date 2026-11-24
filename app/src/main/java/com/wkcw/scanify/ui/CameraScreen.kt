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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.wkcw.scanify.scanner.DocumentScanner
import kotlin.math.hypot
import kotlin.math.max
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Corners are already mapped into the visible preview area, in 0..1 coordinates. */
private val ScannerBackground = Color(0xFF1C1C1E)
private val ScannerControl = Color(0xFF2C2C2E)
private val ScannerReady = Color(0xFF66BB6A)

private data class ScanGuide(
    val corners: List<PointF>,
    val locked: Boolean,
)

@Composable
fun CameraScreen(
    pageCount: Int,
    autoDetect: Boolean,
    autoCapture: Boolean,
    onCaptured: (Bitmap) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.padding(24.dp),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Camera access needed",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Grant camera permission to scan documents.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant access")
                    }
                }
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
    DisposableEffect(liveDetection) {
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
                            // ImageProxy.toBitmap() returns the raw sensor frame. In portrait
                            // that frame is landscape, so rotate it before detecting and before
                            // mapping corners onto the visible preview area.
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
                                    if (!goodFit) 0 else if (stable) stableHits.get() + 1 else 1
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

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val left = size.width * 0.07f
            val top = size.height * 0.12f
            val right = size.width - left
            val bottom = size.height * 0.80f
            val scrim = Color.Black.copy(alpha = 0.58f)

            // Keep the page area clear and dim the rest of the camera feed.
            drawRect(scrim, Offset(0f, 0f), Size(size.width, top))
            drawRect(scrim, Offset(0f, bottom), Size(size.width, size.height - bottom))
            drawRect(scrim, Offset(0f, top), Size(left, bottom - top))
            drawRect(scrim, Offset(right, top), Size(size.width - right, bottom - top))

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
                val lineColor = if (guide.locked) ScannerReady else Color.White.copy(alpha = 0.85f)
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
        Surface(
            Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (locked) ScannerReady else Color.White.copy(alpha = 0.55f)),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        locked -> "Document ready"
                        autoCapture -> "Auto capture"
                        else -> "Manual capture"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }

        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = ScannerBackground.copy(alpha = 0.96f),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when {
                        locked -> "Hold steady — capture is ready"
                        autoCapture -> "Fit the page inside the frame"
                        autoDetect -> "Auto crop is on — tap to capture"
                        else -> "Tap the shutter to capture"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        Modifier.align(Alignment.CenterStart),
                        shape = CircleShape,
                        color = ScannerControl,
                    ) {
                        Text(
                            if (pageCount == 0) "0" else "$pageCount",
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }

                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { takePicture() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(66.dp).clip(CircleShape).background(ScannerBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(12.dp).clip(CircleShape)
                                    .background(if (locked) ScannerReady else Color.White),
                            )
                        }
                    }

                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                        )
                    }
                }
            }
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
    return kotlin.math.abs(sum) / 2f
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
