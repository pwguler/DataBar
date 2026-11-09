package dev.kognos.databar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews

class DataBarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, manager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle
    ) {
        render(context, manager, widgetId)
    }

    companion object {

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, DataBarWidget::class.java))
            for (id in ids) render(context, manager, id)
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val values = if (UsageRepo.hasUsageAccess(context)) {
                UsageRepo.dailyLast(context, UsageRepo.BARS, UsageRepo.includeWifi(context)).map { it.bytes }
            } else {
                emptyList()
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
                RemoteViews(sizes.associateWith { views(context, values, it.width, it.height) })
            } else {
                // Cells are narrowest in portrait and shortest in landscape.
                val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
                val w = opts.getInt(
                    if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 110
                )
                val h = opts.getInt(
                    if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40
                )
                views(context, values, w.toFloat(), h.toFloat())
            }
            manager.updateAppWidget(widgetId, views)
        }

        /** The widget at one size in dp: white masks of the days and of today, tinted by the layout. */
        private fun views(context: Context, values: List<Long>, widthDp: Float, heightDp: Float): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget)
            val tap = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(android.R.id.background, tap)

            val density = context.resources.displayMetrics.density
            val padding = 2 * context.resources.getDimension(R.dimen.widget_padding)
            val w = (widthDp * density - padding).toInt()
            val h = (heightDp * density - padding).toInt()
            val today = values.size - 1
            val on = Color.WHITE
            val off = Color.TRANSPARENT
            views.setImageViewBitmap(R.id.ivBars, Bars.draw(values, w, h, today, bar = on, accent = off))
            views.setImageViewBitmap(R.id.ivToday, Bars.draw(values, w, h, today, bar = off, accent = on))
            return views
        }
    }
}
