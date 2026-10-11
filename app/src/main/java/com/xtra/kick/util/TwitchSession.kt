package com.xtra.kick.util

import android.content.Context
import androidx.core.content.edit

object TwitchSession {

    fun isLoggedIn(context: Context): Boolean {
        return !getGQLToken(context).isNullOrBlank() || !getHelixToken(context).isNullOrBlank()
    }

    fun username(context: Context): String? {
        return context.tokenPrefs().getString(C.USERNAME, null)?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        SecurePrefs.tokens(context.applicationContext).edit {
            putString(C.TOKEN, null)
            putString(C.GQL_TOKEN, null)
            putString(C.GQL_TOKEN2, null)
            putString(C.GQL_TOKEN_WEB, null)
        }
        context.tokenPrefs().edit {
            putString(C.USERNAME, null)
            putString(C.USER_ID, null)
        }
    }

    private fun getGQLToken(context: Context): String? {
        return TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN]
    }

    private fun getHelixToken(context: Context): String? {
        return TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN]
    }
}
