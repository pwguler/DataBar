package dev.kognos.databar

import android.app.AppOpsManager
import android.app.WallpaperColors
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import android.util.Xml
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

/** Widgets as an Android 12+ launcher places them: at one listed size, laid out with real text measurement. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class WidgetTest {

    private val app = RuntimeEnvironment.getApplication()
    private val manager = AppWidgetManager.getInstance(app)

    /** Adds the widget the way a launcher does, with its data from the phone. */
    private fun place(provider: Class<out AppWidgetProvider>, layout: Int, widthDp: Int, heightDp: Int): ViewGroup {
        val shadow = shadowOf(manager)
        val id = shadow.createWidget(provider, layout)
        val sizes = arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
        manager.updateAppWidgetOptions(id, Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes) })
        shadow.getAppWidgetProviderFor(id).onAppWidgetOptionsChanged(app, manager, id, manager.getAppWidgetOptions(id))
        return measured(shadow.getViewFor(id) as ViewGroup, widthDp, heightDp)
    }

    /** Applies [views] the way the launcher does. */
    private fun show(views: RemoteViews, layout: Int, widthDp: Int, heightDp: Int): ViewGroup {
        val root = LayoutInflater.from(app).inflate(layout, null) as ViewGroup
        views.reapply(app, root)
        return measured(root, widthDp, heightDp)
    }

    private fun measured(root: ViewGroup, widthDp: Int, heightDp: Int): ViewGroup {
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

    private fun visible(v: View, root: View): Boolean =
        generateSequence(v) { if (it === root) null else it.parent as View }.all { it.visibility == View.VISIBLE }

    /** The visible texts of the tile, in reading order. */
    private fun tileTexts(root: ViewGroup): List<String> =
        all(root.findViewById(android.R.id.background), TextView::class.java)
            .filter { visible(it, root) && it.text.isNotEmpty() }.map { it.text.toString() }

    private val providerAttrs = intArrayOf(
        android.R.attr.minWidth, android.R.attr.minHeight, android.R.attr.minResizeWidth, android.R.attr.minResizeHeight,
        android.R.attr.previewImage, android.R.attr.previewLayout
    ).sortedArray()

    /** The appwidget-provider attributes of [info], as the launcher reads them. */
    private fun provider(info: Int): TypedArray {
        val parser = app.resources.getXml(info)
        while (parser.next() != XmlPullParser.START_TAG) Unit
        return app.obtainStyledAttributes(Xml.asAttributeSet(parser), providerAttrs)
    }

    private fun TypedArray.res(attr: Int): Int = getResourceId(providerAttrs.indexOf(attr), 0)

    private fun TypedArray.dp(attr: Int): Float = getDimension(providerAttrs.indexOf(attr), 0f) / app.resources.displayMetrics.density

    /** The smallest size in dp a launcher may give the widget: a resize minimum counts only below the default minimum. */
    private fun smallest(info: Int): Pair<Int, Int> {
        val p = provider(info)
        fun min(default: Int, resize: Int): Int {
            val d = p.dp(default)
            val r = p.dp(resize)
            return (if (r > 0f) minOf(r, d) else d).toInt()
        }
        return min(android.R.attr.minWidth, android.R.attr.minResizeWidth) to min(android.R.attr.minHeight, android.R.attr.minResizeHeight)
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

    /** "DataBar" sits centered below the tile, uncut, and every text in the tile is whole and inside its padding. */
    private fun assertTitledTile(root: ViewGroup) {
        val tile = root.findViewById<View>(android.R.id.background)
        assertTileTextsWhole(root)
        val title = all(root, TextView::class.java).single { it.text.toString() == "DataBar" }
        val t = bounds(title, root)
        assertTrue("the title is below the tile", t.top >= bounds(tile, root).bottom)
        assertTrue("the title is inside the widget", t.bottom <= root.height)
        assertTrue("the title is cut off", title.layout.height <= title.height - title.totalPaddingTop - title.totalPaddingBottom + 1)
        val ink = t.left + title.totalPaddingLeft + (title.layout.getLineLeft(0) + title.layout.getLineRight(0)) / 2f
        assertEquals("the title is centered", root.width / 2f, ink, 2f)
        assertEquals("the title is white without wallpaper colors", Color.WHITE, title.currentTextColor)
    }

    /** Every text in the tile is whole and inside its padding. */
    private fun assertTileTextsWhole(root: ViewGroup) {
        val tile = root.findViewById<View>(android.R.id.background)
        val tb = bounds(tile, root)
        val inner = Rect(tb.left + tile.paddingLeft, tb.top + tile.paddingTop, tb.right - tile.paddingRight, tb.bottom - tile.paddingBottom)
        for (text in all(tile, TextView::class.java).filter { visible(it, root) && it.text.isNotEmpty() }) {
            assertTrue("'${text.text}' is outside the tile", inner.contains(bounds(text, root)))
            val width = text.width - text.totalPaddingLeft - text.totalPaddingRight
            for (i in 0 until text.layout.lineCount) {
                assertTrue("'${text.text}' is cut off at the side", text.layout.getLineWidth(i) <= width + 1 || text.layout.getEllipsisCount(i) > 0)
            }
            assertTrue("'${text.text}' is cut off", text.layout.height <= text.height - text.totalPaddingTop - text.totalPaddingBottom + 1)
        }
    }

    /** The bars are drawn at the tile's inner size, so the launcher does not scale them. */
    private fun assertBarsFillTheTile(root: ViewGroup) {
        val tile = root.findViewById<View>(android.R.id.background)
        for (image in all(tile, ImageView::class.java)) {
            val bmp = (image.drawable as BitmapDrawable).bitmap
            assertEquals("image width", (tile.width - tile.paddingLeft - tile.paddingRight).toFloat(), bmp.width.toFloat(), 1f)
            assertEquals("image height", (tile.height - tile.paddingTop - tile.paddingBottom).toFloat(), bmp.height.toFloat(), 1f)
        }
    }

    @Test
    fun barsWidgetHasTheTitleBelowTheTile() {
        val root = place(DataBarWidget::class.java, R.layout.widget, 180, 90)
        assertTitledTile(root)
        assertBarsFillTheTile(root)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun barsWidgetHasTheTitleBelowTheTileAtALargeFontSize() {
        val root = place(DataBarWidget::class.java, R.layout.widget, 180, 90)
        assertTitledTile(root)
        assertBarsFillTheTile(root)
    }

    /** The picker preview of [info], applied as a launcher applies it, at [widthDp] x [heightDp]. */
    private fun preview(info: Int, widthDp: Int, heightDp: Int): ViewGroup {
        val layout = provider(info).res(android.R.attr.previewLayout)
        assertTrue("no previewLayout", layout != 0)
        return measured(RemoteViews(app.packageName, layout).apply(app, FrameLayout(app)) as ViewGroup, widthDp, heightDp)
    }

    /** The fallback preview image draws the tile and something on it. */
    private fun assertPreviewImage(info: Int) {
        val id = provider(info).res(android.R.attr.previewImage)
        assertTrue("no previewImage", id != 0)
        val image = app.getDrawable(id)!!
        val bmp = Bitmap.createBitmap(image.intrinsicWidth, image.intrinsicHeight, Bitmap.Config.ARGB_8888)
        image.setBounds(0, 0, bmp.width, bmp.height)
        image.draw(Canvas(bmp))
        val solid = (0 until bmp.width).flatMap { x -> (0 until bmp.height).map { y -> bmp.getPixel(x, y) } }
            .filter { Color.alpha(it) == 255 }.toSet()
        assertTrue("the preview image has only ${solid.size} solid colors", solid.size >= 3)
    }

    @Test
    fun barsWidgetHasAPickerPreview() {
        val root = preview(R.xml.widget_info, 180, 90)
        assertTileTextsWhole(root)
        val bars = all(root.findViewById(android.R.id.background), ImageView::class.java)
        assertEquals(2, bars.count { it.drawable != null && it.drawable.intrinsicWidth > 0 })
        assertPreviewImage(R.xml.widget_info)
    }

    @Test
    fun flaggedWidgetHasAPickerPreview() {
        val root = preview(R.xml.widget_flagged_info, 340, 200)
        assertTileTextsWhole(root)
        val ratios = tileTexts(root).filter { it.endsWith("\u00d7") }
        assertEquals(3, ratios.size)
        assertEquals(ratios.sortedByDescending { it.dropLast(1).replace(',', '.').toFloat() }, ratios)
        assertPreviewImage(R.xml.widget_flagged_info)
    }

    private fun watch(uid: Int, ratio: Float, bgTx: Long = 600) = UsageRepo.Watch(uid, tx = 1000, rx = 0, bgTx = bgTx, ratio = ratio)

    // Four flagged apps, and one at 9x its average that is not flagged because it uploads mostly in the foreground.
    private val today = listOf(watch(10_001, 3.2f), watch(10_002, 6f), watch(10_003, 9f, bgTx = 100), watch(10_004, 4.5f), watch(10_005, 3f))

    private fun flagged(data: List<UsageRepo.Watch>?, widthDp: Int, heightDp: Int) =
        show(FlaggedWidget().views(app, data, widthDp.toFloat(), heightDp.toFloat()), R.layout.widget_flagged, widthDp, heightDp)

    @Test
    fun flaggedWidgetListsFlaggedAppsHighestFirst() {
        val root = flagged(today, 250, 170)
        assertTitledTile(root)
        val texts = tileTexts(root)
        assertEquals(listOf("6,0\u00d7", "4,5\u00d7", "3,2\u00d7", "3,0\u00d7"), texts.filter { it.endsWith("\u00d7") })
        assertTrue("upload today", "\u2191 1000 B" in texts)
        assertFalse(texts.any { it.contains("more") || it.contains("flagged") })
    }

    @Test
    fun flaggedWidgetEndsWithMoreWhenSomeDoNotFit() {
        val root = flagged(today, 250, 110)
        assertTitledTile(root)
        val texts = tileTexts(root)
        assertEquals(listOf("6,0\u00d7"), texts.filter { it.endsWith("\u00d7") })
        assertEquals("+3 more", texts.last())
    }

    @Test
    @Config(fontScale = 1.3f)
    fun flaggedWidgetRowsStayWholeAtALargeFontSize() {
        val root = flagged(today, 250, 110)
        assertTitledTile(root)
        val texts = tileTexts(root)
        val shown = texts.count { it.endsWith("\u00d7") }
        assertEquals("+${4 - shown} more", texts.last())
    }

    @Test
    fun flaggedWidgetShowsAppsAtItsSmallestSize() {
        val (w, h) = smallest(R.xml.widget_flagged_info)
        val root = flagged(today, w, h)
        assertTitledTile(root)
        assertTrue("app rows at ${w}x$h dp: ${tileTexts(root)}", tileTexts(root).any { it.endsWith("\u00d7") })
    }

    @Test
    fun flaggedWidgetCountsThemWhenOnlyOneRowFits() {
        val root = flagged(today, 250, 85)
        assertTitledTile(root)
        assertEquals(listOf("4 flagged"), tileTexts(root))
    }

    @Test
    fun flaggedWidgetSaysSoWhenNothingIsFlagged() {
        val root = flagged(listOf(watch(10_003, 9f, bgTx = 100)), 250, 110)
        assertTitledTile(root)
        assertEquals(listOf("Nothing flagged today"), tileTexts(root))
    }

    @Test
    fun flaggedWidgetAsksForUsageAccess() {
        // As on a fresh install; Robolectric allows every app op by default.
        shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_DEFAULT
        )
        val root = place(FlaggedWidget::class.java, R.layout.widget_flagged, 250, 110)
        assertTitledTile(root)
        assertEquals(listOf("Usage access needed"), tileTexts(root))
    }

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
