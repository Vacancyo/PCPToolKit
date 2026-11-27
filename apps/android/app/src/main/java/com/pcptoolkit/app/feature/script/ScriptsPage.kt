/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 *
 * @file ScriptsPage.kt
 * @brief 脚本管理界面。
 *
 * 该代码文件实现 PCP 外部脚本目录、脚本列表和预制脚本的管理操作。
 */
package com.pcptoolkit.app.feature.script

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pcptoolkit.app.feature.editor.ScriptTextEditorTarget
import com.pcptoolkit.app.feature.editor.TextEditorTarget
import com.pcptoolkit.app.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.fake.provider.FileProvider as BlackBoxFileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PRESET_DIR = "scripts"

private enum class ScriptSection(val titleKey: String) {
    LIST("scripts.list"),
    PRESETS("scripts.preset")
}

/**
 * @brief 显示脚本管理页面。
 */
@Composable
internal fun ScriptsPage(
    onEditText: (TextEditorTarget) -> Unit,
    onToast: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(ScriptSection.LIST) }
    var scripts by remember { mutableStateOf(emptyList<ScriptEntry>()) }
    val presets = remember {
        context.assets.list(PRESET_DIR)
            ?.filter { it.endsWith(ScriptStore.EXTENSION, ignoreCase = true) }
            ?.sorted()
            .orEmpty()
    }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ScriptEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<ScriptEntry?>(null) }
    var pendingRename by remember { mutableStateOf<ScriptEntry?>(null) }
    var renameName by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<ScriptEntry?>(null) }
    var pendingProperties by remember { mutableStateOf<ScriptProperties?>(null) }
    fun reportFailure(action: String, error: Throwable) {
        onToast(
            "$action: ${error.message ?: error.javaClass.simpleName}",
            true
        )
    }

    fun reload(showRefreshIndicator: Boolean = false) {
        if (showRefreshIndicator) {
            refreshing = true
        } else {
            loading = true
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ScriptStore.list(context)
            }
            loading = false
            refreshing = false
            if (result == null) {
                scripts = emptyList()
                onToast(t("scripts.read_failed"), true)
            } else {
                scripts = result
            }
        }
    }

    fun addPreset(name: String) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ScriptStore.addPreset(context, name) }
            }
            busy = false
            result.onSuccess {
                reload()
            }.onFailure {
                reportFailure(t("scripts.add_failed"), it)
            }
        }
    }

    fun deleteEntry(entry: ScriptEntry) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ScriptStore.delete(context, entry) }
            }
            busy = false
            result.onSuccess {
                reload()
            }.onFailure {
                reportFailure(t("scripts.delete_failed"), it)
            }
        }
    }

    fun renameEntry() {
        val entry = pendingRename ?: return
        val requestedName = renameName.trim()
        if (requestedName.isBlank()) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ScriptStore.rename(context, entry, requestedName) }
            }
            busy = false
            result.onSuccess {
                pendingRename = null
                renameName = ""
                reload()
            }.onFailure {
                reportFailure(t("scripts.rename_failed"), it)
            }
        }
    }

    fun showProperties(entry: ScriptEntry) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ScriptStore.getProperties(context, entry) }
            }
            busy = false
            result.onSuccess { pendingProperties = it }
                .onFailure {
                    reportFailure(t("scripts.properties_failed"), it)
                }
        }
    }

    fun shareEntry(entry: ScriptEntry) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ScriptStore.prepareShareFile(context, entry) }
            }
            busy = false
            result.onSuccess { file ->
                runCatching {
                    val authority = context.packageName + ".blackbox.FileProvider"
                    val uri = BlackBoxFileProvider.getUriForFile(context, authority, file)
                    val mime = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
                        ?: "application/octet-stream"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, t("scripts.share"))
                    )
                }.onFailure {
                    reportFailure(t("scripts.share_failed"), it)
                }
            }.onFailure {
                reportFailure(t("scripts.share_failed"), it)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { ScriptStore.importScript(context, uri) }
                }
                busy = false
                result.onSuccess {
                    section = ScriptSection.LIST
                    reload()
                }.onFailure {
                    reportFailure(t("scripts.import_failed"), it)
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
                    runCatching { ScriptStore.exportToUri(context, target, uri) }
                }
                busy = false
                result.onFailure {
                    reportFailure(t("scripts.export_failed"), it)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    pendingAction?.let { entry ->
        ScriptActionDialog(
            entry = entry,
            onDelete = {
                pendingAction = null
                pendingDelete = entry
            },
            onProperties = {
                pendingAction = null
                showProperties(entry)
            },
            onExport = {
                pendingAction = null
                pendingExport = entry
                exportLauncher.launch(entry.name)
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
            onDismiss = { pendingAction = null }
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(t("scripts.delete_title")) },
            text = { Text(entry.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        deleteEntry(entry)
                    }
                ) {
                    Text(t("scripts.delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(t("common.cancel"))
                }
            }
        )
    }

    pendingRename?.let {
        RenameScriptDialog(
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
        ScriptPropertiesDialog(
            properties = properties,
            onDismiss = { pendingProperties = null }
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
                ScriptSection.entries.forEach { candidate ->
                    FilterChip(
                        selected = candidate == section,
                        onClick = { section = candidate },
                        label = { Text(t(candidate.titleKey)) }
                    )
                }
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
                    Text(t("scripts.working"))
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
                        section == ScriptSection.LIST && scripts.isEmpty() -> item {
                            EmptyScriptsMessage(t("scripts.no_scripts"))
                        }
                        section == ScriptSection.PRESETS && presets.isEmpty() -> item {
                            EmptyScriptsMessage(t("scripts.no_presets"))
                        }
                        section == ScriptSection.LIST -> items(scripts, key = { it.name }) { entry ->
                            ScriptRow(
                                entry = entry,
                                onOpen = {
                                    onEditText(ScriptTextEditorTarget(entry))
                                },
                                onLongPress = { pendingAction = entry }
                            )
                        }
                        else -> items(presets, key = { it }) { name ->
                            PresetRow(
                                name = name,
                                enabled = !busy,
                                onAdd = { addPreset(name) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }

        ScriptToolBar(
            enabled = !busy,
            onAdd = { importLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyScriptsMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScriptRow(
    entry: ScriptEntry,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val modifiedTime = remember(entry.lastModified) {
        formatModifiedTime(entry.lastModified)
    }
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
        ScriptEntryIcon()
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
                    text = modifiedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
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
private fun PresetRow(
    name: String,
    enabled: Boolean,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScriptEntryIcon()
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        TextButton(
            onClick = onAdd,
            enabled = enabled
        ) {
            Text(t("scripts.add"))
        }
    }
}

@Composable
private fun ScriptEntryIcon() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color(0xFFDCEBFF), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val color = Color(0xFF2563EB)
            val stroke = 1.8.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.22f, size.height * 0.1f),
                size = Size(size.width * 0.56f, size.height * 0.8f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(stroke)
            )
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

@Composable
private fun ScriptToolBar(
    enabled: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onAdd,
                enabled = enabled,
                modifier = Modifier
                    .size(64.dp)
                    .semantics { contentDescription = t("scripts.import") }
            ) {
                Canvas(Modifier.size(24.dp)) {
                    val stroke = 2.dp.toPx()
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.25f, size.height * 0.5f),
                        end = Offset(size.width * 0.75f, size.height * 0.5f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.5f, size.height * 0.25f),
                        end = Offset(size.width * 0.5f, size.height * 0.75f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptActionDialog(
    entry: ScriptEntry,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
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
                Row(modifier = Modifier.height(168.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        ScriptActionItem(
                            icon = ScriptActionIcon.DELETE,
                            text = t("scripts.delete"),
                            onClick = onDelete,
                            isDestructive = true
                        )
                        ScriptActionItem(
                            icon = ScriptActionIcon.PROPERTIES,
                            text = t("scripts.properties"),
                            onClick = onProperties
                        )
                        ScriptActionItem(
                            icon = ScriptActionIcon.EXPORT,
                            text = t("scripts.export"),
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
                        ScriptActionItem(
                            icon = ScriptActionIcon.RENAME,
                            text = t("scripts.rename"),
                            onClick = onRename
                        )
                        ScriptActionItem(
                            icon = ScriptActionIcon.SHARE,
                            text = t("scripts.share"),
                            onClick = onShare
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptActionItem(
    icon: ScriptActionIcon,
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val color = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScriptActionGlyph(icon = icon, color = color)
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
private fun ScriptActionGlyph(icon: ScriptActionIcon, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        when (icon) {
            ScriptActionIcon.DELETE -> {
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
                val body = Path().apply {
                    moveTo(size.width * 0.27f, size.height * 0.34f)
                    lineTo(size.width * 0.33f, size.height * 0.86f)
                    lineTo(size.width * 0.67f, size.height * 0.86f)
                    lineTo(size.width * 0.73f, size.height * 0.34f)
                }
                drawPath(body, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            ScriptActionIcon.PROPERTIES -> {
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
            ScriptActionIcon.EXPORT -> {
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
            ScriptActionIcon.RENAME -> {
                val pencil = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.8f)
                    lineTo(size.width * 0.25f, size.height * 0.56f)
                    lineTo(size.width * 0.64f, size.height * 0.17f)
                    lineTo(size.width * 0.83f, size.height * 0.36f)
                    lineTo(size.width * 0.44f, size.height * 0.75f)
                    close()
                }
                drawPath(pencil, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            ScriptActionIcon.SHARE -> {
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

private enum class ScriptActionIcon {
    DELETE,
    PROPERTIES,
    EXPORT,
    RENAME,
    SHARE
}

@Composable
private fun RenameScriptDialog(
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
                    text = t("scripts.rename"),
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
                    label = { Text(t("scripts.name")) }
                )
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
private fun ScriptPropertiesDialog(
    properties: ScriptProperties,
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
                    text = t("scripts.properties"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider()
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    ScriptPropertyRow(t("scripts.name"), properties.name)
                    ScriptPropertyRow(t("scripts.type"), t("scripts.type_file"))
                    ScriptPropertyRow(t("scripts.size"), formatFileSize(properties.size))
                    ScriptPropertyRow(t("scripts.modified"), modifiedTime)
                    ScriptPropertyRow(
                        t("scripts.permissions"),
                        properties.permissions,
                        monospace = true
                    )
                    ScriptPropertyRow(t("scripts.owner"), properties.owner)
                    ScriptPropertyRow(t("scripts.group"), properties.group)
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(t("scripts.close"), color = Color(0xFF2563EB))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptPropertyRow(
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
