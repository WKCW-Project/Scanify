package com.wkcw.scanify.scanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

object ImageExporter {

    /** Save every page as a JPEG in Downloads/Scanify/ and return their Uris. */
    fun export(context: Context, pages: List<Bitmap>, name: String): List<Uri> {
        val resolver = context.contentResolver
        return pages.mapIndexed { index, bitmap ->
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "${name}_${index + 1}.jpg")
                put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scanify")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create the JPG file")
            resolver.openOutputStream(uri)!!.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }
    }
}
