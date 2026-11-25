package com.pcptoolkit.app.platform.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
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
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

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

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification(running = false))
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        addBubble()
    }

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

    override fun onBind(intent: Intent?): IBinder? = null

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

    private fun togglePanel() {
        if (panel != null) {
            removePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(12).toFloat()
            background = rounded(0xF7FFFFFF.toInt(), 20)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = "PCP 脚本"
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

        statusView = TextView(this).apply {
            text = if (running) "脚本运行中" else "请选择脚本"
            setTextColor(0xFF536174.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, dp(12))
        }
        root.addView(statusView)

        root.addView(
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
        root.addView(
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
        actions.addView(
            run,
            LinearLayout.LayoutParams(0, dp(46), 1f)
        )
        actions.addView(
            stop,
            LinearLayout.LayoutParams(0, dp(46), 0.65f).apply { marginStart = dp(8) }
        )
        root.addView(actions)

        root.addView(
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
        root.addView(logButton)
        root.addView(
            TextView(this).apply {
                text = "关闭悬浮窗"
                setTextColor(0xFFB42318.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(10), 0, 0)
                setOnClickListener { stopSelf() }
            }
        )

        val (screenWidth, screenHeight) = screenSize()
        val width = minOf(screenWidth - dp(32), dp(360)).coerceAtLeast(dp(240))
        val maxHeight = (screenHeight - dp(24)).coerceAtLeast(dp(220))
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val panelScroll = MaxHeightScrollView(this, maxHeight).apply {
            addView(root)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = true
        }
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        runCatching {
            windowManager.addView(panelScroll, params)
            panel = panelScroll
            refreshScripts()
        }.onFailure {
            statusView?.text = "无法显示悬浮窗"
        }
    }

    private fun removePanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        statusView = null
        scriptGroup = null
        runButton = null
        stopButton = null
        logButton = null
    }

    private fun toggleLogConsole() {
        if (logConsole == null) showLogConsole() else removeLogConsole()
    }

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

    private fun removeLogConsole() {
        logConsole?.let { runCatching { windowManager.removeView(it) } }
        logConsole = null
        logText = null
        logParams = null
        updateLogButton()
    }

    private fun updateLogButton() {
        logButton?.text = if (logConsole == null) "打开日志" else "关闭日志"
    }

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

    private fun refreshScripts() {
        val group = scriptGroup ?: return
        val previous = selectedScript?.name
        selectedScript = null
        group.removeAllViews()
        val tree = ScriptStore.treeUri(this)
        if (tree == null) {
            statusView?.text = "请先在“我的脚本”选择脚本文件夹"
            return
        }
        val loaded = ScriptStore.list(this, tree)
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

    private fun requestStop() {
        if (!running) return
        cancelRequested = true
        postStatus("正在停止脚本…")
        scriptThread?.interrupt()
    }

    private fun postStatus(message: String) {
        appendLog(message)
        mainHandler.post {
            statusView?.text = message
        }
    }

    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

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

    private fun scheduleBubbleRestore() {
        bubbleRestore?.let { mainHandler.removeCallbacks(it) }
        val restore = Runnable {
            bubble?.visibility = View.VISIBLE
            bubbleRestore = null
        }
        bubbleRestore = restore
        mainHandler.postDelayed(restore, 1_000L)
    }

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

        override fun nowMillis(): Long = SystemClock.elapsedRealtime()

        override fun log(message: String) {
            postStatus(message)
        }

        override fun sleep(milliseconds: Long) {
            if (milliseconds <= 0) return
            try {
                Thread.sleep(milliseconds)
            } catch (_: InterruptedException) {
                cancelRequested = true
            }
        }

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

    private data class CachedTemplate(val bitmap: Bitmap, val pixels: IntArray)

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

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun isLandscape(): Boolean {
        val (width, height) = screenSize()
        return width > height ||
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

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

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

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

    private fun notification(running: Boolean) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
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

    private fun updateNotification(running: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(running))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private class MaxHeightScrollView(
        context: Context,
        private val maxHeight: Int
    ) : ScrollView(context) {
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
        private const val CHANNEL_ID = "pcp_floating"
        private const val NOTIFICATION_ID = 41
        private const val MATCH_THRESHOLD = 0.82
        private const val ACTION_STOP_SCRIPT = "com.pcptoolkit.app.STOP_SCRIPT"
        private const val ACTION_CLOSE = "com.pcptoolkit.app.CLOSE_FLOATING"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingWindowService::class.java)
            )
        }
    }
}
