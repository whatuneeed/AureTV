package com.whatuneed.ovisiontv.kotlin.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvCanvasFactorTest {

    @Test
    fun sixteenByNineWindowsScaleLikeBefore() {
        assertEquals(1f, tvCanvasFactor(1920f, 1080f), 0.0001f)
        assertEquals(0.5f, tvCanvasFactor(960f, 540f), 0.0001f)
        assertEquals(2f, tvCanvasFactor(3840f, 2160f), 0.0001f)
    }

    @Test
    fun theShorterAxisLimitsTheCanvas() {
        // 21:9: fitting by width would push the dock past the bottom edge.
        assertEquals(1f, tvCanvasFactor(2560f, 1080f), 0.0001f)
        // 4:3: fitting by height would push the last column past the right edge.
        assertEquals(1024f / 1920f, tvCanvasFactor(1024f, 768f), 0.0001f)
    }

    @Test
    fun safeAreaInsetsShrinkTheFit() {
        assertEquals(1000f / 1080f, tvCanvasFactor(1920f, 1000f), 0.0001f)
    }

    @Test
    fun anUnboundedWindowKeepsTheDesignScale() {
        assertEquals(1f, tvCanvasFactor(Float.POSITIVE_INFINITY, 1080f), 0.0001f)
        assertEquals(1f, tvCanvasFactor(0f, 0f), 0.0001f)
    }

    @Test
    fun dockAndSafeAreaStayRelated() {
        assertEquals(TvOs.SAFE_AREA - TvOs.DOCK_BLEED, TvOs.DOCK_X, 0.0001f)
        assertEquals(50f, TvOs.DOCK_X, 0.0001f)
        assertEquals(TvOs.SCREEN_WIDTH, TvOs.DOCK_X + TvOs.DOCK_WIDTH + TvOs.DOCK_X, 0.0001f)
        assertTrue(TvOs.DOCK_Y + TvOs.DOCK_HEIGHT <= TvOs.SCREEN_HEIGHT)
        assertTrue(TvOs.SAFE_AREA + 6 * TvOs.ICON_WIDTH + 5 * TvOs.ICON_GAP <=
            TvOs.SCREEN_WIDTH - TvOs.SAFE_AREA)
    }

    @Test
    fun aGridBannerNeverCoversTheScreen() {
        // 1080p: one design point is one pixel. Store banners are 160×90 or
        // 320×180, so magnifying them 6–12× would only show pixels (UX-19).
        assertFalse(bannerIsHero(160, 1f))
        assertFalse(bannerIsHero(320, 1f))
        assertFalse(bannerIsHero(959, 1f))
    }

    @Test
    fun aRealHeroBannerCoversTheScreenAtMostTwice() {
        assertTrue(bannerIsHero(960, 1f))
        assertTrue(bannerIsHero(1920, 1f))
        // 4K: a point is two pixels, so the same asset is magnified twice as much.
        assertFalse(bannerIsHero(960, 2f))
        assertTrue(bannerIsHero(1920, 2f))
    }
}
