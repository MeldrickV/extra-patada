package com.xtra.kick.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object TvMode {

    fun isTv(context: Context): Boolean {
        val hasLeanback = try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        } catch (e: Exception) {
            false
        }
        val uiModeType = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        return isTvDevice(hasLeanback, uiModeType)
    }

    fun isTvDevice(hasLeanbackFeature: Boolean, uiModeType: Int?): Boolean {
        return hasLeanbackFeature || uiModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun defaultChatTextSize(context: Context): Float {
        context.prefs().getString(C.CHAT_TEXT_SIZE, null)?.toFloatOrNull()?.let { return it }
        return if (isTv(context)) 18f else 14f
    }
}
