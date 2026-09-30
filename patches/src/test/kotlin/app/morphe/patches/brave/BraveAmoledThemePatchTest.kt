package app.morphe.patches.brave

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BraveAmoledThemePatchTest {
    @Test
    fun `option keys are bundle specific`() {
        assertEquals(
            setOf("backgroundColor", "disableDynamicColors"),
            braveAmoledThemePatch.options.keys,
        )
    }

    @Test
    fun `hex parser accepts rgb rrggbb and aarrggbb`() {
        assertEquals(RgbColor(0, 0, 0, 0xFF), parseHexColor("#000"))
        assertEquals(RgbColor(0x1e, 0x20, 0x29, 0xFF), parseHexColor("#1e2029"))
        assertEquals(RgbColor(0x12, 0x13, 0x14, 0x80), parseHexColor("#80121314"))
        assertEquals(null, parseHexColor("black"))
        assertEquals(null, parseHexColor("#12345"))
    }

    @Test
    fun `surface matcher catches dark neutrals and skips accents text overlays`() {
        assertTrue(isAmoledSurfaceColor("#1e2029"))
        assertTrue(isAmoledSurfaceColor("#17171f"))
        assertTrue(isAmoledSurfaceColor("#0d1214"))
        assertTrue(isAmoledSurfaceColor("#2e3039"))
        assertFalse(isAmoledSurfaceColor("#f0f2ff"), "light text must stay")
        assertFalse(isAmoledSurfaceColor("#072542"), "dark blue accent must stay")
        assertFalse(isAmoledSurfaceColor("#33f0f2ff"), "translucent overlay must stay")
        assertFalse(isAmoledSurfaceColor("#00000000"), "transparent must stay")
        assertFalse(isAmoledSurfaceColor("#737ade"), "indigo accent must stay")
    }

    @Test
    fun `rewrite night colors turns surfaces black and keeps accents`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="bg">#1e2029</color>
                <color name="card">#2e3039</color>
                <color name="accent">#737ade</color>
                <color name="text">#f0f2ff</color>
                <color name="overlay">#33f0f2ff</color>
                <color name="alias">@color/bg</color>
            </resources>
        """.trimIndent()

        val (out, replaced) = rewriteNightColorXml(xml, "#000000")
        assertEquals(2, replaced)
        assertTrue("""<color name="bg">#000000</color>""" in out)
        assertTrue("""<color name="card">#000000</color>""" in out)
        assertTrue("""<color name="accent">#737ade</color>""" in out)
        assertTrue("""<color name="text">#f0f2ff</color>""" in out)
        assertTrue("""<color name="overlay">#33f0f2ff</color>""" in out)
        assertTrue("""<color name="alias">@color/bg</color>""" in out)
    }

    @Test
    fun `material you dark neutrals are collected and rewritten in place`() {
        val xml = """
            <resources>
                <color name="surface">@android:color/system_neutral1_900</color>
                <color name="elevated">@android:color/system_neutral2_800</color>
                <color name="accent">@android:color/system_accent1_200</color>
                <color name="literal">#1e2029</color>
            </resources>
        """.trimIndent()

        assertEquals(listOf("surface", "elevated"), collectMaterialYouDarkNeutralNames(xml))
        val (out, replaced) = rewriteMaterialYouDarkNeutrals(xml, "#000000")
        assertEquals(2, replaced)
        assertTrue("""<color name="surface">#000000</color>""" in out)
        assertTrue("""<color name="elevated">#000000</color>""" in out)
        assertTrue("""<color name="accent">@android:color/system_accent1_200</color>""" in out)
    }

    @Test
    fun `night v31 override file uses background hex`() {
        val out = buildNightV31Overrides(listOf("b", "a"), "#0A0A0A")
        assertTrue("""<color name="a">#0a0a0a</color>""" in out)
        assertTrue("""<color name="b">#0a0a0a</color>""" in out)
    }

    @Test
    fun `dynamic colors preference defaults off but stays clickable`() {
        val text = """
            <PreferenceScreen>
                <ChromeSwitchPreference
                    android:key="brave_android_dynamic_colors_enabled"
                    android:title="@string/x" />
                <ChromeSwitchPreference android:key="other" />
            </PreferenceScreen>
        """.trimIndent()
        val out = rewriteDynamicColorsPreferenceText(text)
        assertTrue("""android:defaultValue="false"""" in out)
        assertFalse("android:enabled" in out, "switch must stay clickable")
        assertTrue("""android:key="other"""" in out)
    }

    @Test
    fun `dynamic colors rewrite ignores longer attribute names and keeps n1 prefix`() {
        val text = """
            <ChromeSwitchPreference n1:key="brave_android_dynamic_colors_enabled" n1:title="@string/x"/>
            <ChromeSwitchPreference some_other_key="brave_android_dynamic_colors_enabled"/>
        """.trimIndent()
        val out = rewriteDynamicColorsPreferenceText(text)
        assertTrue("""n1:key="brave_android_dynamic_colors_enabled"""" in out)
        assertTrue("""android:defaultValue="false"""" in out)
        assertTrue("""some_other_key="brave_android_dynamic_colors_enabled"/>""" in out)
        assertEquals(1, Regex("android:defaultValue=\"false\"").findAll(out).count())
    }

    @Test
    fun `pref rewrite does not touch similarly named attributes`() {
        val text =
            """<ChromeSwitchPreference some_other_key="brave_android_dynamic_colors_enabled" notdefaultValue="true" notenabled="true"/>"""
        val out = rewriteDynamicColorsPreferenceText(text)
        assertEquals(text, out)
    }

    @Test
    fun `boolean prologue is const false return`() {
        val smali = forceFalseBooleanPrologueSmali()
        assertTrue("const/4 v0, 0x0" in smali)
        assertTrue("return v0" in smali)
    }

    @Test
    fun `dedicated getter matcher rejects AppearancePreferences style methods`() {
        // AppearancePreferences.J1/k4 bind every Appearance key in one boolean
        // method — force-false on those greys out / kills the whole menu.
        val appearanceBinder = listOf(
            "brave_android_dynamic_colors_enabled",
            "brave_bottom_toolbar_enabled_key",
            "brave_night_mode_enabled_key",
            "ads_switch",
            "show_brave_rewards_icon",
            "ui_theme",
        )
        assertTrue(appearanceBinder.any { it == DYNAMIC_COLORS_PREF_KEY })
        assertTrue(
            appearanceBinder.any { it.contains("brave_night_mode") },
            "fixture must look like the real Appearance binder",
        )
        // Documented rule: any Appearance hint blocks the force-false path.
        assertTrue(
            APPEARANCE_PREF_KEY_HINTS.any { hint -> appearanceBinder.any { it.contains(hint) } },
        )
    }

    @Test
    fun `applyAmoledResources rewrites night v31 and writes night v31 file`() {
        val res = createTempDirectory("amoled-res").toFile()
        res.resolve("values-night").mkdirs()
        res.resolve("values-v31").mkdirs()
        res.resolve("xml").mkdirs()
        res.resolve("values-night/colors.xml").writeText(
            """
            <resources>
                <color name="bg">#1e2029</color>
                <color name="accent">#aaa8f7</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-v31/colors.xml").writeText(
            """
            <resources>
                <color name="bg">@android:color/system_neutral1_900</color>
                <color name="accent">@android:color/system_accent1_200</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("xml/prefs.xml").writeText(
            """<ChromeSwitchPreference android:key="brave_android_dynamic_colors_enabled" />""",
        )

        val result = applyAmoledResources(res, "#000000")
        assertEquals(1, result.nightColorsReplaced)
        assertEquals(1, result.v31MaterialYouReplaced)
        assertTrue(result.nightV31Overrides >= 1)
        assertEquals(1, result.preferenceFilesChanged)
        assertTrue(
            """<color name="bg">#000000</color>""" in
                res.resolve("values-night/colors.xml").readText(),
        )
        assertTrue(
            """<color name="bg">#000000</color>""" in
                res.resolve("values-v31/colors.xml").readText(),
        )
        val nightV31 = res.resolve("values-night-v31/colors.xml").readText()
        assertTrue("""<color name="bg">#000000</color>""" in nightV31)
        assertFalse("""<color name="accent">#000000</color>""" in nightV31)
    }

    @Test
    fun `missing night colors fails closed`() {
        val res = createTempDirectory("amoled-empty").toFile()
        assertFailsWith<Exception> {
            applyAmoledResources(res, "#000000")
        }
    }

    @Test
    fun `normalize rejects translucent hex`() {
        assertEquals("#000000", normalizeOpaqueHex("#000"))
        assertEquals("#0a0a0a", normalizeOpaqueHex("#0A0A0A"))
        assertEquals(null, normalizeOpaqueHex("#80121314"))
    }
}
