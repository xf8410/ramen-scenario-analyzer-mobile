package com.xf8410.ramen.analyzer

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * hlpatch SO 端点客户端
 *
 * 通过 HTTP 连接本地 hlpatch 插件（127.0.0.1:18765），
 * 读取游戏运行时状态。
 *
 * 上游参考：URA-Plugins OnsenScenarioAnalyzer 通过 MITM 代理获取协议数据，
 * 本项目改为通过 hlpatch IL2CPP 内存读取端点获取。
 */
class SoClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 18765,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** 拉取 /summary */
    fun fetchSummary(): SummaryResponse? {
        return try {
            val req = Request.Builder()
                .url("http://$host:$port/summary")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                gson.fromJson(body, SummaryResponse::class.java)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 检查 SO 是否在线 */
    fun health(): Boolean {
        return try {
            val req = Request.Builder()
                .url("http://$host:$port/health")
                .get()
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
