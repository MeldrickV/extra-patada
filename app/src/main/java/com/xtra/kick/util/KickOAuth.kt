package com.xtra.kick.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import okhttp3.HttpUrl.Companion.toHttpUrl

object KickOAuth {

    fun generateVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    fun generateState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return base64UrlNoPadding(digest)
    }

    fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String,
        scope: String,
        state: String,
        codeChallenge: String,
    ): String {
        return "https://id.kick.com/oauth/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("scope", scope)
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    private fun base64UrlNoPadding(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}