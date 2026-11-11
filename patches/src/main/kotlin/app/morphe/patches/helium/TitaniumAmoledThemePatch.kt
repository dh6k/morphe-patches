/*
 * Titanium AMOLED theme (issue #47).
 *
 * A resource-only patch: Titanium ships Chromium's stock dark theme and its
 * dark surfaces live in the app resources (the Chromium #202124 family) plus
 * the Material You system-role files, so the Brave AMOLED patch (#21) ports
 * over as a literal rewrite. There is NO bytecode half here: Titanium has no
 * `brave_android_dynamic_colors_enabled` pref key, no
 * `BraveAndroidDynamicColorsByDefault` flag and no `brave_*` string anywhere
 * in the decoded APK, so there is no dynamic-colors getter to force-false.
 * Pinning Material You is done by rewriting the v31/v34 role files
 * themselves (see `pinMaterialYou`).
 *
 * Three tiers, not flat black. The target palette is the Ungoogled Chromium
 * `ThemeOverlay.BrowserUI.PureBlack` palette (patches/Own/pure-black-theme.patch),
 * verified against the reporter's screenshots — the reference is NOT flat
 * #000000 everywhere:
 *   background  #000000  window backgrounds, NTP/bookmarks/tab-switcher bg
 *   settings    #121212  Settings background (Ungoogled containerHigh)
 *   surface     #1A1A1A  raised surfaces: cards, menus, pills, sheets
 *                               (Ungoogled containerBright / containerHighest)
 * The reporter's screenshots answer the flat-vs-two-tier question by example:
 * Ungoogled Settings is #121212 bg + #1a1a1a cards, its menu is #1a1a1a.
 *
 * How a tier is decided (in order — name evidence always beats value):
 *   1. window-background ids (from styles, alias-expanded) -> background.
 *      A window a step brighter than #000000 is exactly the mix-up the tiers
 *      exist to avoid, and the value heuristic cannot tell a #1f1f1f window
 *      from a #1f1f1f card.
 *   2. Material You role evidence: an id the v31/v34 files declare as a
 *      containerHigh role -> settings; any other container role -> surface;
 *      a base role -> background.
 *   3. Measured hexes (SCREENSHOTS.md, PIL region modes, JPEG noise +/- a
 *      few levels): #2a2a2a (Settings bg) -> settings, #353535 / #393939
 *      (bookmarks pill/toolbar, Settings cards, menu) -> surface, #131313
 *      (bookmarks / tab-switcher bg) -> background.
 *   4. Value heuristic: maxChannel <= 0x1c -> background, else surface.
 *
 * The trap in step 3/4: Titanium's Settings background #2a2a2a (maxChannel 42)
 * is BRIGHTER than Brave's base threshold (0x1c), so a naive two-tier
 * classifier puts it in the raised tier and the Settings page comes out
 * #1A1A1A instead of #121212. It is handled explicitly by the measured-hex
 * map, and by the containerHigh role evidence on API 31+.
 *
 * KNOWN-set decision (explicit): the ceiling stays at maxChannel 80, it is
 * NOT raised to 120. The near-misses #5e5e5e / #5f6368 / #757575 (maxChannel
 * 81-120) are most likely dividers / disabled states, and sweeping them in
 * would paint UI chrome that is not a surface. The three screenshot hexes
 * above the ceiling (#2a2a2a / #353535 / #393939) enter through the KNOWN set
 * only, surgically. Note they do NOT appear as literals in Titanium 157's
 * values/colors.xml at all (verified: zero occurrences in the whole decoded
 * res/ except #353535 in eight unrelated gradient drawables) — on the
 * reporter's device they arrive at runtime through the Material You system
 * roles, so the KNOWN entries are forward-protection for future builds plus
 * documentation, and the actual Settings-bg fix rides on the role-file,
 * lStar-selector and night-v31-override paths.
 *
 * The alias trap: Titanium's day file is 604/1089 @color aliases, and the
 * window background is two hops away — styles point at @color/0x7f070037,
 * which points at @color/0x7f07054f, which holds #ff303030. Brave's
 * collectWindowBackgroundIds only records the alias id, so the literal
 * would fall through to the value heuristic and land in the surface tier.
 * `expandColorAliases` follows every @color chain to its literal id and
 * adds it to the name sets, for window backgrounds AND role tiers.
 *
 * Material You default: pin the role files (default true). Deliberate
 * difference from Ungoogled, which leaves dynamic accents (colorPrimary)
 * alive — the pink #ffb3a3 / brown #332421 tints in the reference shots.
 * The reporter already uses theme colour #000000, so they see no tints
 * today; pinning removes dynamic colour entirely while the patch is
 * applied. Turning the option off leaves the role files and the low-lStar
 * selectors untouched, same shape as Brave's disableDynamicColors.
 *
 * Night-file rewrite is a no-op on Titanium: values-night/colors.xml holds
 * zero literals, every entry is @color/ indirection over the day file (plus
 * 4 @android:color refs). The sweep must bite via the day file + role files
 * + drawables, and the refuse-empty-rewrite guard counts day + role +
 * drawable + override rewrites (night counts are included in the formula
 * but are zero on 157, so they can never be the sole signal).
 *
 * The window-background bypass color is declared in BOTH files: #ff000000 in
 * values-night/colors.xml (dark) and the stock light window background
 * #ffffffff in values/colors.xml (light). The attr rewrite touches day
 * styles too, so a night-only declaration crashes light mode at
 * Activity.onCreate with Resources$NotFoundException — verified on a real
 * device (issue #47 follow-up, realme RMX3370, Android 17). The day
 * declaration is injected AFTER every sweep on purpose: #ffffffff reads as
 * light text ink, so the text rewrite would tint it to the text color if it
 * were present during the sweep; the text step also excludes the bypass id
 * by name so a re-run over an already-patched APK cannot tint it either.
 *
 * Known open item, NOT claimed as fixed: the light-grey "New tab" card
 * header #c6c6c6 in the tab switcher (SCREENSHOTS.md img9). It is most
 * likely the per-tab theme colour (Chromium TabThemeColorProvider), not a
 * static resource, so a resource patch probably cannot reach it. Needs a
 * real-device test; do not promise it.
 *
 * Not owned: web content force-dark (the "darken websites" checkbox),
 * Chromium ColorProvider / native .pak chrome (toolbar, omnibox and some
 * WebUI surfaces are painted there — same blind spot as Brave #21), NTP
 * theme collections, or a runtime color picker. The patch only recolors
 * Dark theme; it adds no theme toggle.
 */
package app.morphe.patches.helium

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.ResourcePatch
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.colorOption
import app.morphe.patcher.patch.resourcePatch
import java.io.File

internal const val TITANIUM_PACKAGE = "io.github.jqssun.helium"
internal const val TITANIUM_DAY_COLORS_PATH = "values/colors.xml"
internal const val TITANIUM_NIGHT_COLORS_PATH = "values-night/colors.xml"
internal const val TITANIUM_NIGHT_V31_COLORS_PATH = "values-night-v31/colors.xml"

/**
 * Stock light-mode window background (issue #47 follow-up: light-mode crash).
 * ChromeTabbedActivity's theme resolves windowBackground through ?attr to
 * #ffffff in light mode — attr 0x7f050184 -> @color/0x7f070228 -> #ffffff,
 * no night override — and the same theme's colorBackground is
 * @android:color/white. The bypass color `amoled_window_background` must be
 * declared in the DAY file with this value: the attr rewrite runs over
 * every styles.xml under a values-qualified directory INCLUDING the day
 * file, so in light mode the theme
 * resolves windowBackground to this id, and a night-only declaration is a
 * Resources$NotFoundException at Activity.onCreate (on device: id
 * 0x7f070825, the first id the patcher assigns past stock's max color id
 * 0x7f070824 on Titanium 157). It must NOT be black: a #000000 day
 * declaration would paint every light-mode window black.
 */
internal const val TITANIUM_LIGHT_WINDOW_BACKGROUND = "#ffffffff"

/** Chrome/Titanium dark surface literals seen on 157; heuristic still covers drift. */
internal val TITANIUM_KNOWN_DARK_SURFACE_HEXES = setOf(
    // Chromium dark family (REPORT §4: night-resolved + drawables).
    "#202124",
    "#28282a",
    "#303030",
    "#313336",
    "#3c4043",
    "#1f1f1f",
    "#1e1f20",
    "#131314",
    "#1b1b1c",
    "#282a2c",
    "#2d2e31",
    "#333537",
    "#37393d",
    "#28282b",
    "#373c42",
    "#2c3137",
    "#171c22",
    "#262628",
    "#292a2d",
    // Screenshot-measured (SCREENSHOTS.md §2, PIL region modes): above the
    // maxChannel-80 ceiling, admitted surgically through this set only.
    // #2a2a2a = Settings background, #353535 = bookmarks pill / toolbar /
    // tab-switcher header, #393939 = Settings cards / main menu.
    "#2a2a2a",
    "#353535",
    "#393939",
)

/** Three tiers of the Ungoogled reference palette. */
internal enum class TitaniumTier {
    BACKGROUND,
    SETTINGS,
    SURFACE,
}

/**
 * Screenshot-measured hexes and the tier they paint (SCREENSHOTS.md §2).
 * #131313 is also caught by the value rule (maxChannel 19 <= 0x1c); it is
 * listed here so the mapping is explicit, not accidental.
 */
internal val MEASURED_TIER_HEXES = mapOf(
    "#2a2a2a" to TitaniumTier.SETTINGS,
    "#353535" to TitaniumTier.SURFACE,
    "#393939" to TitaniumTier.SURFACE,
    "#131313" to TitaniumTier.BACKGROUND,
)

