package com.wkcw.scanify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wkcw.scanify.history.ScanRecord
import com.wkcw.scanify.ui.CameraScreen
import com.wkcw.scanify.ui.CropScreen
import com.wkcw.scanify.ui.HomeScreen
import com.wkcw.scanify.ui.ResultScreen
import com.wkcw.scanify.ui.theme.ScanifyTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opencvOk = OpenCVLoader.initLocal()
        enableEdgeToEdge()
        setContent {
            ScanifyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    if (opencvOk) App() else OpenCvError()
                }
            }
        }
    }
}

@Composable
private fun OpenCvError() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                "OpenCV failed to load.",
                Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: ScanViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshHistory(context) }

    BackHandler(vm.screen != Screen.HOME) { vm.back() }

    val title = when (vm.screen) {
        Screen.HOME -> "Scanify"
        Screen.CAMERA -> "Scanify"
        Screen.CROP -> "Crop document"
        Screen.RESULT -> "Preview"
    }

    Scaffold(
        topBar = {
            if (vm.screen != Screen.HOME) {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = vm::back) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (vm.screen) {
                Screen.HOME -> HomeScreen(
                    history = vm.history,
                    onNewScan = vm::newScan,
                    onOpen = { openPdf(context, it) },
                    onDelete = { vm.deleteScan(context, it) },
                )
                Screen.CAMERA -> CameraScreen(pageCount = vm.pages.size, onCaptured = vm::onCaptured)
                Screen.CROP -> CropScreen(vm.captured!!, vm.corners, onConfirm = vm::confirmCrop, onRetake = vm::back)
                Screen.RESULT -> ResultScreen(vm)
            }

            if (vm.busy) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                    ) {
                        CircularProgressIndicator(
                            Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun openPdf(context: android.content.Context, record: ScanRecord) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(record.uri)).apply {
        type = "application/pdf"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open PDF"))
}
