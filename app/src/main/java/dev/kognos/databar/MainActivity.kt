package dev.kognos.databar

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

class MainActivity : Activity() {

    // Resolved after attach; the activity is recreated when the system switches light/dark.
    private val surface by lazy { getColor(R.color.surface) }
    private val ink by lazy { getColor(R.color.ink) }
    private val inkSecondary by lazy { getColor(R.color.ink_secondary) }
    private val rule by lazy { getColor(R.color.rule) }
    private val signal by lazy { getColor(R.color.signal) }
    private val background by lazy { getColor(R.color.background) }

    private lateinit var heroNumber: TextView
    private lateinit var heroUnit: TextView
    private lateinit var todayLine: TextView
    private lateinit var chart: ImageView
    private lateinit var accessValue: TextView
    private lateinit var appsDate: TextView
    private lateinit var appsList: android.widget.ListView
    private lateinit var appsAdapter: AppAdapter

    private var lastDaily: List<UsageRepo.Day> = emptyList()
    private var sortMode = 0
    private var rowsByFlag: List<Row> = emptyList()
    private var rowsByVolume: List<Row> = emptyList()
    private lateinit var chipFlag: TextView
    private lateinit var chipVolume: TextView
    private var selected: Int = -1

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, appearance: Int): TextView =
        TextView(this).apply {
            setTextAppearance(appearance)
            text = s
            includeFontPadding = false
        }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(rule)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun row(label: String, right: View?): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            )
            addView(text(label, R.style.TextAppearance_DataBar_Row), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            if (right != null) addView(right)
        }

    private fun chip(label: String, mode: Int): TextView =
        text(label, R.style.TextAppearance_DataBar_Chip).apply {
            gravity = Gravity.CENTER
            setPadding(dp(13), 0, dp(13), 0)
            setOnClickListener { setSort(mode) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(30), dp(20), dp(30))

        root.addView(text("DataBar", R.style.TextAppearance_DataBar_Title))

        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.HORIZONTAL
        heroNumber = text("\u2014", R.style.TextAppearance_DataBar_Total)
        heroUnit = text("", R.style.TextAppearance_DataBar_Unit)
        hero.addView(heroNumber)
        hero.addView(heroUnit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(10) })
        hero.setOnClickListener {
            if (lastDaily.isNotEmpty()) selectDay(lastDaily.size - 1)
        }
        root.addView(hero, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(30) })

        todayLine = text("", R.style.TextAppearance_DataBar_Summary)
        root.addView(todayLine, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

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
        root.addView(chart, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(132)
        ).apply { topMargin = dp(26) })

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(surface)
        }

        val wifiSwitch = Switch(this)
        wifiSwitch.thumbDrawable = getDrawable(R.drawable.switch_thumb)
        wifiSwitch.trackDrawable = getDrawable(R.drawable.switch_track)
        wifiSwitch.thumbTintList = null
        wifiSwitch.trackTintList = null
        wifiSwitch.isChecked = UsageRepo.includeWifi(this)
        wifiSwitch.setOnCheckedChangeListener { _, checked ->
            UsageRepo.prefs(this).edit().putBoolean(UsageRepo.KEY_WIFI, checked).apply()
            selected = -1
            refresh()
            DataBarWidget.refreshAll(this)
        }
        val wifiRow = row("Include Wi-Fi", wifiSwitch)
        wifiRow.setOnClickListener { wifiSwitch.toggle() }
        card.addView(wifiRow)
        card.addView(divider())

        accessValue = text("", R.style.TextAppearance_DataBar_Value)
        val accessRow = row("Usage access", accessValue)
        accessRow.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        card.addView(accessRow)
        root.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(26) })

        appsDate = text("", R.style.TextAppearance_DataBar_Label)
        root.addView(appsDate, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(30) })

        // Chips are 48dp tall to tap; the pill drawn inside is 30dp, so 9dp of each margin is the inset.
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chipFlag = chip("Highlight", 0)
        chipVolume = chip("Volume", 1)
        chips.addView(chipFlag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        chips.addView(chipVolume, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)
        ).apply { marginStart = dp(8) })
        root.addView(chips, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(3) })
        paintChips()

        appsAdapter = AppAdapter()
        appsList = android.widget.ListView(this)
        appsList.divider = null
        appsList.dividerHeight = 0
        appsList.isVerticalScrollBarEnabled = false
        appsList.adapter = appsAdapter
        root.addView(appsList, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        DataBarWidget.refreshAll(this)
    }

    private fun refresh() {
        val ok = UsageRepo.hasUsageAccess(this)
        accessValue.text = if (ok) "Granted" else "Needed"
        accessValue.setTextColor(if (ok) inkSecondary else signal)

        if (!ok) {
            heroNumber.text = "\u2014"
            heroUnit.text = ""
            todayLine.text = "grant Usage access below"
            chart.setImageBitmap(null)
            appsDate.text = ""
            appsAdapter.submit(emptyList())
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
        // Drawn at the view's own size so the dots stay round.
        if (chart.width == 0) {
            chart.post { drawChart(daily) }
            return
        }
        chart.setImageBitmap(
            DotMatrix.draw(
                daily.map { it.bytes }, chart.width, chart.height, resources.displayMetrics.density,
                selected,
                unlit = getColor(R.color.dot_unlit),
                dot = getColor(R.color.dot),
                ink = ink
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
            return ordered.take(15).map {
                val r = UsageRepo.resolve(this, it.uid)
                Row(
                    r.first, r.second,
                    "\u2191 " + UsageRepo.fmt(it.tx) + "   \u2193 " + UsageRepo.fmt(it.rx),
                    "bg " + it.bgPercent + "%  \u00b7  " + String.format("%.1f", it.ratio) + "\u00d7 avg",
                    (scaleBy(it).toFloat() / max.toFloat()),
                    it.flagged
                )
            }
        }

        rowsByFlag = build(items.sortedByDescending { it.ratio }) { it.tx }
        rowsByVolume = build(items.sortedByDescending { it.tx + it.rx }) { it.tx + it.rx }
        appsAdapter.submit(if (sortMode == 0) rowsByFlag else rowsByVolume)
    }


    private data class Row(
        val label: String,
        val pkg: String?,
        val line1: String,
        val line2: String,
        val share: Float,
        val flag: Boolean
    )

    private val iconCache = HashMap<String, android.graphics.drawable.Drawable?>()

    private fun iconFor(pkg: String?): android.graphics.drawable.Drawable? {
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

    /** Share of the day as a row of 3dp dots every 6dp (DESIGN.md, "Dot matrix"). */
    private inner class DotBar(private val share: Float, private val fill: Int) : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val step = dp(6).toFloat()
            val radius = dp(3) / 2f
            val n = (width / step).toInt()
            val on = if (share <= 0f || n == 0) 0 else (n * share).roundToInt().coerceIn(1, n)
            for (i in 0 until n) {
                paint.color = if (i < on) fill else rule
                canvas.drawCircle(i * step + radius, height / 2f, radius, paint)
            }
        }
    }

    private inner class AppAdapter : android.widget.BaseAdapter() {
        private var items: List<Row> = emptyList()

        fun submit(next: List<Row>) {
            items = next
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val it0 = items[position]

            val wrap = LinearLayout(this@MainActivity)
            wrap.orientation = LinearLayout.VERTICAL
            wrap.setPadding(0, dp(14), 0, dp(12))

            val row = LinearLayout(this@MainActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL

            val icon = ImageView(this@MainActivity)
            icon.scaleType = ImageView.ScaleType.FIT_XY
            row.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginEnd = dp(12)
            })
            val dr = iconFor(it0.pkg)
            if (dr != null) icon.setImageDrawable(dr) else icon.setImageBitmap(null)

            val name = text(it0.label, R.style.TextAppearance_DataBar_AppName)
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            row.addView(name, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))

            val right = LinearLayout(this@MainActivity)
            right.orientation = LinearLayout.VERTICAL
            right.gravity = Gravity.END
            val top = text(it0.line1, R.style.TextAppearance_DataBar_Traffic)
            top.gravity = Gravity.END
            val bottom = text(it0.line2, R.style.TextAppearance_DataBar_Baseline)
            if (it0.flag) bottom.setTextColor(signal)
            bottom.gravity = Gravity.END
            bottom.setPadding(0, dp(3), 0, 0)
            right.addView(top)
            right.addView(bottom)
            row.addView(right)
            wrap.addView(row)

            wrap.addView(
                DotBar(it0.share.coerceIn(0f, 1f), if (it0.flag) signal else ink),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply {
                    topMargin = dp(10)
                }
            )
            return wrap
        }
    }

    private fun paintChips() {
        if (!::chipFlag.isInitialized) return
        for ((chip, mode) in listOf(chipFlag to 0, chipVolume to 1)) {
            val on = sortMode == mode
            chip.setTextColor(if (on) background else inkSecondary)
            val pill = GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                if (on) setColor(ink) else setStroke(dp(1), rule)
            }
            chip.background = InsetDrawable(pill, 0, dp(9), 0, dp(9))
        }
    }

    private fun setSort(mode: Int) {
        if (sortMode == mode) return
        sortMode = mode
        paintChips()
        appsAdapter.submit(if (mode == 0) rowsByFlag else rowsByVolume)
    }

}
