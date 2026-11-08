package dev.kognos.databar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Menggambar bar chart harian ke Bitmap (RemoteViews tidak bisa menggambar sendiri). */
object BarChart {

    fun draw(
        values: List<Long>,
        width: Int,
        height: Int,
        colorBar: Int = Color.parseColor("#4FC3F7"),
        colorToday: Int = Color.parseColor("#FFC107"),
        colorAxis: Int = Color.parseColor("#2E3B4E")
    ): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorAxis; strokeWidth = 1f }
        canvas.drawLine(0f, (h - 1).toFloat(), w.toFloat(), (h - 1).toFloat(), axis)
        if (values.isEmpty()) return bmp

        val top = h * 0.12f
        val base = h - 2f
        val maxV = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val n = values.size
        val gap = w * 0.20f / (n + 1)
        val barW = ((w - gap * (n + 1)) / n).coerceAtLeast(1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until n) {
            val v = values[i]
            val ratio = v / maxV
            val barH = ((base - top) * ratio).coerceAtLeast(if (v > 0) 2f else 1f)
            val left = gap + i * (barW + gap)
            val rect = RectF(left, base - barH, left + barW, base)
            paint.color = if (i == n - 1) colorToday else colorBar
            canvas.drawRoundRect(rect, barW * 0.25f, barW * 0.25f, paint)
        }
        return bmp
    }
}
