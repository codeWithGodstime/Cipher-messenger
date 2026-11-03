package com.example.messenger.data.chat

import com.example.messenger.data.api.ApiMessage
import com.example.messenger.data.api.WsEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Persistent JSON WebSocket to GET /ws.
 * Auth uses Authorization Bearer. Incoming message.created frames are exposed as [incoming].
 */
class MessengerSocket(
    private val http: OkHttpClient,
    private val json: Json,
    private val wsUrl: String,
    private val tokenProvider: suspend () -> String?,
) {
    private val frameJson = Json(from = json) { explicitNulls = false }
    private val _incoming = MutableSharedFlow<ApiMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incoming: SharedFlow<ApiMessage> = _incoming.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _userSearch = MutableSharedFlow<UserSearchFrame>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val userSearch: SharedFlow<UserSearchFrame> = _userSearch.asSharedFlow()

    @Volatile
    private var socket: WebSocket? = null

    private val connected = AtomicBoolean(false)
    private var loopJob: Job? = null

    val isConnected: Boolean
        get() = connected.get()

    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            var backoffMs = 1_000L
            while (isActive) {
                val token = tokenProvider()
                if (token.isNullOrBlank()) {
                    delay(2_000)
                    continue
                }
                val opened = connect(token)
                if (opened) {
                    backoffMs = 1_000L
                    while (isActive && connected.get()) {
                        delay(1_000)
                    }
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        connected.set(false)
        socket?.close(1_000, "client stop")
        socket = null
    }

    fun sendMessage(conversationId: String, body: String): Boolean {
        val active = socket ?: return false
        if (!connected.get()) return false
        val payload = frameJson.encodeToString(
            WsEnvelope.serializer(),
            WsEnvelope(
                type = TYPE_MESSAGE_SEND,
                conversationId = conversationId,
                body = body,
            ),
        )
        return active.send(payload)
    }

    fun sendUserSearch(query: String, requestId: String): Boolean {
        val active = socket ?: return false
        if (!connected.get()) return false
        val payload = frameJson.encodeToString(
            WsEnvelope.serializer(),
            WsEnvelope(
                type = TYPE_USER_SEARCH,
                query = query,
                requestId = requestId,
            ),
        )
        return active.send(payload)
    }

    private suspend fun connect(token: String): Boolean {
        return suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer $token")
                .build()
            val session = http.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        socket = webSocket
                        connected.set(true)
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleFrame(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (socket === webSocket) {
                            connected.set(false)
                            socket = null
                        }
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (socket === webSocket || socket == null) {
                            connected.set(false)
                            socket = null
                        }
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
            socket = session
            cont.invokeOnCancellation {
                connected.set(false)
                session.cancel()
                if (socket === session) socket = null
            }
        }
    }

    private fun handleFrame(text: String) {
        val envelope = runCatching {
            frameJson.decodeFromString(WsEnvelope.serializer(), text)
        }.getOrNull() ?: return
        when (envelope.type) {
            TYPE_MESSAGE_CREATED -> {
                val message = envelope.message ?: return
                _incoming.tryEmit(message)
            }
            TYPE_USER_SEARCH_RESULTS -> {
                _userSearch.tryEmit(
                    UserSearchFrame(
                        query = envelope.query.orEmpty(),
                        requestId = envelope.requestId.orEmpty(),
                        users = envelope.users.orEmpty(),
                        error = envelope.error?.takeIf { it.isNotBlank() },
                    ),
                )
            }
            TYPE_ERROR -> {
                val detail = envelope.error?.takeIf { it.isNotBlank() } ?: "websocket_error"
                _errors.tryEmit(detail)
            }
            TYPE_PONG -> Unit
            else -> Unit
        }
    }

    companion object {
        const val TYPE_MESSAGE_SEND = "message.send"
        const val TYPE_MESSAGE_CREATED = "message.created"
        const val TYPE_USER_SEARCH = "user.search"
        const val TYPE_USER_SEARCH_RESULTS = "user.search.results"
        const val TYPE_PONG = "pong"
        const val TYPE_ERROR = "error"

        fun wsUrlFromApiBase(apiBaseUrl: String): String {
            val trimmed = apiBaseUrl.trimEnd('/')
            return when {
                trimmed.startsWith("https://") -> "wss://${trimmed.removePrefix("https://")}/ws"
                trimmed.startsWith("http://") -> "ws://${trimmed.removePrefix("http://")}/ws"
                else -> error("API_BASE_URL must start with http:// or https://")
            }
        }
    }
}
