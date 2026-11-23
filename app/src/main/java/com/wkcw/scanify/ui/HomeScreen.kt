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
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val filters = listOf("All", "Today", "This week")

@Composable
fun HomeScreen(
    history: List<ScanRecord>,
    onNewScan: () -> Unit,
    onOpen: (ScanRecord) -> Unit,
    onDelete: (ScanRecord) -> Unit,
) {
    var selectedFilter by remember { mutableIntStateOf(0) }
    val visible = remember(history, selectedFilter) {
        val now = System.currentTimeMillis()
        when (selectedFilter) {
            1 -> history.filter { now - it.timestamp < 24 * 60 * 60 * 1000L }
            2 -> history.filter { now - it.timestamp < 7 * 24 * 60 * 60 * 1000L }
            else -> history
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        bottomBar = {
            // Reserved for the bottom bar design; intentionally empty for now.
            Surface(
                Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {}
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "My scans",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Capture, crop, and save documents.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = onNewScan) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("New scan", Modifier.padding(start = 6.dp))
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedFilter == index,
                            onClick = { selectedFilter = index },
                            label = { Text(label) },
                        )
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
                    if (visible.isNotEmpty()) {
                        Text(
                            "${visible.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
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
                items(visible, key = { it.id }) { record ->
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
