package dev.kognos.databar

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The picker of blocked apps, driven by taps and typing. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class BlockActivityTest {

    private val app = RuntimeEnvironment.getApplication()

    private fun install(pkg: String, label: String) {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label; uid = 10_100 + pkg.length }
        })
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main" } }
        )
    }

    private fun open(): BlockActivity {
        install("com.example.zoo", "Zoo")
        install("com.example.chat", "chat")
        install("com.example.maps", "Maps")
        install(app.packageName, "DataBar")
        return Robolectric.buildActivity(BlockActivity::class.java).setup().visible().get()
    }

    private val BlockActivity.list get() = window.decorView.findViewsOfType(ListView::class.java).single()

    private fun android.view.View.findViewsOfType(type: Class<ListView>): List<ListView> = when {
        type.isInstance(this) -> listOf(type.cast(this)!!)
        this is android.view.ViewGroup -> (0 until childCount).flatMap { getChildAt(it).findViewsOfType(type) }
        else -> emptyList()
    }

    /** Each shown row as "name [x]" or "name [ ]". */
    private fun BlockActivity.rows(): List<String> {
        shadowOf(Looper.getMainLooper()).idle()
        val l = list
        l.measure(0, 0)
        l.layout(0, 0, 1080, 2000)
        return (0 until l.childCount).map { i ->
            val row = l.getChildAt(i) as android.view.ViewGroup
            val name = (row.getChildAt(1) as TextView).text
            val box = if ((row.getChildAt(2) as CheckBox).isChecked) "[x]" else "[ ]"
            "$name $box"
        }
    }

    private fun BlockActivity.tap(name: String) {
        val l = list
        val i = (0 until l.adapter.count).first { (l.adapter.getItem(it) as AppBlock.App).label == name }
        l.performItemClick(l.getChildAt(i), i, i.toLong())
    }

    @Test
    fun listsLaunchableAppsByNameWithoutDataBar() {
        assertEquals(listOf("chat [ ]", "Maps [ ]", "Zoo [ ]"), open().rows())
    }

    @Test
    fun blockedAppsComeFirstWhenThePickerOpens() {
        install("com.example.zoo", "Zoo")
        AppBlock.setBlocked(app, "com.example.zoo", true)
        assertEquals(listOf("Zoo [x]", "chat [ ]", "Maps [ ]"), open().rows())
    }

    @Test
    fun aTapBlocksAndUnblocksWithoutMovingTheRow() {
        val picker = open()

        picker.tap("Zoo")
        assertEquals(setOf("com.example.zoo"), AppBlock.blocked(app))
        assertEquals(listOf("chat [ ]", "Maps [ ]", "Zoo [x]"), picker.rows())

        picker.tap("Zoo")
        assertEquals(emptySet<String>(), AppBlock.blocked(app))
        assertEquals(listOf("chat [ ]", "Maps [ ]", "Zoo [ ]"), picker.rows())
    }

    @Test
    fun searchFiltersByNameOrPackage() {
        val picker = open()
        val search = picker.window.decorView.let { v ->
            fun find(x: android.view.View): EditText? = x as? EditText
                ?: (x as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
            find(v)!!
        }

        search.setText("MA")
        assertEquals(listOf("Maps [ ]"), picker.rows())

        search.setText("com.example.c")
        assertEquals(listOf("chat [ ]"), picker.rows())

        search.setText("")
        assertTrue(picker.rows().size == 3)
        assertFalse(picker.rows().any { it.startsWith("DataBar") })
    }
}
