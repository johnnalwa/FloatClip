package com.example.floatclip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object PermissionUtils {
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun requestOverlayPermission(activity: Activity, onDenied: (() -> Unit)? = null) {
        if (!hasOverlayPermission(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("Overlay Permission Required")
                .setMessage("This app needs permission to display over other apps for the floating clipboard widget.")
                .setPositiveButton("Allow") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName))
                    activity.startActivityForResult(intent, 1234)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    onDenied?.invoke()
                }
                .setCancelable(false)
                .show()
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    fun requestBatteryOptimizationWhitelist(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            activity.startActivity(intent)
        }
    }
}
