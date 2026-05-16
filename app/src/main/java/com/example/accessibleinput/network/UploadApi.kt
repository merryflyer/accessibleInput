package com.example.accessibleinput.network

import com.example.accessibleinput.InputEvent
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class UploadResponse(
    val code: Int,
    val message: String
)

interface UploadApi {
    @POST("v1/upload")
    suspend fun uploadEvents(
        @Header("X-User-Name") userName: String,
        @Header("X-User-Phone") userPhone: String,
        @Header("X-User-IdCard") userIdCard: String,
        @Header("X-Device-OS") deviceOs: String,
        @Header("X-Device-Timestamp") deviceTimestamp: String,
        @Body events: List<InputEvent>
    ): Response<UploadResponse>
}
