package com.android.batteryoptimization

import com.google.gson.annotations.SerializedName

data class InputEvent(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String? = null,
    @SerializedName("text") val text: String,
    @SerializedName("isUploaded") val isUploaded: Boolean = false
)
