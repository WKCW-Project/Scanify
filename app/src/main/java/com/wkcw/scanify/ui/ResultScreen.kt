package com.wkcw.scanify.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
        Surface(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Box(Modifier.fillMaxSize().padding(8.dp), Alignment.Center) {
                val preview = vm.preview
                if (preview != null) {
                    Image(
                        preview.asImageBitmap(), null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        "No preview available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Rotate", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { vm.rotate(-90) }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Rotate left",
                    modifier = Modifier.scale(scaleX = -1f, scaleY = 1f),
                )
            }
            IconButton(onClick = { vm.rotate(90) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Rotate right")
            }
            TextButton(onClick = vm::back) { Text("Adjust crop") }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanFilter.entries.forEach { filter ->
                FilterChip(
                    selected = vm.filter == filter,
                    onClick = { vm.setFilter(filter) },
                    label = { Text(filter.label) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = vm::addPage, Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add page", Modifier.padding(start = 4.dp))
            }
            Button(onClick = {
                vm.savePdf(context) {
                    Toast.makeText(context, "Saved to Downloads/Scanify", Toast.LENGTH_LONG).show()
                }
            }, Modifier.weight(1f)) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text("Save PDF", Modifier.padding(start = 4.dp))
            }
        }
    }
}
