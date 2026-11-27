package com.wkcw.scanify.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
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

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"), GRAYSCALE("Gray"), BW("B&W")
}

object DocumentScanner {

    private data class ScoredQuad(
        val points: List<PointF>,
        val score: Double,
    )

    /**
     * Returns the four document corners in [bmp] coordinates, or null when no convincing document
     * is found.
     *
     * @param fast Uses a smaller working size and returns on the first confident match for live
     *   camera preview; false evaluates all passes at higher resolution for final photo auto-crop.
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

        val w = small.cols().toDouble()
        val h = small.rows().toDouble()
        val candidates = mutableListOf<ScoredQuad>()

        // Pass 1: Wash out printed text via morphological close, then run adaptive Canny.
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val closed = Mat()
        val blurred = Mat()
        val edges = Mat()
        Imgproc.morphologyEx(gray, closed, Imgproc.MORPH_CLOSE, closeKernel)
        Imgproc.GaussianBlur(closed, blurred, Size(5.0, 5.0), 0.0)
        val meanLuma = Core.mean(blurred).`val`[0]
        val low = (meanLuma * 0.35).coerceIn(20.0, 70.0)
        val high = (meanLuma * 1.15).coerceIn(75.0, 180.0)
        Imgproc.Canny(blurred, edges, low, high)
        Imgproc.dilate(edges, edges, dilateKernel)
        findBestQuad(edges, w, h, passBonus = 1.0)?.let { candidates.add(it) }

        // Pass 2: Local contrast boost (CLAHE) + gentle Canny for low-contrast paper on bright desks.
        if (!fast || candidates.isEmpty()) {
            val clahe = Imgproc.createCLAHE(2.5, Size(8.0, 8.0))
            val enhanced = Mat()
            val claheEdges = Mat()
            clahe.apply(blurred, enhanced)
            Imgproc.Canny(enhanced, claheEdges, 30.0, 100.0)
            Imgproc.dilate(claheEdges, claheEdges, dilateKernel)
            findBestQuad(claheEdges, w, h, passBonus = 0.96)?.let { candidates.add(it) }
            enhanced.release()
            claheEdges.release()
        }

        // Pass 3: Otsu threshold on closed luminance (strong for white page on darker surfaces).
        if (!fast || candidates.isEmpty()) {
            val otsu = Mat()
            Imgproc.threshold(blurred, otsu, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            Imgproc.morphologyEx(otsu, otsu, Imgproc.MORPH_CLOSE, closeKernel)
            findBestQuad(otsu, w, h, passBonus = 0.92)?.let { candidates.add(it) }
            otsu.release()
        }

        // Pass 4: Adaptive threshold handles uneven lighting and shadows across the page.
        if (!fast || candidates.isEmpty()) {
            val adaptive = Mat()
            Imgproc.adaptiveThreshold(
                blurred,
                adaptive,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                25,
                6.0,
            )
            Imgproc.morphologyEx(adaptive, adaptive, Imgproc.MORPH_CLOSE, closeKernel)
            findBestQuad(adaptive, w, h, passBonus = 0.88)?.let { candidates.add(it) }
            adaptive.release()
        }

        listOf(src, small, gray, closeKernel, dilateKernel, closed, blurred, edges).forEach { it.release() }

        val best = candidates.maxByOrNull { it.score } ?: return null
        return best.points.map {
            PointF(
                (it.x * ratio).toFloat().coerceIn(0f, bmp.width.toFloat()),
                (it.y * ratio).toFloat().coerceIn(0f, bmp.height.toFloat()),
            )
        }.let(::orderPoints)
    }

    /** Pick the highest-scoring convex four-point contour from an edge or binary mask. */
    private fun findBestQuad(mask: Mat, width: Double, height: Double, passBonus: Double): ScoredQuad? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        val frameArea = width * height
        val minArea = frameArea * 0.08
        val maxArea = frameArea * 0.94
        val minSide = min(width, height) * 0.12

        contours.sortByDescending { Imgproc.contourArea(it) }
        var best: ScoredQuad? = null

        for (contour in contours.take(12)) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea) break
            if (area > maxArea) continue

            val quad = approximateToQuad(contour) ?: continue
            val ordered = orderPoints(quad)
            val quadArea = polygonArea(ordered)
            if (quadArea < minArea || quadArea > maxArea) continue

            val minEdge = minEdgeLength(ordered)
            if (minEdge < minSide) continue

            val maxAbsCos = maxAngleCosine(ordered)
            // Allow perspective tilt (~38°..142°) while rejecting pinched slivers.
            if (maxAbsCos > 0.78) continue

            val rectangularity = 1.0 - maxAbsCos
            val areaRatio = quadArea / frameArea
            // Slight penalty if corners cling directly to the frame edges (usually false border).
            val edgeMargin = min(width, height) * 0.01
            val touchingEdges = ordered.count { p ->
                p.x <= edgeMargin || p.x >= width - edgeMargin || p.y <= edgeMargin || p.y >= height - edgeMargin
            }
            val borderPenalty = if (touchingEdges >= 3) 0.72 else 1.0
            val score = areaRatio * (0.55 + 0.45 * rectangularity) * borderPenalty * passBonus

            if (best == null || score > best.score) {
                best = ScoredQuad(ordered, score)
            }
        }

        contours.forEach { it.release() }
        return best
    }

    /** Approximate a contour (and its convex hull if slightly dented) to a convex 4-point quad. */
    private fun approximateToQuad(contour: MatOfPoint): List<PointF>? {
        val direct = tryApproxQuad(contour)
        if (direct != null) return direct

        val hullIdx = MatOfInt()
        Imgproc.convexHull(contour, hullIdx)
        val rawPts = contour.toArray()
        val hullPts = hullIdx.toArray().map { rawPts[it] }.toTypedArray()
        hullIdx.release()
        if (hullPts.size < 4) return null

        val hullContour = MatOfPoint(*hullPts)
        val result = tryApproxQuad(hullContour)
        hullContour.release()
        return result
    }

    private fun tryApproxQuad(contour: MatOfPoint): List<PointF>? {
        val c2f = MatOfPoint2f(*contour.toArray())
        val peri = Imgproc.arcLength(c2f, true)
        val approx = MatOfPoint2f()
        var found: List<PointF>? = null

        for (eps in doubleArrayOf(0.02, 0.03, 0.04, 0.055)) {
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

    private fun minEdgeLength(pts: List<PointF>): Double {
        var minLen = Double.MAX_VALUE
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
            if (len < minLen) minLen = len
        }
        return minLen
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
