package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvCanvasTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun canvasKeepsTheDesignAspectAndFitsTheWindow() {
        rule.setContent {
            OVisionTheme {
                TvCanvas {
                    Box(Modifier.fillMaxSize().testTag("canvasProbe"))
                }
            }
        }

        val canvas = rule.onNodeWithTag("canvasProbe").getUnclippedBoundsInRoot()
        val window = rule.onRoot().getUnclippedBoundsInRoot()
        assertEquals(16f / 9f, canvas.width.value / canvas.height.value, 0.01f)
        assertTrue(canvas.width <= window.width)
        assertTrue(canvas.height <= window.height)
    }
}
