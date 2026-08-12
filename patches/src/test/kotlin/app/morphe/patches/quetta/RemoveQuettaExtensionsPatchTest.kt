package app.morphe.patches.quetta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoveQuettaExtensionsPatchTest {
    @Test
    fun `Quetta compatibility and anchors`() {
        assertEquals(
            setOf("net.quetta.browser", "net.quetta.browser.official"),
            quettaCompatibilities.mapNotNull { it.packageName }.toSet(),
        )
        assertTrue(quettaCompatibilities.all { it.apkFileType == app.morphe.patcher.patch.ApkFileType.APK && it.targets.single().version == null })
        assertTrue(quettaCompatibilities.all { it.targets.single().isExperimental })
        assertTrue(removeQuettaExtensionsPatch.default)
        assertEquals(setOf("nnedfbcpeenmccjbdcnlnhogapndfeoa", "gadlcodpkkelmagfhkldjlobfncbkbmd"), QUETTA_EXTENSION_IDS)
        assertEquals(setOf("q30_from_quetta.crx", "q30_from_translator.crx"), QUETTA_ASSETS)
        assertEquals("return-void", QUETTA_BLOCK_INSTRUCTION)
    }
}
