package dev.kognos.databar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TextRolesTest {

    /** The font family a text appearance hands to TextView.setTextAppearance; null resets it to the platform default. */
    private fun family(style: Int): String? {
        val a = RuntimeEnvironment.getApplication().obtainStyledAttributes(style, intArrayOf(android.R.attr.fontFamily))
        try {
            return a.getString(0)
        } finally {
            a.recycle()
        }
    }

    @Test
    fun everyTextRoleCarriesTheThemeFontFamily() {
        val theme = family(android.R.style.TextAppearance_DeviceDefault)
        assertNotNull(theme)
        val roles = R.style::class.java.fields.filter { it.name.startsWith("TextAppearance_DataBar_") }
        assertTrue("expected the text roles of DESIGN.md", roles.size >= 11)
        for (role in roles) assertEquals(role.name, theme, family(role.getInt(null)))
    }
}
