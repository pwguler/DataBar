package dev.kognos.databar

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews

/** A home-screen widget: a tile drawn at each size the launcher lists, with "DataBar" below it like One UI's widget labels. */
abstract class TileWidget<T> : AppWidgetProvider() {

    /** The layout with the tile (`@android:id/background`) and the title below it. */
    protected abstract val layout: Int

    /** The data of one update, loaded once for all sizes. */
    protected abstract fun load(context: Context): T

    /** Fills the tile at one size; [w] and [h] are its inner size in px. */
    protected abstract fun fill(context: Context, views: RemoteViews, data: T, w: Int, h: Int)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray): Unit = TODO("not implemented")

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle
    ): Unit = TODO("not implemented")

    companion object {

        /** Redraws every placed DataBar widget. */
        fun refreshAll(context: Context): Unit = TODO("not implemented")

        /** The title color for the wallpaper's [hints] (`WallpaperColors.getColorHints`). */
        internal fun titleColor(context: Context, hints: Int): Int = TODO("not implemented")
    }
}
