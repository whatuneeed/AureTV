package app.aure.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BannerFitTest {

    @Test
    fun aSmallBannerIsNeverUpscaled() {
        // A 320×180 launcher banner stays 320×180 even when the shelf asks for
        // screen size: upscaling would only blur it further.
        assertEquals(320 to 180, fitInside(320, 180, 1920, 1080))
    }

    @Test
    fun aBannerThatFitsKeepsItsPixels() {
        assertEquals(1280 to 720, fitInside(1280, 720, 1920, 1080))
    }

    @Test
    fun anOversizedBannerIsCappedByTheShorterAxis() {
        assertEquals(1920 to 1080, fitInside(3840, 2160, 1920, 1080))
        // 5:3 artwork is height-limited inside a 16:9 box.
        assertEquals(1800 to 1080, fitInside(3000, 1800, 1920, 1080))
    }

    @Test
    fun a4kPanelGetsALargerGridBannerDecode() {
        assertEquals(400 to 240, gridBannerBox(1920))
        assertEquals(500 to 300, gridBannerBox(2560))
        assertEquals(500 to 300, gridBannerBox(3840))
    }

    @Test
    fun theGridThumbnailKeepsTheSourceAspect() {
        assertEquals(400 to 225, fitInside(640, 360, 400, 240))
        assertEquals(400 to 240, fitInside(400, 240, 400, 240))
    }
}
