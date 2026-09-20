package app.morphe.patches.quetta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForceHighestRefreshRatePatchTest {
    @Test
    fun `quetta refresh patch targets both quetta packages only`() {
        assertEquals(
            setOf("net.quetta.browser", "net.quetta.browser.official"),
            quettaCompatibilities.mapNotNull { it.packageName }.toSet(),
        )
        assertTrue(quettaCompatibilities.all { it.apkFileType == app.morphe.patcher.patch.ApkFileType.APK })
        assertTrue(quettaCompatibilities.all { it.targets.single().version == null })
        assertTrue(quettaCompatibilities.all { it.targets.single().isExperimental })
    }

    @Test
    fun `quetta fingerprints pin windowandroid and quetta structural shape`() {
        assertEquals("Lorg/chromium/ui/base/WindowAndroid;", QuettaSetPreferredRefreshRateFingerprint.definingClass)
        assertEquals("setPreferredRefreshRate", QuettaSetPreferredRefreshRateFingerprint.name)
        assertEquals("V", QuettaSetPreferredRefreshRateFingerprint.returnType)
        assertEquals(listOf("F"), QuettaSetPreferredRefreshRateFingerprint.parameters)

        assertEquals("Lorg/chromium/ui/base/WindowAndroid;", QuettaNearestDisplayModeFingerprint.definingClass)
        assertEquals("V", QuettaNearestDisplayModeFingerprint.returnType)
        assertEquals(listOf("F"), QuettaNearestDisplayModeFingerprint.parameters)
        assertTrue(QuettaNearestDisplayModeFingerprint.custom != null)
    }

    @Test
    fun `quetta refresh patch is opt-in and distinct kotlin symbol`() {
        assertEquals(false, forceQuettaHighestRefreshRatePatch.default)
        assertEquals("Force highest refresh rate", forceQuettaHighestRefreshRatePatch.name)
        assertTrue(forceQuettaHighestRefreshRatePatch.description.orEmpty().contains("Quetta"))
    }

    @Test
    fun `helium nearest-mode fingerprint stays titanium-only`() {
        // Guard: helium must not grow quetta structural OR-branches.
        val heliumCustom = app.morphe.patches.helium.NearestDisplayModeFingerprint.custom
        assertTrue(heliumCustom != null)
        // helium patch must remain titanium-compatible only
        assertEquals("io.github.jqssun.helium", app.morphe.patches.helium.forceRefreshRateCompatibility.packageName)
        assertEquals(1, listOf(app.morphe.patches.helium.forceRefreshRateCompatibility).size)
    }
}
