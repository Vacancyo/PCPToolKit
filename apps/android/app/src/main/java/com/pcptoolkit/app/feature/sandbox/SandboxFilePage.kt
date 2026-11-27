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

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pcptoolkit.app.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.fake.provider.FileProvider as BlackBoxFileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SandboxFilePage(
    app: SandboxApp,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var area by remember { mutableStateOf(SandboxFileArea.APK) }
    var relativePath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<SandboxFileEntry>()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var editorEntry by remember { mutableStateOf<SandboxFileEntry?>(null) }
    var editorText by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<SandboxFileEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<SandboxFileEntry?>(null) }
    var pendingAction by remember { mutableStateOf<SandboxFileEntry?>(null) }
    var pendingProperties by remember { mutableStateOf<SandboxFileProperties?>(null) }
    var pendingRename by remember { mutableStateOf<SandboxFileEntry?>(null) }
    var renameName by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newEntryName by remember { mutableStateOf("") }
    val backHistory = remember { mutableStateListOf<String>() }
    val forwardHistory = remember { mutableStateListOf<String>() }

    BackHandler {
        if (editorEntry != null) {
            editorEntry = null
        } else {
            onBack()
        }
    }

    fun reportSuccess(text: String) {
        messageIsError = false
        message = text
    }

    fun reportFailure(action: String, error: Throwable) {
        messageIsError = true
        message = "$action: ${error.message ?: error.javaClass.simpleName}"
    }

    fun reload(showRefreshIndicator: Boolean = false) {
        if (showRefreshIndicator) {
            refreshing = true
        } else {
            loading = true
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.list(app.packageName, area, relativePath)
                }
            }
            result.onSuccess { entries = it }
                .onFailure {
                    entries = emptyList()
                    reportFailure(t("sandbox.files.read_failed"), it)
                }
            loading = false
            refreshing = false
        }
    }

    fun navigateTo(path: String) {
        if (path == relativePath) return
        backHistory.add(relativePath)
        forwardHistory.clear()
        relativePath = path
    }

    fun navigateBack() {
        if (backHistory.isEmpty()) return
        forwardHistory.add(relativePath)
        relativePath = backHistory.removeAt(backHistory.lastIndex)
    }

    fun navigateForward() {
        if (forwardHistory.isEmpty()) return
        backHistory.add(relativePath)
        relativePath = forwardHistory.removeAt(forwardHistory.lastIndex)
    }

    fun navigateUp() {
        if (relativePath.isBlank()) return
        navigateTo(parentPath(relativePath))
    }

    fun switchArea(nextArea: SandboxFileArea) {
        if (nextArea == area) return
        area = nextArea
        relativePath = ""
        backHistory.clear()
        forwardHistory.clear()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        SandboxFileRepository.importFile(
                            context,
                            app.packageName,
                            area,
                            relativePath,
                            uri
                        )
                    }
                }
                busy = false
                result.onSuccess {
                    reportSuccess(t("sandbox.files.imported") + " " + it.name)
                    reload()
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
                        SandboxFileRepository.exportToUri(
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
                    reportSuccess(t("sandbox.files.exported") + " " + target.name)
                }.onFailure {
                    reportFailure(t("sandbox.files.export_failed"), it)
                }
            }
        }
    }

    fun openEditor(entry: SandboxFileEntry) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.readText(
                        app.packageName,
                        area,
                        entry.relativePath
                    )
                }
            }
            busy = false
            result.onSuccess {
                editorEntry = entry
                editorText = it.text
            }.onFailure {
                reportFailure(t("sandbox.files.read_failed"), it)
            }
        }
    }

    fun saveEditor() {
        val entry = editorEntry ?: return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.writeText(
                        app.packageName,
                        area,
                        entry.relativePath,
                        editorText
                    )
                }
            }
            busy = false
            result.onSuccess {
                editorEntry = null
                reportSuccess(t("sandbox.files.saved") + " " + entry.name)
                reload()
            }.onFailure {
                reportFailure(t("sandbox.files.write_failed"), it)
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
                reportSuccess(
                    t(
                        if (isDirectory) "sandbox.files.directory_created"
                        else "sandbox.files.file_created"
                    ) + " " + it.name
                )
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
                reportSuccess(t("sandbox.files.renamed") + " " + it.name)
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

    fun shareEntry(entry: SandboxFileEntry) {
        if (entry.isDirectory) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SandboxFileRepository.prepareShareFile(
                        context,
                        app.packageName,
                        area,
                        entry
                    )
                }
            }
            busy = false
            result.onSuccess { file ->
                runCatching {
                    val authority = context.packageName + ".blackbox.FileProvider"
                    val uri = BlackBoxFileProvider.getUriForFile(context, authority, file)
                    val mime = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(
                            file.extension.lowercase(Locale.ROOT)
                        )
                        ?: "application/octet-stream"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, t("sandbox.files.share"))
                    )
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
        FileActionDialog(
            entry = entry,
            onExport = {
                pendingAction = null
                pendingExport = entry
                exportLauncher.launch(
                    if (entry.isDirectory) "${entry.name}.zip" else entry.name
                )
            },
            onProperties = {
                pendingAction = null
                showProperties(entry)
            },
            onRename = {
                pendingAction = null
                pendingRename = entry
                renameName = entry.name
            },
            onShare = {
                pendingAction = null
                shareEntry(entry)
            },
            onDelete = {
                pendingAction = null
                pendingDelete = entry
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

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(t("sandbox.files.delete_title")) },
            text = { Text(file.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    SandboxFileRepository.delete(app.packageName, area, file)
                                }
                            }
                            busy = false
                            result.onSuccess {
                                reportSuccess(t("sandbox.files.deleted") + " " + file.name)
                                reload()
                            }.onFailure {
                                reportFailure(t("sandbox.files.delete_failed"), it)
                            }
                        }
                    }
                ) {
                    Text(t("sandbox.files.delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(t("common.cancel"))
                }
            }
        )
    }

    editorEntry?.let { file ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = file.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                TextButton(onClick = { editorEntry = null }) {
                    Text(t("common.cancel"))
                }
                TextButton(onClick = ::saveEditor, enabled = !busy) {
                    Text(t("sandbox.files.save"))
                }
            }
            OutlinedTextField(
                value = editorText,
                onValueChange = { editorText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            if (busy) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 14.dp,
                    alignment = Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SandboxFileArea.entries.forEach { candidate ->
                    FilterChip(
                        selected = candidate == area,
                        onClick = { switchArea(candidate) },
                        label = { Text(t(candidate.titleKey)) }
                    )
                }
            }

            Text(
                text = displayPath(area, relativePath),
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis
            )

            message?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(bottom = 6.dp),
                    color = if (messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (busy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
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
                    contentPadding = PaddingValues(bottom = 76.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                            SandboxFileRow(
                                entry = entry,
                                apkIcon = app.icon,
                                onOpen = {
                                    if (entry.isDirectory) {
                                        navigateTo(entry.relativePath)
                                    } else if (entry.isEditableText) {
                                        openEditor(entry)
                                    }
                                },
                                onLongPress = { pendingAction = entry }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }

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
    }
}

@Composable
private fun FileActionDialog(
    entry: SandboxFileEntry,
    onExport: () -> Unit,
    onProperties: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
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
                    text = entry.name,
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
                            enabled = false,
                            onClick = {}
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
                            onClick = onProperties
                        )
                        DialogActionItem(
                            icon = FileActionIcon.EXPORT,
                            text = t("sandbox.files.export"),
                            onClick = onExport
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        DialogActionItem(
                            icon = FileActionIcon.MOVE,
                            text = t("sandbox.files.move"),
                            enabled = false,
                            onClick = {}
                        )
                        DialogActionItem(
                            icon = FileActionIcon.RENAME,
                            text = t("sandbox.files.rename"),
                            onClick = onRename
                        )
                        DialogActionItem(
                            icon = FileActionIcon.SHARE,
                            text = t("sandbox.files.share"),
                            enabled = !entry.isDirectory,
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
                        formatModifiedTime(properties.lastModified)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SandboxFileRow(
    entry: SandboxFileEntry,
    apkIcon: Bitmap?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress
            )
            .padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileEntryIcon(entry = entry, apkIcon = apkIcon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                    text = formatModifiedTime(entry.lastModified),
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
