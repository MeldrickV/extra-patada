package com.xtra.kick.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import com.xtra.kick.XtraApp
import com.xtra.kick.ui.player.ExoPlayerService
import com.xtra.kick.ui.player.MediaPlayerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaButtonReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent != null && intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                val useForegroundService = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                val isResumeKey = keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                    || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                if (!useForegroundService || isResumeKey) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val savedStates = (context.applicationContext as XtraApp).xtraModule.playerRepository.getPlaybackStates()
                            if (savedStates.isNotEmpty()) {
                                val serviceClass = when (context.prefs().getString(C.PLAYER, C.EXOPLAYER)) {
                                    C.MEDIA_PLAYER -> MediaPlayerService::class.java
                                    else -> ExoPlayerService::class.java
                                }
                                val serviceIntent = Intent(context, serviceClass).apply { fillIn(intent, 0) }
                                if (useForegroundService) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MediaButtonReceiver", "Failed to resume playback: ${e.message}")
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
