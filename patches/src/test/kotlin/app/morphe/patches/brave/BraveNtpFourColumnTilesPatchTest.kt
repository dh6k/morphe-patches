package app.morphe.patches.brave

import app.morphe.patches.declaredField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BraveNtpFourColumnTilesPatchTest {
    @Test
    fun `patch is off by default`() {
        assertFalse(braveNtpFourColumnTilesPatch.default)
    }

    @Test
    fun `fingerprint anchors on the background image pref literal`() {
        assertEquals(
            listOf(NTP_BACKGROUND_IMAGE_PREF),
            NtpTilesGridModeWriterFingerprint.declaredField("strings"),
        )
        // Brave R8-renames the factory class and the flag itself, so neither the
        // defining class nor a method name can be pinned.
        assertEquals(null, NtpTilesGridModeWriterFingerprint.declaredField("definingClass"))
        assertEquals(null, NtpTilesGridModeWriterFingerprint.declaredField("name"))
    }

    @Test
    fun `fingerprint matches structurally because names are obfuscated`() {
        assertTrue(
            NtpTilesGridModeWriterFingerprint.declaredField("custom") is Function2<*, *, *>,
        )
    }

    @Test
    fun `pref anchor is the exact brave key`() {
        // Changing this string silently stops matching, so pin it.
        assertEquals(
            "brave.new_tab_page.show_background_image",
            NTP_BACKGROUND_IMAGE_PREF,
        )
    }

    @Test
    fun `replacement is a single nop matching the 2 byte if-nez slot`() {
        val smali = forceGridModeWriteSmali()

        assertEquals("nop", smali)
        // if-nez is format 21t (2 bytes) and nop is format 10x (2 bytes), so the
        // swap is byte-identical. The factory method this lands in is ~6.3 kB with
        // many branch offsets that must all stay valid.
        assertEquals(1, smali.trim().lines().size)
        assertFalse(smali.contains("if-"))
        assertFalse(smali.contains("invoke-"))
        assertFalse(smali.contains("const"))
        assertFalse(smali.contains("iget"))
    }

    @Test
    fun `replacement never writes a register or touches the flag value`() {
        // The flag is already written as true by the existing `const/4 v9, 1` +
        // `iput-boolean v9` pair. The patch only has to stop the branch that
        // skips it, so emitting any register write would be redundant at best and
        // a dead-code hazard at worst.
        val smali = forceGridModeWriteSmali()

        assertFalse(Regex("""\bv\d+\b""").containsMatchIn(smali))
        assertFalse(Regex("""\bp\d+\b""").containsMatchIn(smali))
        assertFalse(smali.contains("0x1"))
    }

    @Test
    fun `skip branch search window is bounded`() {
        // The factory method is ~6.3 kB; scanning too far back would eventually
        // latch onto an unrelated branch. Keep the window tight and documented.
        assertNull(
            findSkipBranchIndex(emptyList(), 0),
            "empty instruction list has no gate",
        )
        assertNull(
            findSkipBranchIndex(listOf(), 1_000),
            "out of range write index must not throw",
        )
    }
}
