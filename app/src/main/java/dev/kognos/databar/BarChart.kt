package dev.kognos.databar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Bar chart ke Bitmap (RemoteViews tidak bisa menggambar sendiri).
 * [horizontal] = true untuk widget sempit-tinggi: bar tumbuh dari kiri ke kanan,
 * hari terakhir di baris paling bawah.
 */
object BarChart {

    fun draw(
        values: List<Long>,
        width: Int,
        height: Int,
        colorBar: Int = Color.parseColor("#C4B5FD"),
        colorToday: Int = Color.parseColor("#FFFFFF"),
        barRatio: Float = 0.58f,
        horizontal: Boolean = false,
        annotation: String? = null,
        highlightIndex: Int = -1,
        todayTick: Boolean = true,
        tickColor: Int = Color.parseColor("#5A5F66")
    ): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (values.isEmpty()) return bmp

        val maxV = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val n = values.size
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (!horizontal) {
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = h * 0.125f
                color = colorToday
                textAlign = Paint.Align.RIGHT
                isFakeBoldText = true
            }
            val labelSpace = if (annotation.isNullOrBlank()) 0f else label.textSize * 1.5f
            val base = h - 1f
            val usable = base - labelSpace
            val step = w.toFloat() / n
            val barW = (step * barRatio).coerceAtLeast(1f)
            for (i in 0 until n) {
                val v = values[i]
                val barH = (usable * (v / maxV)).coerceAtLeast(if (v > 0) 2f else 0f)
                if (barH <= 0f) continue
                val left = i * step + (step - barW) / 2f
                paint.color = if (i == highlightIndex) colorToday else colorBar
                val r = (barW / 2.2f).coerceAtMost(barH / 2f)
                canvas.drawRoundRect(RectF(left, base - barH, left + barW, base), r, r, paint)
                if (todayTick && i == n - 1) {
                    paint.color = tickColor
                    canvas.drawRect(left, base - 1f, left + barW, base, paint)
                }
                if (i == n - 1 && !annotation.isNullOrBlank()) {
                    canvas.drawText(annotation, left + barW, labelSpace * 0.95f, label)
                }
            }
        } else {
            val step = h.toFloat() / n
            val barH = (step * barRatio).coerceAtLeast(1f)
            for (i in 0 until n) {
                val v = values[i]
                val barW = (w * (v / maxV)).coerceAtLeast(if (v > 0) 2f else 0f)
                if (barW <= 0f) continue
                val top = h - (i + 1) * step + (step - barH) / 2f
                paint.color = if (i == n - 1) colorToday else colorBar
                val r = (barH / 2.2f).coerceAtMost(barW / 2f)
                canvas.drawRoundRect(RectF(0f, top, barW, top + barH), r, r, paint)
            }
        }
        return bmp
    }
}
