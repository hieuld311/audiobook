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
 * Runtime override for the strip UI's top padding, e.g.:
 * adb shell am broadcast -a com.ivi.audiobook.SET_TOP_PADDING --ei padding_px [value]
 *
 * Persisted to SharedPreferences (same pattern as the sibling widget app's
 * TopPaddingOverrideProvider) so the value survives app restarts and reboots, not just the
 * current process.
 */
@Singleton
class TopPaddingOverrideProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _topPaddingPx = MutableStateFlow(prefs.getInt(KEY_PADDING_PX, DEFAULT_TOP_PADDING_PX))
    val topPaddingPx: StateFlow<Int> = _topPaddingPx.asStateFlow()

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val paddingPx = intent.getIntExtra(EXTRA_PADDING_PX, -1)
                Log.d(TAG, "onReceive: padding_px=$paddingPx")
                if (paddingPx >= 0) {
                    _topPaddingPx.value = paddingPx
                    prefs.edit().putInt(KEY_PADDING_PX, paddingPx).apply()
                }
            }
        }
        val filter = IntentFilter(ACTION_SET_TOP_PADDING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        Log.d(TAG, "receiver registered for $ACTION_SET_TOP_PADDING")
    }

    private companion object {
        const val TAG = "TopPaddingOverride"
        const val ACTION_SET_TOP_PADDING = "com.ivi.audiobook.SET_TOP_PADDING"
        const val EXTRA_PADDING_PX = "padding_px"
        const val DEFAULT_TOP_PADDING_PX = 0
        const val PREFS_NAME = "top_padding_override"
        const val KEY_PADDING_PX = "padding_px"
    }
}
