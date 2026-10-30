package com.example.messenger.data.api

import com.example.messenger.BuildConfig
import com.example.messenger.data.auth.AuthRepository
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.data.chat.ChatRepository
import com.example.messenger.data.chat.MessengerSocket
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    val json: Json = Json { ignoreUnknownKeys = true }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(45, TimeUnit.SECONDS)
            .build()
    }

    private val api: MessengerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MessengerApi::class.java)
    }

    val repository: AuthRepository by lazy {
        AuthRepository(api, json)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(api, json)
    }

    fun messengerSocket(sessionStore: SessionStore): MessengerSocket {
        return MessengerSocket(
            http = http,
            json = json,
            wsUrl = MessengerSocket.wsUrlFromApiBase(BuildConfig.API_BASE_URL),
            tokenProvider = { sessionStore.accessToken },
        )
    }
}
