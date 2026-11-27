package com.wkcw.scanify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wkcw.scanify.history.ScanHistory
import com.wkcw.scanify.history.ScanRecord
import com.wkcw.scanify.scanner.DocumentScanner
import com.wkcw.scanify.scanner.ImageExporter
import com.wkcw.scanify.scanner.PdfExporter
import com.wkcw.scanify.scanner.ScanFilter
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "scanify_prefs"
private const val KEY_AUTO_DETECT = "auto_detect"
private const val KEY_AUTO_CAPTURE = "auto_capture"
private const val KEY_COLOR_MODE = "color_mode"
private const val KEY_MATERIAL_BLUR = "material_blur"

class ScanViewModel : ViewModel() {
    var busy by mutableStateOf(false); private set
    var autoDetect by mutableStateOf(true); private set
    var autoCapture by mutableStateOf(true); private set
    var colorMode by mutableStateOf(ColorSchemeMode.System); private set
    var materialBlurEnabled by mutableStateOf(true); private set
    @get:StringRes
    var pendingSnackbarRes by mutableStateOf<Int?>(null); private set

    var captured by mutableStateOf<Bitmap?>(null); private set
    var corners by mutableStateOf<List<PointF>>(emptyList()); private set
    var preview by mutableStateOf<Bitmap?>(null); private set
    var filter by mutableStateOf(ScanFilter.BW); private set
    var rotation by mutableIntStateOf(0); private set

    val history = mutableStateListOf<ScanRecord>()

    private var baseWarp: Bitmap? = null
    private var editedWarp: Bitmap? = null
    val pages = mutableStateListOf<Bitmap>()

    private var prefsLoaded = false

    fun loadPreferences(context: Context) {
        if (prefsLoaded) return
        prefsLoaded = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        autoDetect = prefs.getBoolean(KEY_AUTO_DETECT, true)
        autoCapture = prefs.getBoolean(KEY_AUTO_CAPTURE, true)
        materialBlurEnabled = prefs.getBoolean(KEY_MATERIAL_BLUR, true)
        val savedMode = prefs.getString(KEY_COLOR_MODE, null)
        colorMode = ColorSchemeMode.entries.firstOrNull { it.name == savedMode } ?: ColorSchemeMode.System
    }

    fun refreshHistory(context: Context) {
        history.clear()
        history.addAll(ScanHistory.load(context))
    }

    fun updateAutoDetect(context: Context, value: Boolean) {
        autoDetect = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_DETECT, value).apply()
    }

    fun updateAutoCapture(context: Context, value: Boolean) {
        autoCapture = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_CAPTURE, value).apply()
    }

    fun updateColorMode(context: Context, mode: ColorSchemeMode) {
        colorMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COLOR_MODE, mode.name).apply()
    }

    fun updateMaterialBlur(context: Context, enabled: Boolean) {
        materialBlurEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MATERIAL_BLUR, enabled).apply()
    }

    fun consumePendingSnackbar() {
        pendingSnackbarRes = null
    }

    fun deleteScan(context: Context, record: ScanRecord) {
        ScanHistory.remove(context, record)
        refreshHistory(context)
    }

    fun onCaptured(
        bmp: Bitmap,
        hintNormalizedCorners: List<PointF>? = null,
        onNavigateToResult: () -> Unit,
        onNavigateToCrop: () -> Unit,
    ) = viewModelScope.launch {
        busy = true
        val detected = withContext(Dispatchers.Default) { DocumentScanner.findDocument(bmp, fast = false) }
        val hintMapped = hintNormalizedCorners
            ?.takeIf { it.size == 4 }
            ?.map { PointF(it.x * bmp.width, it.y * bmp.height) }
            ?.let(DocumentScanner::orderPoints)
        val bestCorners = detected ?: hintMapped
        captured = bmp
        corners = bestCorners ?: withContext(Dispatchers.Default) { DocumentScanner.detectCorners(bmp) }
        if (autoDetect && bestCorners != null) {
            val warped = withContext(Dispatchers.Default) { DocumentScanner.warp(bmp, bestCorners) }
            setWarped(warped)
            busy = false
            onNavigateToResult()
        } else {
            busy = false
            onNavigateToCrop()
        }
    }

    fun confirmCrop(
        finalCorners: List<PointF>,
        onNavigateToResult: () -> Unit,
    ) = viewModelScope.launch {
        val source = captured ?: return@launch
        busy = true
        corners = finalCorners
        val warped = withContext(Dispatchers.Default) { DocumentScanner.warp(source, finalCorners) }
        setWarped(warped)
        busy = false
        onNavigateToResult()
    }

    private suspend fun setWarped(warped: Bitmap) {
        baseWarp = warped
        editedWarp = warped
        rotation = 0
        preview = withContext(Dispatchers.Default) { DocumentScanner.applyFilter(warped, filter) }
    }

    fun rotate(degrees: Int) = viewModelScope.launch {
        val base = baseWarp ?: return@launch
        rotation = (rotation + degrees + 360) % 360
        busy = true
        editedWarp = withContext(Dispatchers.Default) { DocumentScanner.rotate(base, rotation) }
        preview = withContext(Dispatchers.Default) { DocumentScanner.applyFilter(editedWarp!!, filter) }
        busy = false
    }

    fun setFilter(f: ScanFilter) = viewModelScope.launch {
        filter = f
        val source = editedWarp ?: return@launch
        busy = true
        preview = withContext(Dispatchers.Default) { DocumentScanner.applyFilter(source, f) }
        busy = false
    }

    /** Save this page and return to the camera for the next one. */
    fun addPage(onNavigateToCamera: () -> Unit) {
        preview?.let { pages.add(it) }
        onNavigateToCamera()
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
        pendingSnackbarRes = R.string.home_saved_pdf_snackbar
        busy = false
        onDone(uri)
    }

    fun saveJpg(context: Context, onDone: () -> Unit) = viewModelScope.launch {
        busy = true
        preview?.let { pages.add(it) }
        val allPages = pages.toList()
        val name = PdfExporter.newName()
        val uris = withContext(Dispatchers.IO) { ImageExporter.export(context, allPages, name) }
        uris.forEachIndexed { index, uri ->
            val id = uri.lastPathSegment?.toLongOrNull() ?: System.currentTimeMillis() + index
            val thumb = withContext(Dispatchers.IO) {
                ScanHistory.saveThumbnail(context, id, allPages[index])
            }
            ScanHistory.add(
                context,
                ScanRecord(
                    id = id,
                    name = "${name}_${index + 1}.jpg",
                    timestamp = System.currentTimeMillis() + index,
                    pageCount = 1,
                    uri = uri.toString(),
                    thumbnail = thumb,
                ),
            )
        }
        refreshHistory(context)
        pages.clear()
        pendingSnackbarRes = R.string.home_saved_jpg_snackbar
        busy = false
        onDone()
    }
}
