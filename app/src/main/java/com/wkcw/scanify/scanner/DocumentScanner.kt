package com.wkcw.scanify.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"), GRAYSCALE("Gray"), BW("B&W")
}

object DocumentScanner {

    private data class ScoredQuad(
        val points: List<PointF>,
        val score: Double,
    )

    private class ImageSignals(
        val width: Int,
        val height: Int,
        val gradMag: FloatArray,
        val luma: ByteArray,
        val edgeThreshold: Float,
    ) {
        fun maxGradNear(cx: Int, cy: Int, radius: Int = 2): Float {
            var best = 0f
            val x0 = (cx - radius).coerceAtLeast(0)
            val x1 = (cx + radius).coerceAtMost(width - 1)
            val y0 = (cy - radius).coerceAtLeast(0)
            val y1 = (cy + radius).coerceAtMost(height - 1)
            for (y in y0..y1) {
                val row = y * width
                for (x in x0..x1) {
                    val v = gradMag[row + x]
                    if (v > best) best = v
                }
            }
            return best
        }

        fun lumaAt(x: Int, y: Int): Int {
            val cx = x.coerceIn(0, width - 1)
            val cy = y.coerceIn(0, height - 1)
            return luma[cy * width + cx].toInt() and 0xFF
        }
    }

    /**
     * Returns the four document corners in [bmp] coordinates, or null when no high-confidence
     * document boundary is found.
     */
    fun findDocument(bmp: Bitmap, fast: Boolean = false): List<PointF>? {
        val src = Mat()
        Utils.bitmapToMat(bmp, src)

        val targetH = if (fast) 360.0 else 640.0
        val ratio = max(1.0, src.rows() / targetH)
        val small = Mat()
        if (ratio > 1.0) {
            Imgproc.resize(src, small, Size(src.cols() / ratio, src.rows() / ratio))
        } else {
            src.copyTo(small)
        }

        val gray = Mat()
        when (small.channels()) {
            4 -> Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            3 -> Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGB2GRAY)
            else -> small.copyTo(gray)
        }

        // Median blur preserves sharp paper corners while suppressing fine grain and print noise.
        val smooth = Mat()
        Imgproc.medianBlur(gray, smooth, 5)
        val signals = buildSignals(smooth)

        // Erase text/lines inside the page via morphological closing so the page becomes a clean shape.
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val closed = Mat()
        Imgproc.morphologyEx(smooth, closed, Imgproc.MORPH_CLOSE, closeKernel)
        Imgproc.GaussianBlur(closed, closed, Size(5.0, 5.0), 0.0)

        val candidates = mutableListOf<ScoredQuad>()

        // Pass 1: Clean luminance edges with adaptive Canny thresholds.
        val meanLuma = Core.mean(closed).`val`[0]
        val low = (meanLuma * 0.35).coerceIn(25.0, 70.0)
        val high = (meanLuma * 1.10).coerceIn(85.0, 180.0)
        val edges = Mat()
        Imgproc.Canny(closed, edges, low, high)
        Imgproc.dilate(edges, edges, dilateKernel)
        collectQuads(edges, signals, candidates)
        edges.release()

        // Pass 2: HSV paper-vs-desk separation (high value + low saturation isolates white/light
        // paper on wooden, dark, or colored desks even under moderate contrast).
        if (small.channels() >= 3) {
            val rgb = Mat()
            if (small.channels() == 4) {
                Imgproc.cvtColor(small, rgb, Imgproc.COLOR_RGBA2RGB)
            } else {
                small.copyTo(rgb)
            }
            val hsv = Mat()
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            val hsvChannels = ArrayList<Mat>(3)
            Core.split(hsv, hsvChannels)

            val paperMask = Mat()
            Core.addWeighted(hsvChannels[2], 1.0, hsvChannels[1], -0.6, 0.0, paperMask)
            Imgproc.morphologyEx(paperMask, paperMask, Imgproc.MORPH_CLOSE, closeKernel)
            Imgproc.GaussianBlur(paperMask, paperMask, Size(5.0, 5.0), 0.0)

            val otsuPaper = Mat()
            Imgproc.threshold(paperMask, otsuPaper, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            Imgproc.morphologyEx(otsuPaper, otsuPaper, Imgproc.MORPH_OPEN, dilateKernel)
            Imgproc.morphologyEx(otsuPaper, otsuPaper, Imgproc.MORPH_CLOSE, closeKernel)
            collectQuads(otsuPaper, signals, candidates)

            val paperEdges = Mat()
            Imgproc.Canny(paperMask, paperEdges, 35.0, 110.0)
            Imgproc.dilate(paperEdges, paperEdges, dilateKernel)
            collectQuads(paperEdges, signals, candidates)

            hsvChannels.forEach { it.release() }
            listOf(rgb, hsv, paperMask, otsuPaper, paperEdges).forEach { it.release() }
        }

        // Pass 3: Local contrast enhancement (CLAHE) fallback for low-contrast paper on bright desks.
        if (!fast || candidates.isEmpty()) {
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            val enhanced = Mat()
            val claheEdges = Mat()
            clahe.apply(closed, enhanced)
            Imgproc.Canny(enhanced, claheEdges, 40.0, 120.0)
            Imgproc.dilate(claheEdges, claheEdges, dilateKernel)
            collectQuads(claheEdges, signals, candidates)
            enhanced.release()
            claheEdges.release()
        }

        listOf(src, small, gray, smooth, closeKernel, dilateKernel, closed).forEach { it.release() }

        val best = candidates.maxByOrNull { it.score } ?: return null
        return best.points.map {
            PointF(
                (it.x * ratio).toFloat().coerceIn(0f, bmp.width.toFloat()),
                (it.y * ratio).toFloat().coerceIn(0f, bmp.height.toFloat()),
            )
        }.let(::orderPoints)
    }

