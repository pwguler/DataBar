package dev.kognos.databar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** The 30-day chart as pill bars (DESIGN.md, "Bars"). RemoteViews cannot draw, so it renders to a Bitmap. */
object Bars {

    /** Height of a day's bar: none for zero bytes, at least [min] (a round dot) for any traffic, [full] for the largest day [max]. */
    fun height(bytes: Long, max: Long, full: Float, min: Float): Float {
        if (bytes <= 0L || full <= 0f) return 0f
        return (bytes.toFloat() / max.coerceAtLeast(bytes) * full).coerceIn(min.coerceAtMost(full), full)
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
        val barWidth = (pitch / 2f).coerceAtMost(full)
        val max = values.max()
        for ((i, v) in values.withIndex()) {
            val h = height(v, max, full, barWidth)
            val color = if (i == selected) accent else bar
            if (h <= 0f || Color.alpha(color) == 0) continue
            paint.color = color
            val left = i * pitch + (pitch - barWidth) / 2f
            canvas.drawRoundRect(left, full - h, left + barWidth, full, barWidth / 2f, barWidth / 2f, paint)
        }
        return bmp
    }
}
