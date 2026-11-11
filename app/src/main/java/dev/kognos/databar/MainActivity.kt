package dev.kognos.databar

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    // Resolved after attach; the activity is recreated when the system switches light/dark.
    private val block by lazy { getColor(R.color.block) }
    private val rule by lazy { getColor(R.color.rule) }
    private val track by lazy { getColor(R.color.track) }
    private val signal by lazy { getColor(R.color.signal) }
    private val accent by lazy { themeColor(android.R.attr.colorControlActivated) } // the switch's color; Samsung's colorAccent differs
    private val textSecondary by lazy { themeColor(android.R.attr.textColorSecondary) }

    private lateinit var heroNumber: TextView
    private lateinit var heroUnit: TextView
    private lateinit var todayLine: TextView
    private lateinit var chart: ImageView
    private lateinit var accessValue: TextView
    private lateinit var appsDate: TextView
    private lateinit var appRows: LinearLayout

    private var lastDaily: List<UsageRepo.Day> = emptyList()
    private var sortMode = 0
    private var rowsByFlag: List<Row> = emptyList()
    private var rowsByVolume: List<Row> = emptyList()
    private lateinit var chipFlag: TextView
    private lateinit var chipVolume: TextView
    private var selected: Int = -1

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun themeColor(attr: Int): Int {
        val a = obtainStyledAttributes(intArrayOf(attr))
        try {
            return a.getColor(0, 0)
        } finally {
            a.recycle()
        }
    }

    private fun text(s: String, appearance: Int): TextView =
        TextView(this).apply {
            setTextAppearance(appearance)
            text = s
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(color)
        }

    /** A One UI content block: rounded, in the block color, [top] below the previous view. */
    private fun block(parent: LinearLayout, vertical: Int, top: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(block, dp(26))
            setPadding(dp(20), dp(vertical), dp(20), dp(vertical))
            parent.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(top) })
        }

    private fun row(label: String, right: View?): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            addView(text(label, R.style.TextAppearance_DataBar_Row), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            if (right != null) addView(right)
        }

    private fun chip(label: String, mode: Int): TextView =
        text(label, R.style.TextAppearance_DataBar_Chip).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { setSort(mode) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(12), 0, dp(12), dp(24))

        // One UI's title area: centered, sized to its text.
        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.gravity = Gravity.CENTER
        header.setPadding(0, dp(32), 0, dp(24))
        header.addView(text("DataBar", R.style.TextAppearance_DataBar_Header), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.HORIZONTAL
        heroNumber = text("\u2014", R.style.TextAppearance_DataBar_Total)
        heroUnit = text("", R.style.TextAppearance_DataBar_Unit)
        hero.addView(heroNumber)
        hero.addView(heroUnit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(6) })
        hero.setOnClickListener {
            if (lastDaily.isNotEmpty()) selectDay(lastDaily.size - 1)
        }
        header.addView(hero, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
        todayLine = text("", R.style.TextAppearance_DataBar_Summary)
        todayLine.gravity = Gravity.CENTER
        header.addView(todayLine, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
        content.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        chart = ImageView(this)
        chart.scaleType = ImageView.ScaleType.FIT_CENTER
        chart.setOnTouchListener { v, e ->
            if (e.action == android.view.MotionEvent.ACTION_UP && lastDaily.isNotEmpty()) {
                val n = lastDaily.size
                val idx = ((e.x / v.width.toFloat()) * n).toInt().coerceIn(0, n - 1)
                selectDay(idx)
            }
            true
        }
        block(content, 20, top = 0).addView(chart, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(112)
        ))

        val settings = block(content, 4, top = 12)
        val wifiSwitch = Switch(this)
        wifiSwitch.thumbDrawable = getDrawable(R.drawable.switch_thumb)
        wifiSwitch.trackDrawable = getDrawable(R.drawable.switch_track)
        wifiSwitch.thumbTintList = null
        wifiSwitch.trackTintList = null
        wifiSwitch.switchMinWidth = dp(40)
        wifiSwitch.isChecked = UsageRepo.includeWifi(this)
        wifiSwitch.setOnCheckedChangeListener { _, checked ->
            UsageRepo.prefs(this).edit().putBoolean(UsageRepo.KEY_WIFI, checked).apply()
            selected = -1
            refresh()
            TileWidget.refreshAll(this)
        }
        val wifiRow = row("Include Wi-Fi", wifiSwitch)
        wifiRow.setOnClickListener { wifiSwitch.toggle() }
        settings.addView(wifiRow)
        settings.addView(View(this).apply { setBackgroundColor(rule) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        accessValue = text("", R.style.TextAppearance_DataBar_Value)
        val accessRow = row("Usage access", accessValue)
        accessRow.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        settings.addView(accessRow)

        val apps = block(content, 20, top = 12)
        appsDate = text("", R.style.TextAppearance_DataBar_BlockTitle)
        apps.addView(appsDate)

        // Chips are 48dp tall to tap around a 30dp pill; the margins around them give back the 9dp inset.
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chipFlag = chip("Highlight", 0)
        chipVolume = chip("Volume", 1)
        chips.addView(chipFlag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        chips.addView(chipVolume, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)
        ).apply { marginStart = dp(8) })
        apps.addView(chips, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14 - 9) })
        paintChips()

        appRows = LinearLayout(this)
        appRows.orientation = LinearLayout.VERTICAL
        apps.addView(appRows, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6 - 9) })

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(content)
        setContentView(scroll)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        TileWidget.refreshAll(this)
    }

    private fun refresh() {
        val ok = UsageRepo.hasUsageAccess(this)
        accessValue.text = if (ok) "Granted" else "Needed"
        accessValue.setTextColor(if (ok) textSecondary else signal)

        if (!ok) {
            heroNumber.text = "\u2014"
            heroUnit.text = ""
            todayLine.text = "grant Usage access below"
            chart.setImageBitmap(null)
            appsDate.text = ""
            showRows(emptyList())
            return
        }

        val wifi = UsageRepo.includeWifi(this)
        val daily = UsageRepo.dailyLast(this, UsageRepo.BARS, wifi)
        lastDaily = daily

        if (selected !in daily.indices) selected = daily.size - 1

        val total = daily.sumOf { it.bytes }
        val today = daily.lastOrNull()?.bytes ?: 0L
        val avg = total / daily.size.coerceAtLeast(1)

        val (number, unit) = UsageRepo.fmtParts(total)
        heroNumber.text = number
        heroUnit.text = unit
        todayLine.text = "today " + UsageRepo.fmt(today) + "  \u00b7  avg " + UsageRepo.fmt(avg) + " / day"

        drawChart(daily)
        renderApps(daily, selected)
    }

    private fun drawChart(daily: List<UsageRepo.Day>) {
        // Drawn at the view's own size so the bar ends stay round.
        if (chart.width == 0) {
            chart.post { drawChart(daily) }
            return
        }
        chart.setImageBitmap(
            Bars.draw(
                daily.map { it.bytes }, chart.width, chart.height, selected,
                bar = getColor(R.color.bar),
                accent = accent
            )
        )
    }

    private fun selectDay(idx: Int) {
        if (idx !in lastDaily.indices) return
        selected = idx
        drawChart(lastDaily)
        renderApps(lastDaily, idx)
    }

    private fun renderApps(daily: List<UsageRepo.Day>, idx: Int) {
        if (idx !in daily.indices) return
        val day = daily[idx]
        val fmtDate = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.ENGLISH)
            .format(java.util.Date(day.start))
        appsDate.text = if (idx == daily.size - 1) "Watch \u00b7 today" else "Watch \u00b7 $fmtDate"

        val wifi = UsageRepo.includeWifi(this)
        val items = UsageRepo.watch(
            UsageRepo.flows(this, day.start, day.start + 24L * 60 * 60 * 1000, wifi),
            UsageRepo.flows(this, daily.first().start, System.currentTimeMillis(), wifi),
            daily.size
        )

        fun build(ordered: List<UsageRepo.Watch>, scaleBy: (UsageRepo.Watch) -> Long): List<Row> {
            val max = ordered.maxOfOrNull(scaleBy)?.coerceAtLeast(1L) ?: 1L
            return ordered.take(MAX_ROWS).map {
                val r = UsageRepo.resolve(this, it.uid)
                Row(
                    r.first, r.second,
                    "\u2191 " + UsageRepo.fmt(it.tx) + "   \u2193 " + UsageRepo.fmt(it.rx),
                    "bg " + it.bgPercent + "%  \u00b7  " + UsageRepo.fmtRatio(it.ratio) + " avg",
                    (scaleBy(it).toFloat() / max.toFloat()),
                    it.flagged
                )
            }
        }

        rowsByFlag = build(items.sortedByDescending { it.ratio }) { it.tx }
        rowsByVolume = build(items.sortedByDescending { it.tx + it.rx }) { it.tx + it.rx }
        showRows(if (sortMode == 0) rowsByFlag else rowsByVolume)
    }


    private data class Row(
        val label: String,
        val pkg: String?,
        val line1: String,
        val line2: String,
        val share: Float,
        val flag: Boolean
    )

    private val iconCache = HashMap<String, Drawable?>()

    private fun iconFor(pkg: String?): Drawable? {
        if (pkg == null) return null
        if (iconCache.containsKey(pkg)) return iconCache[pkg]
        val dr = try {
            packageManager.getApplicationIcon(pkg)
        } catch (t: Throwable) {
            null
        }
        iconCache[pkg] = dr
        return dr
    }

    /** At most [MAX_ROWS] rows, so they are plain views inside the scrolling screen rather than a list. */
    private fun showRows(rows: List<Row>) {
        appRows.removeAllViews()
        for (it0 in rows) appRows.addView(appRow(it0))
    }

    /** A share as a rounded bar in the track color; any share shows at least a round dot, like a day's bar. */
    private inner class ShareBar(private val share: Float, private val fill: Int) : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.color = track
            canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, paint)
            if (share <= 0f) return
            paint.color = fill
            canvas.drawRoundRect(0f, 0f, (share * w).coerceAtLeast(h).coerceAtMost(w), h, h / 2f, h / 2f, paint)
        }
    }

    private fun appRow(it0: Row): View {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(0, dp(12), 0, dp(12))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val icon = ImageView(this)
        icon.scaleType = ImageView.ScaleType.FIT_XY
        row.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            marginEnd = dp(12)
        })
        icon.setImageDrawable(iconFor(it0.pkg) ?: packageManager.defaultActivityIcon)

        val name = text(it0.label, R.style.TextAppearance_DataBar_AppName)
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        row.addView(name, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))

        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        right.gravity = Gravity.END
        val top = text(it0.line1, R.style.TextAppearance_DataBar_Traffic)
        top.gravity = Gravity.END
        val bottom = text(it0.line2, R.style.TextAppearance_DataBar_Baseline)
        if (it0.flag) bottom.setTextColor(signal)
        bottom.gravity = Gravity.END
        right.addView(top)
        right.addView(bottom)
        row.addView(right)
        wrap.addView(row)

        wrap.addView(
            ShareBar(it0.share.coerceIn(0f, 1f), if (it0.flag) signal else accent),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(10) }
        )
        return wrap
    }

    private fun paintChips() {
        if (!::chipFlag.isInitialized) return
        for ((chip, mode) in listOf(chipFlag to 0, chipVolume to 1)) {
            val on = sortMode == mode
            chip.setTextColor(if (on) accent else textSecondary)
            val pill = GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                // Selected: the accent at 16% opacity.
                if (on) setColor((accent and 0x00FFFFFF) or 0x29000000) else setStroke(dp(1), rule)
            }
            // Layer insets, not an InsetDrawable: a background's padding would replace the chip's own.
            chip.background = LayerDrawable(arrayOf(pill)).apply { setLayerInset(0, 0, dp(9), 0, dp(9)) }
        }
    }

    private fun setSort(mode: Int) {
        if (sortMode == mode) return
        sortMode = mode
        paintChips()
        showRows(if (mode == 0) rowsByFlag else rowsByVolume)
    }

    companion object {
        private const val MAX_ROWS = 15

        /** The tap target of the widgets and the alerts: opens DataBar. */
        fun open(c: Context): PendingIntent = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}
