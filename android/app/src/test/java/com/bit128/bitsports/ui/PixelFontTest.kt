package com.bit128.bitsports.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixelFontTest {
    @Test fun everyGlyphIsFiveBySeven() {
        for (c in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ:-./+!@'&") {
            val g = PixelFont.glyph(c) ?: error("missing $c")
            assertEquals("$c rows", PixelFont.H, g.size)
            g.forEach { assertEquals("$c width", PixelFont.W, it.length) }
        }
        assertNull(PixelFont.glyph('~'))
    }

    @Test fun widthCountsTheGapBetweenLetters() {
        assertEquals(17, PixelFont.width("EDM"))
        assertEquals(34, PixelFont.width("2-1", scale = 2))
        assertEquals(0, PixelFont.width(""))
    }

    @Test fun cellsAreScaled() {
        var n1 = 0
        var n2 = 0
        PixelFont.cells("1", 0, 0) { _, _ -> n1++ }
        PixelFont.cells("1", 0, 0, scale = 2) { _, _ -> n2++ }
        assertEquals(n1 * 4, n2)
    }
}
