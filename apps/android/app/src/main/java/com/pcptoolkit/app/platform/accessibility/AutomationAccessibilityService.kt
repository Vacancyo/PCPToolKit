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

class AutomationAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun screenshotBlocking(timeoutMillis: Long = 2_000L): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        val requested = runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(this),
                object : TakeScreenshotCallback {
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
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        completed = true
                        latch.countDown()
                    }

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

        fun isConnected(): Boolean = instance != null
    }
}
