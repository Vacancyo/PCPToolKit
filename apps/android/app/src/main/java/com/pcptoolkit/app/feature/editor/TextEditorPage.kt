/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * @file TextEditorPage.kt
 * @brief 全局共享文本编辑界面。
 */
package com.pcptoolkit.app.feature.editor

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pcptoolkit.app.feature.sandbox.SandboxFileArea
import com.pcptoolkit.app.feature.sandbox.SandboxFileRepository
import com.pcptoolkit.app.feature.script.ScriptEntry
import com.pcptoolkit.app.feature.script.ScriptStore
import com.pcptoolkit.app.t
import com.pcptoolkit.app.ui.PcpToastState
import com.pcptoolkit.app.R
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @brief 可被全局文本编辑器打开的文件目标。
 */
sealed interface TextEditorTarget {
    val displayName: String

    suspend fun read(context: Context): String

    suspend fun write(context: Context, text: String)
}

/**
 * @brief 沙箱内的文本文件。
 */
data class SandboxTextEditorTarget(
    val packageName: String,
    val area: SandboxFileArea,
    val relativePath: String,
    override val displayName: String
) : TextEditorTarget {
    override suspend fun read(context: Context): String = withContext(Dispatchers.IO) {
        SandboxFileRepository.readText(packageName, area, relativePath).text
    }

    override suspend fun write(context: Context, text: String) {
        withContext(Dispatchers.IO) {
            SandboxFileRepository.writeText(packageName, area, relativePath, text)
        }
    }
}

/**
 * @brief PCP 外部脚本目录中的文本文件。
 */
data class ScriptTextEditorTarget(
    val entry: ScriptEntry
) : TextEditorTarget {
    override val displayName: String = entry.name

    override suspend fun read(context: Context): String =
        ScriptStore.read(context, entry)
            ?: throw IOException("Unable to read ${entry.name}")

    override suspend fun write(context: Context, text: String) {
        withContext(Dispatchers.IO) {
            ScriptStore.write(context, entry, text)
        }
    }
}

/**
 * @brief 已加载的编辑器文档。
 */
data class LoadedTextDocument(
    val target: TextEditorTarget,
    val text: String
)

private data class EditorSnapshot(
    val value: TextFieldValue
)

/**
 * @brief 显示全局共享文本编辑器。
 */
