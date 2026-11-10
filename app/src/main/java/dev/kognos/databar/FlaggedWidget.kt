package dev.kognos.databar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.RemoteViews

/** Today's flagged apps, highest × first, as many rows as fit. */
class FlaggedWidget : TileWidget<List<UsageRepo.Watch>?>() {

    override val layout = R.layout.widget_flagged

    /** Today's apps against their own average, or null without Usage access. */
    override fun load(context: Context): List<UsageRepo.Watch>? =
        if (UsageRepo.hasUsageAccess(context)) UsageRepo.watchToday(context, UsageRepo.includeWifi(context)) else null

    override fun fill(context: Context, views: RemoteViews, data: List<UsageRepo.Watch>?, w: Int, h: Int) {
        views.removeAllViews(R.id.rows)
        val flagged = data.orEmpty().filter { it.flagged }.sortedByDescending { it.ratio }
        val message = when {
            data == null -> "Usage access needed"
            flagged.isEmpty() -> "Nothing flagged today"
            else -> null
        }
        views.setViewVisibility(R.id.message, if (message == null) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.message, message ?: "")
        if (message != null) return

        val res = context.resources
        val rowHeight = maxOf(res.getDimensionPixelSize(R.dimen.widget_row), lineHeight(context, R.style.TextAppearance_DataBar_WidgetApp))
        val fit = h / rowHeight
        // When some don't fit, the last row that fits says how many are left out.
        val shown = if (flagged.size <= fit) flagged.size else (fit - 1).coerceAtLeast(0)
        val iconSize = res.getDimensionPixelSize(R.dimen.widget_icon)
        for (app in flagged.take(shown)) {
            val (name, pkg) = UsageRepo.resolve(context, app.uid)
            val row = RemoteViews(context.packageName, R.layout.widget_flagged_row)
            row.setImageViewBitmap(R.id.icon, icon(context, pkg, iconSize))
            row.setTextViewText(R.id.name, name)
            row.setTextViewText(R.id.upload, "\u2191 " + UsageRepo.fmt(app.tx))
            row.setTextViewText(R.id.ratio, UsageRepo.fmtRatio(app.ratio))
            views.addView(R.id.rows, row)
        }
        if (shown < flagged.size) {
            val more = RemoteViews(context.packageName, R.layout.widget_flagged_more)
            more.setTextViewText(R.id.more, if (shown == 0) "${flagged.size} flagged" else "+${flagged.size - shown} more")
            views.addView(R.id.rows, more)
        }
    }

    private fun icon(context: Context, pkg: String?, size: Int): Bitmap {
        val pm = context.packageManager
        val drawable = pkg?.let {
            try {
                pm.getApplicationIcon(it)
            } catch (e: Exception) {
                null
            }
        } ?: pm.defaultActivityIcon
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bmp))
        return bmp
    }
}
