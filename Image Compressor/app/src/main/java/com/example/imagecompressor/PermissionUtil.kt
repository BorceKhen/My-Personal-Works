package com.example.imagecompressor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionUtil {

    /**
     * Identifies the required read permission based on Android OS version.
     */
    fun getRequiredReadPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * Checks if reading media images is permitted.
     */
    fun hasReadPermission(context: Context): Boolean {
        // Android 13+ Photo Picker doesn't require runtime permissions,
        // but checking ensures compatibility with direct gallery intents or older versions.
        val perm = getRequiredReadPermission()
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if write permission is required (only for legacy Android 9 and lower).
     */
    fun needsLegacyWritePermission(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    }

    fun hasWritePermission(context: Context): Boolean {
        if (!needsLegacyWritePermission()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Creates an Intent leading directly to the application's App Settings page.
     */
    fun getAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
