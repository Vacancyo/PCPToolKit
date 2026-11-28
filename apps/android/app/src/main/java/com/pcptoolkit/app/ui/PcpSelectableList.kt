package com.pcptoolkit.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pcptoolkit.app.t
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PcpSelectableFileRow(
    itemKey: String,
    bookmarked: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
    onSelect: () -> Unit,
    onBookmark: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxReveal = with(density) { 96.dp.toPx() }
    val actionThreshold = with(density) { 54.dp.toPx() }
    var dragOffset by remember(itemKey) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        if (dragOffset < 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF2563EB))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(76.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    SelectionGlyph(Color.White)
                }
            }
        } else if (dragOffset > 0f && !selectionMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFFFD54F))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(76.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    BookmarkGlyph(Color(0xFF7A5600))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onLongPress
                )
                .pointerInput(itemKey, selectionMode, selected) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalX = 0f
                        var totalY = 0f
                        var horizontal: Boolean? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull {
                                it.id == down.id
                            } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            totalX += delta.x
                            totalY += delta.y
                            if (horizontal == null) {
                                val slop = viewConfiguration.touchSlop
                                if (abs(totalX) > slop || abs(totalY) > slop) {
                                    horizontal =
                                        abs(totalX) > abs(totalY) * 1.45f
                                }
                            }
                            if (horizontal == true) {
                                change.consume()
                                dragOffset = when {
                                    totalX > 0f && !selectionMode ->
                                        totalX.coerceIn(0f, maxReveal)
                                    totalX < 0f ->
                                        totalX.coerceIn(-maxReveal, 0f)
                                    else -> 0f
                                }
                            } else if (horizontal == false) {
                                break
                            }
                        }
                        if (horizontal == true) {
                            if (dragOffset <= -actionThreshold && !selected) {
                                onSelect()
                            } else if (dragOffset >= actionThreshold && !selectionMode) {
                                onBookmark()
                            }
                            scope.launch {
                                animate(
                                    initialValue = dragOffset,
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = FastOutSlowInEasing
                                    )
                                ) { value, _ ->
                                    dragOffset = value
                                }
                            }
                        }
                    }
                }
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        if (bookmarked) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFFFD54F).copy(alpha = 0.24f))
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF2563EB).copy(alpha = 0.22f))
            )
        }
    }
}

@Composable
internal fun PcpSelectionToolBar(
    enabled: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onInvert: () -> Unit,
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
            SelectionToolButton(
                icon = SelectionToolIcon.SELECT_ALL,
                description = t("common.select_all"),
                enabled = enabled,
                onClick = onSelectAll
            )
            SelectionToolButton(
                icon = SelectionToolIcon.CLEAR,
                description = t("common.clear_selection"),
                enabled = enabled,
                onClick = onClear
            )
            SelectionToolButton(
                icon = SelectionToolIcon.INVERT,
                description = t("common.invert_selection"),
                enabled = enabled,
                onClick = onInvert
            )
        }
    }
}

@Composable
internal fun PcpBatchDeleteDialog(
    title: String,
    displayName: String,
    remainingLabel: String?,
    warning: String,
    confirmLabel: String,
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
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 14.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (remainingLabel != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = remainingLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Text(
                    text = warning,
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 6.dp,
                        bottom = 14.dp
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = confirmLabel,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SelectionToolButton(
    icon: SelectionToolIcon,
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
            when (icon) {
                SelectionToolIcon.SELECT_ALL -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * 0.14f, size.height * 0.14f),
                        size = Size(size.width * 0.72f, size.height * 0.72f),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        style = Stroke(stroke)
                    )
                    drawLine(
                        color,
                        Offset(size.width * 0.3f, size.height * 0.52f),
                        Offset(size.width * 0.45f, size.height * 0.67f),
                        stroke,
                        StrokeCap.Round
                    )
                    drawLine(
                        color,
                        Offset(size.width * 0.45f, size.height * 0.67f),
                        Offset(size.width * 0.72f, size.height * 0.36f),
                        stroke,
                        StrokeCap.Round
                    )
                }
                SelectionToolIcon.CLEAR -> {
                    drawLine(
                        color,
                        Offset(size.width * 0.25f, size.height * 0.25f),
                        Offset(size.width * 0.75f, size.height * 0.75f),
                        stroke,
                        StrokeCap.Round
                    )
                    drawLine(
                        color,
                        Offset(size.width * 0.75f, size.height * 0.25f),
                        Offset(size.width * 0.25f, size.height * 0.75f),
                        stroke,
                        StrokeCap.Round
                    )
                }
                SelectionToolIcon.INVERT -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * 0.14f, size.height * 0.14f),
                        size = Size(size.width * 0.52f, size.height * 0.52f),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        style = Stroke(stroke)
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * 0.34f, size.height * 0.34f),
                        size = Size(size.width * 0.52f, size.height * 0.52f),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkGlyph(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.24f, size.height * 0.12f)
            lineTo(size.width * 0.76f, size.height * 0.12f)
            lineTo(size.width * 0.76f, size.height * 0.88f)
            lineTo(size.width * 0.5f, size.height * 0.7f)
            lineTo(size.width * 0.24f, size.height * 0.88f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
private fun SelectionGlyph(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * 0.4f,
            style = Stroke(stroke)
        )
        drawLine(
            color,
            Offset(size.width * 0.3f, size.height * 0.52f),
            Offset(size.width * 0.45f, size.height * 0.67f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.45f, size.height * 0.67f),
            Offset(size.width * 0.72f, size.height * 0.36f),
            stroke,
            StrokeCap.Round
        )
    }
}

private enum class SelectionToolIcon {
    SELECT_ALL,
    CLEAR,
    INVERT
}
