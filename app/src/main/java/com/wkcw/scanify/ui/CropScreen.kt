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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun CropScreen(
    bitmap: Bitmap,
    initial: List<PointF>,
    onConfirm: (List<PointF>) -> Unit,
    onRetake: () -> Unit,
) {
    val pts = remember { mutableStateListOf<PointF>().apply { addAll(initial.map { PointF(it.x, it.y) }) } }
    val lineColor = MaterialTheme.colorScheme.primary
    val activeColor = MaterialTheme.colorScheme.primaryContainer

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp)) {
            Text("Adjust crop", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Drag the four corners to match the page edges.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            // The image uses ContentScale.Fit and is centered, so use the same scale and offset here.
            val scale = min(boxW / bitmap.width, boxH / bitmap.height)
            val offX = (boxW - bitmap.width * scale) / 2f
            val offY = (boxH - bitmap.height * scale) / 2f

            fun toView(p: PointF) = Offset(p.x * scale + offX, p.y * scale + offY)

            Surface(
                Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.medium,
                color = Color.White,
                shadowElevation = 2.dp,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap.asImageBitmap(), null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )

                    var active by remember { mutableIntStateOf(-1) }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(scale) {
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
                        val v = pts.map { toView(it) }
                        val scrim = Color.Black.copy(alpha = 0.38f)

                        // Dim the area outside the four corners.
                        drawPath(Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(v[1].x, v[1].y)
                            lineTo(v[0].x, v[0].y)
                            close()
                        }, scrim)
                        drawPath(Path().apply {
                            moveTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(v[2].x, v[2].y)
                            lineTo(v[1].x, v[1].y)
                            close()
                        }, scrim)
                        drawPath(Path().apply {
                            moveTo(0f, size.height)
                            lineTo(v[3].x, v[3].y)
                            lineTo(v[2].x, v[2].y)
                            lineTo(size.width, size.height)
                            close()
                        }, scrim)
                        drawPath(Path().apply {
                            moveTo(0f, 0f)
                            lineTo(v[0].x, v[0].y)
                            lineTo(v[3].x, v[3].y)
                            lineTo(0f, size.height)
                            close()
                        }, scrim)

                        for (i in v.indices) {
                            drawLine(
                                Color.White.copy(alpha = 0.8f), v[i], v[(i + 1) % v.size],
                                strokeWidth = 8f,
                            )
                            drawLine(lineColor, v[i], v[(i + 1) % v.size], strokeWidth = 4f)
                        }
                        v.forEachIndexed { i, o ->
                            drawCircle(lineColor, 22f, o)
                            drawCircle(if (i == active) activeColor else Color.White, 14f, o)
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onRetake, Modifier.weight(1f)) {
                Text("Retake")
            }
            Button(onClick = { onConfirm(pts.toList()) }, Modifier.weight(1f)) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text("Continue", Modifier.padding(start = 6.dp))
            }
        }
    }
}
