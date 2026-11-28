/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * @file SandboxPage.kt
 * @brief 主页中的单游戏沙箱仪表盘。
 */
package com.pcptoolkit.app.feature.sandbox

import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pcptoolkit.app.feature.editor.TextEditorTarget
import com.pcptoolkit.app.platform.overlay.FloatingWindowService
import com.pcptoolkit.app.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SANDBOX_APK_MIME_TYPES = arrayOf(
    "application/vnd.android.package-archive",
    "application/x-apk",
    "application/octet-stream"
)

/**
 * @brief 显示唯一沙箱游戏的主页仪表盘。
 * @param overlayEnabled 启动游戏时是否自动显示悬浮窗。
 */
@Composable
fun SandboxPage(
    overlayEnabled: Boolean,
    fileApp: SandboxApp?,
    onFileAppChange: (SandboxApp?) -> Unit,
    onEditText: (TextEditorTarget) -> Unit,
    onToast: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supported = remember { SandboxRepository.isSupported() }
    var app by remember { mutableStateOf<SandboxApp?>(null) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var pendingUninstall by remember { mutableStateOf<SandboxApp?>(null) }
    var previewEmpty by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }

    fun loadInstalledApp() {
        if (!supported) {
            loading = false
            return
        }
        loading = true
        scope.launch {
            app = withContext(Dispatchers.IO) { SandboxRepository.getInstalledApp() }
            loading = false
        }
    }

    fun importApk(uri: Uri) {
        if (!supported || importing || app != null) return
        importing = true
        statusMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                SandboxRepository.install(context, uri)
            }
            val launchable = result.packageName
                ?.takeIf { result.success }
                ?.let { packageName ->
                    withContext(Dispatchers.IO) {
                        SandboxRepository.canLaunch(packageName)
                    }
                }
                ?: false
            importing = false
            statusIsError = !result.success || !launchable
            statusMessage = if (result.success && !launchable) {
                t("sandbox.imported_without_launcher") + " " +
                    result.packageName.orEmpty()
            } else if (result.success) {
                t("sandbox.install_success") + " " + result.packageName.orEmpty()
            } else {
                t("sandbox.install_failed") + ": " + describeInstallError(result)
            }
            if (result.success) loadInstalledApp()
        }
    }

    fun updateApk(uri: Uri) {
        val target = app ?: return
        if (!supported || importing || updating) return
        updating = true
        statusMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                SandboxRepository.update(context, uri, target.packageName)
            }
            updating = false
            statusIsError = !result.success
            statusMessage = if (result.success) {
                t("sandbox.update_success")
            } else {
                t("sandbox.update_failed") + ": " + describeInstallError(result)
            }
            if (result.success) loadInstalledApp()
        }
    }

    fun launchGame(target: SandboxApp) {
        statusMessage = null
        scope.launch {
            var overlayMessage: String? = null
            if (overlayEnabled) {
                if (!Settings.canDrawOverlays(context)) {
                    overlayMessage = t("sandbox.overlay_permission_missing")
                } else {
                    runCatching { FloatingWindowService.start(context) }
                        .onFailure { error ->
                            overlayMessage = t("sandbox.overlay_start_failed") +
                                ": " + (error.message ?: error.javaClass.simpleName)
                        }
                }
            }

            val result = withContext(Dispatchers.IO) {
                SandboxRepository.launch(target.packageName)
            }
            statusIsError = result !is SandboxLaunchResult.Launched
            statusMessage = when (result) {
                SandboxLaunchResult.Launched -> overlayMessage
                SandboxLaunchResult.NoLaunchActivity ->
                    t("sandbox.no_launch_activity") + " " + target.packageName
                SandboxLaunchResult.NotInstalled -> {
                    loadInstalledApp()
                    t("sandbox.app_not_installed")
                }
                is SandboxLaunchResult.Failed ->
                    t("sandbox.launch_failed") +
                        result.message.orEmpty().let { message ->
                            if (message.isBlank()) "" else ": $message"
                        }
            }
        }
    }

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(::importApk) }
    )
    val updatePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(::updateApk) }
    )

    LaunchedEffect(supported) {
        loadInstalledApp()
    }

    fileApp?.let { selected ->
        SandboxFilePage(
            app = selected,
            onBack = { onFileAppChange(null) },
            onEditText = onEditText,
            onToast = onToast
        )
        return
    }

    pendingUninstall?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text(t("sandbox.uninstall_title")) },
            text = {
                Text(
                    t("sandbox.uninstall_message") + "\n" + selected.label
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUninstall = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                SandboxRepository.uninstall(selected.packageName)
                            }
                            statusMessage = null
                            loadInstalledApp()
                        }
                    }
                ) {
                    Text(t("sandbox.uninstall"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) {
                    Text(t("common.cancel"))
                }
            }
        )
    }

    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text(t("sandbox.test_title")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            previewEmpty = true
                            statusMessage = null
                            showTestDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t("sandbox.test_empty"))
                    }
                    if (previewEmpty) {
                        TextButton(
                            onClick = {
                                previewEmpty = false
                                showTestDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(t("sandbox.test_exit_empty"))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestDialog = false }) {
                    Text(t("sandbox.test_close"))
                }
            }
        )
    }

    val shownApp = app.takeUnless { previewEmpty }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        when {
            !supported -> UnsupportedState()
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            else -> GameDashboard(
                app = shownApp,
                importing = importing,
                onImport = {
                    if (app != null) {
                        onToast(t("sandbox.already_installed"), false)
                    } else {
                        apkPicker.launch(SANDBOX_APK_MIME_TYPES)
                    }
                },
                onLaunch = { shownApp?.let(::launchGame) },
                onUpdate = {
                    if (shownApp != null) {
                        updatePicker.launch(SANDBOX_APK_MIME_TYPES)
                    }
                },
                onUninstall = {
                    shownApp?.let { pendingUninstall = it }
                },
                onFiles = {
                    shownApp?.let(onFileAppChange)
                },
                updating = updating,
                modifier = Modifier.weight(1f)
            )
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(vertical = 10.dp),
                color = if (statusIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { showTestDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(t("sandbox.test_entry"))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UnsupportedState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = t("sandbox.unsupported_abi"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GameDashboard(
    app: SandboxApp?,
    importing: Boolean,
    onImport: () -> Unit,
    onLaunch: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onFiles: () -> Unit,
    updating: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app == null) {
                EmptyGameIcon(onClick = onImport)
            } else {
                val icon = app.icon
                if (icon != null) {
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "APK",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app?.label ?: t("sandbox.pending_import"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    text = t("sandbox.version") + " " +
                        (app?.versionName?.takeIf { it.isNotBlank() } ?: "???"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onFiles,
                enabled = app != null,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = t("sandbox.file_manager")
                    }
            ) {
                FolderIcon(
                    if (app == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Button(
            onClick = if (app == null) onImport else onLaunch,
            enabled = if (app == null) !importing else true,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (app == null && importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (app == null) {
                        t("sandbox.import_game")
                    } else {
                        t("sandbox.launch")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onUpdate,
                enabled = app != null && !updating,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2DBE60),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFCDEFD8),
                    disabledContentColor = Color(0xFF6D8A73)
                )
            ) {
                if (updating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(t("sandbox.update"))
                }
            }
            Button(
                onClick = onUninstall,
                enabled = app != null && !updating,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE5484D),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFF8D0D2),
                    disabledContentColor = Color(0xFF8E6C6E)
                )
            ) {
                Text(t("sandbox.uninstall"))
            }
        }

        Text(
            text = t("sandbox.dashboard_notice"),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun EmptyGameIcon(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = t("sandbox.import_game")
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val inset = stroke
            val color = Color(0xFF9AA0A6)
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(7.dp.toPx(), 5.dp.toPx())
                    )
                )
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.5f, size.height * 0.32f),
                end = Offset(size.width * 0.5f, size.height * 0.68f),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.32f, size.height * 0.5f),
                end = Offset(size.width * 0.68f, size.height * 0.5f),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
private fun FolderIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.3f)
            lineTo(size.width * 0.4f, size.height * 0.3f)
            lineTo(size.width * 0.5f, size.height * 0.42f)
            lineTo(size.width * 0.9f, size.height * 0.42f)
            lineTo(size.width * 0.9f, size.height * 0.82f)
            lineTo(size.width * 0.1f, size.height * 0.82f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke)
        )
    }
}

private fun describeInstallError(result: SandboxInstallResult): String {
    if (result.message == "unsupported_abi") {
        return t("sandbox.unsupported_abi")
    }
    if (result.message == "already_installed") {
        return t("sandbox.already_installed")
    }
    if (result.message == "not_installed") {
        return t("sandbox.app_not_installed")
    }
    if (result.message == "package_mismatch") {
        return t("sandbox.package_mismatch")
    }
    return result.message?.takeIf { it.isNotBlank() } ?: t("sandbox.unknown_error")
}
