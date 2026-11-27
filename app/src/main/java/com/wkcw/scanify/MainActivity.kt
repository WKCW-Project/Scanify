package com.wkcw.scanify

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wkcw.scanify.history.ScanRecord
import com.wkcw.scanify.ui.AboutScreen
import com.wkcw.scanify.ui.CameraScreen
import com.wkcw.scanify.ui.CouiPageScaffold
import com.wkcw.scanify.ui.CouiTopBarStyle
import com.wkcw.scanify.ui.CropScreen
import com.wkcw.scanify.ui.HomeScreen
import com.wkcw.scanify.ui.ResultScreen
import com.wkcw.scanify.ui.SettingsScreen
import com.wkcw.scanify.ui.panelBlur
import com.wkcw.scanify.ui.theme.ScanifyTheme
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.nav.core.NavBackStack
import io.github.suqi8.coui.kmp.nav.core.NavDisplay
import io.github.suqi8.coui.kmp.nav.core.NavKey
import io.github.suqi8.coui.kmp.nav.core.rememberNavBackStack
import io.github.suqi8.coui.kmp.overlay.OverlayLoadingDialog
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import kotlinx.serialization.Serializable
import org.opencv.android.OpenCVLoader

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object Camera : Route

    @Serializable
    data object Crop : Route

    @Serializable
    data object Result : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object About : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opencvOk = OpenCVLoader.initLocal()
        setContent {
            val vm: ScanViewModel = viewModel()
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                vm.loadPreferences(context)
                vm.refreshHistory(context)
            }

            val darkMode = isDarkColorMode(vm.colorMode, isSystemInDarkTheme())
            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }

            ScanifyTheme(
                colorMode = vm.colorMode,
                materialBlurEnabled = vm.materialBlurEnabled,
            ) {
                if (opencvOk) {
                    ScanifyApp(vm = vm)
                } else {
                    OpenCvErrorScreen()
                }
            }
        }
    }
}

private fun isDarkColorMode(mode: ColorSchemeMode, systemDark: Boolean): Boolean = when (mode) {
    ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
    ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
    ColorSchemeMode.System, ColorSchemeMode.MonetSystem -> systemDark
}

@Composable
private fun OpenCvErrorScreen(modifier: Modifier = Modifier) {
    CouiPageScaffold(
        title = stringResource(R.string.app_name),
        modifier = modifier,
        topBarStyle = CouiTopBarStyle.Small,
    ) { scope ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scope.contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.common_opencv_error_title),
                subtitle = stringResource(R.string.common_opencv_error_summary),
                illustration = {
                    Icon(
                        imageVector = COUIIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = COUITheme.colorScheme.error,
                    )
                },
            )
        }
    }
}

@Composable
fun ScanifyApp(
    vm: ScanViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack<Route>(Route.Home)

    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxSize(),
        onBack = { popBack(backStack) },
    ) {
        entry<Route.Home> {
            HomeScreen(
                history = vm.history,
                pendingSnackbarRes = vm.pendingSnackbarRes,
                onSnackbarShown = vm::consumePendingSnackbar,
                onNewScan = { backStack.add(Route.Camera) },
                onSettings = { backStack.add(Route.Settings) },
                onOpen = { openScan(context, it) },
                onDelete = { vm.deleteScan(context, it) },
            )
        }
        entry<Route.Camera> {
            CameraScreen(
                pageCount = vm.pages.size,
                autoDetect = vm.autoDetect,
                autoCapture = vm.autoCapture,
                busy = vm.busy,
                onBack = { popBack(backStack) },
                onToggleAutoCapture = { vm.updateAutoCapture(context, !vm.autoCapture) },
                onCaptured = { bmp ->
                    vm.onCaptured(
                        bmp = bmp,
                        onNavigateToResult = { backStack.add(Route.Result) },
                        onNavigateToCrop = { backStack.add(Route.Crop) },
                    )
                },
                onSettings = { backStack.add(Route.Settings) },
            )
        }
        entry<Route.Crop> {
            val captured = vm.captured
            if (captured != null) {
                CropScreen(
                    bitmap = captured,
                    initial = vm.corners,
                    busy = vm.busy,
                    onConfirm = { finalCorners ->
                        vm.confirmCrop(
                            finalCorners = finalCorners,
                            onNavigateToResult = { backStack.add(Route.Result) },
                        )
                    },
                    onRetake = { popBack(backStack) },
                )
            } else {
                LaunchedEffect(Unit) { popBack(backStack) }
            }
        }
        entry<Route.Result> {
            ResultScreen(
                vm = vm,
                onBack = { popBack(backStack) },
                onAdjustCrop = {
                    if (backStack.getOrNull(backStack.lastIndex - 1) == Route.Crop) {
                        popBack(backStack)
                    } else {
                        backStack.add(Route.Crop)
                    }
                },
                onAddPage = {
                    vm.addPage { popToCamera(backStack) }
                },
                onSaved = { popToHome(backStack) },
            )
        }
        entry<Route.Settings> {
            SettingsScreen(
                vm = vm,
                onBack = { popBack(backStack) },
                onAbout = { backStack.add(Route.About) },
            )
        }
        entry<Route.About> {
            AboutScreen(onBack = { popBack(backStack) })
        }
    }
}

@Composable
fun ProcessingOverlay(
    show: Boolean,
    modifier: Modifier = Modifier,
) {
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayLoadingDialog(
        show = show,
        modifier = modifier,
        text = stringResource(R.string.common_processing),
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = blur != null,
    )
}

private fun popBack(backStack: NavBackStack) {
    if (backStack.size > 1) {
        backStack.removeLastOrNull()
    }
}

private fun popToHome(backStack: NavBackStack) {
    while (backStack.size > 1) {
        backStack.removeLastOrNull()
    }
}

private fun popToCamera(backStack: NavBackStack) {
    while (backStack.size > 1 && backStack.lastOrNull() != Route.Camera) {
        backStack.removeLastOrNull()
    }
    if (backStack.lastOrNull() != Route.Camera) {
        backStack.add(Route.Camera)
    }
}

private fun openScan(context: Context, record: ScanRecord) {
    val mime = if (record.name.endsWith(".jpg", ignoreCase = true)) {
        "image/jpeg"
    } else {
        "application/pdf"
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(record.uri)).apply {
        type = mime
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.common_open_document)))
}
