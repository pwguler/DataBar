package dev.kognos.databar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Donut tersegmentasi ala Digital Wellbeing: track abu-abu + segmen berwarna. */
object DonutChart {

    fun draw(
        segments: List<Pair<Long, Int>>,
        size: Int,
        trackColor: Int = Color.parseColor("#2C2C33")
    ): Bitmap {
        val s = size.coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = s * 0.20f
        val pad = stroke / 2f + 1f
        val rect = RectF(pad, pad, s - pad, s - pad)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.BUTT
        }
        p.color = trackColor
        c.drawArc(rect, 0f, 360f, false, p)

        val total = segments.sumOf { it.first }.coerceAtLeast(1L).toFloat()
        var start = -90f
        for ((value, color) in segments) {
            if (value <= 0L) continue
            val sweep = 360f * (value / total)
            p.color = color
            c.drawArc(rect, start, sweep, false, p)
            start += sweep
        }
        return bmp
    }
}
