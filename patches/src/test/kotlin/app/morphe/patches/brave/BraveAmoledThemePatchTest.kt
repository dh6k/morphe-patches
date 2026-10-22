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
            setOf(
                "backgroundColor",
                "textColor",
                "accentColor",
                "disableDynamicColors",
                "surfaceUsesBackground",
                "surfaceColor",
            ),
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

        val (out, tierCounts) = rewriteSurfaceTiers(xml, "#000000", "#000000")
        assertEquals(2, tierCounts.background)
        assertEquals(0, tierCounts.container)
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

    @Test
    fun `text bearing ids are never flattened to background`() {
        val xml = """
            <resources>
                <color name="ink_only">#1c1c1d</color>
                <color name="panel">#212529</color>
            </resources>
        """.trimIndent()
        // Both hexes pass the surface heuristic; only the one the app renders
        // text with must be spared.
        assertTrue(isAmoledSurfaceColor("#1c1c1d"))
        val (out, tierCounts) = rewriteSurfaceTiers(
            xml,
            "#000000",
            "#000000",
            excludeNames = setOf("ink_only"),
        )
        assertEquals(1, tierCounts.background)
        assertTrue("""<color name="ink_only">#1c1c1d</color>""" in out)
        assertTrue("""<color name="panel">#000000</color>""" in out)
    }

    @Test
    fun `text bearing ids are collected from styles and layouts`() {
        val res = createTempDirectory("amoled-textids").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-v31").mkdirs()
        res.resolve("layout").mkdirs()
        res.resolve("layout-land").mkdirs()
        res.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="A">
                    <item name="android:textColor">@color/from_style</item>
                    <item name="android:textColorHint">@color/from_hint</item>
                    <item name="android:background">@color/from_background</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-v31/styles.xml").writeText(
            """
            <resources>
                <style name="B">
                    <item name="android:textColorPrimary">@color/from_v31</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        res.resolve("layout/screen.xml").writeText(
            """
            <TextView
              android:textColor="@color/from_layout"
              android:background="@color/layout_bg"
              app:tint="@color/from_tint" />
            """.trimIndent(),
        )
        res.resolve("layout-land/screen.xml").writeText(
            """<TextView n0:textColor="@color/from_land" />""",
        )

        val ids = collectTextBearingIds(res)
        assertTrue("from_style" in ids)
        assertTrue("from_hint" in ids)
        assertTrue("from_v31" in ids, "a later qualifier can swap the id set")
        assertTrue("from_layout" in ids)
        assertTrue("from_land" in ids, "layout qualifiers count too")
        assertFalse("from_background" in ids, "background is a surface, not text")
        assertFalse("layout_bg" in ids)
        assertFalse("from_tint" in ids)
    }

    @Test
    fun `day only text ids get a readable night declaration`() {
        val xml = """
            <resources>
                <color name="bg">#1e2029</color>
            </resources>
        """.trimIndent()
        val (out, declared) = declareNightTextColors(
            xml = xml,
            textNames = listOf("ink", "already_night"),
            declaredInNight = setOf("already_night"),
            hex = "#f0f2ff",
        )
        assertEquals(1, declared)
        assertTrue("""<color name="ink">#f0f2ff</color>""" in out)
        assertFalse("already_night" in out, "an id night already defines is not redeclared")
        assertTrue("""<color name="bg">#1e2029</color>""" in out)
    }

    @Test
    fun `night v31 overrides never re-assert text ids as background`() {
        val res = createTempDirectory("amoled-nv31").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values-v31").mkdirs()
        res.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="T">
                    <item name="android:textColor">@color/ink_id</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        // Both look exactly like dark chrome surfaces; only one is really ink.
        res.resolve("values/colors.xml").writeText(
            """
            <resources>
                <color name="panel">#1e2029</color>
                <color name="ink_id">#1c1c1d</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-night/colors.xml").writeText(
            """
            <resources>
                <color name="panel">#1e2029</color>
                <color name="ink_id">#e4e4e5</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-v31/colors.xml").writeText(
            """
            <resources>
                <color name="my_surface">@android:color/system_neutral1_900</color>
            </resources>
            """.trimIndent(),
        )

        applyAmoledResources(res, "#000000", textColorHex = "#f0f2ff")

        val nv31 = res.resolve("values-night-v31/colors.xml")
        assertTrue(nv31.isFile, "the override file still has to exist")
        val text = nv31.readText()
        assertFalse(
            "ink_id" in text,
            "a text id re-asserted here overrides the values-night fix and goes black",
        )
        assertTrue("panel" in text, "real surfaces still need the night-v31 override")
        assertTrue("my_surface" in text, "Material You roles still need it too")

        // values-night keeps the readable ink the patch wrote.
        assertTrue(
            """<color name="ink_id">#f0f2ff</color>""" in
                res.resolve("values-night/colors.xml").readText(),
        )
    }

    @Test
    fun `accent coloured text keeps its colour in dark mode`() {
        val res = createTempDirectory("amoled-accent-text").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="Stats">
                    <item name="android:textColor">@color/stat_orange</item>
                    <item name="android:textColor">@color/stat_grey</item>
                    <item name="android:textColor">@color/body_ink</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        // The privacy report paints its stat numbers in the accent palette, so
        // forcing every text id onto the ink colour erases the colour coding.
        res.resolve("values/colors.xml").writeText(
            """
            <resources>
                <color name="stat_orange">#cd4400</color>
                <color name="stat_grey">#687485</color>
                <color name="body_ink">#212529</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-night/colors.xml").writeText(
            """<resources><color name="bg">#1e2029</color></resources>""",
        )

        applyAmoledResources(res, "#000000", textColorHex = "#f0f2ff")

        val night = res.resolve("values-night/colors.xml").readText()
        assertFalse(
            """<color name="stat_orange">#f0f2ff</color>""" in night,
            "an orange stat number must not be flattened onto the body ink",
        )
        assertFalse("""<color name="stat_grey">#f0f2ff</color>""" in night)
        assertTrue(
            """<color name="body_ink">#f0f2ff</color>""" in night,
            "genuinely dark ink still needs a readable night value",
        )
    }

    @Test
    fun `surface tiers split window fills from raised fills`() {
        val xml = """
            <resources>
                <color name="window_bg">#121212</color>
                <color name="card">#1e2025</color>
                <color name="sheet">#2e3039</color>
                <color name="ink">#f0f2ff</color>
            </resources>
        """.trimIndent()

        val (out, counts) = rewriteSurfaceTiers(xml, "#000000", "#1e2029")
        assertEquals(1, counts.background)
        assertEquals(2, counts.container)
        assertTrue("""<color name="window_bg">#000000</color>""" in out)
        assertTrue("""<color name="card">#1e2029</color>""" in out)
        assertTrue("""<color name="sheet">#1e2029</color>""" in out)
        assertTrue("""<color name="ink">#f0f2ff</color>""" in out)
    }

    @Test
    fun `the default mode writes the background over every surface`() {
        val xml = """
            <resources>
                <color name="window_bg">#121212</color>
                <color name="card">#1e2029</color>
                <color name="ink">#f0f2ff</color>
            </resources>
        """.trimIndent()

        val (out, counts) = rewriteSurfaceTiers(xml, "#000000", "#000000")
        assertEquals(
            """
            <resources>
                <color name="window_bg">#000000</color>
                <color name="card">#000000</color>
                <color name="ink">#f0f2ff</color>
            </resources>
            """.trimIndent(),
            out,
        )
        assertEquals(2, counts.background)
        assertEquals(0, counts.container, "one tier means one target, so nothing reads as raised")
    }

    @Test
    fun `role evidence overrides the value heuristic for a raised id`() {
        val xml = """
            <resources>
                <color name="now_raised">#121212</color>
                <color name="still_flat">#121212</color>
            </resources>
        """.trimIndent()

        val (out, counts) = rewriteSurfaceTiers(
            xml,
            "#000000",
            "#1e2029",
            containerNames = setOf("now_raised"),
        )
        assertEquals(1, counts.background)
        assertEquals(1, counts.container)
        assertTrue("""<color name="now_raised">#1e2029</color>""" in out)
        assertTrue("""<color name="still_flat">#000000</color>""" in out)
    }

    @Test
    fun `material you role files map ids to their tier`() {
        val xml = """
            <resources>
                <color name="a">@android:color/system_surface_dark</color>
                <color name="b">@android:color/system_surface_container_high_dark</color>
                <color name="c">@android:color/system_neutral1_900</color>
                <color name="d">@android:color/system_on_surface_dark</color>
            </resources>
        """.trimIndent()

        val tiers = collectMaterialYouRoleTiers(xml)
        assertEquals(false, tiers["a"])
        assertEquals(true, tiers["b"])
        assertEquals(false, tiers["c"], "a v31 neutral tone is the window background")
        assertEquals(null, tiers["d"], "ink is not a surface role")
    }

    @Test
    fun `window background ids are collected from every values qualifier`() {
        val res = createTempDirectory("amoled-window").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-v31").mkdirs()
        res.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="T">
                    <item name="android:colorBackground">@color/window_bg</item>
                    <item name="android:windowBackground">@color/other_window</item>
                    <item name="android:background">@color/card_bg</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-v31/styles.xml").writeText(
            """
            <resources>
                <style name="V31">
                    <item name="android:colorBackground">@color/v31_window</item>
                </style>
            </resources>
            """.trimIndent(),
        )

        val ids = collectWindowBackgroundIds(res)
        assertTrue("window_bg" in ids)
        assertTrue("other_window" in ids)
        assertTrue("v31_window" in ids, "a later qualifier can swap the id")
        assertFalse("card_bg" in ids, "a card background is not the window")
    }

    @Test
    fun `a window background is never raised by the value heuristic`() {
        val xml = """
            <resources>
                <color name="window_bg">#1f1f1f</color>
                <color name="card_bg">#1f1f1f</color>
            </resources>
        """.trimIndent()

        val (out, counts) = rewriteSurfaceTiers(
            xml,
            "#000000",
            "#1e2029",
            baseNames = setOf("window_bg"),
        )
        assertEquals(1, counts.background)
        assertEquals(1, counts.container)
        assertTrue("""<color name="window_bg">#000000</color>""" in out)
        assertTrue("""<color name="card_bg">#1e2029</color>""" in out)
    }

    @Test
    fun `material you rewrite sends the container ladder to the surface color`() {
        val xml = """
            <resources>
                <color name="bg">@android:color/system_surface_dark</color>
                <color name="card">@android:color/system_surface_container_dark</color>
            </resources>
        """.trimIndent()

        val (out, replaced) = rewriteMaterialYouDarkNeutrals(xml, "#000000", "#1e2029")
        assertEquals(2, replaced)
        assertTrue("""<color name="bg">#000000</color>""" in out)
        assertTrue("""<color name="card">#1e2029</color>""" in out)
    }

    @Test
    fun `low lstar selectors split by role`() {
        val xml = """
            <selector>
                <item n0:color="@android:color/system_surface_dark" n0:lStar="8.0"
                  xmlns:n0="http://schemas.android.com/apk/res/android" />
                <item n0:color="@android:color/system_surface_container_high_dark" n0:lStar="9.0"
                  xmlns:n0="http://schemas.android.com/apk/res/android" />
            </selector>
        """.trimIndent()

        val (out, replaced) = rewriteDarkLStarSelectors(xml, "#000000", "#1e2029")
        assertEquals(2, replaced)
        assertTrue("""n0:color="#000000"""" in out)
        assertTrue("""n0:color="#1e2029"""" in out)
    }

    @Test
    fun `bare palette tones split by tone because the role name is a lie`() {
        // The real shapes from Brave 1.96.60: every v31 chrome fill aliases
        // system_neutral2_600 and only the tone differs — 6 is the window,
        // 24 is the surface_bright behind the popup menu and app bar.
        val xml = """
            <selector>
                <item n0:color="@android:color/system_neutral2_600" n0:lStar="6.0"
                  xmlns:n0="http://schemas.android.com/apk/res/android" />
            </selector>
            <selector>
                <item n0:color="@android:color/system_neutral2_600" n0:lStar="24.0"
                  xmlns:n0="http://schemas.android.com/apk/res/android" />
            </selector>
        """.trimIndent()

        val (out, replaced) = rewriteDarkLStarSelectors(xml, "#000000", "#1e2029")
        assertEquals(2, replaced)
        assertEquals(
            1,
            Regex("""n0:color="#000000"""").findAll(out).count(),
            "tone 6 is the window background",
        )
        assertEquals(
            1,
            Regex("""n0:color="#1e2029"""").findAll(out).count(),
            "tone 24 is a raised surface",
        )
    }

    @Test
    fun `night v31 overrides keep each tier on its own colour`() {
        val out = buildNightV31Overrides(mapOf("b" to "#1e2029", "a" to "#000000"))
        assertTrue("""<color name="a">#000000</color>""" in out)
        assertTrue("""<color name="b">#1e2029</color>""" in out)
        assertTrue(out.indexOf("""name="a"""") < out.indexOf("""name="b""""), "sorted by id")
    }

    @Test
    fun `surface option keeps window and raised ids apart end to end`() {
        val res = createTempDirectory("amoled-tiers").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values-v34").mkdirs()

        res.resolve("values/colors.xml").writeText(
            """<resources><color name="window_bg">#121212</color></resources>""",
        )
        res.resolve("values-night/colors.xml").writeText(
            """
            <resources>
                <color name="window_bg">#121212</color>
                <color name="card_bg">#121212</color>
            </resources>
            """.trimIndent(),
        )
        // The role file is the tier evidence for card_bg: the same id, a raised
        // role, so the value heuristic must not be the thing deciding.
        res.resolve("values-v34/colors.xml").writeText(
            """
            <resources>
                <color name="window_bg">@android:color/system_surface_dark</color>
                <color name="card_bg">@android:color/system_surface_container_high_dark</color>
            </resources>
            """.trimIndent(),
        )

        val result = applyAmoledResources(
            res,
            "#000000",
            surfaceUsesBackground = false,
            surfaceColorHex = "#1e2029",
        )

        assertEquals(1, result.containerColorsReplaced)
        val night = res.resolve("values-night/colors.xml").readText()
        assertTrue("""<color name="window_bg">#000000</color>""" in night)
        assertTrue("""<color name="card_bg">#1e2029</color>""" in night)
        val nv31 = res.resolve("values-night-v31/colors.xml").readText()
        assertTrue("""<color name="card_bg">#1e2029</color>""" in nv31)
        assertTrue("""<color name="window_bg">#000000</color>""" in nv31)
    }

    @Test
    fun `keeping dynamic colors leaves the material you resources alone`() {
        val res = createTempDirectory("amoled-my").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values-v31").mkdirs()
        res.resolve("color-v31").mkdirs()

        res.resolve("values-night/colors.xml").writeText(
            """<resources><color name="window_bg">#121212</color></resources>""",
        )
        val v31 =
            """<resources><color name="s">@android:color/system_neutral1_900</color></resources>"""
        res.resolve("values-v31/colors.xml").writeText(v31)
        val lstar = """
            <selector>
                <item n0:color="@android:color/system_neutral2_600" n0:lStar="6.0"
                  xmlns:n0="http://schemas.android.com/apk/res/android" />
            </selector>
        """.trimIndent()
        res.resolve("color-v31/dark_lstar.xml").writeText(lstar)

        val result = applyAmoledResources(res, "#000000", disableDynamicColors = false)

        assertEquals(0, result.v31MaterialYouReplaced)
        assertEquals(0, result.lStarSelectorsReplaced)
        assertEquals(v31, res.resolve("values-v31/colors.xml").readText())
        assertEquals(lstar, res.resolve("color-v31/dark_lstar.xml").readText())
        // Brave's own hex tokens are flattened in both modes.
        assertTrue(
            """<color name="window_bg">#000000</color>""" in
                res.resolve("values-night/colors.xml").readText(),
        )
        // The Material You role id must not be pinned back to the AMOLED hex.
        val nv31 = res.resolve("values-night-v31/colors.xml")
        assertFalse(nv31.isFile && """name="s"""" in nv31.readText())
    }

    @Test
    fun `lstar selector matches whatever namespace prefix apktool emitted`() {
        // Brave 1.96.61 decodes the v31 palette selectors with n1:, not n0:.
        // A hardcoded n0: silently matched nothing and left the popup menu and
        // the settings window grey (issue #21).
        val xml = """
            <selector>
                <item n1:color="@android:color/system_neutral2_600" n1:lStar="24.0"
                  xmlns:n1="http://schemas.android.com/apk/res/android" />
                <item n2:color="@android:color/system_neutral2_600" n2:lStar="6.0"
                  xmlns:n2="http://schemas.android.com/apk/res/android" />
            </selector>
        """.trimIndent()

        val (out, replaced) = rewriteDarkLStarSelectors(xml, "#000000", "#1e2029")
        assertEquals(2, replaced)
        assertTrue("""n1:color="#1e2029"""" in out, "tone 24 keeps its n1: prefix on the container")
        assertTrue("""n2:color="#000000"""" in out, "tone 6 keeps its n2: prefix on the background")
        assertFalse("lStar" in out, "lStar only exists to let Material You re-tint")
        assertFalse("n0:" in out, "no prefix is forced back to n0:")
    }

    @Test
    fun `lstar rewrite in flat mode sends every tier to the background`() {
        // The exact shape of res/color-v31/m3_ref_palette_dynamic_neutral_variant24
        // on Brave 1.96.61: the popup menu terminal. In flat mode the container
        // hex equals the background, so the menu must come out black.
        val xml = """
            <selector>
                <item n1:color="@android:color/system_neutral2_600" n1:lStar="24.0"
                  xmlns:n1="http://schemas.android.com/apk/res/android" />
            </selector>
        """.trimIndent()

        val (out, replaced) = rewriteDarkLStarSelectors(xml, "#000000")
        assertEquals(1, replaced)
        assertTrue("""n1:color="#000000"""" in out)
    }

    @Test
    fun `vector fills match any namespace prefix`() {
        val xml = """
            <vector>
                <path n2:fillColor="#1e2029" n2:pathData="M0,0" />
            </vector>
        """.trimIndent()

        val (out, replaced) = rewriteVectorSurfaceFills(xml, "#000000")
        assertEquals(1, replaced)
        assertTrue("""n2:fillColor="#000000"""" in out)
    }

    @Test
    fun `window background ids resolve through attr references`() {
        // Nightly themes point windowBackground at ?attr/ instead of @color/
        // directly. Those ids must still land in baseNames, otherwise two-tier
        // mode paints the window with the surface color (issue #21).
        val dir = createTempDirectory("res").toFile()
        dir.resolve("values").mkdirs()
        dir.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="Theme.Night">
                    <item name="android:windowBackground">?APKTOOL_DUMMYVAL_0x7f05018f</item>
                    <item name="APKTOOL_DUMMYVAL_0x7f05018f">@color/APKTOOL_DUMMYVAL_0x7f070554</item>
                </style>
            </resources>
            """.trimIndent(),
        )

        val ids = collectWindowBackgroundIds(dir)
        assertTrue("APKTOOL_DUMMYVAL_0x7f070554" in ids)
    }

    @Test
    fun `attr resolution never consults unrelated themes`() {
        // Regression for issue #21 (dev.6): the old flat scan merged attr
        // assignments from every theme into one map with "last wins" over the
        // unordered File.listFiles(), so an unrelated theme's assignment for
        // the same attr name could win the coin flip and resolve the window
        // background to the wrong color id.
        val dir = createTempDirectory("res").toFile()
        dir.resolve("values").mkdirs()
        dir.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="Theme.App" parent="Base">
                    <item name="android:windowBackground">?my_attr</item>
                </style>
                <style name="Base">
                    <item name="my_attr">@color/right</item>
                </style>
                <style name="Unrelated">
                    <item name="my_attr">@color/wrong</item>
                </style>
            </resources>
            """.trimIndent(),
        )

        val ids = collectWindowBackgroundIds(dir)
        assertTrue("right" in ids, "attr resolves through the style's own parent chain")
        assertFalse("wrong" in ids, "an unrelated theme must never decide the resolution")
    }

    @Test
    fun `night theme inherits windowBackground through parent chain`() {
        // Mirrors Brave Nightly v1.99.40 (issue #21): the night theme declares
        // no items of its own, it just points at the day parent which sets
        // windowBackground to ?attr/ and assigns that attr to the night color.
        val dir = createTempDirectory("res").toFile()
        dir.resolve("values").mkdirs()
        dir.resolve("values-night").mkdirs()
        dir.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="Theme.Day">
                    <item name="android:windowBackground">?APKTOOL_DUMMYVAL_0x7f05018f</item>
                    <item name="APKTOOL_DUMMYVAL_0x7f05018f">@color/APKTOOL_DUMMYVAL_0x7f0702cf</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        dir.resolve("values-night/styles.xml").writeText(
            """
            <resources>
                <style name="Theme.Night" parent="@style/Theme.Day" />
            </resources>
            """.trimIndent(),
        )

        val ids = collectWindowBackgroundIds(dir)
        assertTrue(
            "APKTOOL_DUMMYVAL_0x7f0702cf" in ids,
            "the night theme resolves through its parent chain deterministically",
        )
    }

    @Test
    fun `window background attr refs are bypassed with a direct color`() {
        // ?attr/ in windowBackground resolves per-theme at runtime, which
        // static patching cannot see. Transparent windows also defeat the
        // two-tier separation. Layouts using the same attr for
        // android:background (e.g. Settings root) get the same treatment.
        // (issue #21).
        val dir = createTempDirectory("res").toFile()
        dir.resolve("values").mkdirs()
        dir.resolve("layout").mkdirs()
        dir.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="Theme.Night">
                    <item name="android:windowBackground">?APKTOOL_DUMMYVAL_0x7f05018f</item>
                    <item name="windowBackground">?android:colorBackground</item>
                    <item name="android:colorBackground">@android:color/transparent</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        dir.resolve("layout/settings.xml").writeText(
            """
            <CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:background="?APKTOOL_DUMMYVAL_0x7f05018f">
            </CoordinatorLayout>
            """.trimIndent(),
        )

        val count = replaceWindowBackgroundAttrRefs(dir)
        // 3 theme items + 1 layout background
        assertEquals(4, count)

        val stylesXml = dir.resolve("values/styles.xml").readText()
        assertFalse("?APKTOOL_DUMMYVAL_0x7f05018f" in stylesXml)
        assertFalse("?android:colorBackground" in stylesXml)
        assertFalse("@android:color/transparent" in stylesXml)

        val layoutXml = dir.resolve("layout/settings.xml").readText()
        assertFalse("?APKTOOL_DUMMYVAL_0x7f05018f" in layoutXml)
        assertTrue("@color/amoled_window_background" in layoutXml)

        // The bypass color is now collectible as a window background id.
        val ids = collectWindowBackgroundIds(dir)
        assertTrue("amoled_window_background" in ids)
    }

    @Test
    fun `obfuscated windowBackground attr is discovered from CoordinatorLayout`() {
        // Brave Nightly obfuscates all resource names, so the name-based
        // "windowBackground" lookup finds nothing. The fallback discovers
        // the attr ID from the Settings CoordinatorLayout root.
        // (issue #21, verified on 1.99.42).
        val dir = createTempDirectory("res-obf").toFile()
        dir.resolve("values").mkdirs()
        dir.resolve("layout").mkdirs()
        // Obfuscated theme: names are APKTOOL_DUMMYVAL_0x..., no match.
        dir.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="APKTOOL_DUMMYVAL_0x7f150000">
                    <item name="APKTOOL_DUMMYVAL_0x7f050000">?APKTOOL_DUMMYVAL_0x7f05018f</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        // Settings layout with obfuscated background attr ref.
        dir.resolve("layout/APKTOOL_DUMMYVAL_0x7f0f0000.xml").writeText(
            """
            <androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:n1="http://schemas.android.com/apk/res/android"
                n1:id="@id/settings_activity" n1:background="?APKTOOL_DUMMYVAL_0x7f05018f"
                n1:layout_width="fill_parent" n1:layout_height="fill_parent">
            </androidx.coordinatorlayout.widget.CoordinatorLayout>
            """.trimIndent(),
        )

        val count = replaceWindowBackgroundAttrRefs(dir)
        // 1 layout background (theme items don't match obfuscated names)
        assertEquals(1, count)

        val layoutXml = dir.resolve("layout/APKTOOL_DUMMYVAL_0x7f0f0000.xml").readText()
        assertFalse("?APKTOOL_DUMMYVAL_0x7f05018f" in layoutXml)
        assertTrue("@color/amoled_window_background" in layoutXml)
    }

    @Test
    fun `preference cards get grey rounded backgrounds`() {
        // In two-tier mode, preference items become grey rounded cards on the
        // black window background, for clear visual separation. (issue #21)
        val dir = createTempDirectory("res-cards").toFile()
        dir.resolve("layout").mkdirs()
        dir.resolve("drawable").mkdirs()
        // Preference item layout with transparent ripple background.
        dir.resolve("layout/pref_item.xml").writeText(
            """
            <LinearLayout xmlns:n1="http://schemas.android.com/apk/res/android"
                n1:background="?android:selectableItemBackground"
                n1:layout_width="fill_parent" n1:layout_height="wrap_content">
                <LinearLayout n1:id="@id/icon_frame" />
            </LinearLayout>
            """.trimIndent(),
        )

        val created = createPreferenceCardDrawable(dir, "#ff1e2029")
        assertTrue(created)
        val drawableFile = dir.resolve("drawable/amoled_preference_card.xml")
        assertTrue(drawableFile.exists())
        val drawableXml = drawableFile.readText()
        assertTrue("#1e2029" in drawableXml)
        assertTrue("16dp" in drawableXml)

        val count = applyPreferenceCardBackgrounds(dir)
        assertEquals(1, count)
        val layoutXml = dir.resolve("layout/pref_item.xml").readText()
        assertTrue("@drawable/amoled_preference_card" in layoutXml)
        assertFalse("?android:selectableItemBackground" in layoutXml)
        assertTrue("layout_marginStart" in layoutXml)
    }
}