/**
 * Material You dark roles, split by what the role paints. Same shapes as
 * Brave's regexes — the roles are Material 3, not Brave-specific — except
 * the containerHigh role is split out: Ungoogled paints it #121212 (the
 * Settings tier), while the rest of the container ladder takes #1A1A1A.
 * containerHigh also matches MATERIAL_YOU_CONTAINER_ROLES, so the settings
 * check must always run first.
 */
internal val TITANIUM_SETTINGS_ROLES = Regex(
    pattern = """@android:color/system_surface_container_high_dark$""",
    option = RegexOption.IGNORE_CASE,
)

internal val TITANIUM_MATERIAL_YOU_BASE_ROLES = Regex(
    pattern = """@android:color/system_(?:neutral[123]_(?:700|800|900|1000)""" +
        """|(?:background|surface|surface_dim)_dark)$""",
    option = RegexOption.IGNORE_CASE,
)

internal val TITANIUM_MATERIAL_YOU_CONTAINER_ROLES = Regex(
    pattern = """@android:color/system_(?:surface_bright|surface_variant""" +
        """|surface_container(?:_low|_lowest|_high|_highest)?)_dark$""",
    option = RegexOption.IGNORE_CASE,
)

/** Both families, for callers that only need "is this a surface role at all". */
internal val TITANIUM_MATERIAL_YOU_DARK_NEUTRAL_ROLES = Regex(
    pattern = """@android:color/system_(?:neutral[123]_(?:700|800|900|1000)""" +
        """|(?:background|surface|surface_bright|surface_dim|surface_variant""" +
        """|surface_container(?:_low|_lowest|_high|_highest)?)_dark)$""",
    option = RegexOption.IGNORE_CASE,
)

/**
 * res/color-v31 selectors pin a dark neutral tone to an explicit low lStar so
 * Material You can tint it. lStar only applies to dynamic colors, so the value
 * falls back to the raw tone — a near-black grey. Rewriting the color and
 * dropping lStar is what actually forces AMOLED.
 *
 * The namespace prefix is NOT stable: apktool assigns n0:, n1:, n2:, … per file
 * in declaration order, so the prefix is captured and reused instead (issue #21
 * lesson: hardcoding n0: silently matched nothing and left the popup menu and
 * the settings window grey). It is worse than that on the real run: the
 * patcher's own decoder emits android: instead of n0: (verified on Titanium
 * 157 — files named color_0x…xml, <selector xmlns:android=…>, \r line
 * endings), so the pattern accepts android: too. A test that only covers the
 * apktool shape passes while the real run rewrites zero selectors.
 */
internal val TITANIUM_DARK_LSTAR_SELECTOR = Regex(
    // The prefix is n0:/n1:/… in an apktool CLI decode but android: in the
    // patcher's own decode (verified on Titanium 157: the patcher emits
    // <selector xmlns:android=…> with android:color / android:lStar, and
    // files named color_0x…xml). Both shapes must match or the real run
    // rewrites zero selectors while the apktool-shaped unit test passes.
    pattern = """<item\s+((?:n\d+)|android):color="(@android:color/system_[^"]+)"\s+\1:lStar="(\d+(?:\.\d+)?)"""",
    option = RegexOption.IGNORE_CASE,
)

/** Below this lStar a surface role is too dark to read text on. */
internal const val TITANIUM_DARK_SURFACE_LSTAR_CEILING = 24.0

/**
 * lStar tone ladder for bare palette tones (no named role to decide the tier):
 * tone 6 paints the window, tone 12 is the lowest raised step — the
 * containerHigh / Settings-background step — and tone 17 and up paint the
 * raised steps. [Inference] from the measured ladder: Titanium's selectors
 * use tones 12/17/22/24, and the rendered greys step #2a2a2a (Settings bg) ->
 * #353535/#393939 (pills/cards/menu), so 12 maps to the settings tier and
 * 17+ to the surface tier.
 */
internal const val TITANIUM_LSTAR_SETTINGS_MIN_TONE = 12.0
internal const val TITANIUM_LSTAR_SURFACE_MIN_TONE = 17.0

private val TITANIUM_HEX_COLOR = Regex("""^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$""")
private val TITANIUM_COLOR_ELEMENT = Regex(
    pattern = """<color\s+name="([^"]+)"\s*>([^<]+)</color>""",
)

internal data class TitaniumRgbColor(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int,
) {
    val maxChannel: Int get() = maxOf(r, g, b)
    val chroma: Int get() = maxChannel - minOf(r, g, b)
}

internal fun parseTitaniumHexColor(value: String): TitaniumRgbColor? {
    val trimmed = value.trim()
    if (!TITANIUM_HEX_COLOR.matches(trimmed)) return null
    val body = trimmed.substring(1)
    return when (body.length) {
        3 -> {
            val r = body[0].digitToInt(16) * 17
            val g = body[1].digitToInt(16) * 17
            val b = body[2].digitToInt(16) * 17
            TitaniumRgbColor(r, g, b, 0xFF)
        }
        6 -> TitaniumRgbColor(
            r = body.substring(0, 2).toInt(16),
            g = body.substring(2, 4).toInt(16),
            b = body.substring(4, 6).toInt(16),
            a = 0xFF,
        )
        8 -> TitaniumRgbColor(
            r = body.substring(2, 4).toInt(16),
            g = body.substring(4, 6).toInt(16),
            b = body.substring(6, 8).toInt(16),
            a = body.substring(0, 2).toInt(16),
        )
        else -> null
    }
}

internal fun normalizeTitaniumOpaqueHex(value: String): String? {
    val rgb = parseTitaniumHexColor(value) ?: return null
    if (rgb.a != 0xFF) return null
    return "#%02x%02x%02x".format(rgb.r, rgb.g, rgb.b).lowercase()
}

/**
 * Opaque low-chroma dark fills are chrome surfaces. Overlays (partial alpha),
 * accents (high chroma) and text (light) are left alone.
 *
 * The ceiling is deliberately NOT raised past 80: the near-misses #5e5e5e /
 * #5f6368 / #757575 (maxChannel 81-120) are most likely dividers / disabled
 * states, and the three screenshot hexes above the ceiling enter through
 * TITANIUM_KNOWN_DARK_SURFACE_HEXES only.
 */
internal fun isTitaniumSurfaceColor(value: String): Boolean {
    val normalized = normalizeTitaniumOpaqueHex(value) ?: return false
    val rgb = parseTitaniumHexColor(normalized) ?: return false
    if (rgb.chroma > 40) return false
    return rgb.maxChannel <= 80 || normalized.lowercase() in TITANIUM_KNOWN_DARK_SURFACE_HEXES
}

/** At or below this max channel a fill is window background, not a raised one. */
internal const val TITANIUM_BASE_SURFACE_MAX_CHANNEL = 0x1c

/**
 * The tier for one surface literal. Name evidence (window-background ids,
 * Material You role tiers) always beats the value heuristic; the measured
 * hexes beat the generic value rule. Returns null for non-surfaces.
 */
internal fun titaniumTierFor(
    name: String,
    raw: String,
    settingsNames: Set<String> = emptySet(),
    containerNames: Set<String> = emptySet(),
    baseNames: Set<String> = emptySet(),
): TitaniumTier? {
    if (!isTitaniumSurfaceColor(raw)) return null
    if (name in baseNames) return TitaniumTier.BACKGROUND
    if (name in settingsNames) return TitaniumTier.SETTINGS
    if (name in containerNames) return TitaniumTier.SURFACE
    val normalized = normalizeTitaniumOpaqueHex(raw) ?: return null
    MEASURED_TIER_HEXES[normalized]?.let { return it }
    val rgb = parseTitaniumHexColor(normalized) ?: return null
    return if (rgb.maxChannel <= TITANIUM_BASE_SURFACE_MAX_CHANNEL) {
        TitaniumTier.BACKGROUND
    } else {
        TitaniumTier.SURFACE
    }
}

/** Light low-chroma fills are text / icon ink (primary, secondary, muted). */
internal fun isTitaniumTextColor(value: String): Boolean {
    val normalized = normalizeTitaniumOpaqueHex(value) ?: return false
    if (isTitaniumSurfaceColor(normalized)) return false
    val rgb = parseTitaniumHexColor(normalized) ?: return false
    return rgb.chroma <= 40 && rgb.maxChannel >= 140
}

/**
 * Preserve the ink hierarchy when tinting text: primary stays full, secondary
 * and muted are scaled down the way #e3e3e3 -> #c7c7c7 already does.
 */
internal fun titaniumTextTierFactor(rgb: TitaniumRgbColor): Float = when {
    rgb.maxChannel >= 220 -> 1.0f
    rgb.maxChannel >= 180 -> 0.81f
    else -> 0.62f
}

internal fun scaleTitaniumHexByFactor(hex: String, factor: Float): String {
    val rgb = parseTitaniumHexColor(hex) ?: return hex
    fun ch(v: Int) = (v * factor).toInt().coerceIn(0, 255)
    return "#%02x%02x%02x".format(ch(rgb.r), ch(rgb.g), ch(rgb.b))
}

/**
 * Blue chromatic fills are the Chromium accent family (#8ab4f8 / #a8c7fa / …).
 * Status coral/orange/red is intentionally excluded.
 */
internal fun isTitaniumAccentColor(value: String): Boolean {
    val normalized = normalizeTitaniumOpaqueHex(value) ?: return false
    val rgb = parseTitaniumHexColor(normalized) ?: return false
    if (rgb.chroma < 50 || rgb.maxChannel < 100) return false
    // Blue: blue dominant over red, green sitting between them.
    return rgb.b >= rgb.g && rgb.g >= rgb.r - 30
}

