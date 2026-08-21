package com.ivi.audiobook.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings

/**
 * All Files Access, not a runtime permission — minSdk 32 already guarantees API 30+, so there's
 * no legacy branch to maintain. Granted through a dedicated Settings screen, not a permission
 * dialog: [requestIntent] opens it, and the caller just re-checks [isGranted] when the user
 * returns (this flow has no reliable activity-result callback of its own).
 */
object StoragePermissions {

    fun isGranted(): Boolean = Environment.isExternalStorageManager()

    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
}
