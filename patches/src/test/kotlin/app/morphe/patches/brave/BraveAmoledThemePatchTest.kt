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
            setOf("backgroundColor", "textColor", "accentColor", "disableDynamicColors"),
            braveAmoledThemePatch.options.keys,
        )
    }

    @Test
    fun `text and accent matchers stay out of each other and of surfaces`() {
        // Brave dark ink palette
        assertTrue(isTextColor("#f0f2ff"))
        assertTrue(isTextColor("#c2c4cf"))
        assertTrue(isTextColor("#84889c"))
        assertTrue(isTextColor("#ffffff"))
        // Accent family
        assertTrue(isAccentColor("#737ade"))
        assertTrue(isAccentColor("#a0a5eb"))
        assertTrue(isAccentColor("#bcc6f3"))
        assertTrue(isAccentColor("#434fcf"))
        // Cross-talk must stay false
        assertFalse(isTextColor("#737ade"), "accent is not text")
        assertFalse(isAccentColor("#f0f2ff"), "text is not accent")
        assertFalse(isTextColor("#121212"), "surface is not text")
        assertFalse(isAccentColor("#121212"), "surface is not accent")
        assertFalse(isAmoledSurfaceColor("#f0f2ff"), "text is not surface")
        // Status colors stay
        assertFalse(isAccentColor("#ff7654"), "error coral is not accent")
        assertFalse(isAccentColor("#ff7f72"), "warning coral is not accent")
    }

    @Test
    fun `rewrite text keeps ink hierarchy and accent stays independent`() {
        val xml = """
            <resources>
                <color name="title">#f0f2ff</color>
                <color name="body">#c2c4cf</color>
                <color name="muted">#84889c</color>
                <color name="link">#737ade</color>
                <color name="chip">#a0a5eb</color>
                <color name="bg">#121212</color>
                <color name="err">#ff7654</color>
            </resources>
        """.trimIndent()
        val (textOut, textCount) = rewriteTextColorXml(xml, "#ffffff")
        assertEquals(3, textCount)
        // Primary full, secondary ~81%, muted ~62% — not one flat color.
        assertTrue("""<color name="title">#ffffff</color>""" in textOut)
        assertTrue("""<color name="body">#cecece</color>""" in textOut)
        assertTrue("""<color name="muted">#9e9e9e</color>""" in textOut)
        assertTrue("""<color name="link">#737ade</color>""" in textOut)
        assertTrue("""<color name="err">#ff7654</color>""" in textOut)

        val (accentOut, accentCount) = rewriteAccentColorXml(xml, "#ff0000")
        assertEquals(2, accentCount)
        assertTrue("""<color name="link">#ff0000</color>""" in accentOut)
        assertTrue("""<color name="chip">#ff0000</color>""" in accentOut)
        assertTrue("""<color name="title">#f0f2ff</color>""" in accentOut)
        assertTrue("""<color name="err">#ff7654</color>""" in accentOut)
    }

    @Test
    fun `text tier factors preserve relative lightness`() {
        assertEquals(1.0f, textTierFactor(RgbColor(255, 255, 255, 255)))
        assertEquals(0.81f, textTierFactor(RgbColor(207, 207, 207, 255)))
        assertEquals(0.62f, textTierFactor(RgbColor(156, 156, 156, 255)))
        assertEquals("#c6c6c6", scaleHexByFactor("#ffffff", 0.78f))
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
    fun `applyAmoledResources rewrites day night v31 and writes night v31 file`() {
        val res = createTempDirectory("amoled-res").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values-v31").mkdirs()
        res.resolve("xml").mkdirs()
        // windowBackground / colorBackground live here as dark hex (e.g. #121212).
        res.resolve("values/colors.xml").writeText(
            """
            <resources>
                <color name="window_bg">#121212</color>
                <color name="window_bg_argb">#ff303030</color>
                <color name="light">#ffffff</color>
            </resources>
            """.trimIndent(),
        )
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
        assertEquals(2, result.dayColorsReplaced)
        assertEquals(1, result.nightColorsReplaced)
        assertEquals(1, result.v31MaterialYouReplaced)
        assertTrue(result.nightV31Overrides >= 1)
        assertEquals(1, result.preferenceFilesChanged)

        val day = res.resolve("values/colors.xml").readText()
        assertTrue("""<color name="window_bg">#000000</color>""" in day)
        assertTrue("""<color name="window_bg_argb">#000000</color>""" in day)
        assertTrue("""<color name="light">#ffffff</color>""" in day)
        assertTrue(
            """<color name="bg">#000000</color>""" in
                res.resolve("values-night/colors.xml").readText(),
        )
        assertTrue(
            """<color name="bg">#000000</color>""" in
                res.resolve("values-v31/colors.xml").readText(),
        )
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

    @Test
    fun `declared colors include selector files so no duplicate is created`() {
        val res = createTempDirectory("amoled-declared").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("color-v31").mkdirs()
        res.resolve("values/colors.xml").writeText(
            """<resources><color name="has_color_element">#ffffff</color></resources>""",
        )
        res.resolve("values-night/colors.xml").writeText(
            """<resources><color name="night_only">#f0f2ff</color></resources>""",
        )
        // Selector-backed id: this is the one an earlier fix re-declared and
        // turned every screen's text black.
        res.resolve("color-v31/APKTOOL_RENAMED_0x7f0701f1.xml").writeText(
            "<selector><item n0:color=\"#ffffff\" /></selector>",
        )

        val declared = declaredColorNames(res)
        assertTrue("has_color_element" in declared)
        assertTrue("night_only" in declared)
        assertTrue("APKTOOL_RENAMED_0x7f0701f1" in declared)
    }

    @Test
    fun `text ids backed by a selector are never re-declared`() {
        val xml = "<resources>\n</resources>"
        val (out, injected) = declareMissingTextColors(
            xml = xml,
            textNames = listOf("selector_backed", "truly_missing"),
            declared = setOf("selector_backed"),
            hex = "#f0f2ff",
        )
        assertEquals(1, injected)
        assertFalse("selector_backed" in out, "re-declaring a selector id breaks the build")
        assertTrue("""<color name="truly_missing">#f0f2ff</color>""" in out)
    }

    @Test
    fun `material you regex covers the api34 surface ladder but not ink`() {
        // v31 neutral tones
        assertTrue(
            MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches("@android:color/system_neutral1_900"),
        )
        assertTrue(
            MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches("@android:color/system_neutral2_700"),
        )
        // v34 surface ladder — the gap this closes
        for (role in listOf(
            "system_background_dark",
            "system_surface_dark",
            "system_surface_bright_dark",
            "system_surface_dim_dark",
            "system_surface_variant_dark",
            "system_surface_container_dark",
            "system_surface_container_high_dark",
            "system_surface_container_highest_dark",
            "system_surface_container_low_dark",
            "system_surface_container_lowest_dark",
        )) {
            assertTrue(MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches("@android:color/$role"), role)
        }
        // Ink / accent / light roles must stay untouched
        for (role in listOf(
            "system_on_surface_dark",
            "system_on_background_dark",
            "system_outline_dark",
            "system_primary_dark",
            "system_accent1_900",
            "system_neutral1_100",
            "system_neutral2_500",
            "system_surface_light",
            "system_surface_container_low_light",
        )) {
            assertFalse(MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches("@android:color/$role"), role)
        }
    }

    @Test
    fun `low lstar selectors drop lstar while high ones survive`() {
        val dark = """
            <selector>
                <item n0:color="@android:color/system_neutral2_600" n0:lStar="12.0"
                  xmlns:n0="http://schemas.android.com/apk/res/android" />
            </selector>
        """.trimIndent()
        val (darkOut, darkCount) = rewriteDarkLStarSelectors(dark, "#000000")
        assertEquals(1, darkCount)
        assertTrue("""n0:color="#000000"""" in darkOut)
        assertFalse("lStar" in darkOut, "lStar only exists to let Material You re-tint")
        assertTrue("xmlns:n0" in darkOut, "the namespace declaration must survive")

        val light = dark.replace("12.0", "87.0")
        val (lightOut, lightCount) = rewriteDarkLStarSelectors(light, "#000000")
        assertEquals(0, lightCount)
        assertEquals(light, lightOut)
    }

    @Test
    fun `vector surface fills are rewritten but icons are not`() {
        val panel = """
            <vector xmlns:n0="http://schemas.android.com/apk/res/android">
                <path n0:fillColor="#1e2029" n0:pathData="M0,0" />
                <path n0:fillColor="#84889c" n0:pathData="M1,1" />
            </vector>
        """.trimIndent()
        val (panelOut, panelCount) = rewriteVectorSurfaceFills(panel, "#000000")
        assertEquals(1, panelCount)
        assertTrue("""n0:fillColor="#000000"""" in panelOut)
        assertTrue("""n0:fillColor="#84889c"""" in panelOut, "icon ink inside a panel stays")

        val icon = """<vector xmlns:n0="http://schemas.android.com/apk/res/android">""" +
            """""" + """
            <path n0:fillColor="#212529" n0:pathData="a" />
            <path n0:fillColor="#ffffff" n0:pathData="b" />
            <path n0:fillColor="#000000" n0:pathData="c" />
            <path n0:fillColor="#424242" n0:pathData="d" />
        </vector>
        """.trimIndent()
        val (_, iconCount) = rewriteVectorSurfaceFills(icon, "#000000")
        assertEquals(0, iconCount, "4-path artwork is an icon, not a surface panel")
    }

    @Test
    fun `applyAmoledResources never declares a color a selector already backs`() {
        val res = createTempDirectory("amoled-integration").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values-v31").mkdirs()
        res.resolve("values-v34").mkdirs()
        res.resolve("color-v31").mkdirs()
        res.resolve("drawable").mkdirs()

        res.resolve("values/colors.xml").writeText(
            """
            <resources>
                <color name="window_bg">#121212</color>
                <color name="bg">#212529</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-night/colors.xml").writeText(
            """
            <resources>
                <color name="bg">#1e2029</color>
                <color name="ink">#f0f2ff</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-v31/colors.xml").writeText(
            """<resources><color name="s">@android:color/system_neutral1_900</color></resources>""",
        )
        res.resolve("values-v34/colors.xml").writeText(
            """
            <resources>
                <color name="container">@android:color/system_surface_container_dark</color>
                <color name="on_surface">@android:color/system_on_surface_dark</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="T">
                    <item name="android:textColorPrimary">@color/selector_backed</item>
                    <item name="android:textColor">@color/never_declared</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        res.resolve("color-v31/selector_backed.xml").writeText(
            """<selector><item n0:color="#ffffff" /></selector>""",
        )
        res.resolve("color-v31/dark_lstar.xml").writeText(
            """
            <selector>
                <item n0:color="@android:color/system_neutral2_600" n0:lStar="6.0"
                  xmlns:n0="http://schemas.android.com/apk/res/android" />
            </selector>
            """.trimIndent(),
        )
        res.resolve("drawable/panel.xml").writeText(
            """
            <vector xmlns:n0="http://schemas.android.com/apk/res/android">
                <path n0:fillColor="#1e2029" n0:pathData="M0,0" />
                <path n0:fillColor="#84889c" n0:pathData="M1,1" />
            </vector>
            """.trimIndent(),
        )

        val result = applyAmoledResources(res, "#000000", textColorHex = "#f0f2ff")

        // Only the id that resolves to nothing is declared, and only for light
        // mode (dark ink); dark mode inherits the base declaration.
        assertEquals(1, result.textIdsInjected)
        val night = res.resolve("values-night/colors.xml").readText()
        assertFalse(
            "selector_backed" in night,
            "re-declaring a selector-backed id is what made AMOLED text black",
        )
        assertTrue(
            """<color name="never_declared">#202124</color>""" in
                res.resolve("values/colors.xml").readText(),
        )
        // surfaces flattened, ink preserved
        assertTrue("""<color name="bg">#000000</color>""" in night)
        assertTrue("""<color name="ink">#f0f2ff</color>""" in night)
        // v31 + v34 both handled; on_surface stays ink
        assertEquals(2, result.v31MaterialYouReplaced)
        val v34 = res.resolve("values-v34/colors.xml").readText()
        assertTrue("""<color name="container">#000000</color>""" in v34)
        assertTrue("""<color name="on_surface">@android:color/system_on_surface_dark</color>""" in v34)
        // low lStar selector pinned to AMOLED, drawable panel flattened
        assertEquals(1, result.lStarSelectorsReplaced)
        assertEquals(1, result.drawableSurfaceFills)
        assertTrue("""n0:color="#000000"""" in res.resolve("color-v31/dark_lstar.xml").readText())
        assertTrue(
            """n0:fillColor="#000000"""" in res.resolve("drawable/panel.xml").readText(),
        )
    }
}
