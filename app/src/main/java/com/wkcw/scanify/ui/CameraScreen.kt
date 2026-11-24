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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFloatingActionButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

private data class ScanGuide(
    val corners: List<PointF>,
    val frameWidth: Int,
    val frameHeight: Int,
    val locked: Boolean,
)

@Composable
fun CameraScreen(
    pageCount: Int,
    autoDetect: Boolean,
    autoCapture: Boolean,
    onCaptured: (Bitmap) -> Unit,
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

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    val liveDetection = autoDetect || autoCapture
    DisposableEffect(liveDetection) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
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
                            val frame = image.toBitmap()
                            val found = DocumentScanner.findDocument(frame)
                            val normalized = found?.map {
                                PointF(it.x / frame.width, it.y / frame.height)
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
                                    scanGuide = ScanGuide(normalized, frame.width, frame.height, locked)
                                }
                                if (autoCapture && locked && captured.compareAndSet(false, true)) {
                                    mainExecutor.execute {
                                        imageCapture.takePicture(
                                            mainExecutor,
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(capturedImage: ImageProxy) {
                                                    val bmp = capturedImage.toBitmap()
                                                    val rotation = capturedImage.imageInfo.rotationDegrees
                                                    capturedImage.close()
                                                    onCaptured(prepare(bmp, rotation))
                                                }

                                                override fun onError(e: ImageCaptureException) {
                                                    captured.set(false)
                                                    e.printStackTrace()
                                                }
                                            },
                                        )
                                    }
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
            val insetX = size.width * 0.08f
            val insetY = size.height * 0.12f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.4f),
                topLeft = Offset(insetX, insetY),
                size = Size(size.width - insetX * 2, size.height - insetY * 2),
                cornerRadius = CornerRadius(24f),
                style = Stroke(width = 3f),
            )

            val guide = scanGuide
            if (guide != null) {
                val scale = maxOf(size.width / guide.frameWidth, size.height / guide.frameHeight)
                val offX = (size.width - guide.frameWidth * scale) / 2f
                val offY = (size.height - guide.frameHeight * scale) / 2f
                val corners = guide.corners.map {
                    Offset(it.x * guide.frameWidth * scale + offX, it.y * guide.frameHeight * scale + offY)
                }
                val lineColor = if (guide.locked) Color.Green else Color.White
                for (i in corners.indices) {
                    drawLine(lineColor, corners[i], corners[(i + 1) % corners.size], strokeWidth = 6f)
                }
                corners.forEach {
                    drawCircle(Color.White, 16f, it)
                    drawCircle(lineColor, 10f, it)
                }
            }
        }

        Surface(
            Modifier.align(Alignment.TopStart).padding(16.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                if (pageCount == 0) "No pages yet" else "$pageCount page(s) captured",
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                when {
                    autoCapture -> "Fit the document in the guide to capture"
                    autoDetect -> "Auto crop is on — tap to capture"
                    else -> "Tap to capture a page"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            BadgedBox(badge = { if (pageCount > 0) Badge { Text("$pageCount") } }) {
                LargeFloatingActionButton(
                    onClick = {
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bmp = image.toBitmap()
                                    val rotation = image.imageInfo.rotationDegrees
                                    image.close()
                                    onCaptured(prepare(bmp, rotation))
                                }

                                override fun onError(e: ImageCaptureException) {
                                    e.printStackTrace()
                                }
                            },
                        )
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text("SCAN", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
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
