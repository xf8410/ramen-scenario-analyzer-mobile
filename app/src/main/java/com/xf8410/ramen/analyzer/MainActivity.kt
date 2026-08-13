package com.xf8410.ramen.analyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    private lateinit var dataCollector: DataCollector
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataCollector = DataCollector(this)

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
            text = "拉面杯分析器\nRamen Scenario Analyzer Mobile\nv0.1.0"
            textSize = 16f
            setPadding(0, 0, 0, 24)
        })

        // 启动浮窗
        root.addView(Button(this).apply {
            text = "启动浮窗"
            setOnClickListener {
                FloatingWindowService.start(this@MainActivity)
            }
        })

        // 手动采集
        root.addView(Button(this).apply {
            text = "手动采集快照 (JSON)"
            setOnClickListener {
                val file = dataCollector.capture("manual")
                if (file != null) {
                    Toast.makeText(this@MainActivity, "已保存: ${file.name}", Toast.LENGTH_SHORT).show()
                    updateStatus()
                } else {
                    Toast.makeText(this@MainActivity, "采集失败：SO 未连接", Toast.LENGTH_SHORT).show()
                }
            }
        })

        // 全量二进制采集
        root.addView(Button(this).apply {
            text = "全量采集端点 (二进制明文)"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "采集中...", Toast.LENGTH_SHORT).show()
                Thread {
                    val files = dataCollector.captureAllRamenEndpoints()
                    runOnUiThread {
                        if (files.isEmpty()) {
                            Toast.makeText(this@MainActivity, "采集失败：SO 未连接", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "已保存 ${files.size} 个文件", Toast.LENGTH_LONG).show()
                        }
                        updateStatus()
                    }
                }.start()
            }
        })

        // 上传到仓库
        root.addView(Button(this).apply {
            text = "上传快照到仓库"
            setOnClickListener {
                dataCollector.uploadPending { success, failed ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity,
                            "上传: 成功${success} 失败${failed}",
                            Toast.LENGTH_LONG).show()
                        updateStatus()
                    }
                }
            }
        })

        // 悬浮窗权限
        root.addView(Button(this).apply {
            text = "悬浮窗权限设置"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${packageName}")
                    ))
                } else {
                    Toast.makeText(this@MainActivity, "已有悬浮窗权限", Toast.LENGTH_SHORT).show()
                }
            }
        })

        // 状态显示
        statusText = TextView(this).apply {
            textSize = 13f
            setPadding(0, 24, 0, 0)
        }
        root.addView(statusText)
        updateStatus()

        // 上游信息
        root.addView(TextView(this).apply {
            text = """
                
                -----
                基于 URA-Plugins/OnsenScenarioAnalyzer 改写
                原作者: EtherealAO / xulai1001
                https://github.com/URA-Plugins
                
                数据来源: hlpatch SO 插件
                https://github.com/xf8410/hlpatch
                
                字段字典: docs/field_dictionary.md
            """.trimIndent()
            textSize = 12f
            setPadding(0, 32, 0, 0)
        })

        setContentView(root)
    }

    private fun updateStatus() {
        val snapshots = dataCollector.listLocalSnapshots()
        statusText.text = if (snapshots.isEmpty()) {
            "本地快照: 0"
        } else {
            "本地快照: ${snapshots.size}\n最近: ${snapshots.first().name}"
        }
    }
}
