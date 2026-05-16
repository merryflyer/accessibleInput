package com.example.accessibleinput

data class InputEvent(
    val timestamp: Long,
    val packageName: String,
    val text: String
)