private fun rewriteTitaniumHexGroup(
    xml: String,
    targetHex: String,
    matcher: (String) -> Boolean,
    excludeNames: Set<String> = emptySet(),
): Pair<String, Int> {
    val target = normalizeTitaniumOpaqueHex(targetHex)
        ?: throw PatchException("Color must be opaque hex: $targetHex")
    var replaced = 0
    val out = TITANIUM_COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        if (name in excludeNames) return@replace match.value
        if (matcher(raw) && normalizeTitaniumOpaqueHex(raw) != target) {
            replaced++
            """<color name="$name">$target</color>"""
        } else {
            match.value
        }
    }
    return out to replaced
}

/** How many ids each tier moved. */
internal data class TitaniumSurfaceTierCounts(
    val background: Int,
    val settings: Int,
    val container: Int,
)

/**
 * Three-tier sweep: window fills take [backgroundHex], Settings-background
 * fills take [settingsHex], raised fills take [containerHex]. [settingsNames]
 * / [containerNames] carry the ids a Material You role file already declared
 * in that tier and [baseNames] the ids styles paint the window with — all
 * three signals beat the value heuristic.
 *
 * A colour the patch cannot see the use of is judged by its value alone, and
 * values/colors.xml ships light-mode ink (#1c1c1d, #0d0f14, #202124) that
 * satisfies that test. Those ids are black-on-black once flattened, so callers
 * pass the ids known to be text in [excludeNames] and they are left alone.
 */
internal fun rewriteTitaniumSurfaceTiers(
    xml: String,
    backgroundHex: String,
    settingsHex: String,
    containerHex: String,
    settingsNames: Set<String> = emptySet(),
    containerNames: Set<String> = emptySet(),
    baseNames: Set<String> = emptySet(),
    excludeNames: Set<String> = emptySet(),
): Pair<String, TitaniumSurfaceTierCounts> {
    val background = normalizeTitaniumOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val settings = normalizeTitaniumOpaqueHex(settingsHex)
        ?: throw PatchException("Settings background must be an opaque hex: $settingsHex")
    val container = normalizeTitaniumOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")
    var baseCount = 0
    var settingsCount = 0
    var containerCount = 0
    val out = TITANIUM_COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        if (name in excludeNames) return@replace match.value
        val tier = titaniumTierFor(name, raw, settingsNames, containerNames, baseNames)
            ?: return@replace match.value
        val target = when (tier) {
            TitaniumTier.BACKGROUND -> background
            TitaniumTier.SETTINGS -> settings
            TitaniumTier.SURFACE -> container
        }
        if (normalizeTitaniumOpaqueHex(raw) == target) return@replace match.value
        when (tier) {
            TitaniumTier.BACKGROUND -> baseCount++
            TitaniumTier.SETTINGS -> settingsCount++
            TitaniumTier.SURFACE -> containerCount++
        }
        """<color name="$name">$target</color>"""
    }
    return out to TitaniumSurfaceTierCounts(
        background = baseCount,
        settings = settingsCount,
        container = containerCount,
    )
}

/**
 * The bypass color is never text ink, in either file: the day declaration
 * #ffffffff reads as light text, so without this exclusion the text step
 * would tint the light-mode window background to the text color on a
 * re-run over an already-patched APK.
 */
internal fun rewriteTitaniumTextColorXml(
    xml: String,
    textHex: String,
    excludeNames: Set<String> = emptySet(),
): Pair<String, Int> {
    val base = normalizeTitaniumOpaqueHex(textHex)
        ?: throw PatchException("Text color must be an opaque hex: $textHex")
    var replaced = 0
    val out = TITANIUM_COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        if (name in excludeNames) return@replace match.value
        val raw = match.groupValues[2].trim()
        val rgb = parseTitaniumHexColor(normalizeTitaniumOpaqueHex(raw) ?: "")
            ?.takeIf { isTitaniumTextColor(raw) }
            ?: return@replace match.value
        val target = scaleTitaniumHexByFactor(base, titaniumTextTierFactor(rgb))
        if (normalizeTitaniumOpaqueHex(raw) == target) {
            match.value
        } else {
            replaced++
            """<color name="$name">$target</color>"""
        }
    }
    return out to replaced
}

internal fun rewriteTitaniumAccentColorXml(xml: String, accentHex: String): Pair<String, Int> =
    rewriteTitaniumHexGroup(xml, accentHex, matcher = { isTitaniumAccentColor(it) })

/**
 * In-place: kill Material You dark roles so they cannot win over night-v31.
 * Base roles take [backgroundHex], the containerHigh role takes [settingsHex]
 * (the Settings tier), the rest of the container ladder takes [containerHex].
 * The settings check runs first: containerHigh also matches the container
 * regex.
 */
internal fun rewriteTitaniumMaterialYouDarkNeutrals(
    xml: String,
    backgroundHex: String,
    settingsHex: String,
    containerHex: String = backgroundHex,
): Pair<String, Int> {
    val background = normalizeTitaniumOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val settings = normalizeTitaniumOpaqueHex(settingsHex)
        ?: throw PatchException("Settings background must be an opaque hex: $settingsHex")
    val container = normalizeTitaniumOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")
    var replaced = 0
    val out = TITANIUM_COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        val target = when {
            TITANIUM_SETTINGS_ROLES.matches(raw) -> settings
            TITANIUM_MATERIAL_YOU_CONTAINER_ROLES.matches(raw) -> container
            TITANIUM_MATERIAL_YOU_BASE_ROLES.matches(raw) -> background
            else -> return@replace match.value
        }
        replaced++
        """<color name="$name">$target</color>"""
    }
    return out to replaced
}

internal fun collectTitaniumMaterialYouDarkNeutralNames(xml: String): List<String> =
    TITANIUM_COLOR_ELEMENT.findAll(xml)
        .filter { TITANIUM_MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches(it.groupValues[2].trim()) }
        .map { it.groupValues[1] }
        .toList()

/**
 * App ids a Material You role file points at a dark role, mapped to their
 * tier. This is the one tier signal that is read instead of guessed: the
 * v31/v34 files name the role outright, and the same id in values-night
 * paints that same surface.
 */
internal fun collectTitaniumMaterialYouRoleTiers(xml: String): Map<String, TitaniumTier> =
    TITANIUM_COLOR_ELEMENT.findAll(xml).mapNotNull { match ->
        val raw = match.groupValues[2].trim()
        when {
            TITANIUM_SETTINGS_ROLES.matches(raw) -> match.groupValues[1] to TitaniumTier.SETTINGS
            TITANIUM_MATERIAL_YOU_CONTAINER_ROLES.matches(raw) ->
                match.groupValues[1] to TitaniumTier.SURFACE
            TITANIUM_MATERIAL_YOU_BASE_ROLES.matches(raw) ->
                match.groupValues[1] to TitaniumTier.BACKGROUND
            else -> null
        }
    }.toMap()

/**
 * Follow @color/ alias chains in one colors.xml to the literal id at the end.
 * Titanium's day file is mostly aliases (604/1089 on 157), and the window
 * background is two hops away (styles -> @color/0x7f070037 ->
 * @color/0x7f07054f -> #ff303030), so a name set built from styles or role
 * files must be expanded this way or the literal id falls through to the
 * value heuristic and lands in the wrong tier.
 */
internal fun expandTitaniumColorAliases(
    xml: String,
    names: Set<String>,
): Set<String> {
    val values = TITANIUM_COLOR_ELEMENT.findAll(xml)
        .associate { it.groupValues[1] to it.groupValues[2].trim() }
    val out = linkedSetOf<String>()
    for (start in names) {
        var current: String? = start
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current)) {
            out += current
            val raw = values[current] ?: break
            current = if (raw.startsWith("@color/")) raw.removePrefix("@color/") else null
        }
    }
    return out
}

/** Resolve one @color/ chain in a colors.xml map to its literal value, if any. */
internal fun resolveTitaniumColorAlias(
    values: Map<String, String>,
    name: String,
): String? = resolveTitaniumColorAliasTarget(values, name)?.second

/**
 * Resolve one @color/ chain to the terminal id and its literal value. The
 * terminal id carries the name evidence: a window-background literal two hops
 * away is still a window background, and tiering by the pointing id's name
 * instead lets the value heuristic raise it to a surface (#ff303030 reads as
 * a raised fill by value alone). Callers that only need the value use
 * [resolveTitaniumColorAlias].
 */
internal fun resolveTitaniumColorAliasTarget(
    values: Map<String, String>,
    name: String,
): Pair<String, String>? {
    var current: String? = name
    val seen = mutableSetOf<String>()
    while (current != null && seen.add(current)) {
        val raw = values[current] ?: return null
        if (raw.startsWith("@color/")) {
            current = raw.removePrefix("@color/")
        } else {
            return current to raw
        }
    }
    return null
}

/**
 * Ids that values-night-v31 has to re-assert because a Material You role would
 * otherwise win over values-night on API 31+, each mapped to the hex of its own
 * tier so the override file cannot hand a raised id the background colour.
 *
 * Collected from the files as they were *before* the sweep. Reading them back
 * afterwards would count everything the sweep just wrote — including the ids it
 * flattened — and re-assert all of them, which puts the text ids back to black
 * on top of the values-night fix.
 *
 * Titanium difference from Brave: the night file holds no literals, only
 * @color/ indirection over the day file, so night entries are resolved
 * through the day map before tiering; a night id that points at a surface
 * literal is re-asserted under its own name.
 */
