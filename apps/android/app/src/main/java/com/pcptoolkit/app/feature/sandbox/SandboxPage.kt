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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pcptoolkit.app.platform.overlay.FloatingWindowService
import com.pcptoolkit.app.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @brief 显示唯一沙箱游戏的主页仪表盘。
 * @param overlayEnabled 启动游戏时是否自动显示悬浮窗。
 */
@Composable
fun SandboxPage(
    overlayEnabled: Boolean,
    fileApp: SandboxApp?,
    onFileAppChange: (SandboxApp?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supported = remember { SandboxRepository.isSupported() }
    var app by remember { mutableStateOf<SandboxApp?>(null) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
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

    LaunchedEffect(supported) {
        loadInstalledApp()
    }

    fileApp?.let { selected ->
        SandboxFilePage(
            app = selected,
            onBack = { onFileAppChange(null) }
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
                    OutlinedButton(
                        onClick = {
                            previewEmpty = false
                            pendingUninstall = app
                            showTestDialog = false
                        },
                        enabled = app != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t("sandbox.test_uninstall"))
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
            shownApp == null -> EmptySandboxState(
                importing = importing,
                onImport = {
                    if (app != null) {
                        statusIsError = true
                        statusMessage = t("sandbox.already_installed")
                    } else {
                        apkPicker.launch(
                            arrayOf(
                                "application/vnd.android.package-archive",
                                "application/x-apk",
                                "application/octet-stream"
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
            else -> GameDashboard(
                app = shownApp,
                overlayEnabled = overlayEnabled,
                onLaunch = { launchGame(shownApp) },
                onUninstall = { pendingUninstall = shownApp },
                onFiles = { onFileAppChange(shownApp) },
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
private fun EmptySandboxState(
    importing: Boolean,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "APK",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = t("sandbox.empty_title"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = t("sandbox.empty_body"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onImport,
            enabled = !importing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(t("sandbox.import_apk"), fontWeight = FontWeight.SemiBold)
            }
        }
        if (importing) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = t("sandbox.importing"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GameDashboard(
    app: SandboxApp,
    overlayEnabled: Boolean,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
    onFiles: () -> Unit,
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
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                app.versionName?.takeIf { it.isNotBlank() }?.let { version ->
                    Text(
                        text = t("sandbox.version") + " " + version,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Button(
            onClick = onLaunch,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = t("sandbox.launch"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onFiles,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(t("sandbox.file_manager"))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onUninstall,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(t("sandbox.uninstall"))
        }

        if (overlayEnabled) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = t("sandbox.overlay_enabled"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

private fun describeInstallError(result: SandboxInstallResult): String {
    if (result.message == "unsupported_abi") {
        return t("sandbox.unsupported_abi")
    }
    if (result.message == "already_installed") {
        return t("sandbox.already_installed")
    }
    return result.message?.takeIf { it.isNotBlank() } ?: t("sandbox.unknown_error")
}