    private fun buildSignals(smoothGray: Mat): ImageSignals {
        val w = smoothGray.cols()
        val h = smoothGray.rows()
        val gradX = Mat()
        val gradY = Mat()
        val gradMag = Mat()
        Imgproc.Sobel(smoothGray, gradX, CvType.CV_32F, 1, 0, 3)
        Imgproc.Sobel(smoothGray, gradY, CvType.CV_32F, 0, 1, 3)
        Core.magnitude(gradX, gradY, gradMag)

        val gradBuf = FloatArray(w * h)
        gradMag.get(0, 0, gradBuf)
        val lumaBuf = ByteArray(w * h)
        smoothGray.get(0, 0, lumaBuf)

        // Adaptive gradient threshold derived from the frame's average edge energy.
        val meanGrad = Core.mean(gradMag).`val`[0].toFloat()
        val edgeThreshold = (meanGrad * 1.45f).coerceIn(28f, 95f)

        gradX.release()
        gradY.release()
        gradMag.release()
        return ImageSignals(w, h, gradBuf, lumaBuf, edgeThreshold)
    }

    /** Extract candidate quads from a binary/edge mask and verify each against real image edges. */
    private fun collectQuads(mask: Mat, signals: ImageSignals, out: MutableList<ScoredQuad>) {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        val width = signals.width.toDouble()
        val height = signals.height.toDouble()
        val frameArea = width * height
        val minArea = frameArea * 0.14
        val maxArea = frameArea * 0.88
        val minSide = min(width, height) * 0.18

        contours.sortByDescending { Imgproc.contourArea(it) }

        for (contour in contours.take(10)) {
            val contourArea = Imgproc.contourArea(contour)
            if (contourArea < minArea) break
            if (contourArea > maxArea) continue

            val quad = approximateToQuad(contour, contourArea) ?: continue
            val ordered = orderPoints(quad)
            val quadArea = polygonArea(ordered)
            if (quadArea < minArea || quadArea > maxArea) continue

            // Reject degenerate or overly distorted shapes.
            val sideLengths = sideLengths(ordered)
            if (sideLengths.min() < minSide) continue

            val topBottomRatio = min(sideLengths[0], sideLengths[2]) / max(sideLengths[0], sideLengths[2])
            val leftRightRatio = min(sideLengths[1], sideLengths[3]) / max(sideLengths[1], sideLengths[3])
            if (topBottomRatio < 0.55 || leftRightRatio < 0.55) continue

            val estWidth = (sideLengths[0] + sideLengths[2]) / 2.0
            val estHeight = (sideLengths[1] + sideLengths[3]) / 2.0
            val aspect = estWidth / max(1.0, estHeight)
            if (aspect !in 0.42..2.35) continue

            val maxAbsCos = maxAngleCosine(ordered)
            // Keep corners roughly rectangular (~54°..126°), rejecting wild non-document trapezoids.
            if (maxAbsCos > 0.58) continue

            // Verify all four sides actually sit on real image edges, not imaginary hull lines.
            val (avgEdgeSupport, minSideSupport, contrastStep) = verifyBoundaries(ordered, signals)
            if (avgEdgeSupport < 0.50 || minSideSupport < 0.25) continue
            if (contrastStep < 0.10) continue

            // Penalize quads clinging to the viewport edges (often background/table borders).
            val edgeMargin = min(width, height) * 0.015
            val touchingEdges = ordered.count { p ->
                p.x <= edgeMargin || p.x >= width - edgeMargin || p.y <= edgeMargin || p.y >= height - edgeMargin
            }
            if (touchingEdges >= 4) continue
            val borderPenalty = when (touchingEdges) {
                3 -> 0.65
                2 -> 0.88
                else -> 1.0
            }

            val rectangularity = 1.0 - maxAbsCos
            val areaRatio = quadArea / frameArea
            // Prefer well-framed documents (~25%..75% of frame) rather than giant background boxes.
            val areaScore = when {
                areaRatio < 0.25 -> areaRatio / 0.25
                areaRatio <= 0.75 -> 1.0
                else -> (1.0 - (areaRatio - 0.75) / 0.25).coerceAtLeast(0.4)
            }

            val score = (
                avgEdgeSupport * 0.45 +
                    contrastStep * 0.25 +
                    rectangularity * 0.15 +
                    areaScore * 0.15
                ) * borderPenalty

            if (score >= 0.48) {
                out.add(ScoredQuad(ordered, score))
            }
        }

        contours.forEach { it.release() }
    }