internal fun collectTitaniumOverrideHexes(
    dayXml: String,
    nightXml: String,
    materialYouNames: Collection<String>,
    roleTiers: Map<String, TitaniumTier>,
    settingsNames: Set<String>,
    containerNames: Set<String>,
    baseNames: Set<String>,
    textBearing: Set<String>,
    backgroundHex: String,
    settingsHex: String,
    containerHex: String,
): Map<String, String> {
    val background = normalizeTitaniumOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val settings = normalizeTitaniumOpaqueHex(settingsHex)
        ?: throw PatchException("Settings background must be an opaque hex: $settingsHex")
    val container = normalizeTitaniumOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")
    fun hexFor(tier: TitaniumTier) = when (tier) {
        TitaniumTier.BACKGROUND -> background
        TitaniumTier.SETTINGS -> settings
        TitaniumTier.SURFACE -> container
    }

    val dayValues = TITANIUM_COLOR_ELEMENT.findAll(dayXml)
        .associate { it.groupValues[1] to it.groupValues[2].trim() }
    val tiers = linkedMapOf<String, TitaniumTier>()
    // Day literals (and day aliases resolved to their literal).
    dayValues.forEach { (name, raw) ->
        if (name in textBearing) return@forEach
        if (name == "amoled_window_background") return@forEach
        val literal = if (raw.startsWith("@color/")) {
            resolveTitaniumColorAlias(dayValues, name)
        } else {
            raw
        } ?: return@forEach
        val tier = titaniumTierFor(name, literal, settingsNames, containerNames, baseNames)
            ?: return@forEach
        // A value already at its tier target needs no pin; counting it would
        // let a no-op override defeat the empty-rewrite guard.
        if (normalizeTitaniumOpaqueHex(literal) == hexFor(tier)) return@forEach
        tiers[name] = tier
    }
    // Night ids: resolve the indirection, keep the night id's own name — but
    // tier by the terminal day id, which carries the name evidence. A night id
    // pointing at the window-background literal must land in BACKGROUND even
    // though its own name says nothing and #303030 reads as a surface.
    // A night literal (e.g. the injected amoled_window_background) tiers
    // directly instead of resolving.
    TITANIUM_COLOR_ELEMENT.findAll(nightXml).forEach { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        if (name in textBearing) return@forEach
        if (name == "amoled_window_background") return@forEach
        val (tierName, literal) = if (raw.startsWith("@color/")) {
            resolveTitaniumColorAliasTarget(dayValues, raw.removePrefix("@color/"))
                ?: return@forEach
        } else {
            name to raw
        }
        if (tierName in textBearing) return@forEach
        val tier = titaniumTierFor(tierName, literal, settingsNames, containerNames, baseNames)
            ?: return@forEach
        if (normalizeTitaniumOpaqueHex(literal) == hexFor(tier)) return@forEach
        tiers[name] = tier
    }
    // Role ids carry their role tier outright.
    materialYouNames.forEach { name ->
        if (name in textBearing) return@forEach
        roleTiers[name]?.let { tiers[name] = it }
    }
    return tiers.toSortedMap().mapValues { (_, tier) -> hexFor(tier) }
}

private val TITANIUM_STYLE_OPEN = Regex("""<style\s+name="([^"]+)"([^>]*)>""")
private val TITANIUM_STYLE_PARENT_ATTR = Regex("""parent="([^"]+)"""")
private val TITANIUM_STYLE_ITEM = Regex("""<item\s+name="([^"]+)"[^>]*>\s*([^<]*?)\s*<""")

/** One <style> block parsed out of a values styles.xml / themes.xml. */
private data class TitaniumParsedStyle(
    val name: String,
    val parentName: String?,
    val items: Map<String, String>,
)

private val TITANIUM_WINDOW_BACKGROUND_ATTRS = setOf(
    "windowBackground",
    "android:windowBackground",
    "colorBackground",
    "android:colorBackground",
)

private fun parseTitaniumStyles(xml: String): List<TitaniumParsedStyle> {
    val out = mutableListOf<TitaniumParsedStyle>()
    for (m in TITANIUM_STYLE_OPEN.findAll(xml)) {
        val name = m.groupValues[1]
        val tagTail = m.groupValues[2]
        val selfClosing = tagTail.trimEnd().endsWith("/")
        val parentName = TITANIUM_STYLE_PARENT_ATTR.find(tagTail)?.groupValues?.get(1)
            ?.removePrefix("@style/")
            ?: name.substringBeforeLast('.', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
        val body = if (selfClosing) {
            ""
        } else {
            val end = xml.indexOf("</style>", m.range.last + 1)
            if (end < 0) "" else xml.substring(m.range.last + 1, end)
        }
        val items = TITANIUM_STYLE_ITEM.findAll(body)
            .associate { it.groupValues[1] to it.groupValues[2].trim() }
        out += TitaniumParsedStyle(name, parentName, items)
    }
    return out
}

/**
 * Ids styles paint the window with. These must never be raised: a window a step
 * brighter than the AMOLED background is exactly the flat-versus-elevated mix-up
 * the surface tier exists to avoid, and the value heuristic cannot tell a
 * #1f1f1f window from a #1f1f1f card.
 *
 * Handles direct @color/ references as well as indirect ?attr/ references.
 * ?attr/ is resolved the way Android does at runtime: against the parent chain
 * of the style that sets the window background. It is never resolved by merging
 * assignments from unrelated themes into one map — that made the dev.6
 * resolution depend on the unordered File.listFiles() order (issue #21), a coin
 * flip between the colors of different themes. Every lookup here is
 * chain-local, so no file iteration order can change the result.
 *
 * Note the ids returned here may be aliases; callers must run
 * expandTitaniumColorAliases on the result before sweeping (Titanium's window
 * background is two @color hops from its literal on 157).
 */
internal fun collectTitaniumWindowBackgroundIds(resourceDirectory: File): Set<String> {
    // Deterministic qualifier order throughout.
    val qualifierDirs = resourceDirectory.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .sortedBy { it.name }
    val byQualifier = mutableMapOf<String, Map<String, TitaniumParsedStyle>>()
    for (dir in qualifierDirs) {
        val styles = mutableMapOf<String, TitaniumParsedStyle>()
        dir.listFiles().orEmpty()
            .filter { it.isFile }
            .filter {
                it.name.equals("styles.xml", ignoreCase = true) ||
                    it.name.equals("themes.xml", ignoreCase = true)
            }
            .sortedBy { it.name }
            .forEach { file ->
                for (style in parseTitaniumStyles(file.readText())) styles[style.name] = style
            }
        byQualifier[dir.name] = styles
    }
    // Cross-qualifier parent fallback, deterministic: "values" first, then
    // alphabetical. Only used when the parent is not defined in the same
    // qualifier as the child.
    val byName = linkedMapOf<String, TitaniumParsedStyle>()
    for (dir in qualifierDirs) {
        for ((name, style) in byQualifier.getValue(dir.name).toSortedMap()) {
            byName.putIfAbsent(name, style)
        }
    }

    fun chain(qualifier: String, start: TitaniumParsedStyle): List<TitaniumParsedStyle> {
        val chain = mutableListOf<TitaniumParsedStyle>()
        val seen = mutableSetOf<String>()
        var q: String? = qualifier
        var s: TitaniumParsedStyle? = start
        while (s != null && seen.add(s.name)) {
            chain += s
            val parentName = s.parentName
            s = if (parentName == null) {
                null
            } else {
                val next = q?.let { byQualifier[it]?.get(parentName) } ?: byName[parentName]
                if (next != null) {
                    // Continue the walk in the qualifier where the parent was found.
                    q = byQualifier.entries.firstOrNull { it.value[parentName] === next }?.key ?: q
                }
                next
            }
        }
        return chain
    }

    fun resolveAttr(
        chain: List<TitaniumParsedStyle>,
        attrName: String,
        seen: MutableSet<String>,
    ): String? {
        if (!seen.add(attrName)) return null // ?attr/ cycle guard
        for (style in chain) {
            val raw = style.items[attrName] ?: continue
            return when {
                raw.startsWith("@color/") -> raw.removePrefix("@color/")
                raw.startsWith("?") ->
                    resolveAttr(chain, raw.removePrefix("?").removePrefix("attr/"), seen)
                else -> null // @android:color/, #hex, @drawable/: not a window color id
            }
        }
        return null
    }

    val out = linkedSetOf<String>()
    for (dir in qualifierDirs) {
        val qualifier = dir.name
        val styles = byQualifier.getValue(qualifier)
        for (styleName in styles.keys.sorted()) {
            val styleChain = chain(qualifier, styles.getValue(styleName))
            // Effective window background: the nearest definition up the chain,
            // like the framework resolves it for the active theme.
            val holder = styleChain.firstOrNull { style ->
                TITANIUM_WINDOW_BACKGROUND_ATTRS.any { it in style.items }
            } ?: continue
            for (attr in TITANIUM_WINDOW_BACKGROUND_ATTRS) {
                val raw = holder.items[attr] ?: continue
                when {
                    raw.startsWith("@color/") -> out += raw.removePrefix("@color/")
                    raw.startsWith("?") -> resolveAttr(
                        styleChain,
                        raw.removePrefix("?").removePrefix("attr/"),
                        mutableSetOf(),
                    )?.let { out += it }
                }
            }
        }
    }
    return out
}

/**
 * Replaces ?attr/ references and @android:color/transparent in
 * windowBackground/colorBackground items with a direct @color/ reference.
 * Theme attributes resolve at runtime per-theme, so static patching cannot
 * reliably determine which color they point to. Transparent windows also
 * defeat the tier separation (the View behind shows through instead of pure
 * black). By bypassing the indirection entirely, the tiers get a
 * deterministic window background.
 *
 * Also replaces android:background="?<windowAttr>" in layouts (e.g. the
 * Settings CoordinatorLayout root), which is the same indirection outside
 * of theme items.
 *
 * Returns the number of replacements made.
 *
 * Titanium difference from Brave: there is no hardcoded fallback attr id —
 * Brave's last-resort 0x7f05018f is a Brave Nightly id and means nothing here.
 * If neither the theme scan nor the CoordinatorLayout scan finds an attr,
 * the layout step is skipped instead of guessing.
 */
internal fun replaceTitaniumWindowBackgroundAttrRefs(resourceDirectory: File): Int {
    var count = 0
    val themePattern = Regex(
        """(<item\s+name="(?:android:)?(?:colorBackground|windowBackground)"[^>]*>\s*)(?:\?[^<]+|@android:color/transparent)(\s*</item>)"""
    )
    // Collect the attr ids used as window backgrounds, so layouts using the
    // same attr for android:background get the same treatment.
    val windowAttrs = mutableSetOf<String>()
    val attrIdPattern = Regex("""\?(?:[^"]*?)(0x[0-9a-fA-F]+)""")
    resourceDirectory.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .flatMap { it.listFiles().orEmpty().toList() }
        .filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
        .forEach { file ->
            val original = file.readText()
            // Collect attr ids before replacing.
            themePattern.findAll(original).forEach { m ->
                attrIdPattern.find(m.value)?.let { windowAttrs += it.groupValues[1] }
            }
            val replaced = themePattern.replace(original) { m ->
                count++
                "${m.groupValues[1]}@color/amoled_window_background${m.groupValues[2]}"
            }
            if (replaced != original) {
                file.writeText(replaced)
            }
        }
    // Replace android:background="?<windowAttr>" in layouts.
    // Fallback for obfuscated builds: resource names are obfuscated, so the
    // name-based collection above can find nothing. Discover the attr ID
    // directly from layouts instead — the Settings CoordinatorLayout root
    // uses a ?attr/ background.
    if (windowAttrs.isEmpty()) {
        val coordinatorBgPattern = Regex(
            """<androidx\.coordinatorlayout\.widget\.CoordinatorLayout[^>]*?\w+:background="\?[^"]*?(0x[0-9a-fA-F]+)[^"]*""""
        )
        resourceDirectory.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("layout") }
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
            .forEach { file ->
                coordinatorBgPattern.findAll(file.readText()).forEach { m ->
                    windowAttrs += m.groupValues[1]
                }
            }
    }
    if (windowAttrs.isNotEmpty()) {
        val layoutPattern = Regex(
            """(?:android:)?background="\?[^"]*?(?:${windowAttrs.joinToString("|")})[^"]*""""
        )
        // Handle namespace prefixes like n1:background="..."
        val prefixedPattern = Regex(
            """\w+:background="\?[^"]*?(?:${windowAttrs.joinToString("|")})[^"]*""""
        )
        resourceDirectory.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("layout") }
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
            .forEach { file ->
                val original = file.readText()
                var replaced = layoutPattern.replace(original) {
                    count++
                    """android:background="@color/amoled_window_background""""
                }
                replaced = prefixedPattern.replace(replaced) {
                    // Preserve the prefix (e.g. n1:background="...")
                    val prefix = it.value.substringBefore(":background")
                    count++
                    """$prefix:background="@color/amoled_window_background""""
                }
                if (replaced != original) {
                    file.writeText(replaced)
                }
            }
    }
    return count
}

