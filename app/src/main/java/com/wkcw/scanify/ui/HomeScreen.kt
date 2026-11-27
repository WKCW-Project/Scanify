@file:OptIn(ExperimentalScrollBarApi::class)

package com.wkcw.scanify.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wkcw.scanify.R
import com.wkcw.scanify.history.ScanHistory
import com.wkcw.scanify.history.ScanRecord
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Chip
import io.github.suqi8.coui.kmp.basic.ChipGroup
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.VerticalScrollBar
import io.github.suqi8.coui.kmp.basic.rememberScrollBarAdapter
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarSearchState
import io.github.suqi8.coui.kmp.basic.topAppBarSearchContent
import io.github.suqi8.coui.kmp.basic.topAppBarSearchResults
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.File
import io.github.suqi8.coui.kmp.icon.extended.Scan
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.interfaces.ExperimentalScrollBarApi
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.squircle.squircleClip
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private const val FILTER_ALL = 0
private const val FILTER_TODAY = 1
private const val FILTER_WEEK = 2
private const val ONE_DAY_MS = 24L * 60 * 60 * 1000L
private const val ONE_WEEK_MS = 7L * ONE_DAY_MS

@Composable
fun HomeScreen(
    history: List<ScanRecord>,
    @StringRes pendingSnackbarRes: Int?,
    onSnackbarShown: () -> Unit,
    onNewScan: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (ScanRecord) -> Unit,
    onDelete: (ScanRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = rememberCouiHaptic()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedFilter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ScanRecord?>(null) }

    val searchState = rememberTopAppBarSearchState(active = searching)
    val pageListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()

    LaunchedEffect(pendingSnackbarRes) {
        if (pendingSnackbarRes != null) {
            val message = context.getString(pendingSnackbarRes)
            onSnackbarShown()
            snackbarHostState.showSnackbar(message)
        }
    }

    // Clear keyboard focus as soon as the user starts scrolling search results.
    LaunchedEffect(resultsListState.isScrollInProgress) {
        if (resultsListState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    val now = remember(history.size, selectedFilter) { System.currentTimeMillis() }
    val todayCount = remember(history, now) { history.count { now - it.timestamp < ONE_DAY_MS } }
    val weekCount = remember(history, now) { history.count { now - it.timestamp < ONE_WEEK_MS } }

    val visible = remember(history, selectedFilter, now) {
        when (selectedFilter) {
            FILTER_TODAY -> history.filter { now - it.timestamp < ONE_DAY_MS }
            FILTER_WEEK -> history.filter { now - it.timestamp < ONE_WEEK_MS }
            else -> history
        }
    }

    val searchResults = remember(history, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            history
        } else {
            history.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
    }

    CouiPageScaffold(
        title = stringResource(R.string.home_title),
        modifier = modifier,
        topBarStyle = CouiTopBarStyle.SettingsHome,
        wide = isWideWindow(),
        topBarSearchState = searchState,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = COUIIcons.Settings,
                    contentDescription = stringResource(R.string.common_settings),
                    tint = COUITheme.colorScheme.labelPrimary,
                )
            }
        },
        topBarBottomContent = {
            TopAppBarSearchBar(
                query = query,
                onQueryChange = { query = it },
                expanded = searching,
                onExpandedChange = { expanded ->
                    searching = expanded
                    if (!expanded) query = ""
                },
                cancelText = stringResource(R.string.common_cancel),
                label = stringResource(R.string.home_search_hint),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic(CouiHapticEffect.Switch)
                    onNewScan()
                },
                visible = !searching,
            ) {
                Icon(
                    imageVector = COUIIcons.Scan,
                    contentDescription = stringResource(R.string.home_new_scan),
                    tint = COUITheme.colorScheme.onPrimary,
                )
            }
        },
    ) { pageScope ->
        Box(Modifier.fillMaxSize()) {
            if (searchState.isContentVisible) {
                LazyColumn(
                    state = pageListState,
                    modifier = Modifier
                        .couiPageScroll(pageScope)
                        .topAppBarSearchContent(searchState),
                    contentPadding = pageScope.contentPadding,
                    flingBehavior = rememberCOUIFlingBehavior(),
                ) {
                    item(key = "home_filters") {
                        ChipGroup(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                        ) {
                            Chip(
                                selected = selectedFilter == FILTER_ALL,
                                onClick = { selectedFilter = FILTER_ALL },
                                label = stringResource(R.string.home_filter_all),
                                count = history.size.takeIf { it > 0 }?.toString(),
                            )
                            Chip(
                                selected = selectedFilter == FILTER_TODAY,
                                onClick = { selectedFilter = FILTER_TODAY },
                                label = stringResource(R.string.home_filter_today),
                                count = todayCount.takeIf { it > 0 }?.toString(),
                            )
                            Chip(
                                selected = selectedFilter == FILTER_WEEK,
                                onClick = { selectedFilter = FILTER_WEEK },
                                label = stringResource(R.string.home_filter_week),
                                count = weekCount.takeIf { it > 0 }?.toString(),
                            )
                        }
                    }

                    if (visible.isEmpty()) {
                        item(key = "home_empty") {
                            val isFiltered = selectedFilter != FILTER_ALL && history.isNotEmpty()
                            EmptyState(
                                title = stringResource(
                                    if (isFiltered) R.string.home_filter_empty_title else R.string.home_empty_title,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(380.dp),
                                subtitle = stringResource(
                                    if (isFiltered) R.string.home_filter_empty_summary else R.string.home_empty_summary,
                                ),
                                actionText = if (isFiltered) {
                                    stringResource(R.string.home_filter_all)
                                } else {
                                    stringResource(R.string.home_empty_action)
                                },
                                onActionClick = {
                                    if (isFiltered) {
                                        selectedFilter = FILTER_ALL
                                    } else {
                                        onNewScan()
                                    }
                                },
                                illustration = {
                                    Icon(
                                        imageVector = COUIIcons.Scan,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = COUITheme.colorScheme.primary,
                                    )
                                },
                            )
                        }
                    } else {
                        item(key = "home_recent_section") {
                            PreferenceCategory(
                                title = stringResource(R.string.home_recent_scans),
                                titleBadge = visible.size.toString(),
                            )
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                            ) {
                                CardListGroup {
                                    visible.forEach { record ->
                                        item(key = record.id) {
                                            ScanHistoryRow(
                                                record = record,
                                                onOpen = { onOpen(record) },
                                                onDelete = { pendingDelete = record },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Keep the bottom rows clear of the floating action button.
                    item(key = "home_fab_spacer") {
                        Spacer(Modifier.height(96.dp))
                    }
                }
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(pageListState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    trackPadding = pageScope.contentPadding,
                )
            }

            if (searchState.isResultsVisible) {
                LazyColumn(
                    state = resultsListState,
                    modifier = Modifier
                        .couiPageScroll(pageScope)
                        .topAppBarSearchResults(searchState),
                    contentPadding = pageScope.contentPadding,
                    flingBehavior = rememberCOUIFlingBehavior(),
                ) {
                    if (searchResults.isEmpty()) {
                        item(key = "search_empty") {
                            EmptyState(
                                title = stringResource(R.string.home_search_empty_title),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp),
                                subtitle = stringResource(R.string.home_search_empty_summary),
                                illustration = {
                                    Icon(
                                        imageVector = COUIIcons.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = COUITheme.colorScheme.labelTertiary,
                                    )
                                },
                            )
                        }
                    } else {
                        item(key = "search_results_section") {
                            PreferenceCategory(
                                title = stringResource(R.string.home_search_results),
                                titleBadge = searchResults.size.toString(),
                            )
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                            ) {
                                CardListGroup {
                                    searchResults.forEach { record ->
                                        item(key = "search_${record.id}") {
                                            ScanHistoryRow(
                                                record = record,
                                                onOpen = { onOpen(record) },
                                                onDelete = { pendingDelete = record },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "search_tail") { Spacer(Modifier.height(32.dp)) }
                }
            }

            DeleteScanDialog(
                record = pendingDelete,
                onConfirm = { record ->
                    pendingDelete = null
                    onDelete(record)
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.home_deleted_snackbar))
                    }
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

@Composable
private fun ScanHistoryRow(
    record: ScanRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = remember(record.timestamp) {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(record.timestamp)
    }
    val thumb = remember(record.thumbnail) { ScanHistory.thumbnail(record.thumbnail) }
    val pagesLabel = if (record.pageCount == 1) {
        stringResource(R.string.home_page_count_one, record.pageCount)
    } else {
        stringResource(R.string.home_page_count_other, record.pageCount)
    }
    val formatLabel = if (record.name.endsWith(".jpg", ignoreCase = true)) "JPG" else "PDF"

    BasicComponent(
        title = record.name,
        modifier = modifier,
        summary = "$date · $pagesLabel · $formatLabel",
        onClick = onOpen,
        startAction = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .squircleClip(12.dp)
                    .background(COUITheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = COUIIcons.File,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = COUITheme.colorScheme.primary,
                    )
                }
            }
        },
        endActions = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = COUIIcons.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = COUITheme.colorScheme.labelTertiary,
                )
            }
        },
    )
}

@Composable
private fun DeleteScanDialog(
    record: ScanRecord?,
    onConfirm: (ScanRecord) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayDialog(
        show = record != null,
        modifier = modifier,
        title = stringResource(R.string.home_delete_title),
        summary = record?.let { stringResource(R.string.home_delete_summary, it.name) },
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = blur != null,
        onDismissRequest = onDismiss,
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
            ),
            positive = null,
            neutral = DialogButtonBarAction(
                text = stringResource(R.string.common_delete),
                onClick = { record?.let(onConfirm) },
            ),
        )
    }
}
