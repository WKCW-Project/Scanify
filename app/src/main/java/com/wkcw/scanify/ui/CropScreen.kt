package com.wkcw.scanify.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
    onRetake: () -> Unit
) {
    val pts = remember { mutableStateListOf<PointF>().apply { addAll(initial.map { PointF(it.x, it.y) }) } }
    val lineColor = MaterialTheme.colorScheme.primary
    val activeColor = MaterialTheme.colorScheme.tertiary

    Column(Modifier.fillMaxSize()) {
        Surface(
            Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                "Drag the points to the document corners",
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            // The image uses ContentScale.Fit and is centered, so use the same scale and offset here.
            val scale = min(boxW / bitmap.width, boxH / bitmap.height)
            val offX = (boxW - bitmap.width * scale) / 2f
            val offY = (boxH - bitmap.height * scale) / 2f

            fun toView(p: PointF) = Offset(p.x * scale + offX, p.y * scale + offY)

            Image(
                bitmap.asImageBitmap(), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit
            )

            var active by remember { mutableIntStateOf(-1) }
            Canvas(
                Modifier.fillMaxSize().pointerInput(scale) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val idx = pts.indices.minByOrNull {
                                val v = toView(pts[it]); hypot(v.x - start.x, v.y - start.y)
                            } ?: -1
                            val v = toView(pts[idx])
                            active = if (hypot(v.x - start.x, v.y - start.y) < 112f) idx else -1
                        },
                        onDragEnd = { active = -1 },
                        onDragCancel = { active = -1 },
                        onDrag = { change, drag ->
                            if (active >= 0) {
                                change.consume()
                                val p = pts[active]
                                pts[active] = PointF(
                                    (p.x + drag.x / scale).coerceIn(0f, bitmap.width.toFloat()),
                                    (p.y + drag.y / scale).coerceIn(0f, bitmap.height.toFloat())
                                )
                            }
                        }
                    )
                }
            ) {
                val v = pts.map { toView(it) }
                for (i in v.indices) {
                    drawLine(lineColor, v[i], v[(i + 1) % v.size], strokeWidth = 6f)
                }
                v.forEachIndexed { i, o ->
                    drawCircle(Color.White, 30f, o)
                    drawCircle(if (i == active) activeColor else lineColor, 22f, o)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onRetake, Modifier.weight(1f)) { Text("Retake") }
            Button(onClick = { onConfirm(pts.toList()) }, Modifier.weight(1f)) { Text("Continue") }
        }
    }
}
