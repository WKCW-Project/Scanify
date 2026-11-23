package com.wkcw.scanify.scanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    /** Build the display name used for both the PDF and the history entry. */
    fun newName(): String =
        "Scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    /** Save all pages to Downloads/Scanify/ and return the Uri. */
    fun export(context: Context, pages: List<Bitmap>, name: String = newName()): Uri {
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val pageW = 595 // A4 @72dpi

        pages.forEachIndexed { i, bmp ->
            val pageH = (pageW * bmp.height.toFloat() / bmp.width).toInt()
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
            val page = doc.startPage(info)
            page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), paint)
            doc.finishPage(page)
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scanify")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create the PDF file")
        resolver.openOutputStream(uri)!!.use { doc.writeTo(it) }
        doc.close()

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
