package com.wkcw.scanify.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wkcw.scanify.ScanViewModel
import com.wkcw.scanify.scanner.ScanFilter

@Composable
fun ResultScreen(vm: ScanViewModel) {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        vm.preview?.let {
            Image(
                it.asImageBitmap(), null,
                Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                contentScale = ContentScale.Fit
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanFilter.entries.forEach { f ->
                FilterChip(
                    selected = vm.filter == f,
                    onClick = { vm.setFilter(f) },
                    label = { Text(f.label) }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = vm::addPage, Modifier.weight(1f)) { Text("+ Halaman") }
            Button(onClick = {
                vm.savePdf(context) { uri ->
                    Toast.makeText(context, "Tersimpan di Downloads/Scanify", Toast.LENGTH_LONG).show()
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "Bagikan PDF"))
                }
            }, Modifier.weight(1f)) { Text("Simpan PDF") }
        }
    }
}
