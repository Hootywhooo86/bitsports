package com.bit128.bitsports.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelRinkTest {
    @Test fun markingsLandWhereTheyShould() {
        val g = PixelRink.base
        assertEquals(128, g.cols)
        assertEquals(PixelRink.OUTSIDE, g[0, 0]) // rounded corner
        assertEquals(PixelRink.RED, g[PixelRink.col(0f), 2]) // centre red line
        assertEquals(PixelRink.BLUE, g[PixelRink.col(25f), 4])
        val (dx, dy) = PixelRink.col(69f) to PixelRink.row(22f)
        assertEquals(PixelRink.RED, g[dx, dy]) // an end-zone faceoff dot
        assertEquals(PixelRink.NET, g[PixelRink.col(89f) + 2, PixelRink.row(0f)])
    }
}