    private data class BoundaryMetrics(
        val avgEdgeSupport: Double,
        val minSideSupport: Double,
        val contrastStep: Double,
    )

    /**
     * Samples points along each of the 4 quad sides to confirm:
     * 1) Real gradient edges exist along every side (avg + weakest side).
     * 2) There is a real luminance step across the border (inside vs outside).
     */
    private fun verifyBoundaries(ordered: List<PointF>, signals: ImageSignals): BoundaryMetrics {
        val cx = ordered.sumOf { it.x.toDouble() } / 4.0
        val cy = ordered.sumOf { it.y.toDouble() } / 4.0
        val samplesPerSide = 20
        val stepPx = max(4.0, min(signals.width, signals.height) * 0.025)

        var totalSupport = 0.0
        var minSideSupport = 1.0
        var totalContrast = 0.0

        for (i in 0 until 4) {
            val a = ordered[i]
            val b = ordered[(i + 1) % 4]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val len = hypot(dx, dy)
            if (len < 1.0) return BoundaryMetrics(0.0, 0.0, 0.0)

            // Normal pointing outward from quad center.
            var nx = -dy / len
            var ny = dx / len
            val midX = (a.x + b.x) / 2.0
            val midY = (a.y + b.y) / 2.0
            if ((midX - cx) * nx + (midY - cy) * ny < 0.0) {
                nx = -nx
                ny = -ny
            }

            var edgeHits = 0
            var sideContrast = 0.0
            for (s in 1..samplesPerSide) {
                // Skip the extreme 8% near corners where lines turn.
                val t = 0.08 + 0.84 * (s - 0.5) / samplesPerSide
                val px = a.x + dx * t
                val py = a.y + dy * t

                if (signals.maxGradNear(px.roundToInt(), py.roundToInt(), radius = 2) >= signals.edgeThreshold) {
                    edgeHits++
                }

                val inX = (px - nx * stepPx).roundToInt()
                val inY = (py - ny * stepPx).roundToInt()
                val outX = (px + nx * stepPx).roundToInt()
                val outY = (py + ny * stepPx).roundToInt()
                val diff = abs(signals.lumaAt(inX, inY) - signals.lumaAt(outX, outY))
                sideContrast += (diff / 60.0).coerceAtMost(1.0)
            }

            val sideSupport = edgeHits.toDouble() / samplesPerSide
            totalSupport += sideSupport
            if (sideSupport < minSideSupport) minSideSupport = sideSupport
            totalContrast += sideContrast / samplesPerSide
        }

        return BoundaryMetrics(
            avgEdgeSupport = totalSupport / 4.0,
            minSideSupport = minSideSupport,
            contrastStep = totalContrast / 4.0,
        )
    }

    /**
     * Approximate a contour to a convex 4-point quad. Convex hull is only allowed when the
     * contour is already highly solid (>= 86%), preventing irregular clutter from forming fake quads.
     */
    private fun approximateToQuad(contour: MatOfPoint, contourArea: Double): List<PointF>? {
        val direct = tryApproxQuad(contour)
        if (direct != null) return direct

        val hullIdx = MatOfInt()
        Imgproc.convexHull(contour, hullIdx)
        val rawPts = contour.toArray()
        val hullPts = hullIdx.toArray().map { rawPts[it] }.toTypedArray()
        hullIdx.release()
        if (hullPts.size < 4) return null

        val hullContour = MatOfPoint(*hullPts)
        val hullArea = Imgproc.contourArea(hullContour)
        val result = if (hullArea > 0.0 && contourArea / hullArea >= 0.86) {
            tryApproxQuad(hullContour)
        } else {
            null
        }
        hullContour.release()
        return result
    }

