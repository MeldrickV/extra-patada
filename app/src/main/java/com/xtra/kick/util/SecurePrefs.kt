package com.xtra.kick.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurePrefs {

    private const val FILE = "secure_tokens"
    private const val MIGRATED_KEY = "secure_tokens_migrated_v1"

    private val secretStringKeys = listOf(
        C.TOKEN,
        C.GQL_TOKEN,
        C.GQL_TOKEN2,
        C.GQL_TOKEN_WEB,
        C.KICK_ACCESS_TOKEN,
        C.KICK_REFRESH_TOKEN,
        C.KICK_OAUTH_STATE,
        C.KICK_OAUTH_VERIFIER,
    )

    private val secretLongKeys = listOf(
        C.KICK_TOKEN_EXPIRES_AT,
        C.KICK_REFRESH_EXPIRES_AT,
    )

    fun tokens(context: Context): SharedPreferences {
        val app = context.applicationContext
        val store = openStore(app)
        migrateOnce(app, store)
        return store
    }

    private fun openStore(app: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                FILE,
                masterKeyAlias,
                app,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.w("SecurePrefs", "Encrypted store unavailable, using plain fallback: ${e.message}")
            app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    private fun migrateOnce(app: Context, store: SharedPreferences) {
        val plain = app.tokenPrefs()
        if (plain.getBoolean(MIGRATED_KEY, false)) return
        store.edit {
            secretStringKeys.forEach { key ->
                plain.getString(key, null)?.let { putString(key, it) }
            }
            secretLongKeys.forEach { key ->
                val value = plain.getLong(key, 0)
                if (value != 0L) putLong(key, value)
            }
        }
        plain.edit {
            secretStringKeys.forEach { remove(it) }
            secretLongKeys.forEach { remove(it) }
            putBoolean(MIGRATED_KEY, true)
        }
    }
}
