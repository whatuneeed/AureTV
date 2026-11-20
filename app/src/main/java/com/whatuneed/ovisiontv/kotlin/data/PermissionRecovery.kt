package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * A denial after the system no longer offers the rationale is permanent: the
 * only recovery is App Info. Asking again would silently do nothing (AQ-30).
 */
internal fun permanentDenial(granted: Boolean, asked: Boolean, rationale: Boolean): Boolean =
    asked && !granted && !rationale

internal fun isPermanentDenial(context: Context, permission: String, asked: Boolean): Boolean {
    val activity = context.findActivity() ?: return false
    val granted = ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED
    val rationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    return permanentDenial(granted, asked, rationale)
}

internal fun isWifiNamePermanentDenial(context: Context, asked: Boolean): Boolean =
    isPermanentDenial(context, DeviceFactsReader.wifiNamePermission(), asked)
