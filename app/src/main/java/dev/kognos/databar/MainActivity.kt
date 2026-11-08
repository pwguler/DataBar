package dev.kognos.databar

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var dayInput: EditText
    private lateinit var wifiBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "DataBar"
        title.textSize = 22f
        root.addView(title)

        status = TextView(this)
        status.setPadding(0, pad / 2, 0, pad)
        root.addView(status)

        val grant = Button(this)
        grant.text = "Aktifkan izin Usage access"
        grant.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        root.addView(grant)

        val dayLabel = TextView(this)
        dayLabel.text = "Tanggal mulai siklus tagihan (1-28):"
        dayLabel.setPadding(0, pad, 0, pad / 2)
        root.addView(dayLabel)

        dayInput = EditText(this)
        dayInput.inputType = InputType.TYPE_CLASS_NUMBER
        dayInput.setText(UsageRepo.cycleDay(this).toString())
        root.addView(dayInput)

        wifiBox = CheckBox(this)
        wifiBox.text = "Ikut hitung Wi-Fi"
        wifiBox.isChecked = UsageRepo.includeWifi(this)
        root.addView(wifiBox)

        val save = Button(this)
        save.text = "Simpan & perbarui widget"
        save.setOnClickListener { saveAndRefresh() }
        root.addView(save)

        val reload = Button(this)
        reload.text = "Muat ulang"
        reload.setOnClickListener {
            dayInput.setText(UsageRepo.cycleDay(this).toString())
            wifiBox.isChecked = UsageRepo.includeWifi(this)
            updateStatus()
        }
        root.addView(reload)

        val hint = TextView(this)
        hint.text = "Widget: long-press home screen > Widgets > DataBar. " +
                "Angka dihitung di HP dari NetworkStatsManager; app ini tidak punya izin internet."
        hint.textSize = 12f
        hint.setTextColor(Color.GRAY)
        hint.setPadding(0, pad, 0, 0)
        root.addView(hint)

        val scroll = ScrollView(this)
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(scroll)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        DataBarWidget.refreshAll(this)
    }

    private fun updateStatus() {
        val ok = UsageRepo.hasUsageAccess(this)
        status.text = if (ok) {
            val now = System.currentTimeMillis()
            val start = UsageRepo.cycleStart(now, UsageRepo.cycleDay(this))
            val mtd = UsageRepo.totalBytes(this, start, now, UsageRepo.includeWifi(this))
            "Izin: AKTIF · pemakaian siklus ini: " + UsageRepo.fmt(mtd)
        } else {
            "Izin: BELUM ADA — widget tidak bisa membaca pemakaian data"
        }
        status.setTextColor(if (ok) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
    }

    private fun saveAndRefresh() {
        val v = dayInput.text.toString().trim().toIntOrNull()
        if (v == null || v < 1 || v > 28) {
            Toast.makeText(this, "Isi 1-28", Toast.LENGTH_SHORT).show()
            return
        }
        UsageRepo.prefs(this).edit()
            .putInt(UsageRepo.KEY_CYCLE_DAY, v)
            .putBoolean(UsageRepo.KEY_WIFI, wifiBox.isChecked)
            .apply()
        updateStatus()
        DataBarWidget.refreshAll(this)
        Toast.makeText(this, "Disimpan: siklus tgl $v", Toast.LENGTH_SHORT).show()
    }
}
