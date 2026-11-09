package dev.kognos.databar

import android.graphics.Bitmap

/** The 30-day chart as pill bars (DESIGN.md, "Bars"). RemoteViews cannot draw, so it renders to a Bitmap. */
object Bars {

    /** Height of a day's bar: none for zero bytes, at least [min] (a round dot) for any traffic, [full] for the largest day [max]. */
    fun height(bytes: Long, max: Long, full: Float, min: Float): Float = TODO("not implemented")

    /**
     * One bar per value, oldest left, the [selected] bar in [accent]. Colors are ARGB;
     * pass Color.TRANSPARENT to leave a kind of bar out, as the widget's mask layers do.
     */
    fun draw(values: List<Long>, width: Int, height: Int, selected: Int, bar: Int, accent: Int): Bitmap =
        TODO("not implemented")
}
