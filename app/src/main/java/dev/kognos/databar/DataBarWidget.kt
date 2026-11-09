package dev.kognos.databar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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

        /** Android 12+ resolves the color when the host applies the views, so light/dark switches need no redraw. */
        private fun tint(context: Context, views: RemoteViews, viewId: Int, color: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColor(viewId, "setColorFilter", color)
            } else {
                views.setInt(viewId, "setColorFilter", context.getColor(color))
            }
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget)
            val tap = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, tap)

            val dm = context.resources.displayMetrics
            val opts = manager.getAppWidgetOptions(widgetId) ?: Bundle()
            // The tile has 14dp padding on each side (DESIGN.md, "Shape").
            val w = ((opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110) - 28).coerceAtLeast(24) * dm.density).toInt()
            val h = ((opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40) - 28).coerceAtLeast(12) * dm.density).toInt()
            val values = if (UsageRepo.hasUsageAccess(context)) {
                UsageRepo.dailyLast(context, UsageRepo.BARS, UsageRepo.includeWifi(context)).map { it.bytes }
            } else {
                emptyList()
            }

            // Each layer is a white mask of one kind of dot, tinted with that kind's color.
            fun layer(viewId: Int, color: Int, unlit: Int, dot: Int, ink: Int) {
                views.setImageViewBitmap(viewId, DotMatrix.draw(values, w, h, dm.density, values.size - 1, unlit, dot, ink))
                tint(context, views, viewId, color)
            }
            val on = Color.WHITE
            val off = Color.TRANSPARENT
            layer(R.id.ivUnlit, R.color.dot_unlit, unlit = on, dot = off, ink = off)
            layer(R.id.ivDots, R.color.dot, unlit = off, dot = on, ink = off)
            layer(R.id.ivToday, R.color.ink, unlit = off, dot = off, ink = on)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
