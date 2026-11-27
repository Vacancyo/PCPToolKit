/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * @file SandboxRepository.kt
 * @brief 沙箱应用导入、列表与启动操作。
 */
package com.pcptoolkit.app.feature.sandbox

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import top.niunaijun.blackbox.BlackBoxCore
import java.util.Locale

/**
 * @brief 沙箱中已安装应用的展示信息。
 */
data class SandboxApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val icon: Bitmap?
)

/**
 * @brief 沙箱 APK 导入结果。
 */
data class SandboxInstallResult(
    val success: Boolean,
    val packageName: String?,
    val message: String?
)

/**
 * @brief 沙箱应用启动结果。
 */
sealed interface SandboxLaunchResult {
    data object Launched : SandboxLaunchResult
    data object NoLaunchActivity : SandboxLaunchResult
    data object NotInstalled : SandboxLaunchResult
    data class Failed(val message: String?) : SandboxLaunchResult
}

/**
 * @brief 对 BlackBox 容器能力的轻量封装。
 */
object SandboxRepository {
    private const val SANDBOX_USER_ID = 0

    /**
     * @brief 当前设备是否有容器内核支持的 CPU 架构。
     */
    fun isSupported(): Boolean = Build.SUPPORTED_ABIS.any {
        it == "arm64-v8a" || it == "armeabi-v7a"
    }

    /**
     * @brief 获取沙箱中的主应用。
     */
    fun getInstalledApp(): SandboxApp? {
        return installedApps().firstOrNull()
    }

    /**
     * @brief 列出沙箱中的用户应用。
     */
    private fun installedApps(): List<SandboxApp> {
        if (!isSupported()) return emptyList()
        return runCatching {
            val hostPackage = BlackBoxCore.getHostPkg()
            BlackBoxCore.get()
                .getInstalledApplications(0, SANDBOX_USER_ID)
                .asSequence()
                .filter { info ->
                    info.packageName != hostPackage &&
                        (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
                .map { info -> info.toSandboxApp() }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
                .toList()
        }.getOrDefault(emptyList())
    }

    /**
     * @brief 将 SAF 中的 APK 安装到沙箱。
     */
    fun install(context: Context, uri: Uri): SandboxInstallResult {
        if (!isSupported()) {
            return SandboxInstallResult(false, null, "unsupported_abi")
        }
        if (getInstalledApp() != null) {
            return SandboxInstallResult(false, null, "already_installed")
        }
        return runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val result = BlackBoxCore.get().installPackageAsUser(uri, SANDBOX_USER_ID)
            SandboxInstallResult(
                success = result.success,
                packageName = result.packageName,
                message = result.msg
            )
        }.getOrElse { error ->
            SandboxInstallResult(
                success = false,
                packageName = null,
                message = error.message ?: error.javaClass.simpleName
            )
        }
    }

    /**
     * @brief 启动沙箱应用。
     */
    fun canLaunch(packageName: String): Boolean = runCatching {
        BlackBoxCore.get().let { core ->
            core.isInstalled(packageName, SANDBOX_USER_ID) &&
                BlackBoxCore.getBPackageManager()
                    .getLaunchIntentForPackage(packageName, SANDBOX_USER_ID) != null
        }
    }.getOrDefault(false)

    /**
     * @brief 启动沙箱应用并区分失败原因。
     */
    fun launch(packageName: String): SandboxLaunchResult = runCatching {
        val core = BlackBoxCore.get()
        when {
            !core.isInstalled(packageName, SANDBOX_USER_ID) ->
                SandboxLaunchResult.NotInstalled
            BlackBoxCore.getBPackageManager()
                .getLaunchIntentForPackage(packageName, SANDBOX_USER_ID) == null ->
                SandboxLaunchResult.NoLaunchActivity
            core.launchApk(packageName, SANDBOX_USER_ID) ->
                SandboxLaunchResult.Launched
            else ->
                SandboxLaunchResult.Failed(null)
        }
    }.getOrElse { error ->
        SandboxLaunchResult.Failed(error.message ?: error.javaClass.simpleName)
    }

    /**
     * @brief 从沙箱当前用户中卸载应用。
     */
    fun uninstall(packageName: String) {
        runCatching {
            BlackBoxCore.get().uninstallPackageAsUser(packageName, SANDBOX_USER_ID)
        }
    }

    private fun ApplicationInfo.toSandboxApp(): SandboxApp {
        val packageManager = BlackBoxCore.getPackageManager()
        val manifestLabel = runCatching {
            packageManager.getApplicationLabel(this).toString()
        }.getOrNull()
        val appLabel = manifestLabel
            ?.takeIf { it.isNotBlank() && it != name && it != packageName }
            ?: packageName
        val icon = runCatching {
            packageManager.getApplicationIcon(this).toBitmap(
                width = ICON_SIZE,
                height = ICON_SIZE,
                config = Bitmap.Config.ARGB_8888
            )
        }.getOrNull()
        val versionName = runCatching {
            BlackBoxCore.getBPackageManager()
                .getPackageInfo(packageName, 0, SANDBOX_USER_ID)
                .versionName
        }.getOrNull()
        return SandboxApp(
            packageName = packageName,
            label = appLabel,
            versionName = versionName,
            icon = icon
        )
    }

    private const val ICON_SIZE = 128
}
