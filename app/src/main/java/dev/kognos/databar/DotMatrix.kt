package dev.kognos.databar

import android.graphics.Bitmap

/** The 30-day chart as a dot matrix (DESIGN.md, "Dot matrix"). RemoteViews cannot draw, so it renders to a Bitmap. */
object DotMatrix {

    /** Lit dots in a column of [rows] for [bytes], against the largest value [max]. */
    fun lit(bytes: Long, max: Long, rows: Int): Int = TODO("not implemented")

    /**
     * One column per value, oldest left, the [selected] column in [ink]. Colors are ARGB;
     * pass Color.TRANSPARENT to leave a kind of dot out, as the widget's mask layers do.
     */
    fun draw(
        values: List<Long>, width: Int, height: Int, density: Float, selected: Int,
        unlit: Int, dot: Int, ink: Int
    ): Bitmap = TODO("not implemented")
}
