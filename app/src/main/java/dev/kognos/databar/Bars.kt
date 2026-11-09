package dev.kognos.databar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** The 30-day chart as pill bars (DESIGN.md, "Bars"). RemoteViews cannot draw, so it renders to a Bitmap. */
object Bars {

    /** Width of each bar: half the pitch, capped at the chart height so a round dot always fits. */
    fun barWidth(width: Int, height: Int, count: Int): Float =
        (width.toFloat() / count / 2f).coerceAtMost(height.toFloat())

    /**
     * Height of a day's bar in a [width] x [height] chart of [count] days: none for zero bytes, at least
     * a round dot as tall as the bar is wide for any traffic, the full height for the largest day [max].
     */
    fun barHeight(bytes: Long, max: Long, width: Int, height: Int, count: Int): Float {
        if (bytes <= 0L) return 0f
        val full = height.toFloat()
        return (bytes.toFloat() / max.coerceAtLeast(bytes) * full).coerceIn(barWidth(width, height, count), full)
    }

    /**
     * One bar per value, oldest left, the [selected] bar in [accent]. Colors are ARGB;
     * pass Color.TRANSPARENT to leave a kind of bar out, as the widget's mask layers do.
     */
    fun draw(values: List<Long>, width: Int, height: Int, selected: Int, bar: Int, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        if (values.isEmpty()) return bmp
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val full = bmp.height.toFloat()
        val pitch = bmp.width.toFloat() / values.size
        val barWidth = barWidth(bmp.width, bmp.height, values.size)
        val max = values.max()
        for ((i, v) in values.withIndex()) {
            val h = barHeight(v, max, bmp.width, bmp.height, values.size)
            val color = if (i == selected) accent else bar
            if (h <= 0f || Color.alpha(color) == 0) continue
            paint.color = color
            val left = i * pitch + (pitch - barWidth) / 2f
            canvas.drawRoundRect(left, full - h, left + barWidth, full, barWidth / 2f, barWidth / 2f, paint)
        }
        return bmp
    }
}
