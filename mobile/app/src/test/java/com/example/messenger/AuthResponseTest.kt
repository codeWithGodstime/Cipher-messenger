package com.example.messenger

import com.example.messenger.data.api.TokenPair
import com.example.messenger.data.auth.OtpCall
import com.example.messenger.data.auth.errorMessage
import com.example.messenger.data.auth.parseOtpSuccess
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun otpPendingBodyIsACodeSent() {
        val result = parseOtpSuccess(json, """{"expires_in":300}""")
        assertEquals(OtpCall.CodeSent(300), result)
    }

    @Test
    fun otpSessionBodySignsIn() {
        val result = parseOtpSuccess(
            json,
            """
            {
              "access_token": "access",
              "refresh_token": "refresh",
              "token_type": "Bearer",
              "expires_in": 900,
              "created": true,
              "profile": {
                "id": "user-1",
                "phone": "+2348012345678",
                "display_name": "",
                "about": "",
                "profile_photo": "",
                "created_at": "2026-01-01T00:00:00Z",
                "updated_at": "2026-01-01T00:00:00Z"
              }
            }
            """.trimIndent(),
        )
        val session = (result as OtpCall.SignedIn).session
        assertEquals("access", session.accessToken)
        assertEquals("+2348012345678", session.profile.phone)
        assertEquals(true, session.created)
    }

    @Test
    fun errorBodyUsesServerMessageAndRetry() {
        val message = errorMessage(
            json,
            429,
            """{"error":"rate_limited","message":"Too many code requests. Try again later.","retry_after":30}""",
        )
        assertEquals("Too many code requests. Try again later. Try again in 30s.", message)
    }

    @Test
    fun tokenPairParsesRefreshResponse() {
        val pair = json.decodeFromString(
            TokenPair.serializer(),
            """
            {
              "access_token": "access-2",
              "refresh_token": "refresh-2",
              "token_type": "Bearer",
              "expires_in": 900
            }
            """.trimIndent(),
        )
        assertEquals("access-2", pair.accessToken)
        assertEquals("refresh-2", pair.refreshToken)
        assertEquals(900, pair.expiresIn)
    }
}
