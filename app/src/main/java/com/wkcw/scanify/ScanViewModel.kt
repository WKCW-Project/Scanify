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
import com.wkcw.scanify.scanner.DocumentScanner
import com.wkcw.scanify.scanner.PdfExporter
import com.wkcw.scanify.scanner.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { CAMERA, CROP, RESULT }

class ScanViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.CAMERA); private set
    var busy by mutableStateOf(false); private set

    var captured by mutableStateOf<Bitmap?>(null); private set
    var corners by mutableStateOf<List<PointF>>(emptyList()); private set
    var preview by mutableStateOf<Bitmap?>(null); private set
    var filter by mutableStateOf(ScanFilter.BW); private set

    private var warped: Bitmap? = null
    val pages = mutableStateListOf<Bitmap>()

    fun onCaptured(bmp: Bitmap) = viewModelScope.launch {
        busy = true
        corners = withContext(Dispatchers.Default) { DocumentScanner.detectCorners(bmp) }
        captured = bmp
        screen = Screen.CROP
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

    /** Simpan halaman ini, balik ke kamera buat scan halaman berikutnya. */
    fun addPage() {
        preview?.let { pages.add(it) }
        screen = Screen.CAMERA
    }

    fun savePdf(context: Context, onDone: (Uri) -> Unit) = viewModelScope.launch {
        busy = true
        preview?.let { pages.add(it) }
        val uri = withContext(Dispatchers.IO) { PdfExporter.export(context, pages.toList()) }
        pages.clear()
        busy = false
        screen = Screen.CAMERA
        onDone(uri)
    }

    fun back() {
        screen = when (screen) {
            Screen.RESULT -> Screen.CROP
            else -> Screen.CAMERA
        }
    }
}