private val TITANIUM_TEXT_COLOR_REF = Regex("""textColor[A-Za-z]*">@color/([^<]+)<""")

/**
 * Color names styles mark as text (textColorPrimary / Secondary / Hint / …).
 * Must scan every style qualifier: values-v31/styles.xml re-points the theme at
 * a different id set on Android 12+, and those ids resolve through the
 * res/color selector folders, so reading only values/ misses whatever that
 * qualifier swaps in.
 */
internal fun collectTitaniumTextColorRefs(stylesXml: String): List<String> =
    TITANIUM_TEXT_COLOR_REF.findAll(stylesXml).map { it.groupValues[1] }.distinct().toList()

internal fun collectTitaniumTextColorRefsFromDir(resourceDirectory: File): List<String> {
    val out = linkedSetOf<String>()
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .flatMap { it.listFiles().orEmpty().toList() }
        .filter { it.isFile && it.name.equals("styles.xml", ignoreCase = true) }
        .forEach { out += collectTitaniumTextColorRefs(it.readText()) }
    return out.toList()
}

private val TITANIUM_STYLE_TEXT_ITEM =
    Regex("""<item\s+name="(?:android:)?text[A-Za-z]*"\s*>\s*@color/([^<]+)\s*<""")
private val TITANIUM_LAYOUT_TEXT_ATTR =
    Regex("""(?:android:|\bn\d+:)text[A-Za-z]*\s*=\s*"@color/([^"]+)"""")

/**
 * Colour ids the app actually renders text with, taken from structural evidence
 * rather than from the value itself: every `text*` item in every values styles
 * qualifier, plus every inline `text*` attribute in every layout. Decoded APKs
 * rename the framework namespace to n0: and library namespaces to n1:, n2:, …
 * so both spellings have to be matched.
 *
 * These ids must never be flattened to the background, even when their light-mode
 * value looks exactly like a dark chrome surface.
 */
internal fun collectTitaniumTextBearingIds(resourceDirectory: File): Set<String> {
    val out = linkedSetOf<String>()
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .flatMap { it.listFiles().orEmpty().toList() }
        .filter { it.isFile && it.name.equals("styles.xml", ignoreCase = true) }
        .forEach { file ->
            TITANIUM_STYLE_TEXT_ITEM.findAll(file.readText()).forEach { out += it.groupValues[1] }
        }
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("layout") }
        .forEach { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                .forEach { file ->
                    TITANIUM_LAYOUT_TEXT_ATTR.findAll(file.readText())
                        .forEach { out += it.groupValues[1] }
                }
        }
    return out
}

/** Day ink used when an undefined text id must be re-declared for light mode. */
internal const val TITANIUM_DAY_INK = "#202124"

/** Colour ids backed by a res/color selector folder rather than a <color> element. */
internal fun titaniumSelectorColorNames(resourceDirectory: File): Set<String> {
    val names = linkedSetOf<String>()
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("color") }
        .forEach { dir ->
            dir.listFiles().orEmpty()
                .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                .forEach { names += it.nameWithoutExtension }
        }
    return names
}

/**
 * Every directory that can hold a color definition: values XML plus the
 * res/color selector folders. A `<color name="X">` next to an existing selector
 * file is a duplicate resource and breaks the build, so both forms have to be
 * checked before declaring anything.
 */
internal fun titaniumDeclaredColorNames(resourceDirectory: File): Set<String> {
    val names = linkedSetOf<String>()
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .flatMap { it.listFiles().orEmpty().toList() }
        .filter { it.isFile && it.name.equals("colors.xml", ignoreCase = true) }
        .forEach { file ->
            TITANIUM_COLOR_ELEMENT.findAll(file.readText()).forEach { names += it.groupValues[1] }
        }
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("color") }
        .forEach { dir ->
            dir.listFiles().orEmpty()
                .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                .forEach { names += it.nameWithoutExtension }
        }
    return names
}

/**
 * Only ids that resolve to nothing at all get a new <color>. Anything already
 * backed by a selector file is left untouched — declaring it again is what
 * turned every screen's text black on AMOLED in the first place.
 */
internal fun declareTitaniumMissingTextColors(
    xml: String,
    textNames: Collection<String>,
    declared: Set<String>,
    hex: String,
): Pair<String, Int> {
    val target = normalizeTitaniumOpaqueHex(hex)
        ?: throw PatchException("Text color must be an opaque hex: $hex")
    val missing = textNames.filter { it !in declared }.toSortedSet()
    if (missing.isEmpty()) return xml to 0

    val inject = missing.joinToString("\n") { name -> """    <color name="$name">$target</color>""" }
    val out = if (xml.contains("</resources>")) {
        xml.replace("</resources>", "$inject\n</resources>")
    } else {
        xml + "\n<resources>\n$inject\n</resources>\n"
    }
    return out to missing.size
}

/** Colour ids declared by one specific values qualifier. */
internal fun titaniumDeclaredColorNamesIn(file: File): Set<String> {
    if (!file.isFile) return emptySet()
    return TITANIUM_COLOR_ELEMENT.findAll(file.readText()).map { it.groupValues[1] }.toSet()
}

/**
 * Text ids whose light-mode value is too dark to read on AMOLED. Only these need
 * a night declaration.
 */
internal fun titaniumDarkTextIdsInDay(xml: String, textIds: Collection<String>): List<String> {
    val dark = TITANIUM_COLOR_ELEMENT.findAll(xml)
        .filter { it.groupValues[1] in textIds }
        .filter { isTitaniumSurfaceColor(it.groupValues[2].trim()) }
        .map { it.groupValues[1] }
        .toSet()
    return dark.toList()
}

/**
 * Text ids that have a light-mode colour but no dark-mode counterpart keep that
 * light (dark) value in dark mode, where it is black on black. Declaring the same
 * id again under values-night is an override, not a duplicate — the id already
 * resolves from values/, so no selector file backs it and merge stays happy.
 *
 * Ids that do resolve through a selector folder are skipped: a <color> beside a
 * selector file is the duplicate that blacked out AMOLED text.
 */
