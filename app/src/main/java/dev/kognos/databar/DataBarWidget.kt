package dev.kognos.databar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, DataBarWidget::class.java)
            )
            for (id in ids) render(context, manager, id)
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget)
            val tapIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tvMtd, tapIntent)
            views.setOnClickPendingIntent(R.id.ivBars, tapIntent)
            views.setOnClickPendingIntent(R.id.tvFooter, tapIntent)

            if (!UsageRepo.hasUsageAccess(context)) {
                views.setTextViewText(R.id.tvMtd, "Butuh izin")
                views.setTextViewText(R.id.tvAvg, "buka app, aktifkan Usage access")
                views.setTextViewText(R.id.tvFooter, "tap untuk membuka DataBar")
                manager.updateAppWidget(widgetId, views)
                return
            }

            val wifi = UsageRepo.includeWifi(context)
            val day = UsageRepo.cycleDay(context)
            val now = System.currentTimeMillis()
            val start = UsageRepo.cycleStart(now, day)
            val mtd = UsageRepo.totalBytes(context, start, now, wifi)
            val daily = UsageRepo.daily(context, start, UsageRepo.BARS, wifi)
            val elapsed = daily.size.coerceAtLeast(1)
            val avg = mtd / elapsed

            views.setTextViewText(R.id.tvMtd, UsageRepo.fmt(mtd) + " MTD")
            views.setTextViewText(
                R.id.tvAvg,
                "rata² " + UsageRepo.fmt(avg) + "/hari · " + elapsed + " hari"
            )

            val dm = context.resources.displayMetrics
            val wpx = (320 * dm.density).toInt()
            val hpx = (56 * dm.density).toInt()
            views.setImageViewBitmap(
                R.id.ivBars,
                BarChart.draw(daily.map { it.bytes }, wpx, hpx)
            )

            val jam = SimpleDateFormat("HH:mm", Locale.US).format(Date(now))
            val ket = if (wifi) "seluler+Wi-Fi" else "seluler"
            views.setTextViewText(R.id.tvFooter, "siklus tgl $day · $ket · $jam")

            manager.updateAppWidget(widgetId, views)
        }
    }
}
