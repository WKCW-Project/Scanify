package com.wkcw.scanify.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"), GRAYSCALE("Gray"), BW("B&W")
}

object DocumentScanner {

    /** Returns the four document corners, or null when no convincing document is found. */
    fun findDocument(bmp: Bitmap): List<PointF>? {
        val src = Mat()
        Utils.bitmapToMat(bmp, src)

        // Downscale first: faster and less noisy.
        val targetH = 500.0
        val ratio = src.rows() / targetH
        val small = Mat()
        Imgproc.resize(src, small, Size(src.cols() / ratio, targetH))

        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        // Close small gaps in the document outline.
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        contours.sortByDescending { Imgproc.contourArea(it) }

        val minArea = small.rows() * small.cols() * 0.20
        var found: List<Point>? = null
        for (c in contours.take(6)) {
            if (Imgproc.contourArea(c) < minArea) break
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            if (approx.total() == 4L && Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                found = approx.toArray().toList()
                break
            }
        }

        listOf(src, small, gray, edges, kernel).forEach { it.release() }

        return found?.map { PointF((it.x * ratio).toFloat(), (it.y * ratio).toFloat()) }
            ?.let(::orderPoints)
    }

    /** Fallback frame used by the manual crop screen when detection fails. */
    fun detectCorners(bmp: Bitmap): List<PointF> = findDocument(bmp) ?: run {
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        listOf(
            PointF(w * 0.08f, h * 0.08f), PointF(w * 0.92f, h * 0.08f),
            PointF(w * 0.92f, h * 0.92f), PointF(w * 0.08f, h * 0.92f)
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
        val width = max(hypot(br.x - bl.x, br.y - bl.y), hypot(tr.x - tl.x, tr.y - tl.y)).toDouble()
        val height = max(hypot(tr.x - br.x, tr.y - br.y), hypot(tl.x - bl.x, tl.y - bl.y)).toDouble()

        val srcPts = MatOfPoint2f(
            Point(tl.x.toDouble(), tl.y.toDouble()), Point(tr.x.toDouble(), tr.y.toDouble()),
            Point(br.x.toDouble(), br.y.toDouble()), Point(bl.x.toDouble(), bl.y.toDouble())
        )
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0), Point(width - 1, 0.0),
            Point(width - 1, height - 1), Point(0.0, height - 1)
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
            ScanFilter.GRAYSCALE -> gray.convertTo(result, -1, 1.2, 10.0) // slight contrast boost
            ScanFilter.BW -> Imgproc.adaptiveThreshold(
                gray, result, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 31, 12.0
            )
            else -> {}
        }
        val out = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(result, out)
        listOf(src, gray, result).forEach { it.release() }
        return out
    }
}
