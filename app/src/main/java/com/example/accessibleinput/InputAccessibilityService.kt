package com.example.accessibleinput

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
            val text = event.text.joinToString(" ")
            
            // Avoid capturing empty or blank strings unnecessarily
            if (text.isNotBlank()) {
                val inputEvent = InputEvent(
                    timestamp = System.currentTimeMillis(),
                    packageName = packageName,
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

    companion object {
        private const val TAG = "InputAccessibility"
    }
}
