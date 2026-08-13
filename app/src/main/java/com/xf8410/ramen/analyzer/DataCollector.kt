package com.xf8410.ramen.analyzer

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 数据采集器 — 采集原始响应并上传到 GitHub 仓库
 *
 * 两层采集：
 * 1. 结构化层：/summary 返回的 JSON（已解析，但保留原始明文）
 * 2. 二进制层：直接拉 /api/sniff/metadata 等端点的原始响应体，拆开找明文字符串
 *
 * 原则：
 * - 存原始明文，不做裁剪、不做过滤
 * - 二进制响应体以 hex + 提取的明文字符串 双份保存
 * - 关键回合自动采集
 */
class DataCollector(
    private val context: Context,
    private val soHost: String = "127.0.0.1",
    private val soPort: Int = 18765,
    private val githubToken: String = "",
    private val githubRepo: String = "xf8410/ramen-scenario-analyzer-mobile",
    private val dataBranch: String = "main",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())

    private val localDir: File by lazy {
        File(context.filesDir, "snapshots").apply { mkdirs() }
    }

    private val pendingUploads = mutableListOf<File>()

    // ===== 结构化层：/summary 原始 JSON =====

    /**
     * 采集 /summary 原始响应（不解析，存原始 JSON 文本）
     */
    fun capture(label: String, turn: Int = -1): File? {
        val rawJson = fetchRawSummary() ?: return null

        val actualTurn = if (turn >= 0) turn else {
            try {
                JsonParser.parseString(rawJson).asJsonObject
                    .get("raw_total_turn_num")?.asInt ?: -1
            } catch (e: Exception) { -1 }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
            .format(Date())

        val filename = if (actualTurn >= 0) {
            "t${actualTurn}_${label}_${timestamp}.json"
        } else {
            "${label}_${timestamp}.json"
        }

        val file = File(localDir, filename)
        file.writeText(rawJson)

        pendingUploads.add(file)
        return file
    }

    fun fetchRawSummary(): String? {
        return try {
            val req = Request.Builder()
                .url("http://$soHost:$soPort/summary")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ===== 二进制层：原始端点 + 明文提取 =====

    /**
     * 采集指定端点的原始响应，拆开二进制找明文字符串
     *
     * @param endpoint SO 端点路径（如 "/debug/ramenfields", "/debug/ramen_planner_state"）
     * @param label 采集标签
     * @return 保存的文件路径，null 表示失败
     */
    fun captureRawEndpoint(endpoint: String, label: String): File? {
        val rawBytes = fetchRawBytes(endpoint) ?: return null
        val rawText = String(rawBytes, Charsets.UTF_8)

        // 提取二进制中的明文字符串
        val plaintext = extractPrintableStrings(rawBytes)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
            .format(Date())

        val filename = "raw_${label}_${timestamp}.txt"
        val file = File(localDir, filename)

        // 双份保存：原始响应文本 + 提取的明文字符串
        val content = buildString {
            appendLine("=== RAW RESPONSE ($endpoint) ===")
            appendLine("Timestamp: $timestamp")
            appendLine("Size: ${rawBytes.size} bytes")
            appendLine("Content-Type: ${if (rawText.startsWith("{")) "JSON" else "binary/text"}")
            appendLine()
            appendLine("=== RAW TEXT ===")
            appendLine(rawText)
            appendLine()
            appendLine("=== EXTRACTED PRINTABLE STRINGS (len>=4) ===")
            for (s in plaintext) {
                appendLine(s)
            }
        }

        file.writeText(content)
        pendingUploads.add(file)
        return file
    }

    /**
     * 从二进制数据中提取所有可打印字符串（ASCII >= 0x20, 连续长度 >= 4）
     * 同时提取 UTF-8 日文/中文字符串
     */
    private fun extractPrintableStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var start = -1

        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            // 可打印 ASCII 或 UTF-8 多字节序列
            val isPrintable = (b >= 0x20 && b < 0x7F) || b >= 0x80 || b == 0x0A || b == 0x0D
            if (isPrintable) {
                if (start < 0) start = i
                sb.append(bytes[i].toInt().toChar())
            } else {
                if (sb.length >= 4) {
                    result.add(sb.toString().trim())
                }
                sb.clear()
                start = -1
            }
        }
        if (sb.length >= 4) {
            result.add(sb.toString().trim())
        }

        return result
    }

    /**
     * 拉取端点原始字节
     */
    private fun fetchRawBytes(endpoint: String): ByteArray? {
        return try {
            val req = Request.Builder()
                .url("http://$soHost:$soPort$endpoint")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.bytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 采集全部拉面杯相关端点
     * 分两类：
     * 1. 调试端点（主动读内存，可能触发闪退，低频调用）
     * 2. sniff 端点（被动拦截协议，不额外读内存，安全）
     */
    fun captureAllRamenEndpoints(): List<File> {
        val files = mutableListOf<File>()

        // === 调试端点（主动读内存，低频）===
        val debugEndpoints = listOf(
            "/summary" to "summary",
            "/debug/ramen_planner_state" to "planner_state",
            "/debug/rameninfo" to "rameninfo",
            "/debug/ramenfields" to "ramenfields",
            "/debug/ramen_transition" to "transition",
            "/debug/ramen_region_select" to "region_select",
            "/debug/ramengains" to "gains",
            "/debug/gauge" to "gauge",
            "/debug/cmdinfo" to "cmdinfo",
            "/debug/paramsincdec" to "paramsincdec",
            "/debug/all" to "all",
        )
        for ((path, label) in debugEndpoints) {
            captureRawEndpoint(path, label)?.let { files.add(it) }
        }

        // === sniff 端点（被动拦截协议，不读内存）===
        captureSniffData()?.let { files.add(it) }

        return files
    }

    /**
     * 采集 sniff 缓冲区中的拉面杯相关协议数据
     *
     * /api/sniff/metadata 返回最近拦截到的请求/响应
     * 从中筛选拉面杯相关路径（single_mode_ramen/*）
     *
     * 不额外触发 IL2CPP 调用，不会导致闪退
     */
    fun captureSniffData(): File? {
        return try {
            val req = Request.Builder()
                .url("http://$soHost:$soPort/api/sniff/metadata")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val rawBytes = resp.body?.bytes() ?: return null
                val rawText = String(rawBytes, Charsets.UTF_8)

                // 提取二进制中的明文字符串
                val plaintext = extractPrintableStrings(rawBytes)

                // 筛选拉面杯相关的路径
                val ramenPaths = plaintext.filter {
                    it.contains("ramen") || it.contains("single_mode_14") ||
                    it.contains("tasting") || it.contains("check_point") ||
                    it.contains("region_select") || it.contains("uraf") ||
                    it.contains("finals")
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
                    .format(Date())

                val file = File(localDir, "sniff_${timestamp}.txt")
                file.writeText(buildString {
                    appendLine("=== SNIFF METADATA ===")
                    appendLine("Timestamp: $timestamp")
                    appendLine("Size: ${rawBytes.size} bytes")
                    appendLine()
                    appendLine("=== RAMEN-RELATED PATHS FOUND ===")
                    for (p in ramenPaths) {
                        appendLine(p)
                    }
                    appendLine()
                    appendLine("=== ALL EXTRACTED STRINGS (len>=4) ===")
                    for (s in plaintext) {
                        appendLine(s)
                    }
                    appendLine()
                    appendLine("=== RAW RESPONSE ===")
                    appendLine(rawText)
                })

                pendingUploads.add(file)
                file
            }
        } catch (e: Exception) {
            null
        }
    }

    // ===== 上传 =====

    fun uploadPending(callback: (success: Int, failed: Int) -> Unit) {
        if (githubToken.isBlank()) {
            callback(0, pendingUploads.size)
            return
        }
        if (pendingUploads.isEmpty()) {
            callback(0, 0)
            return
        }

        Thread {
            var success = 0
            var failed = 0
            for (file in pendingUploads.toList()) {
                val ok = uploadToGithub(file)
                if (ok) {
                    success++
                    pendingUploads.remove(file)
                } else {
                    failed++
                }
            }
            handler.post { callback(success, failed) }
        }.start()
    }

    private fun uploadToGithub(file: File): Boolean {
        return try {
            val content = file.readBytes()
            val base64 = android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP)
            val path = "data/snapshots/${file.name}"
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
                .format(Date())

            val payload = JsonObject().apply {
                addProperty("message", "snapshot: ${file.name} @ $timestamp")
                addProperty("branch", dataBranch)
                addProperty("content", base64)
            }

            val req = Request.Builder()
                .url("https://api.github.com/repos/$githubRepo/contents/$path")
                .header("Authorization", "token $githubToken")
                .header("Accept", "application/vnd.github+json")
                .put(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    fun listLocalSnapshots(): List<File> {
        return localDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun clearUploaded() {
        pendingUploads.clear()
    }

    fun maybeAutoCapture(turn: Int): Boolean {
        val label = when (turn) {
            2 -> "region_select_y1"
            24 -> "rmj_y1"
            26 -> "region_select_y2"
            48 -> "rmj_y2"
            50 -> "region_select_y3"
            72 -> "rmj_y3"
            else -> return false
        }
        capture(label, turn)
        // 关键回合同时采集全部端点的二进制明文
        captureRawEndpoint("/debug/ramen_planner_state", "${label}_planner")
        captureRawEndpoint("/debug/ramenfields", "${label}_fields")
        return true
    }
}
