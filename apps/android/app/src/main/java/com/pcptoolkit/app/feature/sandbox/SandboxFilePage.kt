/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * @file SandboxFilePage.kt
 * @brief 沙箱应用文件管理界面。
 */
package com.pcptoolkit.app.feature.sandbox

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pcptoolkit.app.feature.editor.SandboxTextEditorTarget
import com.pcptoolkit.app.feature.editor.TextEditorTarget
import com.pcptoolkit.app.t
import com.pcptoolkit.app.ui.PcpBatchDeleteDialog
import com.pcptoolkit.app.ui.PcpCategoryTabs
import com.pcptoolkit.app.ui.PcpSelectableFileRow
import com.pcptoolkit.app.ui.PcpSelectionToolBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.fake.provider.FileProvider as BlackBoxFileProvider
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

@Composable
fun SandboxFilePage(
    app: SandboxApp,
    onBack: () -> Unit,
    onEditText: (TextEditorTarget) -> Unit,
    onToast: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var area by remember { mutableStateOf(SandboxFileArea.APK) }
    var relativePath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<SandboxFileEntry>()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<List<SandboxFileEntry>?>(null) }
    var pendingDelete by remember { mutableStateOf<List<SandboxFileEntry>?>(null) }
    var deleteBatchMode by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<SandboxFileEntry?>(null) }
    var pendingProperties by remember { mutableStateOf<SandboxFileProperties?>(null) }
    var pendingRename by remember { mutableStateOf<SandboxFileEntry?>(null) }
    var renameName by remember { mutableStateOf("") }
    var transferRequest by remember { mutableStateOf<FileTransferRequest?>(null) }
    var transferVisible by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newEntryName by remember { mutableStateOf("") }
    var pendingImportConflict by remember { mutableStateOf<PendingSandboxImport?>(null) }
    var importConflictPolicy by remember {
        mutableStateOf<SandboxImportConflictAction?>(null)
    }
    val backHistory = remember { mutableStateListOf<String>() }
    val forwardHistory = remember { mutableStateListOf<String>() }
    val bookmarkPrefs = remember(context) {
        context.getSharedPreferences("sandbox_file_bookmarks", Context.MODE_PRIVATE)
    }
    var bookmarkedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionAnchor by remember { mutableStateOf<String?>(null) }

    fun closeTransfer() {
        transferVisible = false
        scope.launch {
            delay(280)
            if (!transferVisible) transferRequest = null
        }
    }

    fun reportFailure(action: String, error: Throwable) {
        onToast(
            "$action: ${error.message ?: error.javaClass.simpleName}",
            true
        )
    }

    fun bookmarkKey(targetArea: SandboxFileArea, targetPath: String): String =
        "${app.packageName}|${targetArea.name}|$targetPath"

    fun bookmarksFor(
        targetArea: SandboxFileArea,
        targetPath: String
    ): Set<String> = bookmarkPrefs
        .getStringSet(bookmarkKey(targetArea, targetPath), emptySet())
        .orEmpty()
        .toSet()

    fun toggleBookmark(entry: SandboxFileEntry): Boolean {
        val next = if (entry.relativePath in bookmarkedPaths) {
            bookmarkedPaths - entry.relativePath
        } else {
            bookmarkedPaths + entry.relativePath
        }
        bookmarkPrefs.edit()
            .putStringSet(bookmarkKey(area, relativePath), next)
            .apply()
        bookmarkedPaths = next
        return entry.relativePath in next
    }

    fun clearBookmark(
        targetArea: SandboxFileArea,
        targetPath: String,
        entryPath: String
    ) {
        val key = bookmarkKey(targetArea, targetPath)
        val current = bookmarkPrefs.getStringSet(key, emptySet())
            .orEmpty()
            .toSet()
        if (entryPath !in current) return
        val next = current - entryPath
        bookmarkPrefs.edit().putStringSet(key, next).apply()
        if (targetArea == area && targetPath == relativePath) {
            bookmarkedPaths = next
        }
    }

    fun clearSelection() {
        selectedPaths = emptySet()
        selectionAnchor = null
    }

    fun reload(showRefreshIndicator: Boolean = false) {
        val listArea = area
        val listPath = relativePath
        if (showRefreshIndicator) {
            refreshing = true
        } else {
            loading = true
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val files = SandboxFileRepository.list(
                        app.packageName,
                        listArea,
                        listPath
                    )
                    val marked = bookmarksFor(listArea, listPath)
                    val validPaths = files.mapTo(mutableSetOf()) { it.relativePath }
                    val pruned = marked.intersect(validPaths)
                    if (pruned != marked) {
                        bookmarkPrefs.edit()
                            .putStringSet(
                                bookmarkKey(listArea, listPath),
                                pruned
                            )
                            .apply()
                    }
                    files.sortedWith(
                        compareByDescending<SandboxFileEntry> {
                            it.relativePath in pruned
                        }.thenByDescending { it.isDirectory }
                            .thenBy { it.name.lowercase(Locale.ROOT) }
                    ) to pruned
                }
            }
            result.onSuccess { (loaded, marked) ->
                entries = loaded
                bookmarkedPaths = marked
                val validPaths = loaded.mapTo(mutableSetOf()) { it.relativePath }
                selectedPaths = selectedPaths.intersect(validPaths)
                if (selectedPaths.isEmpty()) selectionAnchor = null
            }
                .onFailure {
                    entries = emptyList()
                    bookmarkedPaths = emptySet()
                    reportFailure(t("sandbox.files.read_failed"), it)
                }
            loading = false
            refreshing = false
        }
    }

    fun navigateTo(path: String) {
        if (path == relativePath) return
        clearSelection()
        backHistory.add(relativePath)
        forwardHistory.clear()
        relativePath = path
    }

    fun navigateBack() {
        if (backHistory.isEmpty()) return
        clearSelection()
        forwardHistory.add(relativePath)
        relativePath = backHistory.removeAt(backHistory.lastIndex)
    }

    fun navigateForward() {
        if (forwardHistory.isEmpty()) return
        clearSelection()
        backHistory.add(relativePath)
        relativePath = forwardHistory.removeAt(forwardHistory.lastIndex)
    }

    fun navigateUp() {
        if (relativePath.isBlank()) return
        navigateTo(parentPath(relativePath))
    }

    fun switchArea(nextArea: SandboxFileArea) {
        if (nextArea == area) return
        clearSelection()
        area = nextArea
        relativePath = ""
        backHistory.clear()
        forwardHistory.clear()
    }

    fun selectBySwipe(entry: SandboxFileEntry) {
        if (entry.relativePath in selectedPaths) return
        val anchor = selectionAnchor
        val targetIndex = entries.indexOfFirst { it.relativePath == entry.relativePath }
        val anchorIndex = entries.indexOfFirst { it.relativePath == anchor }
        if (anchor != null && anchorIndex >= 0 && targetIndex >= 0) {
            val first = minOf(anchorIndex, targetIndex)
            val last = maxOf(anchorIndex, targetIndex)
            selectedPaths = selectedPaths + entries
                .subList(first, last + 1)
                .map { it.relativePath }
            selectionAnchor = null
        } else {
            selectedPaths = selectedPaths + entry.relativePath
            selectionAnchor = entry.relativePath
        }
    }

    fun handleEntryClick(entry: SandboxFileEntry) {
        if (selectedPaths.isNotEmpty()) {
            selectedPaths = if (entry.relativePath in selectedPaths) {
                selectedPaths - entry.relativePath
            } else {
                selectedPaths + entry.relativePath
            }
            selectionAnchor = entry.relativePath.takeIf {
                entry.relativePath in selectedPaths
            }
            return
        }
        if (entry.isDirectory) {
            navigateTo(entry.relativePath)
        } else if (entry.isEditableText) {
            onEditText(
                SandboxTextEditorTarget(
                    packageName = app.packageName,
                    area = area,
                    relativePath = entry.relativePath,
                    displayName = entry.name
                )
            )
        }
    }

    BackHandler {
        when {
            transferVisible -> {
                closeTransfer()
                reload()
            }
            selectedPaths.isNotEmpty() -> clearSelection()
            relativePath.isNotBlank() -> navigateUp()
            else -> onBack()
        }
    }

    fun importCandidate(
        candidate: PendingSandboxImport,
        action: SandboxImportConflictAction
    ) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.importFile(
                        context,
                        app.packageName,
                        candidate.area,
                        candidate.directoryPath,
                        candidate.source,
                        candidate.displayName,
                        action
                    )
                }
            }
            busy = false
            result.onSuccess { reload() }
                .onFailure {
                    reportFailure(t("sandbox.files.import_failed"), it)
                }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val importArea = area
            val importPath = relativePath
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        SandboxFileRepository.inspectImport(
                            context,
                            app.packageName,
                            importArea,
                            importPath,
                            uri
                        )
                    }
                }
                busy = false
                result.onSuccess { candidate ->
                    val pending = PendingSandboxImport(
                        source = uri,
                        area = importArea,
                        directoryPath = importPath,
                        displayName = candidate.displayName
                    )
                    if (!candidate.targetExists) {
                        importCandidate(pending, SandboxImportConflictAction.REPLACE)
                    } else {
                        val policy = importConflictPolicy
                        if (policy == null) {
                            pendingImportConflict = pending
                        } else {
                            importCandidate(pending, policy)
                        }
                    }
                }.onFailure {
                    reportFailure(t("sandbox.files.import_failed"), it)
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val target = pendingExport
        pendingExport = null
        if (uri != null && target != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        SandboxFileRepository.exportEntriesToUri(
                            context,
                            app.packageName,
                            area,
                            target,
                            uri
                        )
                    }
                }
                busy = false
                result.onSuccess {
                    clearSelection()
                }.onFailure {
                    reportFailure(t("sandbox.files.export_failed"), it)
                }
            }
        }
    }

    fun createEntry(isDirectory: Boolean) {
        if (newEntryName.isBlank()) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.createEntry(
                        app.packageName,
                        area,
                        relativePath,
                        newEntryName,
                        isDirectory
                    )
                }
            }
            busy = false
            result.onSuccess {
                showCreateDialog = false
                newEntryName = ""
                reload()
            }.onFailure {
                reportFailure(t("sandbox.files.create_failed"), it)
            }
        }
    }

    fun renameEntry() {
        val entry = pendingRename ?: return
        val newName = renameName.trim()
        if (newName.isBlank()) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.rename(
                        app.packageName,
                        area,
                        entry,
                        newName
                    )
                }
            }
            busy = false
            result.onSuccess {
                pendingRename = null
                renameName = ""
                reload()
            }.onFailure {
                reportFailure(t("sandbox.files.rename_failed"), it)
            }
        }
    }

    fun showProperties(entry: SandboxFileEntry) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.getProperties(app.packageName, area, entry)
                }
            }
            busy = false
            result.onSuccess { pendingProperties = it }
                .onFailure {
                    reportFailure(t("sandbox.files.properties_failed"), it)
                }
        }
    }

    fun shareEntries(entries: List<SandboxFileEntry>) {
        if (entries.isEmpty() || entries.any { it.isDirectory }) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.prepareShareFiles(
                        context,
                        app.packageName,
                        area,
                        entries
                    )
                }
            }
            busy = false
            result.onSuccess { files ->
                runCatching {
                    val authority = context.packageName + ".blackbox.FileProvider"
                    val uris = files.mapTo(ArrayList()) { file ->
                        BlackBoxFileProvider.getUriForFile(context, authority, file)
                    }
                    val sendIntent = Intent(
                        if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                    ).apply {
                        type = "*/*"
                        if (uris.size == 1) {
                            putExtra(Intent.EXTRA_STREAM, uris.first())
                        } else {
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, t("sandbox.files.share"))
                    )
                    clearSelection()
                }.onFailure {
                    reportFailure(t("sandbox.files.share_failed"), it)
                }
            }.onFailure {
                reportFailure(t("sandbox.files.share_failed"), it)
            }
        }
    }

    LaunchedEffect(app.packageName, area, relativePath) {
        reload()
    }

    if (showAddDialog) {
        AddFileDialog(
            onNew = {
                showAddDialog = false
                showCreateDialog = true
            },
            onImport = {
                showAddDialog = false
                importLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showCreateDialog) {
        CreateEntryDialog(
            name = newEntryName,
            onNameChange = { newEntryName = it },
            onFile = { createEntry(isDirectory = false) },
            onDirectory = { createEntry(isDirectory = true) },
            onDismiss = {
                showCreateDialog = false
                newEntryName = ""
            }
        )
    }

    pendingAction?.let { entry ->
        val actionEntries = if (selectedPaths.isNotEmpty() &&
            entry.relativePath in selectedPaths
        ) {
            entries.filter { it.relativePath in selectedPaths }
        } else {
            listOf(entry)
        }
        FileActionDialog(
            entries = actionEntries,
            batchMode = selectedPaths.isNotEmpty(),
            onCopy = {
                pendingAction = null
                transferRequest = FileTransferRequest(
                    entries = actionEntries,
                    batchMode = selectedPaths.isNotEmpty(),
                    sourceArea = area,
                    sourcePath = relativePath,
                    mode = FileTransferMode.COPY
                )
                transferVisible = true
            },
            onMove = {
                pendingAction = null
                transferRequest = FileTransferRequest(
                    entries = actionEntries,
                    batchMode = selectedPaths.isNotEmpty(),
                    sourceArea = area,
                    sourcePath = relativePath,
                    mode = FileTransferMode.MOVE
                )
                transferVisible = true
            },
            onExport = {
                pendingAction = null
                pendingExport = actionEntries
                exportLauncher.launch(
                    if (actionEntries.size > 1) {
                        "PCP-export.zip"
                    } else {
                        val target = actionEntries.first()
                        if (target.isDirectory) "${target.name}.zip" else target.name
                    }
                )
            },
            onProperties = {
                if (actionEntries.size == 1 && selectedPaths.isEmpty()) {
                    pendingAction = null
                    showProperties(actionEntries.first())
                }
            },
            onRename = {
                if (actionEntries.size == 1 && selectedPaths.isEmpty()) {
                    pendingAction = null
                    pendingRename = actionEntries.first()
                    renameName = actionEntries.first().name
                }
            },
            onShare = {
                pendingAction = null
                shareEntries(actionEntries)
            },
            onDelete = {
                pendingAction = null
                deleteBatchMode = selectedPaths.isNotEmpty()
                pendingDelete = actionEntries
            },
            onDismiss = { pendingAction = null }
        )
    }

    pendingRename?.let {
        RenameEntryDialog(
            name = renameName,
            onNameChange = { renameName = it },
            onConfirm = ::renameEntry,
            onDismiss = {
                pendingRename = null
                renameName = ""
            }
        )
    }

    pendingProperties?.let { properties ->
        PropertiesDialog(
            properties = properties,
            onDismiss = { pendingProperties = null }
        )
    }

    pendingImportConflict?.let { candidate ->
        FileConflictDialog(
            fileName = candidate.displayName,
            onDismiss = { pendingImportConflict = null },
            onConfirm = { action, applyToFollowing ->
                pendingImportConflict = null
                importConflictPolicy = if (applyToFollowing) action else null
                importCandidate(candidate, action)
            }
        )
    }

    pendingDelete?.let { files ->
        DeleteEntryDialog(
            entries = files,
            batchMode = deleteBatchMode,
            onConfirm = {
                pendingDelete = null
                deleteBatchMode = false
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            files.forEach {
                                SandboxFileRepository.delete(app.packageName, area, it)
                            }
                        }
                    }
                    busy = false
                    result.onSuccess {
                        clearSelection()
                        reload()
                    }.onFailure {
                        clearSelection()
                        reload()
                        reportFailure(t("sandbox.files.delete_failed"), it)
                    }
                }
            },
            onDismiss = {
                pendingDelete = null
                deleteBatchMode = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Spacer(Modifier.height(8.dp))
            PcpCategoryTabs(
                labels = SandboxFileArea.entries.map { t(it.titleKey) },
                selectedIndex = area.ordinal,
                onSelect = { switchArea(SandboxFileArea.entries[it]) }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = displayPath(area, relativePath),
                    modifier = Modifier.padding(
                        start = 26.dp,
                        end = 26.dp,
                        top = 8.dp,
                        bottom = 6.dp
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis
                )

                if (busy) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(t("sandbox.files.working"))
                    }
                }

                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { reload(showRefreshIndicator = true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 76.dp)
                    ) {
                        if (relativePath.isNotBlank()) {
                            item(key = "parent-directory") {
                                SandboxParentRow(
                                    enabled = selectedPaths.isEmpty(),
                                    onClick = ::navigateUp
                                )
                            }
                        }
                        when {
                            loading -> item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            entries.isEmpty() -> item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = t("sandbox.files.empty"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> items(entries, key = { it.relativePath }) { entry ->
                                PcpSelectableFileRow(
                                    itemKey = entry.relativePath,
                                    bookmarked = entry.relativePath in bookmarkedPaths,
                                    selected = entry.relativePath in selectedPaths,
                                    selectionMode = selectedPaths.isNotEmpty(),
                                    onOpen = { handleEntryClick(entry) },
                                    onLongPress = if (selectedPaths.isEmpty() ||
                                        entry.relativePath in selectedPaths
                                    ) {
                                        { pendingAction = entry }
                                    } else {
                                        null
                                    },
                                    onSelect = { selectBySwipe(entry) },
                                    onBookmark = { toggleBookmark(entry) }
                                ) {
                                    FileEntryIcon(entry = entry, apkIcon = app.icon)
                                    Spacer(Modifier.width(12.dp))
                                    FileEntryInfo(
                                        entry = entry,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }

        if (selectedPaths.isEmpty()) {
            FileToolBar(
                canGoBack = backHistory.isNotEmpty(),
                canGoForward = forwardHistory.isNotEmpty(),
                canGoUp = relativePath.isNotBlank(),
                enabled = !busy,
                onBack = ::navigateBack,
                onForward = ::navigateForward,
                onAdd = { showAddDialog = true },
                onUp = ::navigateUp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        } else {
            PcpSelectionToolBar(
                enabled = !busy,
                onSelectAll = {
                    selectedPaths = entries.mapTo(mutableSetOf()) { it.relativePath }
                    selectionAnchor = null
                },
                onClear = ::clearSelection,
                onInvert = {
                    selectedPaths = entries
                        .mapTo(mutableSetOf()) { it.relativePath }
                        .minus(selectedPaths)
                    selectionAnchor = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }

    if (transferRequest != null) {
        Dialog(
            onDismissRequest = {
                closeTransfer()
                reload()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = transferVisible,
                    enter = fadeIn(tween(140)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.32f))
                            .clickable(
                                interactionSource = remember {
                                    androidx.compose.foundation.interaction.MutableInteractionSource()
                                },
                                indication = null
                            ) {
                                closeTransfer()
                                reload()
                            }
                    )
                }

                AnimatedVisibility(
                    visible = transferVisible,
                    enter = slideInVertically(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = 80,
                            easing = FastOutSlowInEasing
                        ),
                        initialOffsetY = { it }
                    ),
                    exit = slideOutVertically(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        targetOffsetY = { it }
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                ) {
                    transferRequest?.let { request ->
                        FileTransferSheet(
                            app = app,
                            request = request,
                            bookmarksFor = ::bookmarksFor,
                            onClose = {
                                closeTransfer()
                                reload()
                            },
                            onToast = onToast,
                            onCompleted = { mode, _, _, transferredCount ->
                                closeTransfer()
                                clearSelection()
                                reload()
                                if (transferredCount > 0) {
                                    onToast(
                                        t(
                                            if (mode == FileTransferMode.COPY) {
                                                "sandbox.files.copied"
                                            } else {
                                                "sandbox.files.moved"
                                            }
                                        ),
                                        false
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTransferSheet(
    app: SandboxApp,
    request: FileTransferRequest,
    bookmarksFor: (SandboxFileArea, String) -> Set<String>,
    onClose: () -> Unit,
    onToast: (String, Boolean) -> Unit,
    onCompleted: (
        FileTransferMode,
        SandboxFileArea,
        String,
        Int
    ) -> Unit
) {
    val scope = rememberCoroutineScope()
    var targetArea by remember(request) { mutableStateOf(request.sourceArea) }
    var targetPath by remember(request) { mutableStateOf(request.sourcePath) }
    var targetEntries by remember(request) {
        mutableStateOf(emptyList<SandboxFileEntry>())
    }
    var targetBookmarks by remember(request) { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember(request) { mutableStateOf(true) }
    var busy by remember(request) { mutableStateOf(false) }
    var newFolderOpen by remember(request) { mutableStateOf(false) }
    var newFolderName by remember(request) { mutableStateOf("") }
    var conflictOpen by remember(request) { mutableStateOf(false) }
    var conflictIndex by remember(request) { mutableStateOf<Int?>(null) }
    var conflictPolicy by remember(request) {
        mutableStateOf<SandboxImportConflictAction?>(null)
    }
    var completedTransfers by remember(request) { mutableStateOf(0) }
    var nextTransferIndex by remember(request) { mutableStateOf(0) }

    fun loadTarget() {
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val files = SandboxFileRepository.list(
                        app.packageName,
                        targetArea,
                        targetPath
                    )
                    val marked = bookmarksFor(targetArea, targetPath)
                    files.sortedWith(
                        compareByDescending<SandboxFileEntry> {
                            it.relativePath in marked
                        }.thenByDescending { it.isDirectory }
                            .thenBy { it.name.lowercase(Locale.ROOT) }
                    ) to marked
                }
            }
            loading = false
            result.onSuccess { (files, marked) ->
                targetEntries = files
                targetBookmarks = marked
            }
                .onFailure {
                    targetEntries = emptyList()
                    targetBookmarks = emptySet()
                    onToast(
                        t("sandbox.files.read_failed") + ": " +
                            (it.message ?: it.javaClass.simpleName),
                        true
                    )
                }
        }
    }

    LaunchedEffect(targetArea, targetPath) {
        loadTarget()
    }

    val sourceParent = parentPath(request.entry.relativePath)
    val targetInsideSource = request.entries.any { entry ->
        entry.isDirectory &&
            targetArea == request.sourceArea &&
            (
                targetPath == entry.relativePath ||
                    targetPath.startsWith(entry.relativePath + "/")
                )
    }
    val sameDirectory = targetArea == request.sourceArea &&
        targetPath == sourceParent
    val canConfirm = !busy && !loading && !sameDirectory && !targetInsideSource

    fun createFolder() {
        val name = newFolderName.trim()
        if (name.isBlank() || busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.createEntry(
                        app.packageName,
                        targetArea,
                        targetPath,
                        name,
                        isDirectory = true
                    )
                }
            }
            busy = false
            result.onSuccess {
                newFolderOpen = false
                newFolderName = ""
                loadTarget()
            }.onFailure {
                onToast(
                    t("sandbox.files.create_failed") + ": " +
                        (it.message ?: it.javaClass.simpleName),
                    true
                )
            }
        }
    }

    fun performTransfer(
        startIndex: Int = 0,
        firstAction: SandboxImportConflictAction? = null
    ) {
        if (!canConfirm) return
        busy = true
        scope.launch {
            for (index in startIndex until request.entries.size) {
                val entry = request.entries[index]
                val action = if (index == startIndex) firstAction else conflictPolicy
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (request.mode == FileTransferMode.COPY) {
                            SandboxFileRepository.copy(
                                app.packageName,
                                request.sourceArea,
                                entry,
                                targetArea,
                                targetPath,
                                action
                            )
                        } else {
                            SandboxFileRepository.move(
                                app.packageName,
                                request.sourceArea,
                                entry,
                                targetArea,
                                targetPath,
                                action
                            )
                        }
                    }
                }
                val error = result.exceptionOrNull()
                if (error != null) {
                    if (error.message == "file_exists" && action == null) {
                        conflictIndex = index
                        nextTransferIndex = index
                        conflictOpen = true
                        busy = false
                        return@launch
                    }
                    busy = false
                    onToast(
                        t(
                            if (request.mode == FileTransferMode.COPY) {
                                "sandbox.files.copy_failed"
                            } else {
                                "sandbox.files.move_failed"
                            }
                        ) + ": " + (error.message ?: error.javaClass.simpleName),
                        true
                    )
                    return@launch
                }
                if (result.getOrNull() != null) {
                    completedTransfers++
                }
                nextTransferIndex = index + 1
            }
            busy = false
            conflictIndex = null
            conflictPolicy = null
            nextTransferIndex = 0
            onCompleted(
                request.mode,
                targetArea,
                targetPath,
                completedTransfers
            )
            completedTransfers = 0
        }
    }

    fun confirmTransfer() {
        if (!canConfirm) return
        if (conflictIndex != null) {
            conflictOpen = true
        } else {
            performTransfer(nextTransferIndex)
        }
    }

    if (conflictOpen) {
        val index = conflictIndex ?: 0
        FileConflictDialog(
            fileName = request.entries[index].name,
            showApplyToFollowing = request.entries.size > 1,
            replaceLabel = t(
                if (request.mode == FileTransferMode.COPY) {
                    "sandbox.files.conflict_copy_replace"
                } else {
                    "sandbox.files.conflict_replace"
                }
            ),
            keepBothLabel = t(
                if (request.mode == FileTransferMode.COPY) {
                    "sandbox.files.conflict_copy_keep_both"
                } else {
                    "sandbox.files.conflict_keep_both"
                }
            ),
            onDismiss = { conflictOpen = false },
            onConfirm = { action, applyToFollowing ->
                conflictOpen = false
                if (applyToFollowing) conflictPolicy = action
                performTransfer(index, action)
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.semantics {
                        contentDescription = t("common.cancel")
                    }
                ) {
                    TransferCloseIcon()
                }
                Text(
                    text = if (request.batchMode) {
                        String.format(
                            Locale.getDefault(),
                            t(
                                if (request.mode == FileTransferMode.COPY) {
                                    "sandbox.files.copy_items_to"
                                } else {
                                    "sandbox.files.move_items_to"
                                }
                            ),
                            request.entries.size
                        )
                    } else {
                        t(
                            if (request.mode == FileTransferMode.COPY) {
                                "sandbox.files.copy_to"
                            } else {
                                "sandbox.files.move_to"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { newFolderOpen = true },
                    enabled = !busy,
                    modifier = Modifier.semantics {
                        contentDescription = t("sandbox.files.new_folder")
                    }
                ) {
                    FolderPlusIcon()
                }
                IconButton(
                    onClick = ::confirmTransfer,
                    enabled = canConfirm,
                    modifier = Modifier.semantics {
                        contentDescription = t("common.confirm")
                    }
                ) {
                    TransferConfirmIcon(enabled = canConfirm)
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileEntryIcon(entry = request.entry, apkIcon = app.icon)
                Spacer(Modifier.width(12.dp))
                FileEntryInfo(entry = request.entry, modifier = Modifier.weight(1f))
            }

            PcpCategoryTabs(
                labels = SandboxFileArea.entries.map { t(it.titleKey) },
                selectedIndex = targetArea.ordinal,
                onSelect = { index ->
                    val nextArea = SandboxFileArea.entries[index]
                    if (nextArea != targetArea) {
                        targetArea = nextArea
                        targetPath = ""
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayPath(targetArea, targetPath),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    if (targetPath.isNotBlank()) {
                        item(key = "transfer-parent-directory") {
                            SandboxParentRow(
                                enabled = true,
                                onClick = { targetPath = parentPath(targetPath) }
                            )
                        }
                    }
                    if (targetEntries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = t("sandbox.files.empty"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(targetEntries, key = { it.relativePath }) { entry ->
                            FileTransferBrowserRow(
                                entry = entry,
                                apkIcon = app.icon,
                                bookmarked = entry.relativePath in targetBookmarks,
                                onOpen = {
                                    if (entry.isDirectory) {
                                        targetPath = childPath(
                                            targetPath,
                                            entry.name
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (newFolderOpen) {
        CreateTransferFolderDialog(
            name = newFolderName,
            onNameChange = { newFolderName = it },
            onCancel = {
                newFolderOpen = false
                newFolderName = ""
            },
            onConfirm = ::createFolder
        )
    }
}

@Composable
private fun FileTransferBrowserRow(
    entry: SandboxFileEntry,
    apkIcon: Bitmap?,
    bookmarked: Boolean,
    onOpen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable(enabled = entry.isDirectory, onClick = onOpen)
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileEntryIcon(entry = entry, apkIcon = apkIcon)
            Spacer(Modifier.width(12.dp))
            FileEntryInfo(entry = entry, modifier = Modifier.weight(1f))
        }
        if (bookmarked) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFFFD54F).copy(alpha = 0.24f))
            )
        }
    }
}

@Composable
private fun CreateTransferFolderDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Text(
                    text = t("sandbox.files.new_folder"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    singleLine = true,
                    placeholder = {
                        Text(t("sandbox.files.name_placeholder"))
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text(t("common.cancel"), color = Color(0xFF2563EB))
                    }
                    TextButton(
                        onClick = onConfirm,
                        enabled = name.isNotBlank()
                    ) {
                        Text(t("common.confirm"), color = Color(0xFF2563EB))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCloseIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            color,
            Offset(size.width * 0.28f, size.height * 0.28f),
            Offset(size.width * 0.72f, size.height * 0.72f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.72f, size.height * 0.28f),
            Offset(size.width * 0.28f, size.height * 0.72f),
            stroke,
            StrokeCap.Round
        )
    }
}

@Composable
private fun FolderPlusIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val folder = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.28f)
            lineTo(size.width * 0.42f, size.height * 0.28f)
            lineTo(size.width * 0.52f, size.height * 0.4f)
            lineTo(size.width * 0.84f, size.height * 0.4f)
            lineTo(size.width * 0.84f, size.height * 0.78f)
            lineTo(size.width * 0.12f, size.height * 0.78f)
            close()
        }
        drawPath(folder, color, style = Stroke(stroke))
        drawCircle(
            color = color,
            radius = size.width * 0.18f,
            center = Offset(size.width * 0.76f, size.height * 0.74f)
        )
        drawLine(
            background,
            Offset(size.width * 0.67f, size.height * 0.74f),
            Offset(size.width * 0.85f, size.height * 0.74f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            background,
            Offset(size.width * 0.76f, size.height * 0.65f),
            Offset(size.width * 0.76f, size.height * 0.83f),
            stroke,
            StrokeCap.Round
        )
    }
}

@Composable
private fun TransferConfirmIcon(enabled: Boolean) {
    val color = MaterialTheme.colorScheme.primary.copy(
        alpha = if (enabled) 1f else 0.35f
    )
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.2.dp.toPx()
        drawLine(
            color,
            Offset(size.width * 0.2f, size.height * 0.52f),
            Offset(size.width * 0.42f, size.height * 0.74f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.42f, size.height * 0.74f),
            Offset(size.width * 0.82f, size.height * 0.28f),
            stroke,
            StrokeCap.Round
        )
    }
}

@Composable
private fun FileActionDialog(
    entries: List<SandboxFileEntry>,
    batchMode: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onProperties: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val primary = entries.first()
    val heading = if (entries.size > 1) {
        String.format(
            Locale.getDefault(),
            t("sandbox.files.multi_item_name"),
            primary.name,
            entries.size - 1
        )
    } else {
        primary.name
    }
    val singleEditable = entries.size == 1 && !batchMode
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Text(
                    text = heading,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider()
                Row(modifier = Modifier.height(224.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        DialogActionItem(
                            icon = FileActionIcon.COPY,
                            text = t("sandbox.files.copy"),
                            onClick = onCopy
                        )
                        DialogActionItem(
                            icon = FileActionIcon.DELETE,
                            text = t("sandbox.files.delete"),
                            onClick = onDelete,
                            isDestructive = true
                        )
                        DialogActionItem(
                            icon = FileActionIcon.PROPERTIES,
                            text = t("sandbox.files.properties"),
                            onClick = onProperties,
                            enabled = singleEditable
                        )
                        DialogActionItem(
                            icon = FileActionIcon.EXPORT,
                            text = t("sandbox.files.export"),
                            onClick = onExport
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        DialogActionItem(
                            icon = FileActionIcon.MOVE,
                            text = t("sandbox.files.move"),
                            onClick = onMove
                        )
                        DialogActionItem(
                            icon = FileActionIcon.RENAME,
                            text = t("sandbox.files.rename"),
                            onClick = onRename,
                            enabled = singleEditable
                        )
                        DialogActionItem(
                            icon = FileActionIcon.SHARE,
                            text = t("sandbox.files.share"),
                            enabled = entries.all { !it.isDirectory },
                            onClick = onShare
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogActionItem(
    icon: FileActionIcon,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileActionGlyph(icon = icon, color = color)
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FileActionGlyph(icon: FileActionIcon, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        when (icon) {
            FileActionIcon.COPY -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.16f, size.height * 0.2f),
                    size = Size(size.width * 0.48f, size.height * 0.66f),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(stroke)
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.36f, size.height * 0.08f),
                    size = Size(size.width * 0.48f, size.height * 0.66f),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(stroke)
                )
            }
            FileActionIcon.DELETE -> {
                drawLine(
                    color,
                    Offset(size.width * 0.18f, size.height * 0.28f),
                    Offset(size.width * 0.82f, size.height * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.38f, size.height * 0.18f),
                    Offset(size.width * 0.62f, size.height * 0.18f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.38f, size.height * 0.18f),
                    Offset(size.width * 0.38f, size.height * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.62f, size.height * 0.18f),
                    Offset(size.width * 0.62f, size.height * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                val body = Path().apply {
                    moveTo(size.width * 0.27f, size.height * 0.34f)
                    lineTo(size.width * 0.33f, size.height * 0.86f)
                    lineTo(size.width * 0.67f, size.height * 0.86f)
                    lineTo(size.width * 0.73f, size.height * 0.34f)
                }
                drawPath(body, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(
                    color,
                    Offset(size.width * 0.43f, size.height * 0.43f),
                    Offset(size.width * 0.45f, size.height * 0.75f),
                    strokeWidth = stroke * 0.7f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.57f, size.height * 0.43f),
                    Offset(size.width * 0.55f, size.height * 0.75f),
                    strokeWidth = stroke * 0.7f,
                    cap = StrokeCap.Round
                )
            }
            FileActionIcon.PROPERTIES -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.36f,
                    center = center,
                    style = Stroke(stroke)
                )
                drawCircle(
                    color = color,
                    radius = stroke * 0.65f,
                    center = Offset(size.width * 0.5f, size.height * 0.35f)
                )
                drawLine(
                    color,
                    Offset(size.width * 0.5f, size.height * 0.48f),
                    Offset(size.width * 0.5f, size.height * 0.7f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            FileActionIcon.EXPORT -> {
                drawLine(
                    color,
                    Offset(size.width * 0.5f, size.height * 0.7f),
                    Offset(size.width * 0.5f, size.height * 0.18f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.32f, size.height * 0.36f),
                    Offset(size.width * 0.5f, size.height * 0.18f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.68f, size.height * 0.36f),
                    Offset(size.width * 0.5f, size.height * 0.18f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.2f, size.height * 0.56f),
                    Offset(size.width * 0.2f, size.height * 0.84f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.2f, size.height * 0.84f),
                    Offset(size.width * 0.8f, size.height * 0.84f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.8f, size.height * 0.56f),
                    Offset(size.width * 0.8f, size.height * 0.84f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            FileActionIcon.MOVE -> {
                drawLine(
                    color,
                    Offset(size.width * 0.16f, size.height * 0.2f),
                    Offset(size.width * 0.16f, size.height * 0.8f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.26f, size.height * 0.5f),
                    Offset(size.width * 0.8f, size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.62f, size.height * 0.32f),
                    Offset(size.width * 0.8f, size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.62f, size.height * 0.68f),
                    Offset(size.width * 0.8f, size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            FileActionIcon.RENAME -> {
                val pencil = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.8f)
                    lineTo(size.width * 0.25f, size.height * 0.56f)
                    lineTo(size.width * 0.64f, size.height * 0.17f)
                    lineTo(size.width * 0.83f, size.height * 0.36f)
                    lineTo(size.width * 0.44f, size.height * 0.75f)
                    close()
                }
                drawPath(pencil, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(
                    color,
                    Offset(size.width * 0.25f, size.height * 0.56f),
                    Offset(size.width * 0.44f, size.height * 0.75f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            FileActionIcon.SHARE -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.12f,
                    center = Offset(size.width * 0.24f, size.height * 0.5f),
                    style = Stroke(stroke)
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.12f,
                    center = Offset(size.width * 0.75f, size.height * 0.25f),
                    style = Stroke(stroke)
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.12f,
                    center = Offset(size.width * 0.75f, size.height * 0.75f),
                    style = Stroke(stroke)
                )
                drawLine(
                    color,
                    Offset(size.width * 0.35f, size.height * 0.43f),
                    Offset(size.width * 0.64f, size.height * 0.31f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * 0.35f, size.height * 0.57f),
                    Offset(size.width * 0.64f, size.height * 0.69f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private enum class FileActionIcon {
    COPY,
    DELETE,
    PROPERTIES,
    EXPORT,
    MOVE,
    RENAME,
    SHARE
}

@Composable
private fun PropertiesDialog(
    properties: SandboxFileProperties,
    onDismiss: () -> Unit
) {
    val modifiedTime = remember(properties.lastModified) {
        formatModifiedTime(properties.lastModified)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Text(
                    text = t("sandbox.files.properties"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider()
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    PropertyRow(t("sandbox.files.name"), properties.name)
                    PropertyRow(
                        t("sandbox.files.type"),
                        t(
                            if (properties.isDirectory) "sandbox.files.folder"
                            else "sandbox.files.file"
                        )
                    )
                    PropertyRow(
                        t("sandbox.files.size"),
                        if (properties.isDirectory) "—"
                        else formatFileSize(properties.size)
                    )
                    PropertyRow(
                        t("sandbox.files.modified"),
                        modifiedTime
                    )
                    PropertyRow(
                        t("sandbox.files.permissions"),
                        properties.permissions,
                        monospace = true
                    )
                    PropertyRow(t("sandbox.files.owner"), properties.owner)
                    PropertyRow(t("sandbox.files.group"), properties.group)
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = t("sandbox.files.close"),
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileConflictDialog(
    fileName: String,
    showApplyToFollowing: Boolean = true,
    replaceLabel: String = t("sandbox.files.conflict_replace"),
    keepBothLabel: String = t("sandbox.files.conflict_keep_both"),
    onDismiss: () -> Unit,
    onConfirm: (SandboxImportConflictAction, Boolean) -> Unit
) {
    var selected by remember(fileName) {
        mutableStateOf(SandboxImportConflictAction.REPLACE)
    }
    var applyToFollowing by remember(fileName) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Text(
                    text = t("sandbox.files.conflict_title"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider()
                Text(
                    text = fileName,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ConflictChoice(
                    text = replaceLabel,
                    selected = selected == SandboxImportConflictAction.REPLACE,
                    onClick = { selected = SandboxImportConflictAction.REPLACE }
                )
                ConflictChoice(
                    text = t("sandbox.files.conflict_skip"),
                    selected = selected == SandboxImportConflictAction.SKIP,
                    onClick = { selected = SandboxImportConflictAction.SKIP }
                )
                ConflictChoice(
                    text = keepBothLabel,
                    selected = selected == SandboxImportConflictAction.KEEP_BOTH,
                    onClick = { selected = SandboxImportConflictAction.KEEP_BOTH }
                )
                if (showApplyToFollowing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applyToFollowing = !applyToFollowing }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = applyToFollowing,
                            onCheckedChange = { applyToFollowing = it }
                        )
                        Text(
                            text = t("sandbox.files.conflict_apply_to_following"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(t("common.cancel"), color = Color(0xFF2563EB))
                    }
                    TextButton(onClick = { onConfirm(selected, applyToFollowing) }) {
                        Text(t("common.confirm"), color = Color(0xFF2563EB))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteEntryDialog(
    entries: List<SandboxFileEntry>,
    batchMode: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (batchMode) {
        String.format(
            Locale.getDefault(),
            t("sandbox.files.delete_many_title"),
            entries.size
        )
    } else {
        t("sandbox.files.delete_title")
    }
    val displayName = entries.first().name
    val remainingLabel = if (entries.size > 1) {
        String.format(
            Locale.getDefault(),
            t("sandbox.files.and_other_items"),
            entries.size - 1
        )
    } else null
    PcpBatchDeleteDialog(
        title = title,
        displayName = displayName,
        remainingLabel = remainingLabel,
        warning = t("sandbox.files.delete_warning"),
        confirmLabel = t("sandbox.files.delete"),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun ConflictChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PropertyRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.42f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
            ),
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddFileDialog(
    onNew: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Text(
                    text = t("sandbox.files.add_title"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider()
                TextButton(
                    onClick = onNew,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(t("sandbox.files.new"))
                }
                TextButton(
                    onClick = onImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(t("sandbox.files.import_here"))
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = t("common.cancel"),
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateEntryDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onFile: () -> Unit,
    onDirectory: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Text(
                    text = t("sandbox.files.new_title"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider()
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(t("sandbox.files.name")) }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onFile) {
                            Text(t("sandbox.files.file"))
                        }
                        TextButton(onClick = onDirectory) {
                            Text(t("sandbox.files.folder"))
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = t("common.cancel"),
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameEntryDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Text(
                    text = t("sandbox.files.rename"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    singleLine = true,
                    label = { Text(t("sandbox.files.name")) }
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = t("common.cancel"),
                            color = Color(0xFF2563EB)
                        )
                    }
                    TextButton(
                        onClick = onConfirm,
                        enabled = name.isNotBlank()
                    ) {
                        Text(
                            text = t("common.confirm"),
                            color = if (name.isNotBlank()) {
                                Color(0xFF2563EB)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileToolBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    canGoUp: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAdd: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileToolButton(
                icon = FileToolIcon.BACK,
                description = t("sandbox.files.undo_directory"),
                enabled = enabled && canGoBack,
                onClick = onBack
            )
            FileToolButton(
                icon = FileToolIcon.FORWARD,
                description = t("sandbox.files.redo_directory"),
                enabled = enabled && canGoForward,
                onClick = onForward
            )
            FileToolButton(
                icon = FileToolIcon.ADD,
                description = t("sandbox.files.add"),
                enabled = enabled,
                onClick = onAdd
            )
            FileToolButton(
                icon = FileToolIcon.UP,
                description = t("sandbox.files.parent"),
                enabled = enabled && canGoUp,
                onClick = onUp
            )
        }
    }
}

@Composable
private fun RowScope.FileToolButton(
    icon: FileToolIcon,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { contentDescription = description }
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            val start = 0.28f
            val end = 0.72f
            val mid = 0.5f
            when (icon) {
                FileToolIcon.BACK, FileToolIcon.FORWARD -> {
                    val direction = if (icon == FileToolIcon.BACK) 1f else -1f
                    val tipX = if (icon == FileToolIcon.BACK) start else end
                    val tailX = if (icon == FileToolIcon.BACK) end else start
                    drawLine(
                        color = color,
                        start = Offset(size.width * tipX, size.height * mid),
                        end = Offset(size.width * tailX, size.height * mid),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * tipX, size.height * mid),
                        end = Offset(
                            size.width * (tipX + 0.2f * direction),
                            size.height * 0.3f
                        ),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * tipX, size.height * mid),
                        end = Offset(
                            size.width * (tipX + 0.2f * direction),
                            size.height * 0.7f
                        ),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                FileToolIcon.ADD -> {
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.25f, size.height * mid),
                        end = Offset(size.width * 0.75f, size.height * mid),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * mid, size.height * 0.25f),
                        end = Offset(size.width * mid, size.height * 0.75f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                FileToolIcon.UP -> {
                    drawLine(
                        color = color,
                        start = Offset(size.width * mid, size.height * 0.72f),
                        end = Offset(size.width * mid, size.height * 0.28f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * mid, size.height * 0.28f),
                        end = Offset(size.width * 0.3f, size.height * 0.48f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * mid, size.height * 0.28f),
                        end = Offset(size.width * 0.7f, size.height * 0.48f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun SandboxParentRow(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFFFFE2A8), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            FolderGlyph(Color(0xFF8A5A00))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "..",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FileEntryInfo(entry: SandboxFileEntry, modifier: Modifier = Modifier) {
    val modifiedTime = remember(entry.lastModified) {
        formatModifiedTime(entry.lastModified)
    }
    Column(modifier = modifier) {
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = modifiedTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (!entry.isDirectory) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatFileSize(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FileEntryIcon(entry: SandboxFileEntry, apkIcon: Bitmap?) {
    val kind = entry.kind()
    if (kind == FileEntryKind.APK && apkIcon != null) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            Image(
                bitmap = apkIcon.asImageBitmap(),
                contentDescription = entry.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        return
    }

    val background = when (kind) {
        FileEntryKind.FOLDER -> Color(0xFFFFE2A8)
        FileEntryKind.TEXT -> Color(0xFFDCEBFF)
        FileEntryKind.APK -> MaterialTheme.colorScheme.primaryContainer
        FileEntryKind.GENERIC -> Color(0xFFE8EAED)
    }
    val foreground = when (kind) {
        FileEntryKind.FOLDER -> Color(0xFF8A5A00)
        FileEntryKind.TEXT -> Color(0xFF2563EB)
        FileEntryKind.APK -> MaterialTheme.colorScheme.onPrimaryContainer
        FileEntryKind.GENERIC -> Color(0xFF626872)
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .background(background, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            FileEntryKind.FOLDER -> FolderGlyph(foreground)
            FileEntryKind.TEXT -> FileGlyph(foreground, showTextLines = true)
            FileEntryKind.GENERIC -> FileGlyph(foreground, showTextLines = false)
            FileEntryKind.APK -> Text(
                text = "APK",
                color = foreground,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FolderGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.3f)
            lineTo(size.width * 0.4f, size.height * 0.3f)
            lineTo(size.width * 0.5f, size.height * 0.42f)
            lineTo(size.width * 0.88f, size.height * 0.42f)
            lineTo(size.width * 0.88f, size.height * 0.82f)
            lineTo(size.width * 0.12f, size.height * 0.82f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
private fun FileGlyph(color: Color, showTextLines: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.22f, size.height * 0.1f),
            size = Size(size.width * 0.56f, size.height * 0.8f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(stroke)
        )
        if (showTextLines) {
            repeat(3) { index ->
                val y = size.height * (0.36f + index * 0.16f)
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.34f, y),
                    end = Offset(size.width * 0.66f, y),
                    strokeWidth = stroke * 0.75f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private enum class FileEntryKind {
    FOLDER,
    APK,
    TEXT,
    GENERIC
}

private data class PendingSandboxImport(
    val source: Uri,
    val area: SandboxFileArea,
    val directoryPath: String,
    val displayName: String
)

private enum class FileTransferMode {
    COPY,
    MOVE
}

private data class FileTransferRequest(
    val entries: List<SandboxFileEntry>,
    val batchMode: Boolean,
    val sourceArea: SandboxFileArea,
    val sourcePath: String,
    val mode: FileTransferMode
) {
    val entry: SandboxFileEntry
        get() = entries.first()
}

private fun SandboxFileEntry.kind(): FileEntryKind = when {
    isDirectory -> FileEntryKind.FOLDER
    name.substringAfterLast('.', "").equals("apk", ignoreCase = true) -> FileEntryKind.APK
    isEditableText -> FileEntryKind.TEXT
    else -> FileEntryKind.GENERIC
}

private enum class FileToolIcon {
    BACK,
    FORWARD,
    ADD,
    UP
}

private fun parentPath(path: String): String =
    if ('/' !in path) "" else path.substringBeforeLast('/')

private fun childPath(parent: String, child: String): String =
    if (parent.isBlank()) child else "$parent/$child"

private fun displayPath(area: SandboxFileArea, relativePath: String): String {
    val base = when (area) {
        SandboxFileArea.APK -> "/data/app/"
        SandboxFileArea.DATA -> "/data/user/0/"
        SandboxFileArea.EXTERNAL -> "/storage/emulated/0/"
    }
    return if (relativePath.isBlank()) {
        base
    } else {
        base + relativePath.trimEnd('/') + "/"
    }
}

private fun formatModifiedTime(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(timestamp))
}

private fun formatFileSize(size: Long): String {
    if (size < 1024) return "$size B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}
