package dev.kognos.databar

import android.app.Activity
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** The picker of the blocked apps: the launchable apps with a checkbox each, searchable, blocked ones first. */
class BlockActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val block by lazy { getColor(R.color.block) }

    private fun blockBackground() = GradientDrawable().apply {
        cornerRadius = dp(26).toFloat()
        setColor(block)
    }

    // shortcut: reads the app list and names on the main thread at open; move off it if the picker opens slowly
    private val apps by lazy { AppBlock.launchable(this) }
    private var shown: List<AppBlock.App> = emptyList()
    private val icons = HashMap<String, Drawable?>()
    private lateinit var search: EditText
    private val adapter = Rows()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(12), 0, dp(12), dp(24))

        val title = TextView(this).apply {
            setTextAppearance(R.style.TextAppearance_DataBar_ScreenTitle)
            text = "Block apps"
            gravity = Gravity.CENTER
            setPadding(0, dp(32), 0, dp(24))
        }
        content.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        search = EditText(this).apply {
            setTextAppearance(R.style.TextAppearance_DataBar_Row)
            hint = "Search apps"
            setHintTextColor(getColor(R.color.bar))
            background = blockBackground()
            setPadding(dp(20), 0, dp(20), 0)
            minHeight = dp(56)
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = reorder()
            })
        }
        content.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val list = ListView(this).apply {
            divider = null
            isVerticalScrollBarEnabled = false
            background = blockBackground()
            clipToOutline = true
            setPadding(dp(20), dp(4), dp(20), dp(4))
            clipToPadding = false
            adapter = this@BlockActivity.adapter
            setOnItemClickListener { _, _, position, _ -> toggle(shown[position]) }
        }
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })

        setContentView(content)
        reorder()
    }

    /** Sorts and filters the list. Only at open and on a search, so a tick does not move its row. */
    private fun reorder() {
        shown = AppBlock.order(apps, AppBlock.blocked(this), search.text.toString())
        adapter.notifyDataSetChanged()
    }

    private fun toggle(app: AppBlock.App) {
        AppBlock.setBlocked(this, app.pkg, app.pkg !in AppBlock.blocked(this))
        adapter.notifyDataSetChanged()
    }

    private inner class Rows : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: newRow()
            val app = shown[position]
            val icon = icons.getOrPut(app.pkg) { UsageRepo.icon(this@BlockActivity, app.pkg) }
            (row.getChildAt(0) as ImageView).setImageDrawable(icon ?: packageManager.defaultActivityIcon)
            (row.getChildAt(1) as TextView).text = app.label
            (row.getChildAt(2) as CheckBox).isChecked = app.pkg in AppBlock.blocked(this@BlockActivity)
            return row
        }

        private fun newRow() = LinearLayout(this@BlockActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(0, dp(12), 0, dp(12))
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12) })
            addView(TextView(context).apply {
                setTextAppearance(R.style.TextAppearance_DataBar_AppName)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            // The row takes the tap; the checkbox only shows the state.
            addView(CheckBox(context).apply {
                isClickable = false
                isFocusable = false
            })
        }
    }
}
