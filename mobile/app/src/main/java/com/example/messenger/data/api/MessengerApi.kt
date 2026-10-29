package com.example.messenger.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST

interface MessengerApi {
    @POST("auth/otp")
    suspend fun requestOtp(@Body body: OtpRequest): Response<ResponseBody>

    @POST("auth/verify")
    suspend fun verify(@Body body: VerifyRequest): Response<ResponseBody>

    @PATCH("me")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body body: ProfileUpdateRequest,
    ): Response<ResponseBody>
}
