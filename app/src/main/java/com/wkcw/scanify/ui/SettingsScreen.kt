package com.wkcw.scanify.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wkcw.scanify.R
import com.wkcw.scanify.ScanViewModel
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Background
import io.github.suqi8.coui.kmp.icon.extended.Layers
import io.github.suqi8.coui.kmp.icon.extended.Scan
import io.github.suqi8.coui.kmp.icon.extended.ScreenCapture
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.squircle.squircleClip
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode

private val ThemeModes = listOf(
    ColorSchemeMode.System,
    ColorSchemeMode.Light,
    ColorSchemeMode.Dark,
    ColorSchemeMode.MonetSystem,
)

@Composable
fun SettingsScreen(
    vm: ScanViewModel,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val (versionName, _) = appVersion(context)
    val selectedThemeIndex = ThemeModes.indexOf(vm.colorMode).coerceAtLeast(0)

    CouiPage(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        onBack = onBack,
        wide = isWideWindow(),
    ) {
        item(key = "scanning") {
            PreferenceCategory(title = stringResource(R.string.settings_category_scanning))
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                CardListGroup {
                    item(key = "auto_detect") {
                        SwitchPreference(
                            checked = vm.autoDetect,
                            onCheckedChange = { vm.updateAutoDetect(context, it) },
                            title = stringResource(R.string.settings_auto_detect_title),
                            summary = stringResource(R.string.settings_auto_detect_summary),
                            startAction = {
                                Icon(
                                    imageVector = COUIIcons.Scan,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = COUITheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                    item(key = "auto_capture") {
                        SwitchPreference(
                            checked = vm.autoCapture,
                            onCheckedChange = { vm.updateAutoCapture(context, it) },
                            title = stringResource(R.string.settings_auto_capture_title),
                            summary = stringResource(R.string.settings_auto_capture_summary),
                            startAction = {
                                Icon(
                                    imageVector = COUIIcons.ScreenCapture,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = COUITheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                }
            }
        }

        item(key = "appearance") {
            PreferenceCategory(title = stringResource(R.string.settings_category_appearance))
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                CardListGroup {
                    item(key = "color_mode") {
                        OverlayDropdownPreference(
                            items = listOf(
                                stringResource(R.string.settings_theme_system),
                                stringResource(R.string.settings_theme_light),
                                stringResource(R.string.settings_theme_dark),
                                stringResource(R.string.settings_theme_monet),
                            ),
                            selectedIndex = selectedThemeIndex,
                            title = stringResource(R.string.settings_theme_title),
                            summary = stringResource(R.string.settings_theme_summary),
                            startAction = {
                                Icon(
                                    imageVector = COUIIcons.Background,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = COUITheme.colorScheme.primary,
                                )
                            },
                            onSelectedIndexChange = { index ->
                                vm.updateColorMode(context, ThemeModes[index])
                            },
                        )
                    }
                    item(key = "material_blur") {
                        SwitchPreference(
                            checked = vm.materialBlurEnabled,
                            onCheckedChange = { vm.updateMaterialBlur(context, it) },
                            title = stringResource(R.string.settings_blur_title),
                            summary = stringResource(R.string.settings_blur_summary),
                            startAction = {
                                Icon(
                                    imageVector = COUIIcons.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = COUITheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                }
            }
        }

        item(key = "about") {
            PreferenceCategory(title = stringResource(R.string.settings_category_about))
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                CardListGroup {
                    item(key = "about_entry") {
                        ArrowPreference(
                            title = stringResource(R.string.settings_about_title),
                            summary = stringResource(R.string.settings_about_summary),
                            startAction = {
                                Image(
                                    painter = painterResource(R.drawable.ic_app),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .squircleClip(6.dp),
                                )
                            },
                            endActions = {
                                Text(
                                    text = versionName,
                                    style = COUITheme.textStyles.bodyM,
                                    color = COUITheme.colorScheme.labelTertiary,
                                )
                            },
                            onClick = onAbout,
                        )
                    }
                }
            }
        }
    }
}
