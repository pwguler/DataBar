package dev.kognos.databar

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityChipsTest {

    private fun find(view: View, text: String): TextView? = when {
        view is TextView && view.text.toString() == text -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it), text) }
        else -> null
    }

    @Test
    fun chipsKeepTheirSidePaddingAroundTheText() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val side = (14 * activity.resources.displayMetrics.density).toInt()
        val highlight = find(activity.window.decorView, "Highlight")!!
        val volume = find(activity.window.decorView, "Volume")!!

        volume.performClick() // repaints both chips with new pills

        for (chip in listOf(highlight, volume)) {
            assertEquals(side, chip.paddingLeft)
            assertEquals(side, chip.paddingRight)
        }
    }
}
