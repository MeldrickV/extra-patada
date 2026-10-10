package com.xtra.kick.ui.common

import android.content.Context
import com.xtra.kick.R
import com.xtra.kick.ui.main.MainActivity
import com.xtra.kick.util.KickSession
import com.xtra.kick.util.TwitchSession
import com.xtra.kick.util.getAlertDialogBuilder

object LoginMenuHelper {

    fun isLoggedIn(context: Context): Boolean {
        return TwitchSession.isLoggedIn(context) || KickSession.isLoggedIn(context)
    }

    fun showLogoutDialog(activity: MainActivity) {
        val twitchLogin = TwitchSession.username(activity)
        val kickLogin = KickSession.username(activity)
        activity.getAlertDialogBuilder().apply {
            setTitle(activity.getString(R.string.logout_title))
            val accounts = listOfNotNull(
                twitchLogin?.let { "Twitch: $it" },
                kickLogin?.let { "Kick: $it" },
            )
            if (accounts.isNotEmpty()) {
                setMessage(accounts.joinToString("\n"))
            }
            setNegativeButton(activity.getString(R.string.no), null)
            if (twitchLogin != null) {
                setNeutralButton(activity.getString(R.string.logout_twitch)) { _, _ -> activity.logoutTwitch() }
            }
            if (kickLogin != null) {
                setPositiveButton(activity.getString(R.string.logout_kick)) { _, _ -> activity.logoutKick() }
            }
        }.show()
    }
}
