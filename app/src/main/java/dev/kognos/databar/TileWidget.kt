package dev.kognos.databar

import android.app.PendingIntent
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.RemoteViews
import android.widget.TextView

/** A home-screen widget: a tile drawn at each size the launcher lists, with "DataBar" below it like One UI's widget labels. */
abstract class TileWidget<T> : AppWidgetProvider() {

    /** The layout with the tile (`@android:id/background`) and the title below it. */
    protected abstract val layout: Int

    /** The data of one update, loaded once for all sizes. */
    protected abstract fun load(context: Context): T

    /** Fills the tile at one size; [w] and [h] are its inner size in px. */
    protected abstract fun fill(context: Context, views: RemoteViews, data: T, w: Int, h: Int)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, manager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle
    ) {
        render(context, manager, widgetId)
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val data = load(context)
        val color = titleColor(context, wallpaperHints(context))
        val res = context.resources
        val titleHeight = lineHeight(context, R.style.TextAppearance_DataBar_WidgetTitle) +
            res.getDimensionPixelSize(R.dimen.widget_title_gap)
        val padding = 2 * res.getDimension(R.dimen.widget_padding)

        fun at(widthDp: Float, heightDp: Float): RemoteViews {
            val views = RemoteViews(context.packageName, layout)
            val tap = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(android.R.id.background, tap)
            views.setTextColor(R.id.title, color)
            val density = res.displayMetrics.density
            fill(context, views, data, (widthDp * density - padding).toInt(), (heightDp * density - padding - titleHeight).toInt())
            return views
        }

        val opts = manager.getAppWidgetOptions(widgetId) ?: Bundle()
        // Android 12+ launchers list the exact sizes, typically portrait and landscape; the host picks one.
        val sizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        } else {
            null
        }
        val views = if (!sizes.isNullOrEmpty()) {
            RemoteViews(sizes.associateWith { at(it.width, it.height) })
        } else {
            // Cells are narrowest in portrait and shortest in landscape.
            val portrait = res.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val w = opts.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 110
            )
            val h = opts.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40
            )
            at(w.toFloat(), h.toFloat())
        }
        manager.updateAppWidget(widgetId, views)
    }

    companion object {

        /** Redraws every placed DataBar widget. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            for (widget in listOf<TileWidget<*>>(DataBarWidget())) {
                for (id in manager.getAppWidgetIds(ComponentName(context, widget.javaClass))) {
                    widget.render(context, manager, id)
                }
            }
        }

        /** The title color for the wallpaper's [hints] (`WallpaperColors.getColorHints`). */
        internal fun titleColor(context: Context, hints: Int): Int = context.getColor(
            if (hints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) R.color.widget_title_dark_text else R.color.widget_title
        )

        private fun wallpaperHints(context: Context): Int {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
            return try {
                WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.colorHints ?: 0
            } catch (e: RuntimeException) {
                0 // the wallpaper service is not running
            }
        }

        /** Height in px of one line of [appearance] as the launcher lays it out: the same font, size and font scale. */
        private fun lineHeight(context: Context, appearance: Int): Int {
            val probe = TextView(ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight))
            probe.setTextAppearance(appearance)
            probe.text = context.getString(R.string.app_name)
            probe.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            return probe.measuredHeight
        }
    }
}