@Composable
fun TextEditorPage(
    document: LoadedTextDocument,
    toastState: PcpToastState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var value by remember(document) {
        mutableStateOf(TextFieldValue(document.text, selection = TextRange(0)))
    }
    var savedText by remember(document) { mutableStateOf(document.text) }
    val undoStack = remember(document) { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember(document) { mutableStateListOf<EditorSnapshot>() }
    var saving by remember { mutableStateOf(false) }
    var showCloseConfirmation by remember { mutableStateOf(false) }

    val modified = value.text != savedText

    fun showToast(text: String, isError: Boolean = false) {
        scope.launch { toastState.show(text, isError) }
    }

    fun save(closeAfterSave: Boolean = false) {
        if (saving) return
        saving = true
        val textToSave = value.text
        scope.launch {
            val result = runCatching { document.target.write(context, textToSave) }
            saving = false
            result.onSuccess {
                savedText = textToSave
                showToast(t("editor.saved"))
                if (closeAfterSave) onClose()
            }.onFailure {
                showToast(
                    t("editor.save_failed") + ": " +
                        (it.message ?: it.javaClass.simpleName),
                    isError = true
                )
            }
        }
    }

    fun requestClose() {
        if (modified) {
            showCloseConfirmation = true
        } else {
            onClose()
        }
    }

    BackHandler(onBack = ::requestClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        EditorActionBar(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            onUndo = {
                if (undoStack.isNotEmpty()) {
                    redoStack.add(EditorSnapshot(value))
                    value = undoStack.removeAt(undoStack.lastIndex).value
                }
            },
            onRedo = {
                if (redoStack.isNotEmpty()) {
                    undoStack.add(EditorSnapshot(value))
                    value = redoStack.removeAt(redoStack.lastIndex).value
                }
            },
            onSave = { save() },
            onSearch = {},
            onClose = ::requestClose
        )
        EditorInfoBar(
            fileName = document.target.displayName,
            modified = modified,
            position = editorPosition(value)
        )
        EditorTextArea(
            value = value,
            onValueChange = { next ->
                if (next.text != value.text) {
                    undoStack.add(EditorSnapshot(value))
                    redoStack.clear()
                }
                value = next
            },
            modifier = Modifier.weight(1f)
        )
    }

    if (saving) {
        SavingDialog()
    }

    if (showCloseConfirmation) {
        UnsavedChangesDialog(
            fileName = document.target.displayName,
            onDismiss = { showCloseConfirmation = false },
            onDiscard = {
                showCloseConfirmation = false
                onClose()
            },
            onSaveAndExit = {
                showCloseConfirmation = false
                save(closeAfterSave = true)
            }
        )
    }
}

@Composable
private fun EditorActionBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit
) {
    Surface(color = colorResource(R.color.pcp_top_bar)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorIconButton(
                    icon = EditorIcon.UNDO,
                    description = t("editor.undo"),
                    enabled = canUndo,
                    onClick = onUndo,
                    modifier = Modifier.weight(1f)
                )
                EditorIconButton(
                    icon = EditorIcon.REDO,
                    description = t("editor.redo"),
                    enabled = canRedo,
                    onClick = onRedo,
                    modifier = Modifier.weight(1f)
                )
                EditorIconButton(
                    icon = EditorIcon.SAVE,
                    description = t("editor.save"),
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                )
                EditorIconButton(
                    icon = EditorIcon.SEARCH,
                    description = t("editor.search"),
                    onClick = onSearch,
                    modifier = Modifier.weight(1f)
                )
                EditorIconButton(
                    icon = EditorIcon.CLOSE,
                    description = t("editor.close"),
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun EditorInfoBar(
    fileName: String,
    modified: Boolean,
    position: String
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (modified) "*$fileName" else fileName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = position,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EditorTextArea(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val density = LocalDensity.current
    val lineCount = value.text.count { it == '\n' } + 1
    val digits = lineCount.toString().length.coerceAtLeast(1)
    val gutterWidth = (digits * 10 + 14).dp
    val textStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val viewportHeight = maxHeight
        val minimumTextWidth = (maxWidth - gutterWidth).coerceAtLeast(1.dp)
        val dividerOffset = gutterWidth - with(density) {
            horizontalScroll.value.toDp()
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll)
                .verticalScroll(verticalScroll)
        ) {
            Column(
                modifier = Modifier
                    .width(gutterWidth)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                repeat(lineCount) { index ->
                    Text(
                        text = (index + 1).toString(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 7.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .widthIn(min = minimumTextWidth)
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
        }
        Box(
            modifier = Modifier
                .offset(x = dividerOffset - 1.dp)
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun EditorIconButton(
    icon: EditorIcon,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description }
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            when (icon) {
                EditorIcon.UNDO, EditorIcon.REDO -> {
                    val reverse = icon == EditorIcon.REDO
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.24f, size.height * 0.5f),
                        end = Offset(size.width * 0.76f, size.height * 0.5f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    val tipX = if (reverse) size.width * 0.76f else size.width * 0.24f
                    drawLine(
                        color,
                        Offset(tipX, size.height * 0.5f),
                        Offset(
                            if (reverse) size.width * 0.57f else size.width * 0.43f,
                            size.height * 0.3f
                        ),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color,
                        Offset(tipX, size.height * 0.5f),
                        Offset(
                            if (reverse) size.width * 0.57f else size.width * 0.43f,
                            size.height * 0.7f
                        ),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                EditorIcon.SAVE -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * 0.18f, size.height * 0.14f),
                        size = Size(size.width * 0.64f, size.height * 0.72f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                        style = Stroke(stroke)
                    )
                    drawRect(
                        color = color,
                        topLeft = Offset(size.width * 0.33f, size.height * 0.2f),
                        size = Size(size.width * 0.34f, size.height * 0.22f),
                        style = Stroke(stroke * 0.8f)
                    )
                    drawCircle(
                        color = color,
                        radius = size.minDimension * 0.12f,
                        center = Offset(size.width * 0.5f, size.height * 0.66f),
                        style = Stroke(stroke * 0.8f)
                    )
                }
                EditorIcon.SEARCH -> {
                    drawCircle(
                        color = color,
                        radius = size.minDimension * 0.25f,
                        center = Offset(size.width * 0.42f, size.height * 0.42f),
                        style = Stroke(stroke)
                    )
                    drawLine(
                        color,
                        Offset(size.width * 0.6f, size.height * 0.6f),
                        Offset(size.width * 0.82f, size.height * 0.82f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                EditorIcon.CLOSE -> {
                    drawLine(
                        color,
                        Offset(size.width * 0.25f, size.height * 0.25f),
                        Offset(size.width * 0.75f, size.height * 0.75f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color,
                        Offset(size.width * 0.75f, size.height * 0.25f),
                        Offset(size.width * 0.25f, size.height * 0.75f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

private enum class EditorIcon {
    UNDO,
    REDO,
    SAVE,
    SEARCH,
    CLOSE
}

@Composable
private fun SavingDialog() {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(
                    text = t("editor.loading"),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun UnsavedChangesDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onSaveAndExit: () -> Unit
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
                    text = fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider()
                Text(
                    text = t("editor.unsaved_prompt"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDiscard) {
                        Text(t("editor.discard"), color = Color(0xFF2563EB))
                    }
                    TextButton(onClick = onSaveAndExit) {
                        Text(t("editor.save_and_exit"), color = Color(0xFF2563EB))
                    }
                }
            }
        }
    }
}

private fun editorPosition(value: TextFieldValue): String {
    val text = value.text
    val cursor = value.selection.end.coerceIn(0, text.length)
    val beforeCursor = text.substring(0, cursor)
    val line = beforeCursor.count { it == '\n' } + 1
    val column = cursor - beforeCursor.lastIndexOf('\n')
    val selectedCount = if (value.selection.collapsed) {
        0
    } else {
        val start = minOf(value.selection.start, value.selection.end).coerceAtLeast(0)
        val end = maxOf(value.selection.start, value.selection.end).coerceAtMost(text.length)
        text.codePointCount(start, end)
    }
    return if (selectedCount > 0) "$line:$column ($selectedCount)" else "$line:$column"
}
