package com.example.messenger.data.api

import com.example.messenger.BuildConfig
import com.example.messenger.data.auth.AuthRepository
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.data.auth.TokenAuthenticator
import com.example.messenger.data.auth.TokenRefresher
import com.example.messenger.data.chat.ChatRepository
import com.example.messenger.data.chat.MediaRepository
import com.example.messenger.data.chat.MessengerSocket
import com.example.messenger.data.keys.KeyRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    val json: Json = Json { ignoreUnknownKeys = true }

    private fun newHttpBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // A 32 MiB ciphertext upload or download needs longer than a JSON call.
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .pingInterval(45, TimeUnit.SECONDS)

    /** No authenticator — used for login and token refresh to avoid recursion. */
    private val plainHttp: OkHttpClient by lazy { newHttpBuilder().build() }

    private val plainApi: MessengerApi by lazy { retrofit(plainHttp) }

    val repository: AuthRepository by lazy {
        AuthRepository(plainApi, json)
    }

    @Volatile
    private var session: BoundSession? = null

    val chatRepository: ChatRepository
        get() = requireSession().chatRepository

    val keyRepository: KeyRepository
        get() = requireSession().keyRepository

    fun tokenRefresher(sessionStore: SessionStore): TokenRefresher =
        bind(sessionStore).tokenRefresher

    fun messengerSocket(sessionStore: SessionStore): MessengerSocket {
        val bound = bind(sessionStore)
        // Dedicated client: WebSocket handshakes should not share the HTTP authenticator.
        return MessengerSocket(
            http = plainHttp,
            json = json,
            wsUrl = MessengerSocket.wsUrlFromApiBase(BuildConfig.API_BASE_URL),
            tokenProvider = { bound.tokenRefresher.ensureFreshAccessToken() },
        )
    }

    @Synchronized
    fun bind(sessionStore: SessionStore): BoundSession {
        session?.let { existing ->
            if (existing.sessionStore === sessionStore) return existing
        }
        val refresher = TokenRefresher(sessionStore, plainApi, json)
        val http = newHttpBuilder()
            .authenticator(TokenAuthenticator(refresher))
            .build()
        val api = retrofit(http)
        val bound = BoundSession(
            sessionStore = sessionStore,
            tokenRefresher = refresher,
            http = http,
            chatRepository = ChatRepository(api, json),
            mediaRepository = MediaRepository(api, json),
            keyRepository = KeyRepository(api, json),
        )
        session = bound
        return bound
    }

    private fun requireSession(): BoundSession =
        session ?: error("Call ApiClient.bind(sessionStore) before using chat APIs.")

    private fun retrofit(client: OkHttpClient): MessengerApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MessengerApi::class.java)

    data class BoundSession(
        val sessionStore: SessionStore,
        val tokenRefresher: TokenRefresher,
        val http: OkHttpClient,
        val chatRepository: ChatRepository,
        val mediaRepository: MediaRepository,
        val keyRepository: KeyRepository,
    )
}
