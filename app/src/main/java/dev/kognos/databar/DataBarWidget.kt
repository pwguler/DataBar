package dev.kognos.databar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

class DataBarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAll(context)
    }

    companion object {
        const val ACTION_REFRESH = "dev.kognos.databar.REFRESH"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, DataBarWidget::class.java))
            for (id in ids) render(context, manager, id)
        }

        private fun color(context: Context, id: Int): Int = context.resources.getColor(id, null)

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget)
            val tap = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.ivBars, tap)

            val dm = context.resources.displayMetrics
            val opts = manager.getAppWidgetOptions(widgetId) ?: android.os.Bundle()
            val optW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 70)
            val optH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val chartW = ((optW - 24).coerceAtLeast(24) * dm.density).toInt()
            val chartH = ((optH - 26).coerceAtLeast(24) * dm.density).toInt()
            val daily = if (UsageRepo.hasUsageAccess(context)) {
                UsageRepo.dailyLast(context, UsageRepo.BARS, UsageRepo.includeWifi(context))
            } else {
                emptyList()
            }

            views.setImageViewBitmap(
                R.id.ivBars,
                BarChart.draw(
                    daily.map { it.bytes },
                    chartW,
                    chartH,
                    colorBar = color(context, R.color.w_bar),
                    colorToday = color(context, R.color.w_bar_today),
                    barRatio = 0.58f,
                    horizontal = false
                )
            )
            manager.updateAppWidget(widgetId, views)
        }
    }
}
