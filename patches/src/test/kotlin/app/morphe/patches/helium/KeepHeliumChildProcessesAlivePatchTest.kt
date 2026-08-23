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
        assertEquals("const/16 v%s, 0x4", HELIUM_SPAWN_INSTRUCTION)
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
}
