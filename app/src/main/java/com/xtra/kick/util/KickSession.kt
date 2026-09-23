package com.xtra.kick.util

import android.content.Context
import androidx.core.content.edit
import com.xtra.kick.R
import com.xtra.kick.repository.KickRepository

class KickSession(
    context: Context,
    val repository: KickRepository,
) {
    private val context = context.applicationContext

    fun followFailedMessage(): String = context.getString(R.string.kick_follow_failed)

    suspend fun accessToken(): String? {
        val tokenPrefs = context.tokenPrefs()
        val accessToken = tokenPrefs.getString(C.KICK_ACCESS_TOKEN, null)
        if (accessToken.isNullOrBlank()) return null
        val expiresAt = tokenPrefs.getLong(C.KICK_TOKEN_EXPIRES_AT, 0)
        if (System.currentTimeMillis() < expiresAt) return accessToken
        val refreshToken = tokenPrefs.getString(C.KICK_REFRESH_TOKEN, null)
        if (refreshToken.isNullOrBlank()) return null
        val prefs = context.prefs()
        return try {
            val response = repository.refreshOAuthToken(
                clientId = prefs.getString(C.KICK_CLIENT_ID, null),
                clientSecret = prefs.getString(C.KICK_CLIENT_SECRET, null),
                refreshToken = refreshToken,
            )
            tokenPrefs.edit {
                putString(C.KICK_ACCESS_TOKEN, response.accessToken)
                putString(C.KICK_REFRESH_TOKEN, response.refreshToken ?: refreshToken)
                putLong(C.KICK_TOKEN_EXPIRES_AT, System.currentTimeMillis() + response.expiresIn * 1000)
                if (response.refreshExpiresIn > 0) {
                    putLong(C.KICK_REFRESH_EXPIRES_AT, System.currentTimeMillis() + response.refreshExpiresIn * 1000)
                }
            }
            response.accessToken
        } catch (e: Exception) {
            null
        }
    }
}