package com.wkcw.scanify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlin.math.max

@Composable
fun CameraScreen(pageCount: Int, onCaptured: (Bitmap) -> Unit) {
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
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Izin kamera dibutuhkan buat scan dokumen")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Beri izin") }
        }
        return
    }

    val lifecycleOwner = context as LifecycleOwner
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }, ContextCompat.getMainExecutor(context))
        onDispose { future.get().unbindAll() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        Column(
            Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pageCount > 0) Text("$pageCount halaman tersimpan", color = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = image.toBitmap()
                            val rotation = image.imageInfo.rotationDegrees
                            image.close()
                            onCaptured(prepare(bmp, rotation))
                        }
                        override fun onError(e: ImageCaptureException) { e.printStackTrace() }
                    }
                )
            }) { Text("Ambil foto") }
        }
    }
}

/** Putar sesuai orientasi sensor & kecilin kalau kegedean (hemat memori). */
private fun prepare(src: Bitmap, rotation: Int, maxSide: Int = 2400): Bitmap {
    val scale = maxSide.toFloat() / max(src.width, src.height)
    val m = Matrix().apply {
        postRotate(rotation.toFloat())
        if (scale < 1f) postScale(scale, scale)
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}
