package com.example.messenger.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MessengerApi {
    @POST("auth/login")
    suspend fun login(@Body body: AuthRequest): Response<ResponseBody>

    @POST("auth/signup")
    suspend fun signup(@Body body: AuthRequest): Response<ResponseBody>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<ResponseBody>

    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String,
    ): Response<ResponseBody>

    @GET("me")
    suspend fun getProfile(
        @Header("Authorization") authorization: String,
    ): Response<ResponseBody>

    @PATCH("me")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body body: ProfileUpdateRequest,
    ): Response<ResponseBody>

    @GET("users/lookup")
    suspend fun lookupUser(
        @Header("Authorization") authorization: String,
        @Query("username") username: String,
    ): Response<ResponseBody>

    @GET("contacts")
    suspend fun listContacts(
        @Header("Authorization") authorization: String,
    ): Response<ResponseBody>

    @POST("contacts")
    suspend fun addContact(
        @Header("Authorization") authorization: String,
        @Body body: AddContactRequest,
    ): Response<ResponseBody>

    @DELETE("contacts/{id}")
    suspend fun deleteContact(
        @Header("Authorization") authorization: String,
        @Path("id") contactId: String,
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

    @POST("keys/")
    suspend fun createIdentityKey(
        @Header("Authorization") authorization: String,
        @Body body: CreateIdentityKeyRequest,
    ): Response<ResponseBody>

    @POST("keys/signed-prekey")
    suspend fun uploadSignedPreKey(
        @Header("Authorization") authorization: String,
        @Body body: UploadSignedPreKeyRequest,
    ): Response<ResponseBody>

    @GET("keys/one-time-prekeys")
    suspend fun oneTimePreKeyCount(
        @Header("Authorization") authorization: String,
    ): Response<ResponseBody>

    @POST("keys/one-time-prekeys")
    suspend fun uploadOneTimePreKeys(
        @Header("Authorization") authorization: String,
        @Body body: UploadOneTimePreKeysRequest,
    ): Response<ResponseBody>

    @POST("keys/{userId}/bundle")
    suspend fun claimPreKeyBundle(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
    ): Response<ResponseBody>
}
