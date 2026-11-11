package dev.kognos.databar

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UsageRepoResolveTest {

    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun namesAnAppInstalledAfterTheFirstLookup() {
        UsageRepo.resolve(app, Process.myUid()) // the first lookup reads the installed apps, once
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.example.chat"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.example.chat"
                uid = 10_123
                nonLocalizedLabel = "Chat"
            }
        })

        assertEquals("Chat" to "com.example.chat", UsageRepo.resolve(app, 10_123))
    }
}
