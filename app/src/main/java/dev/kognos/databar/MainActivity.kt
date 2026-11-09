package dev.kognos.databar

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private val bg = Color.parseColor("#0F0F12")
    private val surface = Color.parseColor("#17171B")
    private val ink = Color.parseColor("#FFFFFF")
    private val muted = Color.parseColor("#9AA0A6")
    private val dim = Color.parseColor("#71767C")
    private val divider = Color.parseColor("#1FFFFFFF")
    private val accent = Color.parseColor("#7FB4FF")
    private val trackColor = Color.parseColor("#26262E")


    private lateinit var heroNumber: TextView
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

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }

    private fun dividers(): View = View(this).apply {
        setBackgroundColor(divider)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
    }

    private fun row(label: String, right: View?): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            )
            addView(text(label, 15f, ink), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            if (right != null) addView(right)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bg)
        root.setPadding(dp(20), dp(32), dp(20), dp(28))

        root.addView(text("DataBar", 28f, ink, true))

        val headRow = LinearLayout(this)
        headRow.orientation = LinearLayout.HORIZONTAL
        headRow.gravity = Gravity.CENTER_VERTICAL

        val headLeft = LinearLayout(this)
        headLeft.orientation = LinearLayout.VERTICAL

        heroNumber = text("—", 42f, ink, false).apply {
            fontFeatureSettings = "tnum"
            setPadding(0, dp(4), 0, 0)
        }
        heroNumber.setOnClickListener {
            if (lastDaily.isNotEmpty()) selectDay(lastDaily.size - 1)
        }
        headLeft.addView(heroNumber)
        todayLine = text("", 13f, muted).apply { setPadding(0, dp(8), 0, 0) }
        headLeft.addView(todayLine)

        headRow.addView(headLeft, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        root.addView(headRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })


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
        ).apply { topMargin = dp(14) })

        val gap = View(this)
        root.addView(gap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)))

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(surface)
        }

        val wifiSwitch = Switch(this)
        wifiSwitch.isChecked = UsageRepo.includeWifi(this)
        wifiSwitch.thumbTintList = ColorStateList.valueOf(bg)
        wifiSwitch.trackTintList = ColorStateList.valueOf(ink)
        wifiSwitch.setOnCheckedChangeListener { _, checked ->
            UsageRepo.prefs(this).edit().putBoolean(UsageRepo.KEY_WIFI, checked).apply()
            selected = -1
            refresh()
            DataBarWidget.refreshAll(this)
        }
        card.addView(row("Include Wi-Fi", wifiSwitch))
        card.addView(dividers())

        accessValue = text("", 15f, muted)
        val accessRow = row("Usage access", accessValue)
        accessRow.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        card.addView(accessRow)
        root.addView(card)

        appsDate = text("", 13f, dim).apply { setPadding(0, dp(26), 0, 0) }
        root.addView(appsDate)

        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(0, dp(8), 0, dp(4))

        chipFlag = text("Highlight", 13f, ink)
        chipFlag.setPadding(dp(14), dp(7), dp(14), dp(7))
        chipFlag.setOnClickListener { setSort(0) }

        chipVolume = text("Volume", 13f, dim)
        chipVolume.setPadding(dp(14), dp(7), dp(14), dp(7))
        chipVolume.setOnClickListener { setSort(1) }

        chips.addView(chipFlag)
        chips.addView(chipVolume, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(6) })
        root.addView(chips)
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
        accessValue.setTextColor(if (ok) muted else Color.parseColor("#F2A0A0"))

        if (!ok) {
            heroNumber.text = "—"
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

        heroNumber.text = UsageRepo.fmt(total)
        todayLine.text = "today " + UsageRepo.fmt(today) + "  ·  avg " + UsageRepo.fmt(avg) + " / day"

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
                selected, unlit = surface, dot = dim, ink = ink
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
        appsDate.text = if (idx == daily.size - 1) "Watch · today" else "Watch · $fmtDate"

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
                    "bg " + it.bgPercent + "%  ·  " + String.format("%.1f", it.ratio) + "\u00d7 avg",
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
            wrap.setPadding(0, dp(9), 0, dp(9))

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

            row.addView(text(it0.label, 15f, ink), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))

            val right = LinearLayout(this@MainActivity)
            right.orientation = LinearLayout.VERTICAL
            right.gravity = Gravity.END
            val top = text(it0.line1, 12f, ink)
            top.gravity = Gravity.END
            val bottom = text(it0.line2, 11f, if (it0.flag) Color.parseColor("#FF8080") else muted)
            bottom.gravity = Gravity.END
            bottom.setPadding(0, dp(2), 0, 0)
            right.addView(top)
            right.addView(bottom)
            row.addView(right)
            wrap.addView(row)

            val share = it0.share.coerceIn(0f, 1f)
            val bar = LinearLayout(this@MainActivity)
            bar.orientation = LinearLayout.HORIZONTAL
            val fill = View(this@MainActivity)
            fill.setBackgroundColor(if (it0.flag) Color.parseColor("#FF6B6B") else accent)
            val rest = View(this@MainActivity)
            rest.setBackgroundColor(trackColor)
            bar.addView(fill, LinearLayout.LayoutParams(0, dp(3), share.coerceAtLeast(0.001f)))
            bar.addView(rest, LinearLayout.LayoutParams(0, dp(3), (1f - share).coerceAtLeast(0.001f)))
            wrap.addView(bar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
            ).apply { topMargin = dp(7) })
            return wrap
        }
    }

    private fun paintChips() {
        if (!::chipFlag.isInitialized) return
        chipFlag.setTextColor(if (sortMode == 0) ink else dim)
        chipVolume.setTextColor(if (sortMode == 1) ink else dim)
        chipFlag.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (sortMode == 0) Color.parseColor("#26262E") else Color.TRANSPARENT)
        }
        chipVolume.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (sortMode == 1) Color.parseColor("#26262E") else Color.TRANSPARENT)
        }
    }

    private fun setSort(mode: Int) {
        if (sortMode == mode) return
        sortMode = mode
        paintChips()
        appsAdapter.submit(if (mode == 0) rowsByFlag else rowsByVolume)
    }

}
