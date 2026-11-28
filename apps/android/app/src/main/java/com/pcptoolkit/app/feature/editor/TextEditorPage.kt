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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Constraints
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
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var activeMatch by remember { mutableIntStateOf(-1) }
    var scrollSelectionRequest by remember { mutableIntStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val modified = value.text != savedText
    val matches = remember(value.text, searchQuery, caseSensitive, wholeWord) {
        findTextMatches(value.text, searchQuery, caseSensitive, wholeWord)
    }

    LaunchedEffect(value.text, searchQuery, caseSensitive, wholeWord) {
        activeMatch = -1
    }

    LaunchedEffect(searchVisible) {
        if (searchVisible) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

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

    fun selectMatch(index: Int) {
        if (matches.isEmpty()) return
        val normalized = ((index % matches.size) + matches.size) % matches.size
        activeMatch = normalized
        value = value.copy(
            selection = matches[normalized],
            composition = null
        )
        scrollSelectionRequest++
    }

    fun replaceCurrentMatch() {
        val index = activeMatch
        if (index !in matches.indices || value.selection != matches[index]) {
            selectMatch(0)
            return
        }
        val match = matches[index]
        undoStack.add(EditorSnapshot(value))
        redoStack.clear()
        val nextText = value.text.replaceRange(match.start, match.end, replaceQuery)
        value = TextFieldValue(
            text = nextText,
            selection = TextRange(match.start + replaceQuery.length)
        )
        activeMatch = -1
    }

    fun replaceAllMatches() {
        if (matches.isEmpty()) return
        undoStack.add(EditorSnapshot(value))
        redoStack.clear()
        val nextText = buildString {
            var cursor = 0
            matches.forEach { match ->
                append(value.text, cursor, match.start)
                append(replaceQuery)
                cursor = match.end
            }
            append(value.text, cursor, value.text.length)
        }
        value = TextFieldValue(
            text = nextText,
            selection = TextRange(matches.first().start + replaceQuery.length)
        )
        activeMatch = -1
    }

    BackHandler {
        if (searchVisible) {
            searchVisible = false
            activeMatch = -1
        } else {
            requestClose()
        }
    }

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
            onSearch = {
                searchVisible = !searchVisible
                if (!searchVisible) activeMatch = -1
            },
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
            scrollSelectionRequest = scrollSelectionRequest,
            modifier = Modifier.weight(1f)
        )
        if (searchVisible) {
            EditorSearchPanel(
                query = searchQuery,
                replacement = replaceQuery,
                currentMatch = activeMatch,
                matchCount = matches.size,
                caseSensitive = caseSensitive,
                wholeWord = wholeWord,
                onQueryChange = { searchQuery = it },
                onReplacementChange = { replaceQuery = it },
                onToggleCaseSensitive = { caseSensitive = !caseSensitive },
                onToggleWholeWord = { wholeWord = !wholeWord },
                onPrevious = {
                    selectMatch(if (activeMatch < 0) matches.lastIndex else activeMatch - 1)
                },
                onNext = { selectMatch(if (activeMatch < 0) 0 else activeMatch + 1) },
                onReplace = ::replaceCurrentMatch,
                onReplaceAll = ::replaceAllMatches,
                searchFocusRequester = searchFocusRequester,
                onClose = {
                    searchVisible = false
                    activeMatch = -1
                }
            )
        }
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
private fun EditorSearchPanel(
    query: String,
    replacement: String,
    currentMatch: Int,
    matchCount: Int,
    caseSensitive: Boolean,
    wholeWord: Boolean,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onToggleCaseSensitive: () -> Unit,
    onToggleWholeWord: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    searchFocusRequester: FocusRequester,
    onClose: () -> Unit
) {
    val blue = Color(0xFF2563EB)
    val disabledBlue = blue.copy(alpha = 0.35f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(
                        Locale.getDefault(),
                        t("editor.search_results"),
                        if (currentMatch >= 0) currentMatch + 1 else 0,
                        matchCount
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onToggleCaseSensitive,
                    modifier = Modifier.semantics {
                        contentDescription = t("editor.case_sensitive")
                    }
                ) {
                    Text(
                        text = "Aa",
                        color = if (caseSensitive) blue else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(
                    onClick = onToggleWholeWord,
                    modifier = Modifier.semantics {
                        contentDescription = t("editor.whole_word")
                    }
                ) {
                    Text(
                        text = "W",
                        color = if (wholeWord) blue else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            SearchInputRow(
                label = t("editor.find"),
                value = query,
                onValueChange = onQueryChange,
                focusRequester = searchFocusRequester
            )
            SearchInputRow(
                label = t("editor.replace"),
                value = replacement,
                onValueChange = onReplacementChange
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchTextAction(
                    text = t("editor.previous_match"),
                    enabled = matchCount > 0,
                    color = if (matchCount > 0) blue else disabledBlue,
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f)
                )
                SearchTextAction(
                    text = t("editor.next_match"),
                    enabled = matchCount > 0,
                    color = if (matchCount > 0) blue else disabledBlue,
                    onClick = onNext,
                    modifier = Modifier.weight(1f)
                )
                SearchTextAction(
                    text = t("editor.replace"),
                    enabled = matchCount > 0,
                    color = if (matchCount > 0) blue else disabledBlue,
                    onClick = onReplace,
                    modifier = Modifier.weight(1f)
                )
                SearchTextAction(
                    text = t("editor.replace_all"),
                    enabled = matchCount > 0,
                    color = if (matchCount > 0) blue else disabledBlue,
                    onClick = onReplaceAll,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = t("common.cancel")
                        }
                ) {
                    Canvas(Modifier.size(22.dp)) {
                        val stroke = 2.dp.toPx()
                        drawLine(
                            blue,
                            Offset(size.width * 0.24f, size.height * 0.24f),
                            Offset(size.width * 0.76f, size.height * 0.76f),
                            stroke,
                            StrokeCap.Round
                        )
                        drawLine(
                            blue,
                            Offset(size.width * 0.76f, size.height * 0.24f),
                            Offset(size.width * 0.24f, size.height * 0.76f),
                            stroke,
                            StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(42.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .then(
                        if (focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                interactionSource = interactionSource
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (focused) 2.dp else 1.dp)
                    .background(
                        if (focused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
            )
        }
    }
}

@Composable
private fun SearchTextAction(
    text: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EditorTextArea(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    scrollSelectionRequest: Int,
    modifier: Modifier = Modifier
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
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
        LaunchedEffect(scrollSelectionRequest) {
            if (scrollSelectionRequest <= 0 ||
                value.selection.collapsed ||
                value.text.isEmpty()
            ) {
                return@LaunchedEffect
            }
            val textLayout = textMeasurer.measure(
                text = AnnotatedString(value.text),
                style = textStyle,
                softWrap = false,
                maxLines = Int.MAX_VALUE,
                constraints = Constraints()
            )
            val offset = minOf(value.selection.start, value.selection.end)
                .coerceIn(0, value.text.lastIndex)
            val box = textLayout.getBoundingBox(offset)
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { viewportHeight.toPx() }
            val textStartPx = with(density) { (gutterWidth + 8.dp).toPx() }
            horizontalScroll.animateScrollTo(
                (textStartPx + box.left - viewportWidthPx / 3f)
                    .roundToInt()
                    .coerceIn(0, horizontalScroll.maxValue)
            )
            verticalScroll.animateScrollTo(
                (with(density) { 8.dp.toPx() } + box.top - viewportHeightPx / 3f)
                    .roundToInt()
                    .coerceIn(0, verticalScroll.maxValue)
            )
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

private fun findTextMatches(
    text: String,
    query: String,
    caseSensitive: Boolean,
    wholeWord: Boolean
): List<TextRange> {
    if (query.isEmpty() || text.isEmpty()) return emptyList()
    val haystack = if (caseSensitive) text else text.lowercase()
    val needle = if (caseSensitive) query else query.lowercase()
    if (needle.isEmpty()) return emptyList()
    val queryStartsWord = needle.first().isWordCharacter()
    val queryEndsWord = needle.last().isWordCharacter()
    val matches = mutableListOf<TextRange>()
    var offset = haystack.indexOf(needle)
    while (offset >= 0) {
        val end = offset + needle.length
        val wholeWordMatches = !wholeWord ||
            (
                (!queryStartsWord || offset == 0 ||
                    !haystack[offset - 1].isWordCharacter()) &&
                    (!queryEndsWord || end == haystack.length ||
                        !haystack[end].isWordCharacter())
                )
        if (wholeWordMatches) {
            matches.add(TextRange(offset, end))
        }
        offset = haystack.indexOf(needle, offset + 1)
    }
    return matches
}

private fun Char.isWordCharacter(): Boolean =
    isLetterOrDigit() || this == '_'
