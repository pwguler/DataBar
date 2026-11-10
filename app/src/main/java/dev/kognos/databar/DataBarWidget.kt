package dev.kognos.databar

import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews

/** The last 30 days as bars; today is the selected day. */
class DataBarWidget : TileWidget<List<Long>>() {

    override val layout = R.layout.widget

    override fun load(context: Context): List<Long> =
        if (UsageRepo.hasUsageAccess(context)) {
            UsageRepo.dailyLast(context, UsageRepo.BARS, UsageRepo.includeWifi(context)).map { it.bytes }
        } else {
            emptyList()
        }

    /** White masks of the days and of today; the layout tints them. */
    override fun fill(context: Context, views: RemoteViews, data: List<Long>, w: Int, h: Int) {
        val today = data.size - 1
        views.setImageViewBitmap(R.id.ivBars, Bars.draw(data, w, h, today, bar = Color.WHITE, accent = Color.TRANSPARENT))
        views.setImageViewBitmap(R.id.ivToday, Bars.draw(data, w, h, today, bar = Color.TRANSPARENT, accent = Color.WHITE))
    }
}
