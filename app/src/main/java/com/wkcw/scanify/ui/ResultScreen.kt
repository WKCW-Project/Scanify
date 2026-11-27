package com.wkcw.scanify.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wkcw.scanify.ProcessingOverlay
import com.wkcw.scanify.R
import com.wkcw.scanify.ScanViewModel
import com.wkcw.scanify.scanner.ScanFilter
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Chip
import io.github.suqi8.coui.kmp.basic.ChipGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Cut
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.RotateLeft
import io.github.suqi8.coui.kmp.squircle.squircleClip
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic

@Composable
fun ResultScreen(
    vm: ScanViewModel,
    onBack: () -> Unit,
    onAdjustCrop: () -> Unit,
    onAddPage: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = rememberCouiHaptic()
    val subtitle = if (vm.pages.isEmpty()) {
        stringResource(R.string.result_subtitle_single)
    } else {
        stringResource(R.string.result_subtitle_multi, vm.pages.size + 1)
    }

    CouiPageScaffold(
        title = stringResource(R.string.result_title),
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        topBarStyle = CouiTopBarStyle.Small,
        actions = {
            IconButton(onClick = onAdjustCrop) {
                Icon(
                    imageVector = COUIIcons.Cut,
                    contentDescription = stringResource(R.string.result_adjust_crop),
                    tint = COUITheme.colorScheme.labelPrimary,
                )
            }
        },
    ) { scope ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scope.contentPadding),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .squircleClip(24.dp)
                        // Document preview sits on neutral white paper backdrop.
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val preview = vm.preview
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.result_no_preview),
                            style = COUITheme.textStyles.bodyM,
                            color = COUITheme.colorScheme.labelSecondary,
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.result_rotate),
                                modifier = Modifier.weight(1f),
                                style = COUITheme.textStyles.headlineXS,
                                color = COUITheme.colorScheme.labelPrimary,
                            )
                            IconButton(
                                onClick = {
                                    haptic(CouiHapticEffect.Switch)
                                    vm.rotate(-90)
                                },
                                enabled = !vm.busy,
                            ) {
                                Icon(
                                    imageVector = COUIIcons.RotateLeft,
                                    contentDescription = stringResource(R.string.result_rotate_left),
                                    tint = COUITheme.colorScheme.labelPrimary,
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic(CouiHapticEffect.Switch)
                                    vm.rotate(90)
                                },
                                enabled = !vm.busy,
                            ) {
                                Icon(
                                    imageVector = COUIIcons.RotateLeft,
                                    contentDescription = stringResource(R.string.result_rotate_right),
                                    modifier = Modifier.scale(scaleX = -1f, scaleY = 1f),
                                    tint = COUITheme.colorScheme.labelPrimary,
                                )
                            }
                            TextButton(
                                text = stringResource(R.string.result_adjust_crop),
                                onClick = onAdjustCrop,
                                enabled = !vm.busy,
                                colors = ButtonDefaults.textButtonColorsBorderless(),
                                textStyle = COUITheme.textStyles.buttonM,
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.result_filter),
                            style = COUITheme.textStyles.headlineXS,
                            color = COUITheme.colorScheme.labelPrimary,
                        )
                        Spacer(Modifier.height(8.dp))
                        ChipGroup(
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        ) {
                            ScanFilter.entries.forEach { filter ->
                                Chip(
                                    selected = vm.filter == filter,
                                    onClick = { vm.setFilter(filter) },
                                    label = stringResource(filterLabelRes(filter)),
                                    enabled = !vm.busy,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onAddPage,
                        modifier = Modifier.weight(1f),
                        enabled = !vm.busy,
                    ) {
                        Icon(
                            imageVector = COUIIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.result_add_page),
                            style = COUITheme.textStyles.buttonL,
                        )
                    }
                    Button(
                        onClick = {
                            vm.savePdf(context) { onSaved() }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !vm.busy,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Icon(
                            imageVector = COUIIcons.Ok,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.result_save_pdf),
                            style = COUITheme.textStyles.buttonL,
                        )
                    }
                }

                TextButton(
                    text = stringResource(R.string.result_save_jpg),
                    onClick = {
                        vm.saveJpg(context) { onSaved() }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 8.dp),
                    enabled = !vm.busy,
                    colors = ButtonDefaults.textButtonColorsBorderless(),
                    textStyle = COUITheme.textStyles.buttonM,
                )
            }

            ProcessingOverlay(show = vm.busy)
        }
    }
}

private fun filterLabelRes(filter: ScanFilter): Int = when (filter) {
    ScanFilter.ORIGINAL -> R.string.result_filter_original
    ScanFilter.GRAYSCALE -> R.string.result_filter_gray
    ScanFilter.BW -> R.string.result_filter_bw
}
