package app.morphe.patches.helium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeepHeliumChildProcessesAlivePatchTest {
    @Test
    fun `compatibility is unpinned experimental Helium APK target`() {
        assertEquals("io.github.jqssun.helium", heliumChildProcessCompatibility.packageName)
        assertEquals(null, heliumChildProcessCompatibility.targets.single().version)
        assertTrue(heliumChildProcessCompatibility.targets.single().isExperimental)
        assertFalse(keepHeliumChildProcessesAlivePatch.default)
        assertEquals("Lorg/chromium/content/browser/ChildProcessLauncherHelperImpl;", HELIUM_CHILD_PROCESS_CLASS)
        assertEquals("setPriority", HELIUM_SET_PRIORITY_METHOD)
        assertEquals(listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "Z", "Z", "Z", "I"), HELIUM_SET_PRIORITY_PARAMETERS)
        assertEquals("const/16 p12, 0x3", HELIUM_PRIORITY_INSTRUCTION)
        assertEquals("const/16 v%s, 0x4", HELIUM_SPAWN_INSTRUCTION)
        assertEquals("ChildProcessLauncher.start", HELIUM_SPAWN_START_ANCHOR)
    }
}
