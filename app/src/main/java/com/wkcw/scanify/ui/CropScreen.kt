package com.wkcw.scanify.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wkcw.scanify.ProcessingOverlay
import com.wkcw.scanify.R
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.squircle.squircleClip
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun CropScreen(
    bitmap: Bitmap,
    initial: List<PointF>,
    busy: Boolean,
    onConfirm: (List<PointF>) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pts = remember(bitmap, initial) {
        mutableStateListOf<PointF>().apply { addAll(initial.map { PointF(it.x, it.y) }) }
    }
    val lineColor = COUITheme.colorScheme.primary
    val handleCenterColor = COUITheme.colorScheme.onPrimary
    val activeCenterColor = COUITheme.colorScheme.secondary

    CouiPageScaffold(
        title = stringResource(R.string.crop_title),
        modifier = modifier,
        onBack = onRetake,
        topBarStyle = CouiTopBarStyle.Small,
    ) { scope ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scope.contentPadding),
            ) {
                Text(
                    text = stringResource(R.string.crop_instruction),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = COUITheme.textStyles.bodyM,
                    color = COUITheme.colorScheme.labelSecondary,
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .squircleClip(24.dp)
                        // Document canvas stays neutral white so paper edges stay crisp.
                        .background(Color.White),
                ) {
                    val density = LocalDensity.current
                    val boxW = with(density) { maxWidth.toPx() }
                    val boxH = with(density) { maxHeight.toPx() }
                    val scale = min(boxW / bitmap.width, boxH / bitmap.height)
                    val offX = (boxW - bitmap.width * scale) / 2f
                    val offY = (boxH - bitmap.height * scale) / 2f

                    fun toView(p: PointF) = Offset(p.x * scale + offX, p.y * scale + offY)

                    Box(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )

                        var active by remember { mutableIntStateOf(-1) }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(scale) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val idx = pts.indices.minByOrNull {
                                                val v = toView(pts[it])
                                                hypot(v.x - start.x, v.y - start.y)
                                            } ?: -1
                                            if (idx < 0) {
                                                active = -1
                                            } else {
                                                val v = toView(pts[idx])
                                                active = if (hypot(v.x - start.x, v.y - start.y) < 112f) idx else -1
                                            }
                                        },
                                        onDragEnd = { active = -1 },
                                        onDragCancel = { active = -1 },
                                        onDrag = { change, drag ->
                                            if (active >= 0) {
                                                change.consume()
                                                val p = pts[active]
                                                pts[active] = PointF(
                                                    (p.x + drag.x / scale).coerceIn(0f, bitmap.width.toFloat()),
                                                    (p.y + drag.y / scale).coerceIn(0f, bitmap.height.toFloat()),
                                                )
                                            }
                                        },
                                    )
                                },
                        ) {
                            if (pts.size < 4) return@Canvas
                            val v = pts.map { toView(it) }
                            val scrim = Color.Black.copy(alpha = 0.4f)

                            drawPath(
                                Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(size.width, 0f)
                                    lineTo(v[1].x, v[1].y)
                                    lineTo(v[0].x, v[0].y)
                                    close()
                                },
                                scrim,
                            )
                            drawPath(
                                Path().apply {
                                    moveTo(size.width, 0f)
                                    lineTo(size.width, size.height)
                                    lineTo(v[2].x, v[2].y)
                                    lineTo(v[1].x, v[1].y)
                                    close()
                                },
                                scrim,
                            )
                            drawPath(
                                Path().apply {
                                    moveTo(0f, size.height)
                                    lineTo(v[3].x, v[3].y)
                                    lineTo(v[2].x, v[2].y)
                                    lineTo(size.width, size.height)
                                    close()
                                },
                                scrim,
                            )
                            drawPath(
                                Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(v[0].x, v[0].y)
                                    lineTo(v[3].x, v[3].y)
                                    lineTo(0f, size.height)
                                    close()
                                },
                                scrim,
                            )

                            for (i in v.indices) {
                                drawLine(
                                    Color.White.copy(alpha = 0.85f),
                                    v[i],
                                    v[(i + 1) % v.size],
                                    strokeWidth = 8f,
                                )
                                drawLine(
                                    lineColor,
                                    v[i],
                                    v[(i + 1) % v.size],
                                    strokeWidth = 4f,
                                )
                            }
                            v.forEachIndexed { i, o ->
                                drawCircle(lineColor, 22f, o)
                                drawCircle(if (i == active) activeCenterColor else handleCenterColor, 14f, o)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = stringResource(R.string.crop_retake),
                        onClick = onRetake,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    )
                    Button(
                        onClick = { onConfirm(pts.toList()) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Icon(
                            imageVector = COUIIcons.Ok,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.common_continue),
                            style = COUITheme.textStyles.buttonL,
                        )
                    }
                }
            }

            ProcessingOverlay(show = busy)
        }
    }
}
