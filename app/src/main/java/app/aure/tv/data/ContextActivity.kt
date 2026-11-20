package app.aure.tv.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Finds the Activity under a localized or themed ContextWrapper. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
