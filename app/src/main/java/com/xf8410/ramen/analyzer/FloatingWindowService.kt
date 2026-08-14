package com.xf8410.ramen.analyzer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * 浮窗服务
 *
 * 定期轮询 hlpatch /summary 端点，
 * 将拉面杯分析结果显示为悬浮窗。
 *
 * 轮询间隔：2秒（训练界面）/ 10秒（其他场景自动降频）
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var textView: TextView
    private lateinit var titleView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val soClient = SoClient()
    private lateinit var dataCollector: DataCollector
    private var pollInterval = 2000L
    private var lastTurn = -1

    private val pollRunnable = object : Runnable {
        override fun run() {
            Thread {
                val summary = soClient.fetchSummary()
                val isRamen = summary?.scenario?.contains("Ramen") == true
                val displayText = if (summary != null && isRamen) {
                    val result = RamenAnalyzer.analyze(summary)
                    // 关键回合自动采集原始数据
                    val turn = summary.turnNum
                    if (turn != lastTurn) {
                        dataCollector.maybeAutoCapture(turn)
                        lastTurn = turn
                    }
                    // 拉面杯场景：快速轮询
                    pollInterval = 2000L
                    result.toDisplayText()
                } else if (summary != null) {
                    // 非拉面杯场景：降频
                    pollInterval = 10000L
                    "非拉面杯场景\nscenario: ${summary.scenario ?: "unknown"}"
                } else {
                    pollInterval = 5000L
                    "等待 SO 连接...\nhttp://127.0.0.1:18765"
                }
                handler.post {
                    if (floatingView != null) {
                        textView.text = displayText
                    }
                }
            }.start()
            handler.postDelayed(this, pollInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        dataCollector = DataCollector(this)
        createFloatingWindow()
        handler.postDelayed(pollRunnable, 1000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()
        return START_STICKY
    }

    private fun startForeground() {
        val notification = android.app.Notification.Builder(this, "ramen_analyzer")
            .setContentTitle("拉面杯分析器")
            .setContentText("运行中")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(1, notification)
    }

    private fun createFloatingWindow() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 0
        layoutParams.y = 200

        // 容器
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE1A1A2E"))
            setPadding(16, 12, 16, 12)
        }

        // 标题栏
        titleView = TextView(this).apply {
            text = "拉面杯分析器"
            setTextColor(Color.parseColor("#7AA2F7"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleView)

        // 内容区（可滚动，支援卡列表可能较长）
        val scrollView = ScrollView(this)
        textView = TextView(this).apply {
            text = "启动中..."
            setTextColor(Color.parseColor("#C0CAF5"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(false)
            setLineSpacing(2f, 1f)
            // 限制最大高度，超出滚动
            setMaxLines(30)
        }
        scrollView.addView(textView)
        container.addView(scrollView)

        // 拖动支持
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 4 || abs(dy) > 4) isDragging = true
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(container, layoutParams)
                }
            }
            true
        }

        floatingView = container
        windowManager.addView(container, layoutParams)
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
                return
            }
            val intent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
