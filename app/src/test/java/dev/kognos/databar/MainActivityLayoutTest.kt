package dev.kognos.databar

import android.app.AppOpsManager
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real text measurement on a phone-sized screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class MainActivityLayoutTest {

    private fun texts(v: View): List<TextView> = when (v) {
        is TextView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun shownTexts(): List<TextView> {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED
        )
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get()
        shadowOf(Looper.getMainLooper()).idle()
        return texts(activity.window.decorView).filter { it.isShown && it.text.isNotEmpty() }
    }

    /** No text may be cut off or stick out of its parent. */
    private fun assertNothingClipped() {
        val shown = shownTexts()
        assertTrue("expected the header, settings and chips", shown.size >= 8)
        for (t in shown) {
            val layout = t.layout
            val width = t.width - t.totalPaddingLeft - t.totalPaddingRight
            val height = t.height - t.totalPaddingTop - t.totalPaddingBottom
            for (i in 0 until layout.lineCount) {
                assertTrue("'${t.text}' is cut off at the side", layout.getLineWidth(i) <= width + 1 || layout.getEllipsisCount(i) > 0)
            }
            assertTrue("'${t.text}' is cut off at the top or bottom", layout.height <= height + 1)
            val parent = t.parent as View
            assertTrue("'${t.text}' sticks out of its parent", t.left >= 0 && t.top >= 0 && t.right <= parent.width && t.bottom <= parent.height)
        }
    }

    @Test
    fun nothingIsCutOffAtTheDefaultFontSize() = assertNothingClipped()

    @Test
    @Config(fontScale = 1.3f)
    fun nothingIsCutOffAtALargeFontSize() = assertNothingClipped()

    @Test
    fun headerLinesShareOneCenter() {
        val header = shownTexts().first { it.text.toString() == "DataBar" }.parent as ViewGroup
        for (i in 0 until header.childCount) {
            val line = header.getChildAt(i)
            // Where the ink is: a full-width TextView can still draw its text at the start.
            val center = if (line is TextView) {
                line.left + line.totalPaddingLeft + (line.layout.getLineLeft(0) + line.layout.getLineRight(0)) / 2f
            } else {
                line.left + line.width / 2f
            }
            assertEquals("header line $i", header.width / 2f, center, 2f)
        }
    }
}
