package app.morphe.patches.helium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForceHighestRefreshRatePatchTest {
    @Test
    fun `patch targets titanium with version unpinned experimental compatibility`() {
        assertEquals("io.github.jqssun.helium", forceRefreshRateCompatibility.packageName)
        assertEquals(null, forceRefreshRateCompatibility.targets.single().version)
        assertTrue(forceRefreshRateCompatibility.targets.single().isExperimental)
    }

    @Test
    fun `max refresh const uses float max bits`() {
        // 0x7f7fFFFF == Float.MAX_VALUE; must be a full 32-bit const, not const/16.
        assertEquals(Float.MAX_VALUE, Float.fromBits(MAX_REFRESH_BITS))
        assertEquals("const p1, 2139095039", maxRefreshConstInstruction("p1"))
    }

    @Test
    fun `setter fingerprint pins exact windowandroid shape`() {
        assertEquals("Lorg/chromium/ui/base/WindowAndroid;", SetPreferredRefreshRateFingerprint.definingClass)
        assertEquals("setPreferredRefreshRate", SetPreferredRefreshRateFingerprint.name)
        assertEquals("V", SetPreferredRefreshRateFingerprint.returnType)
        assertEquals(listOf("F"), SetPreferredRefreshRateFingerprint.parameters)
    }
    @Test
    fun `nearest-mode fingerprint excludes setter and pins float param`() {
        assertEquals("Lorg/chromium/ui/base/WindowAndroid;", NearestDisplayModeFingerprint.definingClass)
        assertEquals("V", NearestDisplayModeFingerprint.returnType)
        assertEquals(listOf("F"), NearestDisplayModeFingerprint.parameters)
    }

    @Test
    fun `patch is opt-in`() {
        assertEquals(false, forceHighestRefreshRatePatch.default)
        assertEquals("Force highest refresh rate", forceHighestRefreshRatePatch.name)
    }
}
