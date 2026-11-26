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
 * @file HomePage.kt
 * @brief 首页界面。
 *
 * 该代码文件实现首页功能入口，并提供悬浮窗权限申请和快捷导航。
 */
package com.pcptoolkit.app.feature.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcptoolkit.app.platform.overlay.FloatingWindowService

/**
 * @brief 显示首页功能入口。
 */
@Composable
internal fun HomePage() {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }

    /**
     * @brief 检查悬浮窗权限并启动悬浮窗服务。
     */
    fun openOverlay() {
        if (Settings.canDrawOverlays(context)) {
            runCatching { FloatingWindowService.start(context) }
                .onSuccess { message = "悬浮窗已开启，可返回游戏后点击 PCP 悬浮球" }
                .onFailure { message = "悬浮窗启动失败：${it.message ?: "未知错误"}" }
        } else {
            message = "请允许 PCP 显示在其他应用上层"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            runCatching { FloatingWindowService.start(context) }
                .onSuccess { message = "悬浮窗已开启，可返回游戏后点击 PCP 悬浮球" }
                .onFailure { message = "悬浮窗启动失败：${it.message ?: "未知错误"}" }
        } else {
            message = "未获得悬浮窗权限"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "PCPToolKit",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "开启悬浮窗后，返回游戏，点击 PCP 悬浮球即可选择并运行脚本。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = {
                if (Settings.canDrawOverlays(context)) {
                    openOverlay()
                } else {
                    runCatching {
                        permissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }.onFailure {
                        message = "无法打开悬浮窗权限设置"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("开启悬浮窗", fontWeight = FontWeight.SemiBold)
        }
        if (message.isNotEmpty()) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "运行脚本还需要开启“PCP 脚本自动化”无障碍服务，用于截屏和模拟点击/滑动。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
