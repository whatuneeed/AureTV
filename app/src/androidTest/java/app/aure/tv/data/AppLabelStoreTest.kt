package app.aure.tv.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aure.tv.ui.PrefsSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLabelStoreTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var savedPrefs: Map<String, Any?>

    @Before
    fun clearLabels() {
        savedPrefs = PrefsSnapshot.capture(context)
        AppLabelStore.clear(context)
    }

    @After
    fun restoreLabels() {
        AppLabelStore.clear(context, LauncherPrefs.NAME + "_p2")
        PrefsSnapshot.restore(context, savedPrefs)
    }

    @Test
    fun a_name_is_trimmed_persisted_and_cleared() {
        AppLabelStore.setLabel(context, "com.example.player", "  Мой плеер  ")

        assertEquals("Мой плеер", AppLabelStore.label(context, "com.example.player"))
        assertEquals(
            mapOf("com.example.player" to "Мой плеер"),
            AppLabelStore.labels(context),
        )

        AppLabelStore.clearLabel(context, "com.example.player")
        assertNull(AppLabelStore.label(context, "com.example.player"))
    }

    @Test
    fun labels_are_scoped_to_the_profile_file() {
        AppLabelStore.setLabel(context, "com.example.player", "Главный")
        AppLabelStore.setLabel(
            context,
            "com.example.player",
            "Детский",
            prefsName = LauncherPrefs.NAME + "_p2",
        )

        assertEquals(
            "Главный",
            AppLabelStore.label(context, "com.example.player"),
        )
        assertEquals(
            "Детский",
            AppLabelStore.label(context, "com.example.player", LauncherPrefs.NAME + "_p2"),
        )
        assertEquals(
            emptyMap<String, String>(),
            AppLabelStore.labels(context, LauncherPrefs.NAME + "_p3"),
        )
    }

    @Test
    fun blank_names_are_not_stored_and_long_names_are_clamped() {
        AppLabelStore.setLabel(context, "com.example.player", "   ")
        assertNull(AppLabelStore.label(context, "com.example.player"))

        AppLabelStore.setLabel(context, "com.example.player", "a".repeat(100))
        assertEquals(64, AppLabelStore.label(context, "com.example.player")!!.length)
    }
}
