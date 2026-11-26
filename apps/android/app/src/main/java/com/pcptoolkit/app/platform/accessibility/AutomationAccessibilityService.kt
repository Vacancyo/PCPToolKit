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
 * @file AutomationAccessibilityService.kt
 * @brief 自动化无障碍服务。
 *
 * 该代码文件提供屏幕截图、单击、滑动和显示方向归一化等自动化基础能力。
 */
package com.pcptoolkit.app.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * @brief 为脚本提供截图、单击和滑动能力的无障碍服务。
 */
class AutomationAccessibilityService : AccessibilityService() {

    /**
     * @brief 记录无障碍服务连接状态。
     */
    override fun onServiceConnected() {
        instance = this
    }

    /**
     * @brief 忽略无障碍事件。
     * @param event 无障碍事件。
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    /**
     * @brief 处理服务中断回调。
     */
    override fun onInterrupt() = Unit

    /**
     * @brief 清理无障碍服务连接状态。
     */
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * @brief 同步等待一次屏幕截图。
     * @param timeoutMillis 最长等待毫秒数。
     * @return 截图成功返回位图，否则返回空。
     */
    fun screenshotBlocking(timeoutMillis: Long = 2_000L): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        val requested = runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(this),
                object : TakeScreenshotCallback {

                    /**
                     * @brief 接收异步截图成功结果。
                     * @param screenshot 截图结果。
                     */
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        try {
                            bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Throwable) {
                            bitmap = null
                        } finally {
                            runCatching { buffer.close() }
                            latch.countDown()
                        }
                    }

                    /**
                     * @brief 接收异步截图失败结果。
                     * @param errorCode 截图失败错误码。
                     */
                    override fun onFailure(errorCode: Int) {
                        latch.countDown()
                    }
                }
            )
        }.isSuccess
        if (!requested) return null
        latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        return bitmap?.let(::normalizeToDisplayOrientation)
    }

    /**
     * @brief 同步执行一次单击手势。
     * @param x 归一化横坐标。
     * @param y 归一化纵坐标。
     * @param timeoutMillis 最长等待毫秒数。
     */
    fun tapBlocking(
        x: Float,
        y: Float,
        durationMillis: Long = 50L,
        timeoutMillis: Long = 2_000L
    ): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMillis.coerceAtLeast(1L)
        )
        return dispatchBlocking(
            GestureDescription.Builder().addStroke(stroke).build(),
            timeoutMillis
        )
    }

    /**
     * @brief 同步执行一次滑动手势。
     * @param x1 起点横坐标。
     * @param y1 起点纵坐标。
     * @param x2 终点横坐标。
     * @param y2 终点纵坐标。
     * @param durationMillis 滑动持续时间，单位为毫秒。
     * @param timeoutMillis 最长等待毫秒数。
     */
    fun swipeBlocking(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMillis: Long,
        timeoutMillis: Long = 3_000L
    ): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMillis.coerceAtLeast(1L)
        )
        return dispatchBlocking(
            GestureDescription.Builder().addStroke(stroke).build(),
            timeoutMillis.coerceAtLeast(durationMillis + 1_000L)
        )
    }

    /**
     * @brief 同步等待手势分发完成。
     * @param gesture 待分发手势。
     * @param timeoutMillis 最长等待毫秒数。
     * @param action 失败时记录的动作名称。
     * @return 手势成功完成返回 true。
     */
    private fun dispatchBlocking(
        gesture: GestureDescription,
        timeoutMillis: Long
    ): Boolean {
        val latch = CountDownLatch(1)
        var completed = false
        val dispatched = runCatching {
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {

                    /**
                     * @brief 标记手势执行完成。
                     * @param gestureDescription 完成的手势描述。
                     */
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        completed = true
                        latch.countDown()
                    }

                    /**
                     * @brief 标记手势执行取消。
                     * @param gestureDescription 取消的手势描述。
                     */
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        latch.countDown()
                    }
                },
                null
            )
        }.getOrDefault(false)
        if (!dispatched) return false
        latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        return completed
    }

    /**
     * @brief 把截图旋转到当前显示方向。
     * @param bitmap 原始截图。
     * @return 方向归一化后的截图。
     */
    private fun normalizeToDisplayOrientation(bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return bitmap
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = manager.currentWindowMetrics.bounds
        val displayLandscape = bounds.width() > bounds.height()
        val bitmapLandscape = bitmap.width > bitmap.height
        if (displayLandscape == bitmapLandscape) return bitmap

        val displays = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val rotation = displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        val degrees = when (rotation) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_270 -> 270f
            else -> 90f
        }
        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(degrees) },
            true
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    companion object {
        @Volatile
        var instance: AutomationAccessibilityService? = null
            private set

        /**
         * @brief 判断无障碍服务是否已经连接。
         * @return 已连接返回 true。
         */
        fun isConnected(): Boolean = instance != null
    }
}
