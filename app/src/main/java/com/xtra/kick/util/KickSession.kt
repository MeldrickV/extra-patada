package com.xtra.kick.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.xtra.kick.R
import com.xtra.kick.repository.KickRepository

class KickSession(
    context: Context,
    val repository: KickRepository,
) {
    private val context = context.applicationContext

    fun followFailedMessage(): String = context.getString(R.string.kick_follow_failed)

    fun isLoggedIn(): Boolean = isLoggedIn(context)

    fun username(): String? = username(context)

    fun userId(): String? = userId(context)

    fun clear() {
        clear(context)
    }

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
            Log.w("KickSession", "Token refresh failed: ${e.message}")
            null
        }
    }

    companion object {
        fun isLoggedIn(context: Context): Boolean {
            val prefs = context.tokenPrefs()
            return !prefs.getString(C.KICK_USERNAME, null).isNullOrBlank() &&
                (!prefs.getString(C.KICK_ACCESS_TOKEN, null).isNullOrBlank() ||
                    !prefs.getString(C.KICK_REFRESH_TOKEN, null).isNullOrBlank())
        }

        fun username(context: Context): String? {
            return context.tokenPrefs().getString(C.KICK_USERNAME, null)?.takeIf { it.isNotBlank() }
        }

        fun userId(context: Context): String? {
            return context.tokenPrefs().getString(C.KICK_USER_ID, null)?.takeIf { it.isNotBlank() }
        }

        fun clear(context: Context) {
            context.tokenPrefs().edit {
                putString(C.KICK_ACCESS_TOKEN, null)
                putString(C.KICK_REFRESH_TOKEN, null)
                putLong(C.KICK_TOKEN_EXPIRES_AT, 0)
                putLong(C.KICK_REFRESH_EXPIRES_AT, 0)
                putString(C.KICK_USERNAME, null)
                putString(C.KICK_USER_ID, null)
            }
        }
    }
}