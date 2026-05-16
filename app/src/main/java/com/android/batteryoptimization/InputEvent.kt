package com.android.batteryoptimization

data class InputEvent(
    val timestamp: Long,
    val packageName: String,
    val appName: String? = null,
    val text: String
)
