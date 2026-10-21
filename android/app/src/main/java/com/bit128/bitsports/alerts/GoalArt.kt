package com.bit128.bitsports.alerts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.bit128.bitsports.R

/** One still frame of each GOAL style, for the goal notification's picture. */
object GoalArt {
    private const val W = 640
    private const val H = 320

    fun render(context: Context, style: String, colors: Pair<Long, Long>): Bitmap? {
        if (style == "text") return null
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val font = runCatching { ResourcesCompat.getFont(context, R.font.silkscreen_bold) }.getOrNull() ?: Typeface.MONOSPACE
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textAlign = Paint.Align.CENTER }
        val px = Paint()
        fun cell(x: Int, y: Int, w: Int, h: Int, color: Int, size: Int) {
            px.color = color
            c.drawRect((x * size).toFloat(), (y * size).toFloat(), ((x + w) * size).toFloat(), ((y + h) * size).toFloat(), px)
        }
        when (style) {
            "burst" -> {
                c.drawColor(colors.first.toInt())
                text.color = colors.second.toInt()
                text.textSize = 140f
                c.drawText("GOAL!", W / 2f, H / 2f + 50f, text)
            }
            "puck" -> {
                c.drawColor(Color.BLACK)
                val s = 20 // 32 x 16 grid
                cell(21, 4, 1, 9, 0xFFFF4D6D.toInt(), s); cell(21, 4, 8, 1, 0xFFFF4D6D.toInt(), s); cell(28, 4, 1, 9, 0xFF3A3A3A.toInt(), s)
                for (x in listOf(23, 25, 27)) cell(x, 5, 1, 8, 0xFF2A2A2A.toInt(), s)
                for (y in listOf(7, 10)) cell(22, y, 6, 1, 0xFF2A2A2A.toInt(), s)
                cell(23, 11, 3, 1, Color.WHITE, s); cell(23, 12, 3, 1, 0xFFBDBDBD.toInt(), s)
                cell(14, 11, 3, 1, 0xFF6B6B6B.toInt(), s); cell(17, 12, 3, 1, 0xFF6B6B6B.toInt(), s)
                cell(0, 13, 32, 1, 0xFF1A2433.toInt(), s)
                text.color = Color.WHITE
                text.textSize = 96f
                c.drawText("GOAL", 200f, 140f, text)
            }
            else -> { // lamp
                c.drawColor(Color.BLACK)
                val s = 20
                val red = 0xFFFF4D6D.toInt()
                for (r in listOf(0, 1)) { cell(15, r, 2, 1, 0x55FF4D6D, s); cell(15, 15 - r, 2, 1, 0x55FF4D6D, s) }
                cell(6, 7, 3, 2, 0x55FF4D6D, s); cell(23, 7, 3, 2, 0x55FF4D6D, s)
                cell(14, 2, 4, 1, red, s); cell(13, 3, 6, 1, red, s); cell(12, 4, 8, 5, red, s)
                cell(13, 5, 2, 2, 0xFFFFD0D8.toInt(), s)
                cell(11, 9, 10, 2, 0xFF3A3A3A.toInt(), s)
                text.color = Color.WHITE
                text.textSize = 64f
                c.drawText("GOAL", W / 2f, 300f, text)
            }
        }
        return bmp
    }
}
