package app.morphe.patches.helium

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TitaniumAmoledThemePatchTest {
    @Test
    fun `option keys are bundle specific`() {
        assertEquals(
            setOf(
                "backgroundColor",
                "settingsBackgroundColor",
                "surfaceColor",
                "textColor",
                "accentColor",
                "pinMaterialYou",
            ),
            titaniumAmoledThemePatch.options.keys,
        )
    }

    @Test
    fun `text and accent matchers stay out of each other and of surfaces`() {
        // Chromium dark ink palette
        assertTrue(isTitaniumTextColor("#e3e3e3"))
        assertTrue(isTitaniumTextColor("#c7c7c7"))
        assertTrue(isTitaniumTextColor("#ffffff"))
        // Chromium accent family
        assertTrue(isTitaniumAccentColor("#8ab4f8"))
        assertTrue(isTitaniumAccentColor("#a8c7fa"))
        // Cross-talk must stay false
        assertFalse(isTitaniumTextColor("#8ab4f8"), "accent is not text")
        assertFalse(isTitaniumAccentColor("#e3e3e3"), "text is not accent")
        assertFalse(isTitaniumTextColor("#121212"), "surface is not text")
        assertFalse(isTitaniumAccentColor("#121212"), "surface is not accent")
        assertFalse(isTitaniumSurfaceColor("#e3e3e3"), "text is not surface")
        // Status colors stay
        assertFalse(isTitaniumAccentColor("#ff7654"), "error coral is not accent")
        assertFalse(isTitaniumAccentColor("#ff7f72"), "warning coral is not accent")
    }

    @Test
    fun `rewrite text keeps ink hierarchy and accent stays independent`() {
        val xml = """
            <resources>
                <color name="title">#e3e3e3</color>
                <color name="body">#c7c7c7</color>
                <color name="muted">#9aa0a6</color>
                <color name="link">#8ab4f8</color>
                <color name="chip">#a8c7fa</color>
                <color name="bg">#121212</color>
                <color name="err">#ff7654</color>
            </resources>
        """.trimIndent()
        val (textOut, textCount) = rewriteTitaniumTextColorXml(xml, "#ffffff")
        assertEquals(3, textCount)
        // Primary full, secondary ~81%, muted ~62% — not one flat color.
        assertTrue("""<color name="title">#ffffff</color>""" in textOut)
        assertTrue("""<color name="body">#cecece</color>""" in textOut)
        assertTrue("""<color name="muted">#9e9e9e</color>""" in textOut)
        assertTrue("""<color name="link">#8ab4f8</color>""" in textOut)
        assertTrue("""<color name="err">#ff7654</color>""" in textOut)

        val (accentOut, accentCount) = rewriteTitaniumAccentColorXml(xml, "#ff0000")
        assertEquals(2, accentCount)
        assertTrue("""<color name="link">#ff0000</color>""" in accentOut)
        assertTrue("""<color name="chip">#ff0000</color>""" in accentOut)
        assertTrue("""<color name="title">#e3e3e3</color>""" in accentOut)
        assertTrue("""<color name="err">#ff7654</color>""" in accentOut)
    }

    @Test
    fun `text tier factors preserve relative lightness`() {
        assertEquals(1.0f, titaniumTextTierFactor(TitaniumRgbColor(255, 255, 255, 255)))
        assertEquals(0.81f, titaniumTextTierFactor(TitaniumRgbColor(207, 207, 207, 255)))
        assertEquals(0.62f, titaniumTextTierFactor(TitaniumRgbColor(156, 156, 156, 255)))
        assertEquals("#c6c6c6", scaleTitaniumHexByFactor("#ffffff", 0.78f))
    }

    @Test
    fun `hex parser accepts rgb rrggbb and aarrggbb`() {
        assertEquals(TitaniumRgbColor(0, 0, 0, 0xFF), parseTitaniumHexColor("#000"))
        assertEquals(TitaniumRgbColor(0x20, 0x21, 0x24, 0xFF), parseTitaniumHexColor("#202124"))
        assertEquals(TitaniumRgbColor(0x12, 0x13, 0x14, 0x80), parseTitaniumHexColor("#80121314"))
        assertEquals(TitaniumRgbColor(0x30, 0x30, 0x30, 0xFF), parseTitaniumHexColor("#ff303030"))
        assertEquals(null, parseTitaniumHexColor("black"))
        assertEquals(null, parseTitaniumHexColor("#12345"))
    }

    @Test
    fun `surface matcher catches Chromium darks and the screenshot hexes`() {
        // Chromium dark family (REPORT §4)
        assertTrue(isTitaniumSurfaceColor("#202124"))
        assertTrue(isTitaniumSurfaceColor("#28282a"))
        assertTrue(isTitaniumSurfaceColor("#303030"))
        assertTrue(isTitaniumSurfaceColor("#1f1f1f"))
        assertTrue(isTitaniumSurfaceColor("#ff303030"), "8-digit ARGB counts when opaque")
        // Screenshot-measured hexes above the maxChannel-80 ceiling enter
        // through the KNOWN set only.
        assertTrue(isTitaniumSurfaceColor("#2a2a2a"), "Settings background")
        assertTrue(isTitaniumSurfaceColor("#353535"), "bookmarks pill / toolbar")
        assertTrue(isTitaniumSurfaceColor("#393939"), "Settings cards / menu")
        assertTrue(isTitaniumSurfaceColor("#131313"), "bookmarks / tab-switcher bg")
        // The ceiling is NOT raised: near-misses stay out.
        assertFalse(isTitaniumSurfaceColor("#5e5e5e"), "near-miss divider must stay")
        assertFalse(isTitaniumSurfaceColor("#5f6368"), "near-miss must stay")
        assertFalse(isTitaniumSurfaceColor("#757575"), "near-miss must stay")
        assertFalse(isTitaniumSurfaceColor("#e3e3e3"), "light text must stay")
        assertFalse(isTitaniumSurfaceColor("#072542"), "dark blue accent must stay")
        assertFalse(isTitaniumSurfaceColor("#33f0f2ff"), "translucent overlay must stay")
        assertFalse(isTitaniumSurfaceColor("#00000000"), "transparent must stay")
        assertFalse(isTitaniumSurfaceColor("#8ab4f8"), "Chromium accent must stay")
    }

    @Test
    fun `tier assignment prefers name evidence then measured hexes then value`() {
        // Value rule alone: dark enough is background, brighter is surface.
        assertEquals(
            TitaniumTier.BACKGROUND,
            titaniumTierFor("plain", "#131313"),
        )
        assertEquals(
            TitaniumTier.SURFACE,
            titaniumTierFor("plain", "#303030"),
        )
        // The trap: Settings background #2a2a2a is brighter than the base
        // threshold, so the value rule alone would raise it. The measured-hex
        // map sends it to the settings tier.
        assertEquals(
            TitaniumTier.SETTINGS,
            titaniumTierFor("plain", "#2a2a2a"),
        )
        assertEquals(
            TitaniumTier.SURFACE,
            titaniumTierFor("plain", "#393939"),
        )
        assertEquals(
            TitaniumTier.SURFACE,
            titaniumTierFor("plain", "#353535"),
        )
        // Name evidence beats value: a window id holding a raised-looking
        // hex is still background, and a role-declared settings id holding a
        // surface-looking hex is still settings.
        assertEquals(
            TitaniumTier.BACKGROUND,
            titaniumTierFor("win", "#303030", baseNames = setOf("win")),
        )
        assertEquals(
            TitaniumTier.SETTINGS,
            titaniumTierFor("set", "#393939", settingsNames = setOf("set")),
        )
        assertEquals(
            TitaniumTier.SURFACE,
            titaniumTierFor("card", "#121212", containerNames = setOf("card")),
        )
        // Non-surfaces have no tier.
        assertEquals(null, titaniumTierFor("plain", "#e3e3e3"))
        assertEquals(null, titaniumTierFor("plain", "#8ab4f8"))
    }

    @Test
    fun `alias chains expand to the literal id`() {
        val xml = """
            <resources>
                <color name="alias">#ff303030</color>
                <color name="mid">@color/alias</color>
                <color name="top">@color/mid</color>
                <color name="other">#ffffff</color>
            </resources>
        """.trimIndent()
        val expanded = expandTitaniumColorAliases(xml, setOf("top"))
        assertEquals(setOf("top", "mid", "alias"), expanded)
        // A literal start is its own expansion; unknown names pass through.
        assertEquals(setOf("alias"), expandTitaniumColorAliases(xml, setOf("alias")))
        assertEquals(setOf("ghost"), expandTitaniumColorAliases(xml, setOf("ghost")))
        // The resolver follows the same chain to the literal value.
        val values = mapOf("top" to "@color/mid", "mid" to "@color/alias", "alias" to "#ff303030")
        assertEquals("#ff303030", resolveTitaniumColorAlias(values, "top"))
        assertEquals(null, resolveTitaniumColorAlias(values, "ghost"))
    }

    @Test
    fun `window background literal behind two aliases lands in base names`() {
        // Titanium 157: styles -> @color/alias -> @color/lit -> #ff303030.
        // Without alias expansion the literal falls through to the value
        // heuristic (maxChannel 48) and gets painted as a surface.
        val dir = createTempDirectory("res-alias").toFile()
        dir.resolve("values").mkdirs()
        dir.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="Theme.Dark">
                    <item name="android:windowBackground">@color/alias</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        dir.resolve("values/colors.xml").writeText(
            """
            <resources>
                <color name="alias">@color/lit</color>
                <color name="lit">#ff303030</color>
            </resources>
            """.trimIndent(),
        )
        val collected = collectTitaniumWindowBackgroundIds(dir)
        assertTrue("alias" in collected)
        val expanded = expandTitaniumColorAliases(
            dir.resolve("values/colors.xml").readText(),
            collected,
        )
        assertTrue("lit" in expanded, "the literal id must join the base set")
        assertEquals(TitaniumTier.BACKGROUND, titaniumTierFor("lit", "#ff303030", baseNames = expanded))
    }

    @Test
    fun `three tier sweep splits window settings and raised fills`() {
        val xml = """
            <resources>
                <color name="window_bg">#121212</color>
                <color name="settings_bg">#2a2a2a</color>
                <color name="card">#393939</color>
                <color name="pill">#353535</color>
                <color name="bookmarks_bg">#131313</color>
                <color name="accent">#8ab4f8</color>
                <color name="text">#e3e3e3</color>
            </resources>
        """.trimIndent()
        val (out, counts) = rewriteTitaniumSurfaceTiers(
            xml = xml,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
        )
        assertEquals(TitaniumSurfaceTierCounts(background = 2, settings = 1, container = 2), counts)
        assertTrue("""<color name="window_bg">#000000</color>""" in out)
        assertTrue("""<color name="settings_bg">#121212</color>""" in out)
        assertTrue("""<color name="card">#1a1a1a</color>""" in out)
        assertTrue("""<color name="pill">#1a1a1a</color>""" in out)
        assertTrue("""<color name="bookmarks_bg">#000000</color>""" in out)
        assertTrue("""<color name="accent">#8ab4f8</color>""" in out)
        assertTrue("""<color name="text">#e3e3e3</color>""" in out)
    }

    @Test
    fun `role evidence overrides the value heuristic for every tier`() {
        val xml = """
            <resources>
                <color name="win">#303030</color>
                <color name="set">#393939</color>
                <color name="card">#121212</color>
            </resources>
        """.trimIndent()
        val (out, counts) = rewriteTitaniumSurfaceTiers(
            xml = xml,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
            settingsNames = setOf("set"),
            containerNames = setOf("card"),
            baseNames = setOf("win"),
        )
        assertEquals(TitaniumSurfaceTierCounts(background = 1, settings = 1, container = 1), counts)
        assertTrue("""<color name="win">#000000</color>""" in out)
        assertTrue("""<color name="set">#121212</color>""" in out)
        assertTrue("""<color name="card">#1a1a1a</color>""" in out)
    }

    @Test
    fun `material you rewrite sends each role to its own tier`() {
        val xml = """
            <resources>
                <color name="bg">@android:color/system_background_dark</color>
                <color name="surf">@android:color/system_surface_dark</color>
                <color name="high">@android:color/system_surface_container_high_dark</color>
                <color name="highest">@android:color/system_surface_container_highest_dark</color>
                <color name="bright">@android:color/system_surface_bright_dark</color>
                <color name="neutral">@android:color/system_neutral1_900</color>
                <color name="accent">@android:color/system_accent1_200</color>
            </resources>
        """.trimIndent()
        val (out, count) = rewriteTitaniumMaterialYouDarkNeutrals(
            xml = xml,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
        )
        assertEquals(6, count)
        assertTrue("""<color name="bg">#000000</color>""" in out)
        assertTrue("""<color name="surf">#000000</color>""" in out)
        assertTrue("""<color name="high">#121212</color>""" in out)
        assertTrue("""<color name="highest">#1a1a1a</color>""" in out)
        assertTrue("""<color name="bright">#1a1a1a</color>""" in out)
        assertTrue("""<color name="neutral">#000000</color>""" in out)
        assertTrue("""<color name="accent">@android:color/system_accent1_200</color>""" in out)
    }

    @Test
    fun `material you role files map ids to their tier`() {
        val xml = """
            <resources>
                <color name="a">@android:color/system_surface_dark</color>
                <color name="b">@android:color/system_surface_container_high_dark</color>
                <color name="c">@android:color/system_surface_container_highest_dark</color>
                <color name="d">@android:color/system_neutral2_800</color>
                <color name="e">@android:color/system_accent1_200</color>
            </resources>
        """.trimIndent()
        val tiers = collectTitaniumMaterialYouRoleTiers(xml)
        assertEquals(
            mapOf(
                "a" to TitaniumTier.BACKGROUND,
                "b" to TitaniumTier.SETTINGS,
                "c" to TitaniumTier.SURFACE,
                "d" to TitaniumTier.BACKGROUND,
            ),
            tiers,
        )
    }

    @Test
    fun `material you regex covers the api34 surface ladder but not ink`() {
        // v31 neutral tones
        assertTrue(
            TITANIUM_MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches("@android:color/system_neutral1_900"),
        )
        assertTrue(
            TITANIUM_MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches("@android:color/system_neutral2_700"),
        )
        // v34 surface ladder
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
            assertTrue(
                TITANIUM_MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches("@android:color/$role"),
                "$role must match",
            )
        }
        // containerHigh splits out of the container ladder.
        assertTrue(
            TITANIUM_SETTINGS_ROLES.matches("@android:color/system_surface_container_high_dark"),
        )
        assertFalse(
            TITANIUM_SETTINGS_ROLES.matches("@android:color/system_surface_container_highest_dark"),
        )
        // Ink roles never match.
        assertFalse(
            TITANIUM_MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches("@android:color/system_on_surface_dark"),
        )
        assertFalse(
            TITANIUM_MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches("@android:color/system_primary_dark"),
        )
    }

    @Test
    fun `low lstar selectors split by role and by tone`() {
        val xml = """
            <selector>
                <item n1:color="@android:color/system_neutral2_600" n1:lStar="6.0"
                  xmlns:n1="http://schemas.android.com/apk/res/android" />
                <item n1:color="@android:color/system_neutral2_600" n1:lStar="12.0"
                  xmlns:n1="http://schemas.android.com/apk/res/android" />
                <item n1:color="@android:color/system_neutral2_600" n1:lStar="17.0"
                  xmlns:n1="http://schemas.android.com/apk/res/android" />
                <item n1:color="@android:color/system_neutral2_600" n1:lStar="24.0"
                  xmlns:n1="http://schemas.android.com/apk/res/android" />
                <item n1:color="@android:color/system_neutral2_600" n1:lStar="30.0"
                  xmlns:n1="http://schemas.android.com/apk/res/android" />
            </selector>
        """.trimIndent()
        val (out, replaced) = rewriteTitaniumDarkLStarSelectors(
            xml = xml,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
        )
        // Tone 30 is above the ceiling and survives; the other four move.
        assertEquals(4, replaced)
        assertTrue("""n1:color="#000000"""" in out, "tone 6 is the window")
        assertTrue("""n1:color="#121212"""" in out, "tone 12 is the Settings step")
        assertTrue("""n1:color="#1a1a1a"""" in out, "tone 17 and 24 are raised")
        assertTrue("""n1:lStar="30.0"""" in out, "tone above the ceiling survives")
        assertFalse("""n1:lStar="6.0"""" in out, "rewritten selectors drop lStar")
    }

    @Test
    fun `lstar selectors match the patcher decode shape too, not only apktool`() {
        // The patcher's own decoder emits android: prefixes (and \r line
        // endings), not apktool's n0:. A regex that only knows n0: rewrites
        // zero selectors on the real APK while the apktool-shaped test passes.
        val xml = "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\r" +
            "  <item android:color=\"@android:color/system_neutral2_600\" android:lStar=\"6.0\" />\r" +
            "  <item android:color=\"@android:color/system_neutral2_600\" android:lStar=\"12.0\" />\r" +
            "  <item android:color=\"@android:color/system_neutral2_600\" android:lStar=\"30.0\" />\r" +
            "</selector>"
        val (out, replaced) = rewriteTitaniumDarkLStarSelectors(
            xml = xml,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
        )
        assertEquals(2, replaced)
        assertTrue("""android:color="#000000"""" in out, "tone 6 is the window")
        assertTrue("""android:color="#121212"""" in out, "tone 12 is the Settings step")
        assertTrue("""android:lStar="30.0"""" in out, "tone above the ceiling survives")
        assertFalse("""android:lStar="6.0"""" in out, "rewritten selectors drop lStar")
    }

    @Test
    fun `lstar role decides the tier even when the tone disagrees`() {
        val xml = """
            <selector>
                <item n0:color="@android:color/system_surface_container_high_dark" n0:lStar="6.0"
                  xmlns:n0="http://schemas.android.com/apk/res/android" />
                <item n0:color="@android:color/system_surface_dark" n0:lStar="24.0"
                  xmlns:n0="http://schemas.android.com/apk/res/android" />
            </selector>
        """.trimIndent()
        val (out, replaced) = rewriteTitaniumDarkLStarSelectors(
            xml = xml,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
        )
        assertEquals(2, replaced)
        assertTrue("""n0:color="#121212"""" in out, "containerHigh role wins over tone 6")
        assertTrue("""n0:color="#000000"""" in out, "base role wins over tone 24")
    }

    @Test
    fun `vector surface fills are rewritten but icons are not`() {
        val xml = """
            <vector>
                <path n2:fillColor="#2a2a2a" n2:pathData="M0,0" />
            </vector>
        """.trimIndent()
        val (out, replaced) = rewriteTitaniumVectorSurfaceFills(
            xml = xml,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
        )
        assertEquals(1, replaced)
        assertTrue("""n2:fillColor="#121212"""" in out, "a #2a2a2a panel is the Settings tier")

        // More than three paths means an icon sheet, never a panel.
        val icons = """
            <vector>
                <path n2:fillColor="#2a2a2a" n2:pathData="M0,0" />
                <path n2:fillColor="#2a2a2a" n2:pathData="M0,0" />
                <path n2:fillColor="#2a2a2a" n2:pathData="M0,0" />
                <path n2:fillColor="#2a2a2a" n2:pathData="M0,0" />
            </vector>
        """.trimIndent()
        val (_, iconReplaced) = rewriteTitaniumVectorSurfaceFills(
            xml = icons,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
        )
        assertEquals(0, iconReplaced)
    }

    @Test
    fun `vector fills match the patcher decode shape and skip no-ops`() {
        // Same prefix trap as the lStar selectors: the patcher decodes with
        // android:fillColor, apktool with n0:fillColor. And a fill already at
        // its tier target (#000000 here) is not a rewrite.
        val xml = """
            <vector>
                <path android:fillColor="#303030" android:pathData="M0,0" />
                <path android:fillColor="#000000" android:pathData="M1,1" />
            </vector>
        """.trimIndent()
        val (out, replaced) = rewriteTitaniumVectorSurfaceFills(
            xml = xml,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
        )
        assertEquals(1, replaced, "only the #303030 fill moves; #000000 is already there")
        assertTrue("""android:fillColor="#1a1a1a"""" in out)
        assertTrue("""android:fillColor="#000000"""" in out)
    }

    @Test
    fun `night v31 override builder keeps each tier on its own colour`() {
        val built = buildTitaniumNightV31Overrides(
            mapOf("win" to "#000000", "set" to "#121212", "card" to "#1a1a1a"),
        )
        assertTrue("""<color name="win">#000000</color>""" in built)
        assertTrue("""<color name="set">#121212</color>""" in built)
        assertTrue("""<color name="card">#1a1a1a</color>""" in built)
    }

    @Test
    fun `apply rewrites day roles and drawables while night stays indirection`() {
        val res = createTempDirectory("titanium-amoled-res").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values-v31").mkdirs()
        res.resolve("drawable-night").mkdirs()
        // Day file mirrors Titanium 157: an alias chain behind the window
        // background, a Settings literal, a raised literal, light ink.
        res.resolve("values/colors.xml").writeText(
            """
            <resources>
                <color name="win_alias">@color/win_lit</color>
                <color name="win_lit">#ff303030</color>
                <color name="settings_bg">#2a2a2a</color>
                <color name="card">#393939</color>
                <color name="ink">#e3e3e3</color>
            </resources>
            """.trimIndent(),
        )
        // Night file holds no literals on Titanium: pure indirection.
        res.resolve("values-night/colors.xml").writeText(
            """
            <resources>
                <color name="night_win">@color/win_alias</color>
                <color name="night_set">@color/settings_bg</color>
                <color name="night_card">@color/card</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-v31/colors.xml").writeText(
            """
            <resources>
                <color name="role_bg">@android:color/system_neutral1_900</color>
                <color name="role_high">@android:color/system_surface_container_high_dark</color>
                <color name="role_top">@android:color/system_surface_container_highest_dark</color>
                <color name="role_accent">@android:color/system_accent1_200</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="Theme.Dark">
                    <item name="android:windowBackground">@color/win_alias</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        res.resolve("drawable-night/panel.xml").writeText(
            """
            <vector>
                <path n0:fillColor="#1f1f1f" n0:pathData="M0,0" />
            </vector>
            """.trimIndent(),
        )

        val result = applyTitaniumAmoledResources(
            resourceDirectory = res,
            backgroundHex = "#000000",
            settingsHex = "#121212",
            surfaceColorHex = "#1a1a1a",
        )
        // The window literal behind two aliases is background, not surface.
        assertEquals(1, result.dayColorsReplaced)
        assertEquals(1, result.daySettingsReplaced)
        assertEquals(1, result.dayContainerReplaced)
        // Night holds no literals, so its sweep is a no-op by construction.
        assertEquals(0, result.nightColorsReplaced)
        assertEquals(0, result.nightSettingsReplaced)
        assertEquals(0, result.nightContainerReplaced)
        assertEquals(3, result.v31MaterialYouReplaced)
        assertTrue(result.nightV31Overrides >= 1)
        assertEquals(1, result.drawableSurfaceFills)
        assertTrue(result.windowBackgroundIds >= 2, "alias + literal both collected")

        val day = res.resolve("values/colors.xml").readText()
        assertTrue("""<color name="win_lit">#000000</color>""" in day)
        assertTrue("""<color name="settings_bg">#121212</color>""" in day)
        assertTrue("""<color name="card">#1a1a1a</color>""" in day)
        assertTrue("""<color name="ink">#e3e3e3</color>""" in day)

        val v31 = res.resolve("values-v31/colors.xml").readText()
        assertTrue("""<color name="role_bg">#000000</color>""" in v31)
        assertTrue("""<color name="role_high">#121212</color>""" in v31)
        assertTrue("""<color name="role_top">#1a1a1a</color>""" in v31)
        assertTrue(
            """<color name="role_accent">@android:color/system_accent1_200</color>""" in v31,
        )

        // The night file itself is untouched (indirection preserved), but the
        // night-v31 override file pins each tier for API 31+.
        val night = res.resolve("values-night/colors.xml").readText()
        assertTrue("""<color name="night_win">@color/win_alias</color>""" in night)
        val nv31 = res.resolve("values-night-v31/colors.xml")
        assertTrue(nv31.isFile)
        val nv31Text = nv31.readText()
        assertTrue("""<color name="night_win">#000000</color>""" in nv31Text)
        assertTrue("""<color name="night_set">#121212</color>""" in nv31Text)
        assertTrue("""<color name="night_card">#1a1a1a</color>""" in nv31Text)

        val panel = res.resolve("drawable-night/panel.xml").readText()
        // #1f1f1f reads as a raised fill by value (maxChannel 31 > 28), so the
        // panel lands in the surface tier, not background.
        assertTrue("""n0:fillColor="#1a1a1a"""" in panel)
    }

    @Test
    fun `empty rewrite fails closed`() {
        val res = createTempDirectory("titanium-amoled-empty").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values/colors.xml").writeText(
            """
            <resources>
                <color name="light">#ffffff</color>
                <color name="accent">#8ab4f8</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-night/colors.xml").writeText(
            """
            <resources>
                <color name="night_light">@color/light</color>
            </resources>
            """.trimIndent(),
        )
        assertFailsWith<Exception> {
            applyTitaniumAmoledResources(res, "#000000", "#121212")
        }
    }

    @Test
    fun `missing night colors fails closed`() {
        val res = createTempDirectory("titanium-amoled-nonight").toFile()
        assertFailsWith<Exception> {
            applyTitaniumAmoledResources(res, "#000000", "#121212")
        }
    }

    @Test
    fun `keeping material you leaves the role files and selectors alone`() {
        val res = createTempDirectory("titanium-amoled-my").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values-v31").mkdirs()
        res.resolve("color-v31").mkdirs()

        res.resolve("values/colors.xml").writeText(
            """<resources><color name="window_bg">#121212</color></resources>""",
        )
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

        val result = applyTitaniumAmoledResources(
            res,
            "#000000",
            "#121212",
            pinMaterialYou = false,
        )

        assertEquals(0, result.v31MaterialYouReplaced)
        assertEquals(0, result.lStarSelectorsReplaced)
        assertEquals(v31, res.resolve("values-v31/colors.xml").readText())
        assertEquals(lstar, res.resolve("color-v31/dark_lstar.xml").readText())
        // Day literals are flattened in both modes.
        assertTrue(
            """<color name="window_bg">#000000</color>""" in
                res.resolve("values/colors.xml").readText(),
        )
        // The Material You role id must not be pinned back to the AMOLED hex.
        val nv31 = res.resolve("values-night-v31/colors.xml")
        assertFalse(nv31.isFile && """name="s"""" in nv31.readText())
    }

    @Test
    fun `text bearing ids are never flattened to background`() {
        val res = createTempDirectory("titanium-amoled-text").toFile()
        res.resolve("values").mkdirs()
        res.resolve("values-night").mkdirs()
        res.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="Theme.Dark">
                    <item name="android:textColorPrimary">@color/ink_dark</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values/colors.xml").writeText(
            """
            <resources>
                <color name="ink_dark">#212529</color>
                <color name="bg">#121212</color>
            </resources>
            """.trimIndent(),
        )
        res.resolve("values-night/colors.xml").writeText(
            """<resources><color name="bg">#121212</color></resources>""",
        )

        val result = applyTitaniumAmoledResources(
            res,
            "#000000",
            "#121212",
            textColorHex = "#e3e3e3",
        )
        // A dark text id is correct ink in light mode, so the value-only text
        // step (same shape as Brave's) leaves it alone; the night declaration
        // is what makes it readable on AMOLED. What must never happen is the
        // surface sweep flattening it to background.
        assertEquals(1, result.nightTextDeclared)
        val day = res.resolve("values/colors.xml").readText()
        assertTrue("""<color name="ink_dark">#212529</color>""" in day)
        assertTrue("""<color name="bg">#000000</color>""" in day)
        val night = res.resolve("values-night/colors.xml").readText()
        assertTrue("""<color name="ink_dark">#e3e3e3</color>""" in night)
    }

    @Test
    fun `night v31 overrides never re-assert text ids as background`() {
        val dayXml = """
            <resources>
                <color name="bg">#1f1f1f</color>
                <color name="ink">#212529</color>
            </resources>
        """.trimIndent()
        val nightXml = """
            <resources>
                <color name="night_bg">@color/bg</color>
            </resources>
        """.trimIndent()
        val overrides = collectTitaniumOverrideHexes(
            dayXml = dayXml,
            nightXml = nightXml,
            materialYouNames = emptyList(),
            roleTiers = emptyMap(),
            settingsNames = emptySet(),
            containerNames = emptySet(),
            baseNames = emptySet(),
            textBearing = setOf("ink"),
            backgroundHex = "#000000",
            settingsHex = "#121212",
            containerHex = "#1a1a1a",
        )
        assertTrue("bg" in overrides)
        assertTrue("night_bg" in overrides)
        assertFalse("ink" in overrides, "text ids must never be re-asserted as background")
        assertEquals("#1a1a1a", overrides["bg"], "#1f1f1f is a raised fill by value")
        assertEquals("#1a1a1a", overrides["night_bg"])
    }

    @Test
    fun `window background attr refs are bypassed with a direct color`() {
        val dir = createTempDirectory("titanium-res-attr").toFile()
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

        val count = replaceTitaniumWindowBackgroundAttrRefs(dir)
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
        val ids = collectTitaniumWindowBackgroundIds(dir)
        assertTrue("amoled_window_background" in ids)
    }

    @Test
    fun `normalize rejects translucent hex`() {
        assertEquals("#000000", normalizeTitaniumOpaqueHex("#000"))
        assertEquals("#0a0a0a", normalizeTitaniumOpaqueHex("#0A0A0A"))
        assertEquals(null, normalizeTitaniumOpaqueHex("#80121314"))
    }
}
