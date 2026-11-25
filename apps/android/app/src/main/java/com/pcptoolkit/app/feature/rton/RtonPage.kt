package com.pcptoolkit.app.feature.rton

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcptoolkit.app.t

private val SuccessColor = Color(0xFF177245)

private fun stageKey(stage: Stage): String = when (stage) {
    Stage.IDLE -> ""
    Stage.COPYING -> "app.copying"
    Stage.CONVERTING -> "app.running"
    Stage.EXPORTING -> "app.exporting"
    Stage.DONE -> "app.done"
    Stage.CANCELLED -> "app.cancelled"
}

@Composable
internal fun RtonPage(vm: ConvertViewModel) {
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.useFiles()
            vm.addFiles(listOf(uri))
        }
    }
    val pickInputDirectory =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) vm.selectInputDirectory(uri)
        }
    val pickOutputDirectory =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) vm.outTree = uri
        }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = t("rton.intro"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            RtonCard(
                title = t("rton.input_source"),
                trailing = {
                    if (vm.hasInput()) CountBadge(inputCount(vm).toString())
                }
            ) {
                SourceSelector(vm)
                Spacer(Modifier.height(14.dp))
                when (vm.inputSource) {
                    InputSource.FILES -> FileInput(vm) {
                        pickFile.launch(arrayOf("*/*"))
                    }
                    InputSource.DIRECTORY -> DirectoryInput(vm) {
                        pickInputDirectory.launch(null)
                    }
                }
            }
        }

        item {
            RtonCard(title = t("rton.settings")) {
                val modes = listOf("app.mode_auto", "app.mode_decode", "app.mode_encode")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEachIndexed { index, key ->
                        ModeOption(
                            title = t(key),
                            selected = vm.mode == index,
                            enabled = !vm.running,
                            onClick = { vm.mode = index }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = vm.key,
                    onValueChange = { vm.key = it },
                    label = { Text(t("app.key_label")) },
                    supportingText = { Text(t("app.key_hint")) },
                    singleLine = true,
                    enabled = !vm.running,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(14.dp))
                OutputFolderRow(
                    label = t("app.folder_label"),
                    value = vm.folderLabel().ifEmpty { t("app.folder_none") },
                    enabled = !vm.running,
                    onClick = { pickOutputDirectory.launch(null) }
                )
            }
        }

        item {
            if (vm.running) {
                OutlinedButton(
                    onClick = { vm.cancel() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(t("app.cancel"), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = { vm.start() },
                    enabled = !vm.scanning && vm.hasInput() && vm.outTree != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(t("app.start"), fontWeight = FontWeight.SemiBold)
                }
                val hint = when {
                    vm.scanning -> t("rton.scanning")
                    !vm.hasInput() -> t("rton.need_input")
                    vm.outTree == null -> t("rton.need_output")
                    else -> null
                }
                if (hint != null) {
                    Text(
                        text = hint,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (vm.stage != Stage.IDLE) {
            item {
                RtonCard(title = t(stageKey(vm.stage))) {
                    val progress = if (vm.total > 0) vm.done.toFloat() / vm.total else 0f
                    if (vm.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(CircleShape)
                        )
                    } else if (vm.running) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(CircleShape)
                        )
                    }
                    if (vm.total > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${vm.done}/${vm.total}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val ok = vm.results.count { it.ok }
                            val failed = vm.results.size - ok
                            CountBadge("${t("app.success_count")} $ok", SuccessColor)
                            CountBadge("${t("app.fail_count")} $failed", MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (vm.results.isNotEmpty()) {
            item {
                Text(
                    text = t("app.result_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(vm.results) { result ->
                ResultRow(result)
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun SourceSelector(vm: ConvertViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SourceTab(
            text = t("rton.source_files"),
            selected = vm.inputSource == InputSource.FILES,
            enabled = !vm.running,
            modifier = Modifier.weight(1f),
            onClick = vm::useFiles
        )
        SourceTab(
            text = t("rton.source_directory"),
            selected = vm.inputSource == InputSource.DIRECTORY,
            enabled = !vm.running,
            modifier = Modifier.weight(1f),
            onClick = vm::useDirectory
        )
    }
}

@Composable
private fun SourceTab(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun FileInput(vm: ConvertViewModel, onPick: () -> Unit) {
    if (vm.files.isEmpty()) {
        EmptyPicker(
            title = t("rton.no_files"),
            description = t("rton.file_tip"),
            buttonText = t("rton.add_file"),
            enabled = !vm.running,
            onClick = onPick
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.files.forEach { file ->
            FileRow(file) { vm.removeFile(file) }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPick,
                enabled = !vm.running,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(t("rton.add_file"))
            }
            TextButton(
                onClick = vm::clearFiles,
                enabled = !vm.running,
                modifier = Modifier.weight(0.45f)
            ) {
                Text(t("app.clear"))
            }
        }
    }
}

@Composable
private fun FileRow(file: PickedFile, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileBadge(file.name)
            Spacer(Modifier.width(12.dp))
            Text(
                text = file.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                onClick = onRemove,
                modifier = Modifier.semantics {
                    contentDescription = t("rton.remove_file") + ": " + file.name
                }
            ) {
                Text("×", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun DirectoryInput(vm: ConvertViewModel, onPick: () -> Unit) {
    if (vm.inputTree == null) {
        EmptyPicker(
            title = t("rton.pick_input_dir"),
            description = t("rton.dir_tip"),
            buttonText = t("rton.pick_input_dir"),
            enabled = !vm.running,
            onClick = onPick
        )
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileBadge("DIR")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = vm.inputLabel(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = t("rton.dir_tip"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    vm.scanning -> {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = t("rton.scanning"),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    vm.scanFailed -> {
                        Text(
                            text = t("rton.scan_failed"),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    vm.directoryFiles.isEmpty() -> {
                        Text(
                            text = t("rton.no_convertible"),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> {
                        CountBadge(
                            "${t("rton.files_found")} ${vm.directoryFiles.size}",
                            SuccessColor
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
                TextButton(onClick = onPick, enabled = !vm.running) {
                    Text(t("rton.change_dir"))
                }
            }
        }
    }
}

@Composable
private fun EmptyPicker(
    title: String,
    description: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 25.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            modifier = Modifier.padding(top = 3.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.padding(top = 14.dp),
            shape = RoundedCornerShape(13.dp)
        ) {
            Text(buttonText)
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun OutputFolderRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(15.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = t("rton.change"),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ResultRow(result: ResultLine) {
    val color = if (result.ok) SuccessColor else MaterialTheme.colorScheme.error
    val text = when {
        result.ok -> "${result.input} → ${result.output}" +
            if (result.encrypted) "  [${t("app.encrypted")}]" else ""
        result.input.isEmpty() -> result.message
        else -> "${result.input}: ${result.message}"
    }
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = if (result.ok) "✓" else "!",
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                color = if (result.ok) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RtonCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun FileBadge(name: String) {
    val extension = name.substringAfterLast('.', "")
        .uppercase()
        .take(4)
        .ifEmpty { "DIR" }
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = extension,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CountBadge(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

private fun inputCount(vm: ConvertViewModel): Int = when (vm.inputSource) {
    InputSource.FILES -> vm.files.size
    InputSource.DIRECTORY -> vm.directoryFiles.size
}
