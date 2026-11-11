package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaverPreviewTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun the_preview_names_the_clip_in_the_language_of_the_screen() {
        val clip = AerialClip(
            id = "goa",
            nameEn = "Goa Beaches",
            nameRu = "Пляжи Гоа",
            theme = SaverTheme.LANDSCAPES,
            videoUrl = "https://cdn.example/goa.mov",
            previewUrl = "",
            order = 0,
        )
        val russian = Configuration().apply { setLocale(Locale("ru", "RU")) }
        rule.setContent {
            CompositionLocalProvider(LocalConfiguration provides russian) {
                OVisionTheme {
                    SaverPreview(
                        scale = rememberTvScale(),
                        clip = clip,
                        themes = setOf(SaverTheme.LANDSCAPES),
                    )
                }
            }
        }
        rule.onNodeWithText("Пляжи Гоа").assertExists()
    }
}
