package com.wkcw.scanify.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wkcw.scanify.ScanViewModel
import com.wkcw.scanify.scanner.ScanFilter

@Composable
fun ResultScreen(vm: ScanViewModel) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp)) {
            Text(
                if (vm.pages.isEmpty()) "Page ready" else "Page ${vm.pages.size + 1} ready",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Check the scan, then save it or capture another page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.White,
            shadowElevation = 2.dp,
        ) {
            Box(Modifier.fillMaxSize().padding(12.dp), Alignment.Center) {
                val preview = vm.preview
                if (preview != null) {
                    Image(
                        preview.asImageBitmap(), null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
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

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Rotate",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
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

                Spacer(Modifier.height(12.dp))
                Text("Filter", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = vm::addPage, Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add page", Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = {
                    vm.savePdf(context) {
                        Toast.makeText(context, "Saved to Downloads/Scanify", Toast.LENGTH_LONG).show()
                    }
                },
                Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text("Save PDF", Modifier.padding(start = 6.dp))
            }
        }

        TextButton(
            onClick = {
                vm.saveJpg(context) {
                    Toast.makeText(context, "Saved JPG to Downloads/Scanify", Toast.LENGTH_LONG).show()
                }
            },
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp),
        ) {
            Text("Save as JPG")
        }
    }
}
