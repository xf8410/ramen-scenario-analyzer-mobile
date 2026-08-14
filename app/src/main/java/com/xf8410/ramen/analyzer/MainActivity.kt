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

        // 预加载支援卡数据库：先读本地缓存，后台再从 SO 刷新
        CardDatabase.loadFromCache(this)
        val cardDb = CardDatabase.getInstance()
        val cardDbInfo = if (cardDb != null) "缓存${cardDb.size()}张" else "无缓存"

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

        // 二次 hook 安装（必须进游戏主界面后才能点）
        root.addView(Button(this).apply {
            text = "安装 Sniff+Md5 Hook\n（进游戏主界面后点）"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "安装中...", Toast.LENGTH_SHORT).show()
                Thread {
                    val soClient = SoClient()
                    val results = soClient.installHooks()
                    val status = soClient.checkHookStatus()
                    runOnUiThread {
                        val msg = if (status?.ready == true) {
                            "Hook 安装成功！\n" +
                            "MakeMd5: ✓  Compress: ✓  Decompress: ✓  Post: ✓\n" +
                            "Sniff: ${if (status.sniffEnabled) "✓" else "✗"}"
                        } else {
                            "Hook 安装可能未完成:\n" +
                            results.filter { !it.second }.joinToString("\n") { "  ✗ ${it.first}" }
                        }
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        statusText.text = msg
                    }
                }.start()
            }
        })

        // 检查 hook 状态
        root.addView(Button(this).apply {
            text = "检查 Hook 状态"
            setOnClickListener {
                Thread {
                    val status = SoClient().checkHookStatus()
                    runOnUiThread {
                        statusText.text = status?.let {
                            buildString {
                                appendLine("MakeMd5: ${if (it.makemd5Hooked) "✓" else "✗"}")
                                appendLine("Compress: ${if (it.compressHooked) "✓" else "✗"}")
                                appendLine("Decompress: ${if (it.decompressHooked) "✓" else "✗"}")
                                appendLine("Post: ${if (it.postHooked) "✓" else "✗"}")
                                appendLine("Sniff: ${if (it.sniffEnabled) "✓" else "✗"}")
                                appendLine("Ready: ${if (it.ready) "✓" else "✗"}")
                            }
                        } ?: "SO 未连接"
                    }
                }.start()
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

        // 从 SO 拉取支援卡DB
        root.addView(Button(this).apply {
            text = "从SO拉取支援卡DB\n(/mdb/raw)"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "拉取中...", Toast.LENGTH_SHORT).show()
                Thread {
                    val count = CardDatabase.fetchFromSo(this@MainActivity)
                    runOnUiThread {
                        if (count > 0) {
                            Toast.makeText(this@MainActivity, "拉取成功: ${count}张卡", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "拉取失败：SO未连接或查询出错", Toast.LENGTH_LONG).show()
                        }
                        updateStatus()
                    }
                }.start()
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
            """.trimIndent()
            textSize = 12f
            setPadding(0, 32, 0, 0)
        })

        setContentView(root)
    }

    private fun updateStatus() {
        val snapshots = dataCollector.listLocalSnapshots()
        val cardDb = CardDatabase.getInstance()
        val cardDbInfo = if (cardDb != null) "缓存${cardDb.size()}张" else "无缓存"
        statusText.text = if (snapshots.isEmpty()) {
            "本地快照: 0  卡DB: $cardDbInfo"
        } else {
            "本地快照: ${snapshots.size}\n最近: ${snapshots.first().name}\n卡DB: $cardDbInfo"
        }
    }
}
