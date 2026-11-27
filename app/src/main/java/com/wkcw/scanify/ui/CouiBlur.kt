package com.wkcw.scanify.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.MaterialBlendMode
import io.github.suqi8.coui.kmp.blur.MaterialBlurStyle
import io.github.suqi8.coui.kmp.blur.ViewBlurStyle
import io.github.suqi8.coui.kmp.blur.couiMaterialBlur
import io.github.suqi8.coui.kmp.blur.couiViewBlur
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.effect.MaterialBackdropProvider
import io.github.suqi8.coui.kmp.effect.MaterialBackdropSpec
import io.github.suqi8.coui.kmp.effect.MaterialBlurBlendMode
import io.github.suqi8.coui.kmp.effect.MaterialBlurProvider
import io.github.suqi8.coui.kmp.effect.MaterialBlurSpec
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Whether the COUI 17 material blur is enabled app-wide. */
val LocalCouiBlurEnabled = staticCompositionLocalOf { true }

/**
 * The backdrop recording the current page, provided by [CouiPageScaffold] so dialogs, sheets, and
 * menus opened from the page can blur it.
 */
val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Returns a backdrop recording a page over the window background, or null when blur is off. */
@Composable
fun rememberPageBackdrop(): LayerBackdrop? {
    if (!LocalCouiBlurEnabled.current || !isRuntimeShaderSupported()) return null
    val background = COUITheme.colorScheme.backgroundWithCard
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

/**
 * Bridges a backdrop to coui-ui's `LocalMaterialBackdrop` (buttons, floating bars, FAB, toolbar).
 * The backdrop must record what is behind those elements, never the elements themselves.
 */
@Composable
fun rememberMaterialBackdropProvider(backdrop: Backdrop?): MaterialBackdropProvider? {
    val active = LocalCouiBlurEnabled.current && backdrop != null && isRuntimeShaderSupported()
    return remember(backdrop, active) {
        if (active && backdrop != null) {
            MaterialBackdropProvider { spec, shape ->
                Modifier.couiViewBlur(backdrop = backdrop, style = spec.toViewBlurStyle(), shape = shape)
            }
        } else {
            null
        }
    }
}

/** Bridges a backdrop to coui-ui's `LocalPopupMaterialBlur` (dropdowns, list popups, context menus). */
@Composable
fun rememberPopupMaterialBlur(backdrop: Backdrop?): MaterialBlurProvider? {
    val active = LocalCouiBlurEnabled.current && backdrop != null && isRuntimeShaderSupported()
    return remember(backdrop, active) {
        if (active && backdrop != null) {
            MaterialBlurProvider { spec, shape ->
                Modifier.couiMaterialBlur(backdrop = backdrop, style = spec.toMaterialBlurStyle(), shape = shape)
            }
        } else {
            null
        }
    }
}

/**
 * The COUI 17 blurred panel of a dialog or bottom sheet.
 */
@Immutable
class PanelBlur(val backgroundModifier: Modifier, val backgroundColor: Color)

/**
 * Returns the panel blur for the page behind, or null to keep the opaque surface.
 */
@Composable
fun panelBlur(style: (isDark: Boolean) -> MaterialBlurStyle): PanelBlur? {
    val backdrop = LocalPageBackdrop.current
    if (!LocalCouiBlurEnabled.current || backdrop == null || !isRuntimeShaderSupported()) return null
    return PanelBlur(
        backgroundModifier = Modifier.couiMaterialBlur(
            backdrop = backdrop,
            style = style(COUITheme.colorScheme.isDark),
        ),
        backgroundColor = Color.Transparent,
    )
}

private fun MaterialBackdropSpec.toViewBlurStyle(): ViewBlurStyle = ViewBlurStyle(
    blurRadiusPx = blurRadiusPx,
    layer1 = layer1,
    mode1 = mode1,
    layer2 = layer2,
    mode2 = mode2,
)

private fun MaterialBlurSpec.toMaterialBlurStyle(): MaterialBlurStyle = MaterialBlurStyle(
    blurRadiusPx = blurRadiusPx,
    mixColor = mixColor,
    blendColor = blendColor,
    blendMode = when (blendMode) {
        MaterialBlurBlendMode.Overlay -> MaterialBlendMode.Overlay
        MaterialBlurBlendMode.ColorDodge -> MaterialBlendMode.ColorDodge
        MaterialBlurBlendMode.GlowOverlay -> MaterialBlendMode.GlowOverlay
        MaterialBlurBlendMode.GlowColorDodge -> MaterialBlendMode.GlowColorDodge
    },
)
