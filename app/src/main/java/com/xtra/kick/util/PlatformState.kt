package com.xtra.kick.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PlatformState {
    private val _flow = MutableStateFlow(C.PLATFORM_TWITCH)
    val flow: StateFlow<String> = _flow

    fun refresh(context: Context) {
        _flow.value = platformPref(context.prefs().getString(C.PLATFORM, C.PLATFORM_TWITCH))
    }

    fun set(context: Context, platform: String) {
        val value = platformPref(platform)
        context.prefs().edit().putString(C.PLATFORM, value).apply()
        _flow.value = value
    }
}