    private fun tryApproxQuad(contour: MatOfPoint): List<PointF>? {
        val c2f = MatOfPoint2f(*contour.toArray())
        val peri = Imgproc.arcLength(c2f, true)
        val approx = MatOfPoint2f()
        var found: List<PointF>? = null

        for (eps in doubleArrayOf(0.015, 0.025, 0.035)) {
            Imgproc.approxPolyDP(c2f, approx, eps * peri, true)
            val pts = approx.toArray()
            if (pts.size == 4) {
                val poly = MatOfPoint(*pts)
                val convex = Imgproc.isContourConvex(poly)
                poly.release()
                if (convex) {
                    found = pts.map { PointF(it.x.toFloat(), it.y.toFloat()) }
                    break
                }
            }
        }

        c2f.release()
        approx.release()
        return found
    }

    private fun polygonArea(pts: List<PointF>): Double {
        var sum = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            sum += a.x.toDouble() * b.y.toDouble() - b.x.toDouble() * a.y.toDouble()
        }
        return abs(sum) / 2.0
    }

    private fun sideLengths(pts: List<PointF>): DoubleArray = DoubleArray(4) { i ->
        val a = pts[i]
        val b = pts[(i + 1) % 4]
        hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
    }

    private fun maxAngleCosine(pts: List<PointF>): Double {
        var maxCos = 0.0
        for (i in pts.indices) {
            val prev = pts[(i + pts.size - 1) % pts.size]
            val curr = pts[i]
            val next = pts[(i + 1) % pts.size]
            val dx1 = (prev.x - curr.x).toDouble()
            val dy1 = (prev.y - curr.y).toDouble()
            val dx2 = (next.x - curr.x).toDouble()
            val dy2 = (next.y - curr.y).toDouble()
            val denom = hypot(dx1, dy1) * hypot(dx2, dy2)
            if (denom < 1e-6) return 1.0
            val cos = abs((dx1 * dx2 + dy1 * dy2) / denom)
            if (cos > maxCos) maxCos = cos
        }
        return maxCos
    }

    /** Fallback frame used by the manual crop screen when detection fails. */
    fun detectCorners(bmp: Bitmap): List<PointF> = findDocument(bmp, fast = false) ?: run {
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        listOf(
            PointF(w * 0.08f, h * 0.08f), PointF(w * 0.92f, h * 0.08f),
            PointF(w * 0.92f, h * 0.92f), PointF(w * 0.08f, h * 0.92f),
        )
    }

    /** Order: TL (min x+y), BR (max x+y), TR (min y-x), BL (max y-x). */
    fun orderPoints(p: List<PointF>): List<PointF> {
        val tl = p.minBy { it.x + it.y }
        val br = p.maxBy { it.x + it.y }
        val tr = p.minBy { it.y - it.x }
        val bl = p.maxBy { it.y - it.x }
        return listOf(tl, tr, br, bl)
    }

    /** Flatten the perspective using the four corners. */
    fun warp(bmp: Bitmap, corners: List<PointF>): Bitmap {
        val (tl, tr, br, bl) = orderPoints(corners)
        val width = max(1.0, max(hypot(br.x - bl.x, br.y - bl.y), hypot(tr.x - tl.x, tr.y - tl.y)).toDouble())
        val height = max(1.0, max(hypot(tr.x - br.x, tr.y - br.y), hypot(tl.x - bl.x, tl.y - bl.y)).toDouble())

        val srcPts = MatOfPoint2f(
            Point(tl.x.toDouble(), tl.y.toDouble()), Point(tr.x.toDouble(), tr.y.toDouble()),
            Point(br.x.toDouble(), br.y.toDouble()), Point(bl.x.toDouble(), bl.y.toDouble()),
        )
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0), Point(width - 1, 0.0),
            Point(width - 1, height - 1), Point(0.0, height - 1),
        )

        val src = Mat()
        Utils.bitmapToMat(bmp, src)
        val m = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val dst = Mat()
        Imgproc.warpPerspective(src, dst, m, Size(width, height), Imgproc.INTER_CUBIC)

        val out = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, out)
        listOf(src, m, dst, srcPts, dstPts).forEach { it.release() }
        return out
    }

    /** Rotate the bitmap by a multiple of 90 degrees. */
    fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bmp
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    fun applyFilter(bmp: Bitmap, filter: ScanFilter): Bitmap {
        if (filter == ScanFilter.ORIGINAL) return bmp
        val src = Mat()
        Utils.bitmapToMat(bmp, src)
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        val result = Mat()
        when (filter) {
            ScanFilter.GRAYSCALE -> gray.convertTo(result, -1, 1.2, 10.0)
            ScanFilter.BW -> Imgproc.adaptiveThreshold(
                gray, result, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 31, 12.0,
            )
            else -> {}
        }
        val out = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(result, out)
        listOf(src, gray, result).forEach { it.release() }
        return out
    }
}
