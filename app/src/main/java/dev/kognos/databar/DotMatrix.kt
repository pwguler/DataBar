package dev.kognos.databar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.roundToInt

/** The 30-day chart as a dot matrix (DESIGN.md, "Dot matrix"). RemoteViews cannot draw, so it renders to a Bitmap. */
object DotMatrix {

    /** Lit dots in a column of [rows] for [bytes], against the largest value [max]. */
    fun lit(bytes: Long, max: Long, rows: Int): Int {
        if (bytes <= 0L || rows <= 0) return 0
        return (bytes.toDouble() / max.coerceAtLeast(bytes) * rows).roundToInt().coerceIn(1, rows)
    }

    /**
     * One column per value, oldest left, the [selected] column in [ink]. Colors are ARGB;
     * pass Color.TRANSPARENT to leave a kind of dot out, as the widget's mask layers do.
     */
    fun draw(
        values: List<Long>, width: Int, height: Int, density: Float, selected: Int,
        unlit: Int, dot: Int, ink: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        if (values.isEmpty()) return bmp
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pitch = minOf(bmp.width.toFloat() / values.size, bmp.height.toFloat())
        val rows = (bmp.height / pitch).toInt()
        val small = pitch < 6f * density
        val radius = pitch * (if (small) 0.4f else 0.31f)
        val max = values.max()
        for ((i, v) in values.withIndex()) {
            val on = lit(v, max, rows)
            val cx = i * pitch + pitch / 2f
            for (k in 0 until rows) {
                val color = when {
                    k >= on -> if (small) Color.TRANSPARENT else unlit
                    i == selected -> ink
                    else -> dot
                }
                if (Color.alpha(color) == 0) continue
                paint.color = color
                canvas.drawCircle(cx, bmp.height - k * pitch - pitch / 2f, radius, paint)
            }
        }
        return bmp
    }
}
