package com.ivi.audiobook.data.library

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UsbVolumeObserver"

/**
 * Emits whenever a storage volume's mount state changes, so the library can rescan reactively on
 * USB attach/detach instead of only at app launch or manual refresh.
 *
 * Mirrors CoWatch's approach exactly (AssetVideoRepository.kt, observeVideos()): both
 * StorageManager.StorageVolumeCallback AND the legacy media-mount broadcasts are registered as
 * independent triggers for the same refresh, since CoWatch's own on-hardware testing found the
 * callback alone didn't fire for USB attach/detach on this hardware class.
 */
@Singleton
class UsbVolumeObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun events(): Flow<Unit> = callbackFlow {
        val storageManager = context.getSystemService(StorageManager::class.java)

        val volumeCallback = object : StorageManager.StorageVolumeCallback() {
            override fun onStateChanged(volume: StorageVolume) {
                Log.i(TAG, "storage volume callback: name=${volume.mediaStoreVolumeName} state=${volume.state}")
                trySend(Unit)
            }
        }
        storageManager?.registerStorageVolumeCallback(context.mainExecutor, volumeCallback)
            ?: Log.w(TAG, "StorageManager unavailable, relying on media-mount broadcasts only")

        val mediaMountReceiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                Log.i(TAG, "media broadcast: action=${intent.action} data=${intent.data}")
                trySend(Unit)
            }
        }
        val mediaMountFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, mediaMountReceiver, mediaMountFilter, ContextCompat.RECEIVER_EXPORTED)

        awaitClose {
            storageManager?.unregisterStorageVolumeCallback(volumeCallback)
            context.unregisterReceiver(mediaMountReceiver)
        }
    }
}
