package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context

/**
 * First-run progress lives in its own file: the launcher preferences may be
 * cleared by a layout reset, and that must not bring the wizard back.
 */
object SetupProgress {
    const val NAME = "setup"
    const val KEY_DONE = "setup_done"
    const val KEY_STEP = "setup_step"
    const val KEY_STEP_VER = "setup_step_ver"

    // Bump when a page is inserted or removed: an old step number would then
    // open the wrong question after the update. 2: welcome and language swapped
    // to match the dissected TVSetup.app order (TVOS27_RESEARCH/22). 3: the
    // weather page moved in after Home, so every later step shifted by one.
    // 4: the profile page went in after language (the Java wizard's second
    // question), so Home and everything after it shifted by one again.
    // 5: the screen saver step split into "when" and "themes" pages, so the
    // access and finish pages moved one further.
    // 6: the wizard was condensed to six pages — language and profile share
    // one page, Home and weather share one, and the separate themes page is
    // gone. Old step numbers would open the wrong question.
    const val STEP_VERSION = 6

    /**
     * A re-run from Settings starts at the first page, an update with an older
     * page map starts at the first page, and every interruption resumes the
     * page the user actually reached.
     */
    fun resumeStep(done: Boolean, step: Int, version: Int, currentVersion: Int, count: Int): Int {
        if (count <= 0) return 0
        if (done || version != currentVersion) return 0
        return step.coerceIn(0, count - 1)
    }

    fun done(context: Context): Boolean =
        SafePreferences.boolean(prefs(context), KEY_DONE, false)

    fun markDone(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_DONE, true)
            .remove(KEY_STEP)
            .remove(KEY_STEP_VER)
            .apply()
    }

    fun step(context: Context): Int = SafePreferences.int(prefs(context), KEY_STEP, 0)

    fun stepVersion(context: Context): Int =
        SafePreferences.int(prefs(context), KEY_STEP_VER, 0)

    /** Saved on every page so a force-stop mid-wizard resumes where the user was. */
    fun saveStep(context: Context, step: Int) {
        prefs(context).edit()
            .putInt(KEY_STEP, step)
            .putInt(KEY_STEP_VER, STEP_VERSION)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
