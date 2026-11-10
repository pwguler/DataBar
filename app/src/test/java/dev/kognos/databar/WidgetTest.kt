package dev.kognos.databar

import android.app.WallpaperColors
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Widgets as an Android 12+ launcher places them: at one listed size, laid out with real text measurement. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class WidgetTest {

    private val app = RuntimeEnvironment.getApplication()
    private val manager = AppWidgetManager.getInstance(app)

    private fun place(provider: Class<out AppWidgetProvider>, layout: Int, widthDp: Int, heightDp: Int): ViewGroup {
        val shadow = shadowOf(manager)
        val id = shadow.createWidget(provider, layout)
        val sizes = arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
        manager.updateAppWidgetOptions(id, Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes) })
        shadow.getAppWidgetProviderFor(id).onAppWidgetOptionsChanged(app, manager, id, manager.getAppWidgetOptions(id))
        val root = shadow.getViewFor(id) as ViewGroup
        val d = app.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((widthDp * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((heightDp * d).toInt(), View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    private fun <T : View> all(v: View, type: Class<T>): List<T> = when {
        type.isInstance(v) -> listOf(type.cast(v)!!)
        v is ViewGroup -> (0 until v.childCount).flatMap { all(v.getChildAt(it), type) }
        else -> emptyList()
    }

    private fun bounds(v: View, root: View): Rect {
        val r = Rect(0, 0, v.width, v.height)
        var at = v
        while (at !== root) {
            r.offset(at.left, at.top)
            at = at.parent as View
        }
        return r
    }

    /** "DataBar" sits centered below the tile, uncut, and the tile's images are drawn at its inner size. */
    private fun assertTitledTile(root: ViewGroup) {
        val tile = root.findViewById<View>(android.R.id.background)
        val title = all(root, TextView::class.java).single { it.text.toString() == "DataBar" }
        val t = bounds(title, root)
        assertTrue("the title is below the tile", t.top >= bounds(tile, root).bottom)
        assertTrue("the title is inside the widget", t.bottom <= root.height)
        assertTrue("the title is cut off", title.layout.height <= title.height - title.totalPaddingTop - title.totalPaddingBottom + 1)
        val ink = t.left + title.totalPaddingLeft + (title.layout.getLineLeft(0) + title.layout.getLineRight(0)) / 2f
        assertEquals("the title is centered", root.width / 2f, ink, 2f)
        assertEquals("the title is white without wallpaper colors", Color.WHITE, title.currentTextColor)
        for (image in all(tile, ImageView::class.java).filter { it.visibility == View.VISIBLE }) {
            val bmp = (image.drawable as BitmapDrawable).bitmap
            assertEquals("image width", (tile.width - tile.paddingLeft - tile.paddingRight).toFloat(), bmp.width.toFloat(), 1f)
            assertEquals("image height", (tile.height - tile.paddingTop - tile.paddingBottom).toFloat(), bmp.height.toFloat(), 1f)
        }
    }

    @Test
    fun barsWidgetHasTheTitleBelowTheTile() =
        assertTitledTile(place(DataBarWidget::class.java, R.layout.widget, 180, 90))

    @Test
    @Config(fontScale = 1.3f)
    fun barsWidgetHasTheTitleBelowTheTileAtALargeFontSize() =
        assertTitledTile(place(DataBarWidget::class.java, R.layout.widget, 180, 90))

    @Test
    fun titleTurnsBlackOnlyWhenTheWallpaperPrefersDarkText() {
        val dark = WallpaperColors.HINT_SUPPORTS_DARK_TEXT
        val darkTheme = WallpaperColors.HINT_SUPPORTS_DARK_THEME
        assertEquals(Color.BLACK, TileWidget.titleColor(app, dark))
        assertEquals(Color.BLACK, TileWidget.titleColor(app, dark or darkTheme))
        assertEquals(Color.WHITE, TileWidget.titleColor(app, darkTheme))
        assertEquals(Color.WHITE, TileWidget.titleColor(app, 0))
    }
}
