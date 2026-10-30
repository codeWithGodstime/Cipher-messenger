package com.example.messenger.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MessengerApi {
    @POST("auth/otp")
    suspend fun requestOtp(@Body body: OtpRequest): Response<ResponseBody>

    @POST("auth/verify")
    suspend fun verify(@Body body: VerifyRequest): Response<ResponseBody>

    @GET("me")
    suspend fun getProfile(
        @Header("Authorization") authorization: String,
    ): Response<ResponseBody>

    @PATCH("me")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body body: ProfileUpdateRequest,
    ): Response<ResponseBody>

    @GET("conversations")
    suspend fun listConversations(
        @Header("Authorization") authorization: String,
    ): Response<ResponseBody>

    @POST("conversations")
    suspend fun openConversation(
        @Header("Authorization") authorization: String,
        @Body body: OpenConversationRequest,
    ): Response<ResponseBody>

    @GET("conversations/{id}/messages")
    suspend fun listMessages(
        @Header("Authorization") authorization: String,
        @Path("id") conversationId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ResponseBody>

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Path("id") conversationId: String,
        @Body body: SendMessageRequest,
    ): Response<ResponseBody>
}
