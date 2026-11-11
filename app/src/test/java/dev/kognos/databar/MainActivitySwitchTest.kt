package dev.kognos.databar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each settings switch as drawn: a 40 x 22dp pill, `bar` when off and the accent when on, with a white dot. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class MainActivitySwitchTest {

    private fun switches(v: View): List<Switch> = when (v) {
        is Switch -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { switches(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun hex(c: Int) = "#%08X".format(c)

    @Test
    fun settingsSwitchesAreOneUiPills() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get()
        val all = switches(activity.window.decorView)
        assertEquals("Flag alerts and Include Wi-Fi", 2, all.size)
        for (switch in all) assertIsAPill(activity, switch)
    }

    private fun assertIsAPill(activity: MainActivity, switch: Switch) {
        val d = activity.resources.displayMetrics.density
        val a = activity.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlActivated))
        val accent = a.getColor(0, 0)
        a.recycle()
        val bar = activity.getColor(R.color.bar)

        for (checked in listOf(false, true)) {
            switch.isChecked = checked
            switch.jumpDrawablesToCurrentState()
            val bmp = Bitmap.createBitmap(switch.width, switch.height, Bitmap.Config.ARGB_8888)
            switch.draw(Canvas(bmp))
            val drawn = (0 until bmp.width).flatMap { x -> (0 until bmp.height).map { y -> x to y } }
                .filter { (x, y) -> Color.alpha(bmp.getPixel(x, y)) > 0 }
            val left = drawn.minOf { it.first }
            val top = drawn.minOf { it.second }
            assertEquals("width, checked=$checked", 40 * d, (drawn.maxOf { it.first } - left + 1).toFloat(), 1.5f)
            assertEquals("height, checked=$checked", 22 * d, (drawn.maxOf { it.second } - top + 1).toFloat(), 1.5f)

            val y = top + (11 * d).toInt()
            val start = bmp.getPixel(left + (10 * d).toInt(), y)
            val end = bmp.getPixel(left + (30 * d).toInt(), y)
            if (checked) {
                assertEquals("track when on", hex(accent), hex(start))
                assertEquals("dot when on", hex(Color.WHITE), hex(end))
            } else {
                assertEquals("dot when off", hex(Color.WHITE), hex(start))
                assertEquals("track when off", hex(bar), hex(end))
            }
        }
    }
}
