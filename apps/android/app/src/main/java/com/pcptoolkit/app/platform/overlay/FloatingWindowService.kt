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
 * @file FloatingWindowService.kt
 * @brief 悬浮窗控制服务。
 *
 * 该代码文件实现悬浮球、控制面板、日志窗口、脚本运行、屏幕捕获和通知管理等交互。
 */
package com.pcptoolkit.app.platform.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pcptoolkit.app.MainActivity
import com.pcptoolkit.app.R
import com.pcptoolkit.app.feature.script.MatchResult
import com.pcptoolkit.app.feature.script.ScriptEngine
import com.pcptoolkit.app.feature.script.ScriptEntry
import com.pcptoolkit.app.feature.script.ScriptException
import com.pcptoolkit.app.feature.script.ScriptHost
import com.pcptoolkit.app.feature.script.ScriptStore
import com.pcptoolkit.app.feature.script.ScriptVision
import com.pcptoolkit.app.platform.accessibility.AutomationAccessibilityService
import top.niunaijun.blackbox.BlackBoxCore
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class OverlayPage(val label: String) {
    HOME("主页"),
    SCRIPTS("脚本"),
    SPEED("变速")
}

/**
 * @brief 承载悬浮球、控制面板、日志窗口和脚本运行的悬浮窗服务。
 */
class FloatingWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var statusView: TextView? = null
    private var scriptGroup: RadioGroup? = null
    private var runButton: Button? = null
    private var stopButton: Button? = null
    private var logButton: TextView? = null
    private var logConsole: View? = null
    private var logText: TextView? = null
    private var logParams: WindowManager.LayoutParams? = null
    private var bubbleRestore: Runnable? = null
    private var logRestore: Runnable? = null
    private var panelPage = OverlayPage.HOME
    private var gameSpeed = DEFAULT_GAME_SPEED

    private var scripts: List<ScriptEntry> = emptyList()
    private var selectedScript: ScriptEntry? = null
    private var scriptThread: Thread? = null
    private val templateCache = mutableMapOf<String, Bitmap>()
    private val templatePixels = mutableMapOf<String, IntArray>()
    private var screenPixelsCache: IntArray? = null
    private val logLines = mutableListOf<String>()

    @Volatile
    private var gameWidth = 0

    @Volatile
    private var gameHeight = 0

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var running = false

    /**
     * @brief 初始化悬浮窗服务、通知和界面资源。
     */
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        gameSpeed = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
            .getFloat(SPEED_PREF_KEY, DEFAULT_GAME_SPEED)
            .coerceIn(MIN_INPUT_GAME_SPEED, MAX_INPUT_GAME_SPEED)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification(running = false))
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        addBubble()
    }

    /**
     * @brief 处理悬浮窗服务启动命令。
     * @param intent 启动参数。
     * @param flags 启动标志。
     * @param startId 启动标识。
     * @return 服务粘性模式。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SCRIPT -> requestStop()
            ACTION_CLOSE -> stopSelf()
            else -> if (Settings.canDrawOverlays(this)) {
                if (bubble == null) addBubble()
            }
        }
        return START_STICKY
    }

    /**
     * @brief 处理屏幕方向或配置变化。
     * @param newConfig 新的设备配置。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bubbleRestore?.let { mainHandler.removeCallbacks(it) }
        logRestore?.let { mainHandler.removeCallbacks(it) }
        bubbleRestore = null
        logRestore = null
        bubble?.visibility = View.VISIBLE
        val (width, height) = screenSize()
        bubble?.let { view ->
            bubbleParams?.let { params ->
                params.x = params.x.coerceIn(0, (width - dp(56)).coerceAtLeast(0))
                params.y = params.y.coerceIn(0, (height - dp(56)).coerceAtLeast(0))
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
        if (logConsole != null) {
            removeLogConsole()
            showLogConsole()
        }
        if (panel != null) {
            removePanel()
            showPanel()
        }
    }

    /**
     * @brief 声明悬浮窗服务不支持绑定。
     * @param intent 绑定请求。
     * @return 始终返回空。
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * @brief 释放悬浮窗、日志窗口和脚本运行资源。
     */
    override fun onDestroy() {
        cancelRequested = true
        scriptThread?.interrupt()
        bubbleRestore?.let { mainHandler.removeCallbacks(it) }
        logRestore?.let { mainHandler.removeCallbacks(it) }
        removePanel()
        removeLogConsole()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    /**
     * @brief 创建并显示可拖动悬浮球。
     */
    private fun addBubble() {
        if (bubble != null) return
        val size = dp(56)
        val text = TextView(this).apply {
            this.text = "PCP"
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE62563EB.toInt())
                setStroke(dp(1), 0x99FFFFFF.toInt())
            }
        }
        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - dp(76)
            y = dp(150)
        }
        attachDrag(text, params)
        runCatching {
            windowManager.addView(text, params)
            bubble = text
            bubbleParams = params
        }.onFailure { stopSelf() }
    }

    /**
     * @brief 为悬浮球添加拖动和点击行为。
     * @param view 悬浮球视图。
     * @param params 窗口布局参数。
     */
    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * @brief 为日志窗口添加拖动行为。
     * @param view 日志窗口视图。
     * @param params 窗口布局参数。
     */
    private fun attachLogDrag(
        handle: View,
        root: View,
        params: WindowManager.LayoutParams
    ) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val (width, height) = screenSize()
                    params.x = (startX + (event.rawX - downRawX).toInt())
                        .coerceIn(0, (width - params.width).coerceAtLeast(0))
                    params.y = (startY + (event.rawY - downRawY).toInt())
                        .coerceIn(0, (height - params.height).coerceAtLeast(0))
                    runCatching { windowManager.updateViewLayout(root, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    /**
     * @brief 切换控制面板显示状态。
     */
    private fun togglePanel() {
        if (panel != null) {
            removePanel()
        } else {
            showPanel()
        }
    }

    /**
     * @brief 创建并显示悬浮窗主页及其二级页面。
     */
    private fun showPanel() {
        val (screenWidth, screenHeight) = screenSize()
        val width = minOf(screenWidth - dp(32), dp(420)).coerceAtLeast(dp(280))
        val maxHeight = (screenHeight - dp(24)).coerceAtLeast(dp(240))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dp(12).toFloat()
            background = rounded(0xF7FFFFFF.toInt(), 20)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = "PCPToolKit · ${panelPage.label}"
                setTextColor(0xFF152338.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        header.addView(
            TextView(this).apply {
                text = "×"
                setTextColor(0xFF536174.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, 0, dp(6))
                setOnClickListener { removePanel() }
            }
        )
        root.addView(header)
        root.addView(
            View(this).apply { setBackgroundColor(0xFFE5E7EB.toInt()) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply { topMargin = dp(10) }
        )

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        OverlayPage.entries.forEach { page ->
            val selected = page == panelPage
            navigation.addView(
                TextView(this).apply {
                    text = page.label
                    gravity = Gravity.CENTER
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (selected) Color.WHITE else 0xFF536174.toInt())
                    background = rounded(
                        if (selected) 0xFF2563EB.toInt() else 0xFFEFF3F8.toInt(),
                        12
                    )
                    setOnClickListener {
                        if (panelPage != page) {
                            panelPage = page
                            mainHandler.post {
                                removePanel()
                                showPanel()
                            }
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44)
                ).apply { bottomMargin = dp(7) }
            )
        }
        body.addView(
            navigation,
            LinearLayout.LayoutParams(dp(82), LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        val pageContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        when (panelPage) {
            OverlayPage.HOME -> buildHomePage(pageContent)
            OverlayPage.SCRIPTS -> buildScriptsPage(pageContent)
            OverlayPage.SPEED -> buildSpeedPage(pageContent)
        }
        val pageScroll = MaxHeightScrollView(this, maxHeight - dp(70)).apply {
            addView(pageContent)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = true
        }
        body.addView(
            pageScroll,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(body)

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                if (panelPage == OverlayPage.SPEED) {
                    0
                } else {
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                },
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }
        runCatching {
            windowManager.addView(root, params)
            panel = root
            if (panelPage == OverlayPage.SCRIPTS) {
                refreshScripts()
            }
        }.onFailure {
            statusView?.text = "无法显示悬浮窗"
        }
    }

    /**
     * @brief 构建悬浮窗主页内容。
     */
    private fun buildHomePage(content: LinearLayout) {
        content.addView(
            TextView(this).apply {
                text = "PCPToolKit"
                setTextColor(0xFF152338.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = Typeface.DEFAULT_BOLD
            }
        )
        content.addView(
            TextView(this).apply {
                text = "从左侧选择脚本或变速功能。"
                setTextColor(0xFF536174.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, dp(14))
            }
        )
        content.addView(summaryRow("当前脚本", if (running) "运行中" else "未运行"))
        content.addView(summaryRow("当前速度", formatSpeed(gameSpeed) + "x"))
        content.addView(
            Button(this).apply {
                text = "关闭悬浮窗"
                setTextColor(Color.WHITE)
                background = rounded(0xFFB42318.toInt(), 12)
                setOnClickListener { stopSelf() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply { topMargin = dp(16) }
        )
    }

    /**
     * @brief 构建脚本二级页面。
     */
    private fun buildScriptsPage(content: LinearLayout) {
        statusView = TextView(this).apply {
            text = if (running) "脚本运行中" else "请选择脚本"
            setTextColor(0xFF536174.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(10))
        }
        content.addView(statusView)
        content.addView(
            TextView(this).apply {
                text = "我的脚本"
                setTextColor(0xFF2563EB.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(6))
            }
        )

        scriptGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        content.addView(
            scriptGroup,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val run = Button(this).apply {
            text = "运行脚本"
            setTextColor(Color.WHITE)
            background = rounded(0xFF2563EB.toInt(), 12)
            setOnClickListener { startScript() }
        }
        val stop = Button(this).apply {
            text = "停止"
            setTextColor(0xFF334155.toInt())
            background = rounded(0xFFE7EBF1.toInt(), 12)
            isEnabled = false
            setOnClickListener { requestStop() }
        }
        runButton = run
        stopButton = stop
        run.isEnabled = !running
        stop.isEnabled = running
        actions.addView(run, LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(
            stop,
            LinearLayout.LayoutParams(0, dp(46), 0.65f).apply { marginStart = dp(8) }
        )
        content.addView(actions)

        content.addView(
            TextView(this).apply {
                text = "运行前需开启无障碍服务，用于截屏和模拟手势。"
                setTextColor(0xFF748297.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(10), 0, 0)
                setOnClickListener { openAccessibilitySettings() }
            }
        )
        logButton = TextView(this).apply {
            text = if (logConsole == null) "打开日志" else "关闭日志"
            setTextColor(0xFF2563EB.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { toggleLogConsole() }
        }
        content.addView(logButton)
        content.addView(
            TextView(this).apply {
                text = "关闭悬浮窗"
                setTextColor(0xFFB42318.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(10), 0, 0)
                setOnClickListener { stopSelf() }
            }
        )
    }

    /**
     * @brief 构建变速二级页面。
     */
    private fun buildSpeedPage(content: LinearLayout) {
        content.addView(
            TextView(this).apply {
                text = "游戏速度"
                setTextColor(0xFF152338.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
            }
        )
        content.addView(
            TextView(this).apply {
                text = "输入倍率（${formatSpeed(MIN_INPUT_GAME_SPEED)}–" +
                    "${formatSpeed(MAX_INPUT_GAME_SPEED)}），或拖动下方滑条快速调整。"
                setTextColor(0xFF536174.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(6), 0, dp(12))
            }
        )

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = EditText(this).apply {
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(formatSpeed(gameSpeed))
            setSelection(text.length)
            setTextColor(0xFF152338.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            background = rounded(0xFFF1F4F8.toInt(), 12)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        inputRow.addView(
            TextView(this).apply {
                text = "x"
                setTextColor(0xFF536174.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(32), dp(48))
        )
        content.addView(inputRow)

        val steps = ((MAX_SLIDER_GAME_SPEED - MIN_SLIDER_GAME_SPEED) /
            GAME_SPEED_STEP).roundToInt()
        val slider = SeekBar(this).apply {
            max = steps
            progress = speedToProgress(gameSpeed, steps)
            setPadding(0, dp(8), 0, 0)
        }
        content.addView(slider)

        val range = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        range.addView(
            TextView(this).apply {
                text = formatSpeed(MIN_SLIDER_GAME_SPEED) + "x"
                setTextColor(0xFF748297.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        range.addView(
            TextView(this).apply {
                text = formatSpeed(MAX_SLIDER_GAME_SPEED) + "x"
                setTextColor(0xFF748297.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        content.addView(range)

        val current = TextView(this).apply {
            text = "当前设置：${formatSpeed(gameSpeed)}x"
            setTextColor(0xFF2563EB.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, 0)
        }
        content.addView(current)

        var syncing = false
        fun updateSpeed(value: Float, updateField: Boolean, updateSlider: Boolean) {
            val normalized = value.coerceIn(MIN_INPUT_GAME_SPEED, MAX_INPUT_GAME_SPEED)
            setGameSpeed(normalized)
            current.text = "当前设置：${formatSpeed(normalized)}x"
            syncing = true
            if (updateField) {
                input.setText(formatSpeed(normalized))
                input.setSelection(input.text.length)
            }
            if (updateSlider) {
                slider.progress = speedToProgress(normalized, steps)
            }
            syncing = false
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                val value = s?.toString()?.toFloatOrNull() ?: return
                updateSpeed(value, updateField = false, updateSlider = true)
            }
        })
        input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                input.setText(formatSpeed(gameSpeed))
                input.setSelection(input.text.length)
            }
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (!fromUser || syncing) return
                updateSpeed(
                    progressToSpeed(progress),
                    updateField = true,
                    updateSlider = false
                )
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (gameSpeed < MIN_SLIDER_GAME_SPEED ||
                    gameSpeed > MAX_SLIDER_GAME_SPEED
                ) {
                    seekBar?.let {
                        updateSpeed(
                            progressToSpeed(it.progress),
                            updateField = true,
                            updateSlider = false
                        )
                    }
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    /**
     * @brief 创建主页摘要行。
     */
    private fun summaryRow(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
            addView(
                TextView(this@FloatingWindowService).apply {
                    text = label
                    setTextColor(0xFF536174.toInt())
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                TextView(this@FloatingWindowService).apply {
                    text = value
                    setTextColor(0xFF152338.toInt())
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                }
            )
        }
    }

    private fun setGameSpeed(value: Float) {
        gameSpeed = value.coerceIn(MIN_INPUT_GAME_SPEED, MAX_INPUT_GAME_SPEED)
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
            .edit()
            .putFloat(SPEED_PREF_KEY, gameSpeed)
            .apply()
        runCatching {
            BlackBoxCore.getBActivityManager().setGameSpeed(gameSpeed)
        }.onFailure { error ->
            android.util.Log.e("FloatingWindowService", "apply game speed failed", error)
        }
    }

    private fun speedToProgress(speed: Float, steps: Int): Int {
        return ((speed - MIN_SLIDER_GAME_SPEED) / GAME_SPEED_STEP)
            .roundToInt()
            .coerceIn(0, steps)
    }

    private fun progressToSpeed(progress: Int): Float {
        return (MIN_SLIDER_GAME_SPEED + progress * GAME_SPEED_STEP)
            .coerceIn(MIN_SLIDER_GAME_SPEED, MAX_SLIDER_GAME_SPEED)
    }

    private fun formatSpeed(speed: Float): String =
        String.format(Locale.ROOT, "%.2f", speed)

    /**
     * @brief 移除脚本控制面板。
     */
    private fun removePanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        statusView = null
        scriptGroup = null
        runButton = null
        stopButton = null
        logButton = null
    }

    /**
     * @brief 切换日志窗口显示状态。
     */
    private fun toggleLogConsole() {
        if (logConsole == null) showLogConsole() else removeLogConsole()
    }

    /**
     * @brief 创建并显示日志窗口。
     */
    private fun showLogConsole() {
        if (logConsole != null) {
            updateLogButton()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            elevation = dp(10).toFloat()
            background = rounded(0xCC111827.toInt(), 16)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "日志"
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        header.addView(title)
        header.addView(
            TextView(this).apply {
                text = "×"
                setTextColor(0xFFD1D5DB.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(dp(8), 0, 0, dp(4))
                setOnClickListener { removeLogConsole() }
            }
        )
        root.addView(header)

        val text = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFD1FAE5.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(8), 0, 0)
            text = logLines.joinToString("\n")
        }
        val scroll = ScrollView(this).apply {
            addView(text)
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val (width, height) = screenSize()
        val landscape = width > height
        val panelWidth = if (landscape) {
            minOf((width * 0.50f).toInt(), dp(560))
        } else {
            (width - dp(24)).coerceAtLeast(dp(200))
        }
        val panelHeight = if (landscape) {
            minOf((height * 0.40f).toInt(), dp(280))
        } else {
            minOf((height * 0.35f).toInt(), dp(280))
        }
        val params = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (landscape) {
                (width - panelWidth - dp(12)).coerceAtLeast(0)
            } else {
                dp(12)
            }
            y = (height - panelHeight - dp(12)).coerceAtLeast(0)
        }
        attachLogDrag(title, root, params)
        runCatching {
            windowManager.addView(root, params)
            logConsole = root
            logText = text
            logParams = params
            updateLogButton()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /**
     * @brief 移除日志窗口。
     */
    private fun removeLogConsole() {
        logConsole?.let { runCatching { windowManager.removeView(it) } }
        logConsole = null
        logText = null
        logParams = null
        updateLogButton()
    }

    /**
     * @brief 更新控制面板中的日志按钮状态。
     */
    private fun updateLogButton() {
        logButton?.text = if (logConsole == null) "打开日志" else "关闭日志"
    }

    /**
     * @brief 向日志窗口追加一行文本。
     * @param message 日志文本。
     */
    private fun appendLog(message: String) {
        val line = message.take(500)
        mainHandler.post {
            logLines += line
            while (logLines.size > 400) logLines.removeAt(0)
            logText?.let { view ->
                view.text = logLines.joinToString("\n")
                val scroll = view.parent as? ScrollView
                scroll?.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    /**
     * @brief 重新扫描脚本目录并刷新脚本列表。
     */
    private fun refreshScripts() {
        val group = scriptGroup ?: return
        val previous = selectedScript?.name
        selectedScript = null
        group.removeAllViews()
        val loaded = ScriptStore.list(this)
        if (loaded == null) {
            statusView?.text = "无法读取脚本文件夹"
            return
        }
        scripts = loaded
        if (loaded.isEmpty()) {
            statusView?.text = "文件夹中没有 .pcs 脚本"
            return
        }
        for (script in loaded) {
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = script.name
                setTextColor(0xFF1F2937.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                tag = script
            }
            group.addView(button)
            button.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedScript = script
            }
            if (script.name == previous) button.isChecked = true
        }
        if (group.checkedRadioButtonId == -1) {
            val first = group.getChildAt(0) as? RadioButton
            first?.isChecked = true
            selectedScript = loaded.firstOrNull()
        } else {
            val checked = (0 until group.childCount)
                .map { group.getChildAt(it) }
                .filterIsInstance<RadioButton>()
                .firstOrNull { it.id == group.checkedRadioButtonId }
            selectedScript = checked?.tag as? ScriptEntry ?: loaded.firstOrNull()
        }
        val orientation = if (isLandscape()) "横屏" else "竖屏"
        statusView?.text = if (running) {
            "脚本运行中 · $orientation，可点击停止"
        } else {
            "已加载 ${loaded.size} 个脚本 · $orientation"
        }
    }

    /**
     * @brief 在后台线程启动当前选中的脚本。
     */
    private fun startScript() {
        if (running) return
        val script = selectedScript
        if (script == null) {
            postStatus("请选择脚本")
            return
        }
        if (!AutomationAccessibilityService.isConnected()) {
            postStatus("请先开启 PCP 脚本自动化无障碍服务")
            openAccessibilitySettings()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            postStatus("脚本运行需要 Android 11 或更高版本")
            return
        }
        if (!isLandscape()) {
            postStatus("请先切换到横屏游戏，再运行脚本")
            return
        }
        val source = ScriptStore.read(this, script)
        if (source.isNullOrBlank()) {
            postStatus("脚本内容为空或读取失败")
            return
        }

        cancelRequested = false
        gameWidth = 0
        gameHeight = 0
        running = true
        runButton?.isEnabled = false
        stopButton?.isEnabled = true
        val (width, height) = screenSize()
        postStatus("正在运行 ${script.name}（${width}x${height}）")
        removePanel()
        updateNotification(running = true)

        scriptThread = Thread({
            var finalMessage = "脚本运行完成"
            try {
                ScriptEngine.execute(source, AndroidScriptHost())
                if (cancelRequested) finalMessage = "脚本已停止"
            } catch (error: ScriptException) {
                finalMessage = if (cancelRequested) "脚本已停止" else (error.message ?: "脚本错误")
            } catch (error: Throwable) {
                finalMessage = "运行失败：${error.message ?: error.javaClass.simpleName}"
            } finally {
                running = false
                mainHandler.post {
                    updateNotification(running = false)
                    runButton?.isEnabled = true
                    stopButton?.isEnabled = false
                    statusView?.text = finalMessage
                    appendLog(finalMessage)
                }
            }
        }, "pcp-script").also { it.start() }
    }

    /**
     * @brief 请求停止正在运行的脚本。
     */
    private fun requestStop() {
        if (!running) return
        cancelRequested = true
        postStatus("正在停止脚本…")
        scriptThread?.interrupt()
    }

    /**
     * @brief 把脚本状态更新投递到主线程。
     * @param message 状态文本。
     */
    private fun postStatus(message: String) {
        appendLog(message)
        mainHandler.post {
            statusView?.text = message
        }
    }

    /**
     * @brief 打开系统无障碍服务设置页面。
     */
    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    /**
     * @brief 按当前屏幕方向截取游戏区域。
     * @param width 目标宽度。
     * @param height 目标高度。
     * @return 截图位图，失败时返回空。
     */
    private fun captureGameScreen(
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): Bitmap? {
        val service = AutomationAccessibilityService.instance ?: return null
        val hidden = CountDownLatch(1)
        val region = Rect(left, top, left + width, top + height)
        var hideBubble = false
        var hideLog = false
        mainHandler.post {
            val bubbleBounds = bubbleParams?.let {
                Rect(it.x, it.y, it.x + it.width, it.y + it.height)
            }
            hideBubble = bubble != null && bubbleBounds != null &&
                Rect.intersects(region, bubbleBounds)
            val logBounds = logParams?.let {
                Rect(it.x, it.y, it.x + it.width, it.y + it.height)
            }
            hideLog = logConsole != null && logBounds != null &&
                Rect.intersects(region, logBounds)
            if (hideBubble) {
                bubble?.visibility = View.INVISIBLE
                scheduleBubbleRestore()
            }
            if (hideLog) {
                logConsole?.visibility = View.INVISIBLE
                scheduleLogRestore()
            }
            hidden.countDown()
        }
        try {
            hidden.await(500, TimeUnit.MILLISECONDS)
            if (hideBubble || hideLog) {
                // 只在与识别区域重叠时隐藏一帧。
                Thread.sleep(16)
            }
            return service.screenshotBlocking()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        }
    }

    /**
     * @brief 在配置变化后安排悬浮球位置恢复。
     */
    private fun scheduleBubbleRestore() {
        bubbleRestore?.let { mainHandler.removeCallbacks(it) }
        val restore = Runnable {
            bubble?.visibility = View.VISIBLE
            bubbleRestore = null
        }
        bubbleRestore = restore
        mainHandler.postDelayed(restore, 1_000L)
    }

    /**
     * @brief 在配置变化后安排日志窗口位置恢复。
     */
    private fun scheduleLogRestore() {
        logRestore?.let { mainHandler.removeCallbacks(it) }
        val restore = Runnable {
            logConsole?.visibility = View.VISIBLE
            logRestore = null
        }
        logRestore = restore
        mainHandler.postDelayed(restore, 1_000L)
    }

    private inner class AndroidScriptHost : ScriptHost {
        private var lastMatchLogAt = 0L

        override val isCancelled: Boolean
            get() = cancelRequested

        /**
         * @brief 获取系统开机后的单调毫秒时间。
         * @return 单调毫秒时间。
         */
        override fun nowMillis(): Long = SystemClock.elapsedRealtime()

        /**
         * @brief 把脚本日志追加到悬浮窗日志窗口。
         * @param message 日志文本。
         */
        override fun log(message: String) {
            postStatus(message)
        }

        /**
         * @brief 让脚本线程休眠并响应停止请求。
         * @param milliseconds 休眠毫秒数。
         */
        override fun sleep(milliseconds: Long) {
            if (milliseconds <= 0) return
            try {
                Thread.sleep(milliseconds)
            } catch (_: InterruptedException) {
                cancelRequested = true
            }
        }

        /**
         * @brief 执行脚本请求的单击。
         * @param x 归一化横坐标。
         * @param y 归一化纵坐标。
         */
        override fun tap(x: Double, y: Double) {
            val service = AutomationAccessibilityService.instance
                ?: throw IllegalStateException("无障碍服务未连接")
            val (width, height) = gameSize()
            if (!service.tapBlocking(
                    (x * width).toFloat(),
                    (y * height).toFloat()
                )
            ) {
                throw IllegalStateException("点击失败")
            }
        }

        /**
         * @brief 执行脚本请求的滑动。
         * @param x1 起点横坐标。
         * @param y1 起点纵坐标。
         * @param x2 终点横坐标。
         * @param y2 终点纵坐标。
         * @param durationMillis 滑动持续时间，单位为毫秒。
         */
        override fun swipe(
            x1: Double,
            y1: Double,
            x2: Double,
            y2: Double,
            durationMillis: Long
        ) {
            val service = AutomationAccessibilityService.instance
                ?: throw IllegalStateException("无障碍服务未连接")
            val (width, height) = gameSize()
            if (!service.swipeBlocking(
                    (x1 * width).toFloat(),
                    (y1 * height).toFloat(),
                    (x2 * width).toFloat(),
                    (y2 * height).toFloat(),
                    durationMillis
                )
            ) {
                throw IllegalStateException("滑动失败")
            }
        }

        /**
         * @brief 执行脚本请求的模板匹配。
         * @param assetPath 模板图像资源路径。
         * @param left 区域左侧坐标。
         * @param top 区域顶部坐标。
         * @param right 区域右侧坐标。
         * @param bottom 区域底部坐标。
         * @return 模板匹配结果。
         */
        override fun match(
            imageAssetPath: String,
            left: Double,
            top: Double,
            right: Double,
            bottom: Double
        ): MatchResult {
            val template = templateFor(imageAssetPath)
                ?: throw IllegalStateException("找不到图像 $imageAssetPath")
            val (estimatedWidth, estimatedHeight) = gameSize()
            val estimatedLeft =
                (left * estimatedWidth).roundToInt().coerceIn(0, estimatedWidth)
            val estimatedTop =
                (top * estimatedHeight).roundToInt().coerceIn(0, estimatedHeight)
            val estimatedRight =
                (right * estimatedWidth).roundToInt().coerceIn(0, estimatedWidth)
            val estimatedBottom =
                (bottom * estimatedHeight).roundToInt().coerceIn(0, estimatedHeight)
            val screenshot = captureGameScreen(
                estimatedLeft,
                estimatedTop,
                (estimatedRight - estimatedLeft).coerceAtLeast(1),
                (estimatedBottom - estimatedTop).coerceAtLeast(1)
            )
                ?: throw IllegalStateException("截屏失败")
            return try {
                val screenWidth = screenshot.width
                val screenHeight = screenshot.height
                gameWidth = screenWidth
                gameHeight = screenHeight
                val regionLeft = (left * screenWidth).roundToInt().coerceIn(0, screenWidth)
                val regionTop = (top * screenHeight).roundToInt().coerceIn(0, screenHeight)
                val regionRight = (right * screenWidth).roundToInt().coerceIn(0, screenWidth)
                val regionBottom = (bottom * screenHeight).roundToInt().coerceIn(0, screenHeight)
                val regionWidth = regionRight - regionLeft
                val regionHeight = regionBottom - regionTop
                if (regionWidth <= 0 || regionHeight <= 0) {
                    postStatus("识别 ${imageAssetPath.substringAfterLast('/')}：区域无效")
                    return MatchResult(false, 0.0, 0.0, 0.0, 0.0, 0.0)
                }
                val needed = regionWidth * regionHeight
                val screenPixels = screenPixelsCache
                    ?.takeIf { it.size >= needed }
                    ?: IntArray(needed).also { screenPixelsCache = it }
                screenshot.getPixels(
                    screenPixels,
                    0,
                    regionWidth,
                    regionLeft,
                    regionTop,
                    regionWidth,
                    regionHeight
                )
                val found = ScriptVision.find(
                    template.pixels,
                    template.bitmap.width,
                    template.bitmap.height,
                    screenPixels,
                    regionWidth,
                    regionHeight,
                    0,
                    0,
                    regionWidth,
                    regionHeight
                )
                if (found == null) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastMatchLogAt >= 500) {
                        postStatus("识别 ${imageAssetPath.substringAfterLast('/')}：区域无效")
                        lastMatchLogAt = now
                    }
                    MatchResult(false, 0.0, 0.0, 0.0, 0.0, 0.0)
                } else {
                    val score = found[4]
                    val accepted = score >= MATCH_THRESHOLD
                    val now = SystemClock.elapsedRealtime()
                    if (accepted || now - lastMatchLogAt >= 500) {
                        postStatus(
                            String.format(
                                Locale.US,
                                "识别 %s：屏幕 %dx%d，区域 [%d,%d]-[%d,%d]，score=%.3f %s",
                                imageAssetPath.substringAfterLast('/'),
                                screenWidth,
                                screenHeight,
                                regionLeft,
                                regionTop,
                                regionRight,
                                regionBottom,
                                score,
                                if (accepted) "命中" else "未命中"
                            )
                        )
                        lastMatchLogAt = now
                    }
                    if (!accepted) {
                        MatchResult(false, 0.0, 0.0, 0.0, 0.0, 0.0)
                    } else {
                        MatchResult(
                            true,
                            (regionLeft + found[0]) / screenWidth,
                            (regionTop + found[1]) / screenHeight,
                            found[2] / screenWidth,
                            found[3] / screenHeight,
                            score
                        )
                    }
                }
            } finally {
                screenshot.recycle()
            }
        }
    }

    /**
     * @brief 缓存模板位图及其像素数组。
     */
    private data class CachedTemplate(val bitmap: Bitmap, val pixels: IntArray)

    /**
     * @brief 读取并缓存模板图像。
     * @param path 模板图像资源路径。
     * @return 缓存的模板，读取失败时返回空。
     */
    private fun templateFor(path: String): CachedTemplate? {
        val cached = templateCache[path]
        val pixels = templatePixels[path]
        if (cached != null && pixels != null) return CachedTemplate(cached, pixels)
        val bitmap = runCatching {
            assets.open(path).use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                )
            }
        }.getOrNull() ?: return null
        val data = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        templateCache[path] = bitmap
        templatePixels[path] = data
        return CachedTemplate(bitmap, data)
    }

    /**
     * @brief 获取当前显示尺寸。
     * @return 显示宽度和高度。
     */
    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * @brief 判断当前是否为横屏。
     * @return 横屏返回 true。
     */
    private fun isLandscape(): Boolean {
        val (width, height) = screenSize()
        return width > height ||
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * @brief 获取用于视觉匹配的游戏画面尺寸。
     * @return 游戏画面宽度和高度。
     */
    private fun gameSize(): Pair<Int, Int> {
        val current = screenSize()
        val cachedWidth = gameWidth
        val cachedHeight = gameHeight
        if (cachedWidth > 0 && cachedHeight > 0 &&
            (cachedWidth > cachedHeight) == (current.first > current.second)
        ) {
            return cachedWidth to cachedHeight
        }
        return current
    }

    /**
     * @brief 获取当前系统版本对应的悬浮窗类型。
     * @return 窗口类型。
     */
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /**
     * @brief 创建悬浮窗前台服务通知频道。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    /**
     * @brief 创建悬浮窗前台服务通知。
     * @param running 脚本是否正在运行。
     * @return 通知构建器。
     */
    private fun notification(running: Boolean) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_pcp_launcher_monochrome)
        .setContentTitle(getString(R.string.floating_notification_title))
        .setContentText(
            if (running) "脚本运行中" else getString(R.string.floating_notification_text)
        )
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                1,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            0,
            "停止脚本",
            PendingIntent.getService(
                this,
                2,
                Intent(this, FloatingWindowService::class.java).setAction(ACTION_STOP_SCRIPT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            0,
            "关闭悬浮窗",
            PendingIntent.getService(
                this,
                3,
                Intent(this, FloatingWindowService::class.java).setAction(ACTION_CLOSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    /**
     * @brief 更新前台服务通知状态。
     * @param running 脚本是否正在运行。
     */
    private fun updateNotification(running: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(running))
    }

    /**
     * @brief 把 dp 数值转换为像素。
     * @param value dp 数值。
     * @return 像素数值。
     */
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    /**
     * @brief 创建圆角纯色背景。
     * @param color 背景颜色。
     * @param radiusDp 圆角半径，单位为 dp。
     * @return 圆角背景绘制对象。
     */
    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    /**
     * @brief 限制最大高度的脚本列表滚动视图。
     */
    private class MaxHeightScrollView(
        context: Context,
        private val maxHeight: Int
    ) : ScrollView(context) {

        /**
         * @brief 测量可拖动视图并保持期望尺寸。
         * @param widthMeasureSpec 宽度测量规格。
         * @param heightMeasureSpec 高度测量规格。
         */
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mode = View.MeasureSpec.getMode(heightMeasureSpec)
            val size = View.MeasureSpec.getSize(heightMeasureSpec)
            val capped = if (mode == View.MeasureSpec.UNSPECIFIED) {
                maxHeight
            } else {
                minOf(size, maxHeight)
            }
            val cappedMode = if (mode == View.MeasureSpec.EXACTLY) {
                View.MeasureSpec.EXACTLY
            } else {
                View.MeasureSpec.AT_MOST
            }
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(capped, cappedMode)
            )
        }
    }

    companion object {
        private const val SETTINGS_PREFS = "settings"
        private const val SPEED_PREF_KEY = "overlay_game_speed"
        private const val DEFAULT_GAME_SPEED = 1.0f
        private const val MIN_INPUT_GAME_SPEED = 0.05f
        private const val MAX_INPUT_GAME_SPEED = 10.0f
        private const val MIN_SLIDER_GAME_SPEED = 0.25f
        private const val MAX_SLIDER_GAME_SPEED = 3.0f
        private const val GAME_SPEED_STEP = 0.05f
        private const val CHANNEL_ID = "pcp_floating"
        private const val NOTIFICATION_ID = 41
        private const val MATCH_THRESHOLD = 0.82
        private const val ACTION_STOP_SCRIPT = "com.pcptoolkit.app.STOP_SCRIPT"
        private const val ACTION_CLOSE = "com.pcptoolkit.app.CLOSE_FLOATING"

        /**
         * @brief 启动悬浮窗前台服务。
         * @param context 用于启动服务的上下文。
         */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingWindowService::class.java)
            )
        }

        /**
         * @brief 停止悬浮窗服务。
         * @param context 用于停止服务的上下文。
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }
}
