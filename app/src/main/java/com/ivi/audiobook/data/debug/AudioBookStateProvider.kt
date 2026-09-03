package com.ivi.audiobook.data.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * System-driven on/off state for the app, same broadcast pattern as [TopPaddingOverrideProvider]:
 * adb shell am broadcast -a com.ivi.audiobook.SET_AUDIO_BOOK_STATE --ei AUDIO_BOOK_STATE [value]
 *
 * A value of 0 means the platform wants this app turned off; any other value means running.
 * Not persisted (unlike the padding override) since this is a live external signal, not a user
 * preference -- every fresh launch should default to "on" rather than possibly refusing to start
 * because of a stale 0 from a previous session.
 */
@Singleton
class AudioBookStateProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val _state = MutableStateFlow(DEFAULT_STATE)
    val state: StateFlow<Int> = _state.asStateFlow()

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val value = intent.getIntExtra(EXTRA_AUDIO_BOOK_STATE, DEFAULT_STATE)
                Log.d(TAG, "onReceive: AUDIO_BOOK_STATE=$value")
                _state.value = value
            }
        }
        val filter = IntentFilter(ACTION_SET_AUDIO_BOOK_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        Log.d(TAG, "receiver registered for $ACTION_SET_AUDIO_BOOK_STATE")
    }

    private companion object {
        const val TAG = "AudioBookState"
        const val ACTION_SET_AUDIO_BOOK_STATE = "com.ivi.audiobook.SET_AUDIO_BOOK_STATE"
        const val EXTRA_AUDIO_BOOK_STATE = "AUDIO_BOOK_STATE"
        const val DEFAULT_STATE = 1
    }
}