internal fun declareTitaniumNightTextColors(
    xml: String,
    textNames: Collection<String>,
    declaredInNight: Set<String>,
    hex: String,
    resolvedElsewhere: Set<String> = emptySet(),
): Pair<String, Int> {
    val target = normalizeTitaniumOpaqueHex(hex)
        ?: throw PatchException("Text color must be an opaque hex: $hex")
    // An id already defined in this file, or backed by a selector folder, is not
    // re-declared: a <color> beside a selector file is a duplicate resource.
    val alreadyHere = TITANIUM_COLOR_ELEMENT.findAll(xml).map { it.groupValues[1] }.toSet()
    val missing = textNames
        .filter { it !in declaredInNight && it !in alreadyHere && it !in resolvedElsewhere }
        .toSortedSet()
    if (missing.isEmpty()) return xml to 0

    val inject = missing.joinToString("\n") { name -> """    <color name="$name">$target</color>""" }
    val out = if (xml.contains("</resources>")) {
        xml.replace("</resources>", "$inject\n</resources>")
    } else {
        xml + "\n<resources>\n$inject\n</resources>\n"
    }
    return out to missing.size
}

/**
 * A res/color-v31 selector that pins a tone to a very low lStar is a dark
 * surface. Point it at the colour of its tier and drop lStar, which only exists
 * to let Material You re-tint the tone. Only the two attributes are touched so
 * the xmlns:n0 declaration on the same tag survives.
 *
 * These selectors are how the popup menu, the app bar and the window background
 * are painted on Android 12/12L, and every one of them aliases the same
 * `system_neutral2_600` palette tone — so the role lookup finds nothing and the
 * tone has to decide the tier: tone 6 paints the window, tone 12 the Settings
 * step, tone 17 and up the raised steps.
 */
