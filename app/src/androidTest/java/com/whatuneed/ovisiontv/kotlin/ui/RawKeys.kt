package com.whatuneed.ovisiontv.kotlin.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

// Compose's own key injector rejects a second ACTION_DOWN for a key that is
// still down, which makes an auto-repeat impossible to express there. A held
// remote does exactly that, so these tests send the raw framework event.

fun dispatchRawKey(
    action: Int,
    keyCode: Int,
    repeat: Int = 0,
    eventTime: Long = SystemClock.uptimeMillis(),
) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val activity = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .firstOrNull() ?: return@runOnMainSync
        activity.dispatchKeyEvent(KeyEvent(eventTime, eventTime, action, keyCode, repeat))
    }
}

/** One hold: the first press, its auto-repeats, then the release. */
fun holdKey(keyCode: Int, repeats: Int, repeatStepMs: Long = 40L) {
    val start = SystemClock.uptimeMillis()
    dispatchRawKey(KeyEvent.ACTION_DOWN, keyCode, eventTime = start)
    for (step in 1..repeats) {
        // A host keyboard can report the repeats with repeatCount 0.
        dispatchRawKey(KeyEvent.ACTION_DOWN, keyCode, eventTime = start + step * repeatStepMs)
    }
    dispatchRawKey(KeyEvent.ACTION_UP, keyCode, eventTime = start + (repeats + 1) * repeatStepMs)
}
