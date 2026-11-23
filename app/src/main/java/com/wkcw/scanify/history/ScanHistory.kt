package com.wkcw.scanify.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

data class ScanRecord(
    val id: Long,
    val name: String,
    val timestamp: Long,
    val pageCount: Int,
    val uri: String,
    val thumbnail: String,
)

/** Stores scan metadata and thumbnails in app-private storage. */
object ScanHistory {
    private const val FILE_NAME = "scan_history.txt"
    private const val DELIMITER = "|"

    fun load(context: Context): List<ScanRecord> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.split(DELIMITER, limit = 6)
            if (parts.size != 6) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            val timestamp = parts[2].toLongOrNull() ?: return@mapNotNull null
            val pageCount = parts[3].toIntOrNull() ?: return@mapNotNull null
            ScanRecord(id, parts[1], timestamp, pageCount, parts[4], parts[5])
        }.sortedByDescending { it.timestamp }
    }

    fun add(context: Context, record: ScanRecord) {
        File(context.filesDir, FILE_NAME).appendText(serialize(record) + "\n")
    }

    fun remove(context: Context, record: ScanRecord) {
        val remaining = load(context).filterNot { it.id == record.id }
        File(context.filesDir, FILE_NAME).writeLines(remaining.map(::serialize))
        File(record.thumbnail).delete()
        runCatching { context.contentResolver.delete(Uri.parse(record.uri), null, null) }
    }

    fun saveThumbnail(context: Context, id: Long, source: Bitmap): String {
        val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
        val file = File(dir, "$id.jpg")
        val maxSide = 480
        val scale = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height))
        val thumb = if (scale == 1f) {
            source
        } else {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt(),
                (source.height * scale).toInt(),
                true,
            )
        }
        file.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return file.absolutePath
    }

    fun thumbnail(path: String): Bitmap? = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()

    private fun serialize(record: ScanRecord) = listOf(
        record.id.toString(),
        record.name,
        record.timestamp.toString(),
        record.pageCount.toString(),
        record.uri,
        record.thumbnail,
    ).joinToString(DELIMITER)
}
