package com.wkcw.scanify.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wkcw.scanify.history.ScanHistory
import com.wkcw.scanify.history.ScanRecord
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HomeScreen(
    history: List<ScanRecord>,
    onNewScan: () -> Unit,
    onOpen: (ScanRecord) -> Unit,
    onDelete: (ScanRecord) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        bottomBar = {
            Surface(
                Modifier.navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Home, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Home", style = MaterialTheme.typography.labelLarge)
                    }
                    FloatingActionButton(
                        onClick = onNewScan,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New scan")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("History", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.List, contentDescription = null)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            "Scan documents",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Capture a page, crop it, and save it as PDF.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onNewScan) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("New scan", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Recent scans",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (history.isNotEmpty()) {
                        Text(
                            "${history.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (history.isEmpty()) {
                item {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.List,
                                contentDescription = null,
                                Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No scans yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Your saved PDFs will show up here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(history, key = { it.id }) { record ->
                    ScanHistoryCard(record, onOpen, onDelete)
                }
            }
        }
    }
}

@Composable
private fun ScanHistoryCard(
    record: ScanRecord,
    onOpen: (ScanRecord) -> Unit,
    onDelete: (ScanRecord) -> Unit,
) {
    val date = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(record.timestamp)
    val thumb = ScanHistory.thumbnail(record.thumbnail)

    Surface(
        Modifier.fillMaxWidth().clickable { onOpen(record) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                Modifier.size(76.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                if (thumb != null) {
                    Image(
                        thumb.asImageBitmap(),
                        contentDescription = null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(Icons.Filled.List, contentDescription = null)
                    }
                }
            }

            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${record.pageCount} page(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = { onDelete(record) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete scan")
            }
        }
    }
}
