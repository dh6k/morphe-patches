package app.morphe.patches.helium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import javax.xml.parsers.DocumentBuilderFactory

class KeepHeliumChildProcessesAlivePatchTest {
    @Test
    fun `compatibility is version unpinned experimental Helium APK target`() {
        assertEquals("io.github.jqssun.helium", heliumChildProcessCompatibility.packageName)
        assertEquals(null, heliumChildProcessCompatibility.targets.single().version)
        assertTrue(heliumChildProcessCompatibility.targets.single().isExperimental)
        assertFalse(keepHeliumChildProcessesAlivePatch.default)
        assertEquals("Lorg/chromium/content/browser/ChildProcessLauncherHelperImpl;", HELIUM_CHILD_PROCESS_CLASS)
        assertEquals("setPriority", HELIUM_SET_PRIORITY_METHOD)
        assertEquals("const/16 v7, 4", heliumStrongBindingInstruction(7))
        assertEquals("ChildProcessLauncher.start", HELIUM_SPAWN_START_ANCHOR)
    }

    @Test
    fun `manifest helper is idempotent`() {
        val d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application/></manifest>".byteInputStream()
        )
        mutateHeliumKeepAliveManifest(d); mutateHeliumKeepAliveManifest(d)
        assertEquals(2, d.getElementsByTagName("uses-permission").length)
        assertEquals(1, d.getElementsByTagName("service").length)
        val service = d.getElementsByTagName("service").item(0) as org.w3c.dom.Element
        assertEquals("false", service.getAttribute("android:exported"))
        assertEquals("specialUse", service.getAttribute("android:foregroundServiceType"))
        assertEquals("", service.getAttribute("android:process"))
        assertEquals(1, service.getElementsByTagName("property").length)
    }

    @Test
    fun `launcher activity resolver handles relative activity and alias names`() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="io.github.jqssun.helium">
                <application>
                    <activity android:name=".MainBrowserActivity">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <activity android:name="io.github.jqssun.helium.RealActivity" />
                    <activity-alias
                        android:name=".Alias"
                        android:targetActivity="io.github.jqssun.helium.RealActivity">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity-alias>
                </application>
            </manifest>
            """.trimIndent().byteInputStream(),
        )
        assertEquals(
            setOf(
                "Lio/github/jqssun/helium/MainBrowserActivity;",
                "Lio/github/jqssun/helium/RealActivity;",
            ),
            resolveLauncherActivityClasses(document),
        )
    }
}
