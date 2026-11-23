package com.wkcw.scanify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wkcw.scanify.ui.CameraScreen
import com.wkcw.scanify.ui.CropScreen
import com.wkcw.scanify.ui.ResultScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opencvOk = OpenCVLoader.initLocal()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (opencvOk) App() else Text("OpenCV gagal dimuat", Modifier.padding(24.dp))
                }
            }
        }
    }
}

@Composable
fun App(vm: ScanViewModel = viewModel()) {
    BackHandler(vm.screen != Screen.CAMERA) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        when (vm.screen) {
            Screen.CAMERA -> CameraScreen(pageCount = vm.pages.size, onCaptured = vm::onCaptured)
            Screen.CROP -> CropScreen(vm.captured!!, vm.corners, onConfirm = vm::confirmCrop, onRetake = vm::back)
            Screen.RESULT -> ResultScreen(vm)
        }
        if (vm.busy) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
