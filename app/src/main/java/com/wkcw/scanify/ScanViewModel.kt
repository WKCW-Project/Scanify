package com.wkcw.scanify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wkcw.scanify.history.ScanHistory
import com.wkcw.scanify.history.ScanRecord
import com.wkcw.scanify.scanner.DocumentScanner
import com.wkcw.scanify.scanner.PdfExporter
import com.wkcw.scanify.scanner.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, CAMERA, CROP, RESULT }

class ScanViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.HOME); private set
    var busy by mutableStateOf(false); private set
    var autoDetect by mutableStateOf(true); private set
    var autoCapture by mutableStateOf(true); private set

    var captured by mutableStateOf<Bitmap?>(null); private set
    var corners by mutableStateOf<List<PointF>>(emptyList()); private set
    var preview by mutableStateOf<Bitmap?>(null); private set
    var filter by mutableStateOf(ScanFilter.BW); private set

    val history = mutableStateListOf<ScanRecord>()

    private var warped: Bitmap? = null
    val pages = mutableStateListOf<Bitmap>()

    fun refreshHistory(context: Context) {
        history.clear()
        history.addAll(ScanHistory.load(context))
    }

    fun newScan() {
        screen = Screen.CAMERA
    }

    fun setAutoDetect(value: Boolean) {
        autoDetect = value
    }

    fun setAutoCapture(value: Boolean) {
        autoCapture = value
    }

    fun goHome() {
        screen = Screen.HOME
    }

    fun deleteScan(context: Context, record: ScanRecord) {
        ScanHistory.remove(context, record)
        refreshHistory(context)
    }

    fun onCaptured(bmp: Bitmap) = viewModelScope.launch {
        busy = true
        val detected = withContext(Dispatchers.Default) { DocumentScanner.findDocument(bmp) }
        captured = bmp
        corners = detected ?: withContext(Dispatchers.Default) { DocumentScanner.detectCorners(bmp) }
        if (autoDetect && detected != null) {
            warped = withContext(Dispatchers.Default) { DocumentScanner.warp(bmp, detected) }
            preview = withContext(Dispatchers.Default) { DocumentScanner.applyFilter(warped!!, filter) }
            screen = Screen.RESULT
        } else {
            screen = Screen.CROP
        }
        busy = false
    }

    fun confirmCrop(finalCorners: List<PointF>) = viewModelScope.launch {
        busy = true
        corners = finalCorners
        warped = withContext(Dispatchers.Default) { DocumentScanner.warp(captured!!, finalCorners) }
        preview = withContext(Dispatchers.Default) { DocumentScanner.applyFilter(warped!!, filter) }
        screen = Screen.RESULT
        busy = false
    }

    fun setFilter(f: ScanFilter) = viewModelScope.launch {
        filter = f
        busy = true
        preview = withContext(Dispatchers.Default) { DocumentScanner.applyFilter(warped!!, f) }
        busy = false
    }

    /** Save this page and return to the camera for the next one. */
    fun addPage() {
        preview?.let { pages.add(it) }
        screen = Screen.CAMERA
    }

    fun savePdf(context: Context, onDone: (Uri) -> Unit) = viewModelScope.launch {
        busy = true
        preview?.let { pages.add(it) }
        val allPages = pages.toList()
        val name = PdfExporter.newName()
        val uri = withContext(Dispatchers.IO) { PdfExporter.export(context, allPages, name) }
        val id = uri.lastPathSegment?.toLongOrNull() ?: System.currentTimeMillis()
        val thumb = withContext(Dispatchers.IO) { ScanHistory.saveThumbnail(context, id, allPages.first()) }
        ScanHistory.add(
            context,
            ScanRecord(id, name, System.currentTimeMillis(), allPages.size, uri.toString(), thumb),
        )
        refreshHistory(context)
        pages.clear()
        busy = false
        screen = Screen.HOME
        onDone(uri)
    }

    fun back() {
        screen = when (screen) {
            Screen.RESULT -> Screen.CROP
            Screen.CROP -> Screen.CAMERA
            else -> Screen.HOME
        }
    }
}
