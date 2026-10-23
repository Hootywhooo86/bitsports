package com.bit128.bitsports.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.bit128.bitsports.ui.LedGrid

/**
 * A [LedGrid] as a small RGB_565 bitmap for widgets and notifications. Updates to either have to
 * fit through a 1 MB binder call, so [cell] stays small and the launcher or shade scales it up.
 * Pure black cells (outside the rink's boards) and unlit cells take the card's ground colour.
 */
object LedBitmaps {
    const val GROUND = 0xFF0D0D0D.toInt()
    private const val UNLIT = 0xFF1A1A1A.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /** [dots]: round LEDs with gaps; false: solid square pixels edge to edge. */
    fun render(grid: LedGrid, cell: Int = 5, dots: Boolean = true, unlit: Int = UNLIT): Bitmap {
        val bmp = Bitmap.createBitmap(grid.cols * cell, grid.rows * cell, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(GROUND)
        val p = Paint(if (dots) Paint.ANTI_ALIAS_FLAG else 0)
        val size = if (dots) cell - 1f else cell.toFloat()
        for (y in 0 until grid.rows) for (x in 0 until grid.cols) {
            val v = grid[x, y]
            if (v == BLACK) continue
            p.color = if (v != 0) v else unlit
            val l = x * cell.toFloat()
            val t = y * cell.toFloat()
            if (dots) c.drawRoundRect(l, t, l + size, t + size, cell / 4f, cell / 4f, p)
            else c.drawRect(l, t, l + size, t + size, p)
        }
        return bmp
    }
}
