package com.xf8410.ramen.analyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(32, 48 + bars.top, 32, 32 + bars.bottom)
                insets
            }
        }

        root.addView(TextView(this).apply {
            text = "拉面杯分析器\nRamen Scenario Analyzer (Mobile)"
            textSize = 18f
            setPadding(0, 0, 0, 24)
        })

        // SO 状态
        val statusText = TextView(this).apply {
            text = "检查 SO 连接..."
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        root.addView(statusText)

        // 检查 SO
        Thread {
            val client = SoClient()
            val ok = client.health()
            val summary = if (ok) client.fetchSummary() else null
            val msg = if (ok && summary != null) {
                val scenario = summary.scenario ?: "unknown"
                val turn = summary.turnNum
                "SO: 已连接 ✓\nscenario: $scenario\nturn: $turn"
            } else {
                "SO: 未连接 ✗\n请确认 hlpatch 已加载\nhttp://127.0.0.1:18765"
            }
            runOnUiThread { statusText.text = msg }
        }.start()

        // 启动浮窗按钮
        root.addView(Button(this).apply {
            text = "启动浮窗"
            setOnClickListener {
                FloatingWindowService.start(this@MainActivity)
            }
        })

        // 停止浮窗按钮
        root.addView(Button(this).apply {
            text = "停止浮窗"
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingWindowService::class.java))
            }
        })

        // 悬浮窗权限按钮
        root.addView(Button(this).apply {
            text = "悬浮窗权限设置"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${packageName}")
                    ))
                }
            }
        })

        // 上游信息
        root.addView(TextView(this).apply {
            text = """
                
                -----
                基于 URA-Plugins/OnsenScenarioAnalyzer 改写
                原作者: EtherealAO / xulai1001
                https://github.com/URA-Plugins
                
                数据来源: hlpatch SO 插件
                https://github.com/xf8410/hlpatch
            """.trimIndent()
            textSize = 12f
            setPadding(0, 32, 0, 0)
        })

        setContentView(root)
    }
}
