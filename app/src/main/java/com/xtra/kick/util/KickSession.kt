package com.xtra.kick.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.xtra.kick.R
import com.xtra.kick.repository.KickRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        SecurePrefs.tokens(context).let { tokens ->
            tokens.getString(C.KICK_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { token ->
                if (System.currentTimeMillis() < tokens.getLong(C.KICK_TOKEN_EXPIRES_AT, 0) - SKEW_MARGIN_MS) {
                    return token
                }
            } ?: return null
        }
        return refreshMutex.withLock {
            val tokens = SecurePrefs.tokens(context)
            tokens.getString(C.KICK_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { token ->
                if (System.currentTimeMillis() < tokens.getLong(C.KICK_TOKEN_EXPIRES_AT, 0) - SKEW_MARGIN_MS) {
                    return@withLock token
                }
            }
            val refreshToken = tokens.getString(C.KICK_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
                ?: return@withLock null
            val refreshExpiresAt = tokens.getLong(C.KICK_REFRESH_EXPIRES_AT, 0)
            if (refreshExpiresAt > 0 && System.currentTimeMillis() >= refreshExpiresAt) {
                return@withLock null
            }
            val prefs = context.prefs()
            try {
                val response = repository.refreshOAuthToken(
                    clientId = prefs.getString(C.KICK_CLIENT_ID, null),
                    clientSecret = prefs.getString(C.KICK_CLIENT_SECRET, null),
                    refreshToken = refreshToken,
                )
                tokens.edit {
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
    }

    companion object {
        private val refreshMutex = Mutex()
        private const val SKEW_MARGIN_MS = 60_000L

        fun isLoggedIn(context: Context): Boolean {
            val tokens = SecurePrefs.tokens(context)
            return !tokens.getString(C.KICK_USERNAME, null).isNullOrBlank() &&
                (!tokens.getString(C.KICK_ACCESS_TOKEN, null).isNullOrBlank() ||
                    !tokens.getString(C.KICK_REFRESH_TOKEN, null).isNullOrBlank())
        }

        fun username(context: Context): String? {
            return context.tokenPrefs().getString(C.KICK_USERNAME, null)?.takeIf { it.isNotBlank() }
        }

        fun userId(context: Context): String? {
            return context.tokenPrefs().getString(C.KICK_USER_ID, null)?.takeIf { it.isNotBlank() }
        }

        fun clear(context: Context) {
            SecurePrefs.tokens(context).edit {
                putString(C.KICK_ACCESS_TOKEN, null)
                putString(C.KICK_REFRESH_TOKEN, null)
                putLong(C.KICK_TOKEN_EXPIRES_AT, 0)
                putLong(C.KICK_REFRESH_EXPIRES_AT, 0)
                putString(C.KICK_OAUTH_STATE, null)
                putString(C.KICK_OAUTH_VERIFIER, null)
            }
            context.tokenPrefs().edit {
                putString(C.KICK_USERNAME, null)
                putString(C.KICK_USER_ID, null)
            }
        }
    }
}
