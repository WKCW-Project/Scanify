package com.wkcw.scanify.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.wkcw.scanify.ui.LocalCouiBlurEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.COUIThemeColors
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController

@Composable
fun ScanifyTheme(
    colorMode: ColorSchemeMode = ColorSchemeMode.System,
    materialBlurEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorMode) {
        ThemeController(
            colorSchemeMode = colorMode,
            themeColor = COUIThemeColors.Green,
        )
    }
    CompositionLocalProvider(LocalCouiBlurEnabled provides materialBlurEnabled) {
        COUITheme(
            controller = controller,
            content = content,
        )
    }
}
