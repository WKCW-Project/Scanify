package com.wkcw.scanify.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

    Column(Modifier.fillMaxSize()) {
        Text("Geser titik biru ke sudut dokumen", Modifier.padding(16.dp))

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            // Image pakai ContentScale.Fit di tengah, jadi hitung scale & offset yang sama
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
                            // pilih titik terdekat (radius sentuh 56px)
                            val idx = pts.indices.minByOrNull {
                                val v = toView(pts[it]); hypot(v.x - start.x, v.y - start.y)
                            } ?: -1
                            val v = toView(pts[idx])
                            active = if (hypot(v.x - start.x, v.y - start.y) < 56f * 2) idx else -1
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
                    drawLine(Color(0xFF2979FF), v[i], v[(i + 1) % v.size], strokeWidth = 5f)
                }
                v.forEachIndexed { i, o ->
                    drawCircle(Color.White, 26f, o)
                    drawCircle(if (i == active) Color(0xFFFF5252) else Color(0xFF2979FF), 20f, o)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onRetake, Modifier.weight(1f)) { Text("Ulang") }
            Button(onClick = { onConfirm(pts.toList()) }, Modifier.weight(1f)) { Text("Lanjut") }
        }
    }
}