internal fun rewriteTitaniumDarkLStarSelectors(
    xml: String,
    backgroundHex: String,
    settingsHex: String,
    containerHex: String = backgroundHex,
): Pair<String, Int> {
    val background = normalizeTitaniumOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val settings = normalizeTitaniumOpaqueHex(settingsHex)
        ?: throw PatchException("Settings background must be an opaque hex: $settingsHex")
    val container = normalizeTitaniumOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")
    var replaced = 0
    val out = TITANIUM_DARK_LSTAR_SELECTOR.replace(xml) { match ->
        val prefix = match.groupValues[1]
        val role = match.groupValues[2]
        val tone = match.groupValues[3].toDoubleOrNull() ?: return@replace match.value
        if (tone > TITANIUM_DARK_SURFACE_LSTAR_CEILING) return@replace match.value
        // A named role decides the tier outright. A bare palette tone carries no
        // name, so the tone is the only evidence left.
        val target = when {
            TITANIUM_SETTINGS_ROLES.matches(role) -> settings
            TITANIUM_MATERIAL_YOU_CONTAINER_ROLES.matches(role) -> container
            TITANIUM_MATERIAL_YOU_BASE_ROLES.matches(role) -> background
            tone >= TITANIUM_LSTAR_SURFACE_MIN_TONE -> container
            tone >= TITANIUM_LSTAR_SETTINGS_MIN_TONE -> settings
            else -> background
        }
        replaced++
        match.value
            .replace(Regex("""${Regex.escape(prefix)}:color="[^"]*""""), """$prefix:color="$target"""")
            .replace(Regex("""\s+${Regex.escape(prefix)}:lStar="[^"]*""""), "")
    }
    return out to replaced
}

private val TITANIUM_VECTOR_PATH = Regex("""<path\b""")

private val TITANIUM_VECTOR_FILL = Regex("((?:n\\d+|android):fillColor)=\"([^\"]+)\"")

/**
 * Vector panels (promo cards, sheets) hardcode the dark chrome fill instead of
 * pointing at a color resource. Rewriting them is limited to small path counts
 * so icons — whose fills are ink, not surfaces — are never touched. The tier
 * comes from the same measured-hex / value rule as the color sweep (vectors
 * carry no id, so there is no name evidence to consult).
 */
internal fun rewriteTitaniumVectorSurfaceFills(
    xml: String,
    backgroundHex: String,
    settingsHex: String,
    containerHex: String = backgroundHex,
): Pair<String, Int> {
    val background = normalizeTitaniumOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val settings = normalizeTitaniumOpaqueHex(settingsHex)
        ?: throw PatchException("Settings background must be an opaque hex: $settingsHex")
    val container = normalizeTitaniumOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")
    val pathCount = TITANIUM_VECTOR_PATH.findAll(xml).count()
    if (pathCount == 0 || pathCount > 3) return xml to 0
    var replaced = 0
    val out = TITANIUM_VECTOR_FILL.replace(xml) { match ->
        val raw = match.groupValues[2]
        val tier = titaniumTierFor("", raw) ?: return@replace match.value
        val target = when (tier) {
            TitaniumTier.BACKGROUND -> background
            TitaniumTier.SETTINGS -> settings
            TitaniumTier.SURFACE -> container
        }
        // A fill already at its tier target is not a rewrite; counting it
        // would inflate the log (Titanium ships many #000000 fills already).
        if (normalizeTitaniumOpaqueHex(raw) == target) return@replace match.value
        replaced++
        match.groupValues[1] + "=\"" + target + "\""
    }
    return out to replaced
}

internal fun buildTitaniumNightV31Overrides(overrides: Map<String, String>): String =
    buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<resources>")
        overrides.toSortedMap().forEach { (name, hex) ->
            val target = normalizeTitaniumOpaqueHex(hex)
                ?: throw PatchException("Override colour must be an opaque hex: $hex")
            appendLine("""    <color name="$name">$target</color>""")
        }
        appendLine("</resources>")
    }

/** Same colour for every id — the flat preset (unused by the three-tier patch). */
internal fun buildTitaniumNightV31Overrides(names: Collection<String>, hex: String): String {
    val target = normalizeTitaniumOpaqueHex(hex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $hex")
    return buildTitaniumNightV31Overrides(names.toSortedSet().associateWith { target })
}

/** Material You role files, oldest first so values-night-v31 wins. */
internal val TITANIUM_MATERIAL_YOU_COLORS_PATHS = listOf(
    "values-v31/colors.xml",
    "values-v34/colors.xml",
    "values-v35/colors.xml",
)

/**
 * Selector directories that can pin a surface to a low lStar. Discovered, not
 * hardcoded: qualifier sets like color-night-v8 exist on real builds and a
 * fixed list silently misses whatever apktool decodes under a new qualifier.
 */
internal fun titaniumSelectorDirs(resourceDirectory: File): List<String> =
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && (it.name == "color" || it.name.startsWith("color-")) }
        .map { it.name }
        .sorted()

internal fun applyTitaniumAmoledResources(
    resourceDirectory: File,
    backgroundHex: String,
    settingsHex: String,
    textColorHex: String? = null,
    accentColorHex: String? = null,
    pinMaterialYou: Boolean = true,
    surfaceColorHex: String? = null,
): TitaniumAmoledRewriteResult {
    val background = normalizeTitaniumOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val settings = normalizeTitaniumOpaqueHex(settingsHex)
        ?: throw PatchException("Settings background must be an opaque hex: $settingsHex")
    val container = normalizeTitaniumOpaqueHex(surfaceColorHex ?: "#1a1a1a")
        ?: throw PatchException(
            "Surface color must be an opaque hex, got: ${surfaceColorHex ?: "null"}",
        )
    val textRefs = collectTitaniumTextColorRefsFromDir(resourceDirectory)
    val declared = titaniumDeclaredColorNames(resourceDirectory)
    val textBearing = collectTitaniumTextBearingIds(resourceDirectory)
    // Read the role files before the sweep rewrites them: an id the app already
    // declares in a tier is the only tier signal that is not a guess.
    val roleTiers = mutableMapOf<String, TitaniumTier>()
    TITANIUM_MATERIAL_YOU_COLORS_PATHS.forEach { path ->
        val file = resourceDirectory.resolve(path)
        if (!file.isFile) return@forEach
        roleTiers += collectTitaniumMaterialYouRoleTiers(file.readText())
    }
    val settingsNames = roleTiers.filterValues { it == TitaniumTier.SETTINGS }.keys
    val containerNames = roleTiers.filterValues { it == TitaniumTier.SURFACE }.keys
    val roleBaseNames = roleTiers.filterValues { it == TitaniumTier.BACKGROUND }.keys

    // Three-tier mode always: first bypass ?attr/ indirection in
    // windowBackground (it resolves per-theme at runtime, which static
    // patching cannot see), then collect the ids. The bypass color is pure
    // black in the night resources.
    replaceTitaniumWindowBackgroundAttrRefs(resourceDirectory)
    val nightColorsFile = resourceDirectory.resolve(TITANIUM_NIGHT_COLORS_PATH)
    if (nightColorsFile.isFile) {
        val xml = nightColorsFile.readText()
        if (!xml.contains("amoled_window_background")) {
            val injected = xml.replace(
                "</resources>",
                "    <color name=\"amoled_window_background\">#ff000000</color>\n</resources>",
            )
            nightColorsFile.writeText(injected)
        }
    }
    // Window backgrounds are never raised, whatever their value looks like.
    // Role-declared background ids join them: a background role paints the
    // window class of surface, same as a windowBackground item.
    val baseNames = collectTitaniumWindowBackgroundIds(resourceDirectory) + roleBaseNames

    val dayFile = resourceDirectory.resolve(TITANIUM_DAY_COLORS_PATH)
    val nightFile = resourceDirectory.resolve(TITANIUM_NIGHT_COLORS_PATH)
    // Snapshot before the sweep: the night-v31 override set is built from what
    // the app shipped, not from what the sweep just wrote.
    val originalDayXml = if (dayFile.isFile) dayFile.readText() else ""
    val originalNightXml = if (nightFile.isFile) nightFile.readText() else ""

    // Titanium's day file is mostly @color aliases: expand every name set
    // through the chains, or the literal at the end of a chain falls through
    // to the value heuristic (on 157 the window background is two hops away
    // and holds #ff303030, which the heuristic would paint as a surface).
    val expandedBase = expandTitaniumColorAliases(originalDayXml, baseNames)
    val expandedSettings = expandTitaniumColorAliases(originalDayXml, settingsNames)
    val expandedContainer = expandTitaniumColorAliases(originalDayXml, containerNames)

    // Chrome windowBackground / colorBackground live as dark hexes in
    // values/colors.xml (#121212 / #ff303030 / …), not only in values-night.
    var dayReplaced = 0
    var daySettingsReplaced = 0
    var dayContainerReplaced = 0
    var dayTextReplaced = 0
    var dayAccentReplaced = 0
    var dayInkInjected = 0
    if (dayFile.isFile) {
        var xml = dayFile.readText()
        val (afterBg, bgTiers) = rewriteTitaniumSurfaceTiers(
            xml = xml,
            backgroundHex = background,
            settingsHex = settings,
            containerHex = container,
            settingsNames = expandedSettings,
            containerNames = expandedContainer,
            baseNames = expandedBase,
            excludeNames = textBearing,
        )
        xml = afterBg
        dayReplaced = bgTiers.background
        daySettingsReplaced = bgTiers.settings
        dayContainerReplaced = bgTiers.container
        if (textColorHex != null) {
            val (afterText, count) = rewriteTitaniumTextColorXml(
                xml,
                textColorHex,
                excludeNames = setOf("amoled_window_background"),
            )
            xml = afterText
            dayTextReplaced = count
            // Light mode keeps dark ink, so only declare what resolves to nothing.
            val (afterInject, injected) = declareTitaniumMissingTextColors(
                xml,
                textRefs,
                declared,
                TITANIUM_DAY_INK,
            )
            xml = afterInject
            dayInkInjected = injected
        }
        if (accentColorHex != null) {
            val (afterAccent, count) = rewriteTitaniumAccentColorXml(xml, accentColorHex)
            xml = afterAccent
            dayAccentReplaced = count
        }
        dayFile.writeText(xml)
    }

    if (!nightFile.isFile) {
        throw PatchException("Titanium night color resources not found: $TITANIUM_NIGHT_COLORS_PATH")
    }
    // On Titanium 157 this sweep is a no-op: values-night/colors.xml holds no
    // literals, only @color/ indirection. It still runs so a future build that
    // does ship night literals is covered.
    var nightXml = nightFile.readText()
    val (afterNightBg, nightTiers) = rewriteTitaniumSurfaceTiers(
        xml = nightXml,
        backgroundHex = background,
        settingsHex = settings,
        containerHex = container,
        settingsNames = expandedSettings,
        containerNames = expandedContainer,
        baseNames = expandedBase,
        excludeNames = textBearing,
    )
    nightXml = afterNightBg
    val nightReplaced = nightTiers.background
    val nightSettingsReplaced = nightTiers.settings
    val nightContainerReplaced = nightTiers.container
    var nightTextReplaced = 0
    var nightAccentReplaced = 0
    var nightInkDeclared = 0
    if (textColorHex != null) {
        val (afterText, count) = rewriteTitaniumTextColorXml(
            nightXml,
            textColorHex,
            excludeNames = setOf("amoled_window_background"),
        )
        nightXml = afterText
        nightTextReplaced = count
        // Ids the app renders text with but that only exist in the light config
        // would stay dark-on-dark here, so give night its own readable value.
        val (afterDeclare, declaredCount) = declareTitaniumNightTextColors(
            nightXml,
            titaniumDarkTextIdsInDay(originalDayXml, textBearing),
            titaniumDeclaredColorNamesIn(nightFile),
            textColorHex,
            // An id a selector folder already resolves must not gain a <color>
            // element — that pair is the duplicate that broke text before.
            titaniumSelectorColorNames(resourceDirectory),
        )
        nightXml = afterDeclare
        nightInkDeclared = declaredCount
    }
    if (accentColorHex != null) {
        val (afterAccent, count) = rewriteTitaniumAccentColorXml(nightXml, accentColorHex)
        nightXml = afterAccent
        nightAccentReplaced = count
    }
    nightFile.writeText(nightXml)

    // With Material You left on, the role files and the low-lStar selectors
    // are deliberately left alone: pinning them to the AMOLED hex is what
    // repaints the wallpaper palette. (Ungoogled leaves dynamic accents alive;
    // pinning is the stronger step — see the file header.)
    var materialYouReplaced = 0
    val materialYouNames = mutableListOf<String>()
    if (pinMaterialYou) {
        TITANIUM_MATERIAL_YOU_COLORS_PATHS.forEach { path ->
            val file = resourceDirectory.resolve(path)
            if (!file.isFile) return@forEach
            val xml = file.readText()
            materialYouNames += collectTitaniumMaterialYouDarkNeutralNames(xml)
            val (next, count) = rewriteTitaniumMaterialYouDarkNeutrals(
                xml,
                background,
                settings,
                container,
            )
            file.writeText(next)
            materialYouReplaced += count
        }
    }

    // Built from the pre-sweep snapshot: reading the rewritten files back would
    // re-assert every flattened id, including the text ones, as background.
    val overrides = collectTitaniumOverrideHexes(
        dayXml = originalDayXml,
        nightXml = originalNightXml,
        materialYouNames = materialYouNames,
        roleTiers = roleTiers,
        settingsNames = expandedSettings,
        containerNames = expandedContainer,
        baseNames = expandedBase,
        textBearing = textBearing,
        backgroundHex = background,
        settingsHex = settings,
        containerHex = container,
    )

    var lStarReplaced = 0
    if (pinMaterialYou) {
        titaniumSelectorDirs(resourceDirectory).forEach { dir ->
            val dirFile = resourceDirectory.resolve(dir)
            dirFile.listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                .forEach { file ->
                    val xml = file.readText()
                    val (next, count) = rewriteTitaniumDarkLStarSelectors(
                        xml,
                        background,
                        settings,
                        container,
                    )
                    if (count > 0) {
                        file.writeText(next)
                        lStarReplaced += count
                    }
                }
        }
    }

    var drawableFills = 0
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("drawable") }
        .forEach { dir ->
            dir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                .forEach { file ->
                    val xml = file.readText()
                    if (!xml.contains("<path")) return@forEach
                    val (next, count) = rewriteTitaniumVectorSurfaceFills(
                        xml,
                        background,
                        settings,
                        container,
                    )
                    if (count > 0) {
                        file.writeText(next)
                        drawableFills += count
                    }
                }
        }

    if (overrides.isNotEmpty()) {
        val nightV31 = resourceDirectory.resolve(TITANIUM_NIGHT_V31_COLORS_PATH)
        nightV31.parentFile?.mkdirs()
        nightV31.writeText(buildTitaniumNightV31Overrides(overrides))
    }

    // The bypass color must also be declared in the DAY file, with the stock
    // light-mode window background — NOT black. The attr rewrite above runs
    // over every values*/styles.xml INCLUDING the day file, so in light mode
    // the theme resolves windowBackground to this id; a night-only
    // declaration is a Resources$NotFoundException at Activity.onCreate
    // (verified on device: id 0x7f070825, the first id the patcher assigns
    // past stock's max color id 0x7f070824 on Titanium 157).
    //
    // Injected AFTER every sweep on purpose: #ffffffff reads as light text
    // ink, so the text rewrite would tint it to the text color if it were
    // present during the sweep (the text step also excludes this id by name,
    // so a re-run over an already-patched APK cannot tint it either). The
    // surface sweep cannot touch it either way — white is not a surface —
    // and the override collector skips this id by name. The injection is not
    // counted toward the empty-rewrite guard below: declaring the bypass
    // color alone must never defeat the fail-closed check.
    var dayWindowBackgroundDeclared = false
    if (dayFile.isFile) {
        val xml = dayFile.readText()
        if (!xml.contains("amoled_window_background")) {
            val injected = xml.replace(
                "</resources>",
                "    <color name=\"amoled_window_background\">$TITANIUM_LIGHT_WINDOW_BACKGROUND</color>\n</resources>",
            )
            dayFile.writeText(injected)
        }
        dayWindowBackgroundDeclared = true
    }

    // Fail closed on an empty rewrite. The night sweep is a no-op on Titanium
    // 157 (values-night holds no literals), so the guard counts day + role +
    // drawable + overrides — the same formula the patch execute block repeats
    // as a backstop. Overrides only count real pins: the collector skips the
    // injected amoled_window_background id and values already at their tier
    // target, so a no-op cannot defeat this check.
    val rewritten = dayReplaced + daySettingsReplaced + dayContainerReplaced +
        nightReplaced + nightSettingsReplaced + nightContainerReplaced +
        materialYouReplaced + lStarReplaced + drawableFills + overrides.size
    if (rewritten == 0) {
        throw PatchException(
            "No dark surface colors matched; refusing to ship an empty AMOLED rewrite",
        )
    }

    return TitaniumAmoledRewriteResult(
        dayColorsReplaced = dayReplaced,
        daySettingsReplaced = daySettingsReplaced,
        dayContainerReplaced = dayContainerReplaced,
        nightColorsReplaced = nightReplaced,
        nightSettingsReplaced = nightSettingsReplaced,
        nightContainerReplaced = nightContainerReplaced,
        v31MaterialYouReplaced = materialYouReplaced,
        nightV31Overrides = overrides.size,
        textColorsReplaced = dayTextReplaced + nightTextReplaced,
        accentColorsReplaced = dayAccentReplaced + nightAccentReplaced,
        textIdsInjected = dayInkInjected,
        nightTextDeclared = nightInkDeclared,
        textBearingIds = textBearing.size,
        lStarSelectorsReplaced = lStarReplaced,
        drawableSurfaceFills = drawableFills,
        windowBackgroundIds = expandedBase.size,
        dayWindowBackgroundDeclared = dayWindowBackgroundDeclared,
    )
}

internal data class TitaniumAmoledRewriteResult(
    val dayColorsReplaced: Int,
    val daySettingsReplaced: Int,
    val dayContainerReplaced: Int,
    val nightColorsReplaced: Int,
    val nightSettingsReplaced: Int,
    val nightContainerReplaced: Int,
    val v31MaterialYouReplaced: Int,
    val nightV31Overrides: Int,
    val textColorsReplaced: Int = 0,
    val accentColorsReplaced: Int = 0,
    val textIdsInjected: Int = 0,
    val nightTextDeclared: Int = 0,
    val textBearingIds: Int = 0,
    val lStarSelectorsReplaced: Int = 0,
    val drawableSurfaceFills: Int = 0,
    val windowBackgroundIds: Int = 0,
    /** The bypass color is declared in the day file (light-mode value). */
    val dayWindowBackgroundDeclared: Boolean = false,
)

private fun titaniumAmoledCompatibilities() = listOf(
    Compatibility(
        name = "Titanium Browser for Android",
        packageName = TITANIUM_PACKAGE,
        apkFileType = ApkFileType.APK,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
)

/**
 * Patch-time AMOLED dark theme (issue #47): rewrites Titanium dark chrome
 * surfaces to the three-tier Ungoogled reference palette — window backgrounds
 * #000000, Settings background #121212, raised surfaces (cards, menus, pills,
 * sheets) #1A1A1A — so the UI keeps the elevation step Material 3 uses
 * instead of one flat black. Optional text and accent colors (defaults keep
 * Chromium's #e3e3e3 / #8ab4f8). Pins the Material You system roles to the
 * AMOLED hexes by default (the wallpaper palette stops affecting the browser
 * while the patch is applied); turning that off leaves the role files and the
 * low-lStar selectors untouched, closer to Ungoogled, which leaves dynamic
 * accents alive. Resource-only: Titanium has no Brave-style dynamic-colors
 * getter to force-false, so there is no bytecode half. Apply Dark theme in
 * Titanium to see it. Does not change web content force-dark, does not reach
 * Chromium ColorProvider / native .pak chrome, and adds no runtime color
 * picker. Default off.
 */
@Suppress("unused")
val titaniumAmoledThemePatch: ResourcePatch = resourcePatch(
    name = "Titanium AMOLED theme",
    description = "Patch-time AMOLED dark theme (issue #47): rewrites Titanium dark chrome " +
        "surfaces to pure black (or custom opaque hexes) in three tiers — window " +
        "backgrounds #000000, Settings background #121212, raised surfaces #1A1A1A — " +
        "matching the Ungoogled Chromium pure-black reference. Pins Material You " +
        "dark roles to the AMOLED hexes by default; leaving that off keeps the " +
        "wallpaper palette. Resource-only, no bytecode. Apply Dark theme in " +
        "Titanium to see it. Does not change web content force-dark, does not " +
        "reach ColorProvider / native .pak chrome, and adds no runtime color " +
        "picker. Default off.",
    default = false,
) {
    compatibleWith(*titaniumAmoledCompatibilities().toTypedArray())

    val backgroundColor by colorOption(
        key = "backgroundColor",
        default = "#000000",
        title = "AMOLED background",
        description = "Opaque hex for window backgrounds and the rest of dark chrome. " +
            "Use #000000 for pure black OLED. Example elevated near-black: #0a0a0a.",
        required = true,
        validator = { value -> value == null || normalizeTitaniumOpaqueHex(value) != null },
    )

    val settingsBackgroundColor by colorOption(
        key = "settingsBackgroundColor",
        default = "#121212",
        title = "Settings background",
        description = "Opaque hex for the Settings page background (Ungoogled " +
            "containerHigh). #121212 matches the reference; use #000000 for flat black.",
        required = true,
        validator = { value -> value == null || normalizeTitaniumOpaqueHex(value) != null },
    )

    val surfaceColor by colorOption(
        key = "surfaceColor",
        default = "#1A1A1A",
        title = "Surface / button color",
        description = "Opaque hex for raised surfaces — buttons, cards, sheets, menu " +
            "panels and the Material You container ladder. #1A1A1A matches the " +
            "Ungoogled reference (containerBright / containerHighest).",
        required = false,
        validator = { value -> value == null || normalizeTitaniumOpaqueHex(value) != null },
    )

    val textColor by colorOption(
        key = "textColor",
        default = "#e3e3e3",
        title = "Text color",
        description = "Opaque hex for text and icon ink (primary/secondary/muted). " +
            "Chromium dark default: #e3e3e3.",
        required = true,
        validator = { value -> value == null || normalizeTitaniumOpaqueHex(value) != null },
    )

    val accentColor by colorOption(
        key = "accentColor",
        default = "#8ab4f8",
        title = "Accent color",
        description = "Opaque hex for Chromium accent (toggles, links, highlights). " +
            "Default: #8ab4f8. Status coral/orange is left alone.",
        required = true,
        validator = { value -> value == null || normalizeTitaniumOpaqueHex(value) != null },
    )

    val pinMaterialYou by booleanOption(
        key = "pinMaterialYou",
        default = true,
        title = "Pin Material You dark roles to black",
        description = "Rewrite the Material You system-role files (values-v31/v34/v35) " +
            "and the low-lStar selectors to the AMOLED hexes, so the wallpaper " +
            "palette stops affecting the browser. Turning this off leaves those " +
            "files untouched — closer to Ungoogled, which leaves dynamic accents " +
            "alive — but role-driven surfaces will stay grey.",
        required = false,
    )

    execute {
        val background = normalizeTitaniumOpaqueHex(backgroundColor ?: "#000000")
            ?: throw PatchException("Invalid AMOLED background color")
        val settings = normalizeTitaniumOpaqueHex(settingsBackgroundColor ?: "#121212")
            ?: throw PatchException("Invalid Settings background color")
        val surface = (surfaceColor as? String)
            ?.let { normalizeTitaniumOpaqueHex(it) }
        val text = (textColor as? String)
            ?.let { normalizeTitaniumOpaqueHex(it) }
        val accent = (accentColor as? String)
            ?.let { normalizeTitaniumOpaqueHex(it) }
        val res = get("res")
        if (!res.isDirectory) {
            throw PatchException("Decoded res/ directory not found")
        }
        val pin = (pinMaterialYou as? Boolean) ?: true
        // Patcher 2.3.0 decodes resources on request: a directory only holds
        // the files decoded so far, so a patch that lists it must decode first
        // (ResourcePatchContext KDoc). This patch lists values*/layout*/
        // drawable*/color* and checks files by isFile, so decode the whole
        // res tree up front — the same work 1.15.0 did before patches ran.
        decode("res/**")
        val result = applyTitaniumAmoledResources(
            resourceDirectory = res,
            backgroundHex = background,
            settingsHex = settings,
            textColorHex = text,
            accentColorHex = accent,
            pinMaterialYou = pin,
            surfaceColorHex = surface,
        )
        // The night sweep is a no-op on Titanium 157 (values-night holds no
        // literals), so the guard counts day + role + drawable + overrides.
        val rewritten = result.dayColorsReplaced +
            result.daySettingsReplaced +
            result.dayContainerReplaced +
            result.nightColorsReplaced +
            result.nightSettingsReplaced +
            result.nightContainerReplaced +
            result.v31MaterialYouReplaced +
            result.lStarSelectorsReplaced +
            result.drawableSurfaceFills +
            result.nightV31Overrides
        if (rewritten == 0) {
            throw PatchException(
                "No dark surface colors matched; refusing to ship an empty AMOLED rewrite",
            )
        }
        println(
            "[TITANIUM-AMOLED] background=$background settings=$settings " +
                "surface=${surface ?: "-"} text=${text ?: "-"} accent=${accent ?: "-"} " +
                "pinMaterialYou=$pin " +
                "dayReplaced=${result.dayColorsReplaced} " +
                "daySettings=${result.daySettingsReplaced} " +
                "dayContainer=${result.dayContainerReplaced} " +
                "nightReplaced=${result.nightColorsReplaced} " +
                "nightSettings=${result.nightSettingsReplaced} " +
                "nightContainer=${result.nightContainerReplaced} " +
                "materialYouReplaced=${result.v31MaterialYouReplaced} " +
                "nightV31Overrides=${result.nightV31Overrides} " +
                "textReplaced=${result.textColorsReplaced} " +
                "accentReplaced=${result.accentColorsReplaced} " +
                "textIdsInjected=${result.textIdsInjected} " +
                "nightTextDeclared=${result.nightTextDeclared} " +
                "textBearingIds=${result.textBearingIds} " +
                "lStarSelectors=${result.lStarSelectorsReplaced} " +
                "drawableFills=${result.drawableSurfaceFills} " +
                "windowBackgroundIds=${result.windowBackgroundIds} " +
                "dayWindowBgDeclared=${result.dayWindowBackgroundDeclared}",
        )
    }
}
