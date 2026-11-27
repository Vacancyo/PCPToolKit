/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * @file PcpToast.kt
 * @brief PCP 底部浮现提示。
 */
package com.pcptoolkit.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal data class ToastMessage(
    val id: Long,
    val text: String,
    val isError: Boolean
)

/**
 * @brief 控制底部淡入淡出提示。
 */
@Stable
class PcpToastState {
    internal var message by mutableStateOf<ToastMessage?>(null)
        private set
    var visible by mutableStateOf(false)
        private set
    private var version = 0

    /**
     * @brief 显示一条提示。
     * @param text 提示文本。
     * @param isError 是否为错误提示。
     */
    suspend fun show(text: String, isError: Boolean = false) {
        val current = ++version
        message = ToastMessage(current.toLong(), text, isError)
        visible = true
    }

    internal fun hide(id: Long) {
        if (message?.id == id) visible = false
    }

    internal fun clear(id: Long) {
        if (message?.id == id) message = null
    }
}

/**
 * @brief 创建底部提示控制器。
 */
@Composable
fun rememberPcpToastState(): PcpToastState = remember { PcpToastState() }

/**
 * @brief 显示圆角淡入淡出的底部提示。
 */
@Composable
fun PcpToastHost(
    state: PcpToastState,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.message?.id, state.visible) {
        val current = state.message ?: return@LaunchedEffect
        if (!state.visible) return@LaunchedEffect
        delay(1_800)
        if (state.message?.id != current.id) return@LaunchedEffect
        state.hide(current.id)
        delay(220)
        if (state.message?.id == current.id) {
            state.clear(current.id)
        }
    }

    AnimatedVisibility(
        visible = state.visible,
        modifier = modifier
            .navigationBarsPadding()
            .imePadding(),
        enter = fadeIn(tween(180)) + slideInVertically(
            animationSpec = tween(220),
            initialOffsetY = { it / 2 }
        ),
        exit = fadeOut(tween(180)) + slideOutVertically(
            animationSpec = tween(180),
            targetOffsetY = { it / 2 }
        )
    ) {
        state.message?.let { message ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF303030),
                tonalElevation = 6.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
