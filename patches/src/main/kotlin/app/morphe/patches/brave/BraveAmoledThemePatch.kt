/*
 * Brave AMOLED theme (issue #21).
 *
 * Brave dark chrome uses near-black neutrals (#1e2029 / #17171f / …), not pure
 * black. Resource names in release builds are obfuscated, so surfaces are
 * matched by hex luminance + chroma instead of names.
 *
 * Material You is the other half: brave_android_dynamic_colors_enabled is
 * persistent="false" and backed by Chromium prefs, so XML defaultValue cannot
 * turn it off. The bytecode prologue forces the Z-returning readers to false.
 *
 * Two surface tiers. Window backgrounds take the AMOLED background; raised
 * surfaces (buttons, cards, sheets, the Material You container ladder) take a
 * separate surface colour so the UI keeps the elevation ramp Material 3 has
 * instead of one flat black. With "surfaces use background" on — the default —
 * both tiers collapse to the same hex and the rewrite is byte-identical to the
 * flat version.
 *
 * The tier is decided by evidence in this order: the id's role in
 * values-v31/v34/v35, then the fill's value (a raised surface is a step brighter
 * than #1c1c1d). Style and layout names cannot help — they are obfuscated too.
 *
 * Layers owned here:
 *   values/colors.xml + values-night/colors.xml  — dark surface hex → background / surface
 *   values-v31/v34/v35/colors.xml — Material You dark roles → background / surface
 *   res/color selector files      — low-lStar dynamic surfaces pinned to the tier
 *   res/drawable vector files     — panels that hardcode the dark chrome fill
 *   values-night-v31/colors.xml — re-assert every surface id over Material You
 *   res/xml preference switches  — default the dynamic-colors widget to OFF
 *   bytecode getters             — force Material You readers to return false
 *
 * Turning the dynamic-colours force off (the user wants Material You) also stops
 * the Material You rewrites: leaving the role files pinned to the AMOLED hex
 * would repaint the wallpaper palette with the flat background and make the
 * switch a no-op. Brave's own hex tokens are still flattened in both modes.
 *
 * Declaring a <color> for an id that already has a selector file under res/color
 * is a duplicate resource: it breaks the build and, when it slips through,
 * renders that text black. Text ids are only declared when nothing else defines
 * them.
 *
 * Not owned: web content force-dark (brave_night_mode_enabled_key), NTP theme
 * collections, Chromium ColorProvider / native .pak chrome.
 */
package app.morphe.patches.brave

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.ResourcePatch
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.colorOption
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.io.File

internal const val DYNAMIC_COLORS_PREF_KEY = "brave_android_dynamic_colors_enabled"
internal const val DYNAMIC_COLORS_FEATURE_FLAG = "BraveAndroidDynamicColorsByDefault"
internal const val DAY_COLORS_PATH = "values/colors.xml"
internal const val NIGHT_COLORS_PATH = "values-night/colors.xml"
internal const val NIGHT_V31_COLORS_PATH = "values-night-v31/colors.xml"

/** Chrome/Brave dark surface literals seen on 1.9x; heuristic still covers drift. */
internal val KNOWN_DARK_SURFACE_HEXES = setOf(
    "#070608",
    "#0d0f14",
    "#0d1214",
    "#0f0f0f",
    "#121212",
    "#131221",
    "#17171f",
    "#1a1a1a",
    "#1c1c1d",
    "#1d1d36",
    "#1e2025",
    "#1e2029",
    "#1f1f1f",
    "#1f1f23",
    "#1f1f27",
    "#212121",
    "#212529",
    "#272733",
    "#2e3039",
    "#3b3e4f",
)

/**
 * Material You dark roles, split by what the role paints:
 *   base      — the window itself (background / surface / surface_dim) and the
 *               API 31 neutral tones, which pre-date the v34 surface ladder and
 *               paint the window on Android 12/12L
 *   container — the raised ladder (surface_bright, surface_variant, the
 *               surface_container steps)
 * on_surface / outline / accent roles are ink, never surfaces, and stay.
 *
 * Keeping the split is what lets the container ladder survive as a second colour
 * instead of collapsing into the background. The neutral tones stay on the base
 * side on purpose: on a v31-only device they are the window background, and the
 * original flat AMOLED rewrite already treated them as background.
 */
internal val MATERIAL_YOU_BASE_ROLES = Regex(
    pattern = """@android:color/system_(?:neutral[123]_(?:700|800|900|1000)""" +
        """|(?:background|surface|surface_dim)_dark)$""",
    option = RegexOption.IGNORE_CASE,
)

internal val MATERIAL_YOU_CONTAINER_ROLES = Regex(
    pattern = """@android:color/system_(?:surface_bright|surface_variant""" +
        """|surface_container(?:_low|_lowest|_high|_highest)?)_dark$""",
    option = RegexOption.IGNORE_CASE,
)

/** Both families, for callers that only need "is this a surface role at all". */
internal val MATERIAL_YOU_DARK_NEUTRAL_ROLES = Regex(
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
 */
internal val DARK_LSTAR_SELECTOR = Regex(
    pattern = """<item\s+n0:color="(@android:color/system_[^"]+)"\s+n0:lStar="(\d+(?:\.\d+)?)"""",
    option = RegexOption.IGNORE_CASE,
)

/** Below this lStar a surface role is too dark to read text on. */
internal const val DARK_SURFACE_LSTAR_CEILING = 24.0

/**
 * At or above this tone a v31 palette selector paints a raised surface. The
 * lStar on those selectors is the Material tone itself: res/color-v31 carries
 * m3_ref_palette_dynamic_neutral_variant6 (the window) through variant24 (the
 * surface_bright behind popups and menus), so the tone — not a name nobody kept
 * — is what separates the two tiers.
 */
internal const val RAISED_SURFACE_MIN_TONE = 12.0

private val HEX_COLOR = Regex("""^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$""")
private val COLOR_ELEMENT = Regex(
    pattern = """<color\s+name="([^"]+)"\s*>([^<]+)</color>""",
)

internal data class RgbColor(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int,
) {
    val maxChannel: Int get() = maxOf(r, g, b)
    val chroma: Int get() = maxChannel - minOf(r, g, b)
}

internal fun parseHexColor(value: String): RgbColor? {
    val trimmed = value.trim()
    if (!HEX_COLOR.matches(trimmed)) return null
    val body = trimmed.substring(1)
    return when (body.length) {
        3 -> {
            val r = body[0].digitToInt(16) * 17
            val g = body[1].digitToInt(16) * 17
            val b = body[2].digitToInt(16) * 17
            RgbColor(r, g, b, 0xFF)
        }
        6 -> RgbColor(
            r = body.substring(0, 2).toInt(16),
            g = body.substring(2, 4).toInt(16),
            b = body.substring(4, 6).toInt(16),
            a = 0xFF,
        )
        8 -> RgbColor(
            r = body.substring(2, 4).toInt(16),
            g = body.substring(4, 6).toInt(16),
            b = body.substring(6, 8).toInt(16),
            a = body.substring(0, 2).toInt(16),
        )
        else -> null
    }
}

internal fun normalizeOpaqueHex(value: String): String? {
    val rgb = parseHexColor(value) ?: return null
    if (rgb.a != 0xFF) return null
    return "#%02x%02x%02x".format(rgb.r, rgb.g, rgb.b).lowercase()
}

/**
 * Opaque low-chroma dark fills are chrome surfaces. Overlays (partial alpha),
 * accents (high chroma) and text (light) are left alone.
 *
 * ponytail: the split below is a value heuristic, not a name lookup — release
 * builds obfuscate every resource name. Raise BASE_SURFACE_MAX_CHANNEL if a
 * window background ends up painted with the surface colour.
 */
internal fun isAmoledSurfaceColor(value: String): Boolean {
    val normalized = normalizeOpaqueHex(value) ?: return false
    val rgb = parseHexColor(normalized) ?: return false
    if (rgb.chroma > 40) return false
    return rgb.maxChannel <= 80 || normalized.lowercase() in KNOWN_DARK_SURFACE_HEXES
}

/** At or below this max channel a fill is window background, not a raised one. */
internal const val BASE_SURFACE_MAX_CHANNEL = 0x1c

/**
 * Brave's own ramp splits here: #121212 / #17171f paint the window, #1e2029 /
 * #2e3039 paint buttons, cards and sheets.
 */
internal fun isBaseSurfaceColor(value: String): Boolean {
    if (!isAmoledSurfaceColor(value)) return false
    val rgb = parseHexColor(normalizeOpaqueHex(value) ?: return false) ?: return false
    return rgb.maxChannel <= BASE_SURFACE_MAX_CHANNEL
}

internal fun isContainerSurfaceColor(value: String): Boolean =
    isAmoledSurfaceColor(value) && !isBaseSurfaceColor(value)

/** Light low-chroma fills are text / icon ink (primary, secondary, muted). */
internal fun isTextColor(value: String): Boolean {
    val normalized = normalizeOpaqueHex(value) ?: return false
    if (isAmoledSurfaceColor(normalized)) return false
    val rgb = parseHexColor(normalized) ?: return false
    return rgb.chroma <= 40 && rgb.maxChannel >= 140
}

/**
 * Preserve Brave's ink hierarchy when tinting text: primary stays full, secondary
 * and muted are scaled down the way #f0f2ff → #c2c4cf → #84889c already does.
 */
internal fun textTierFactor(rgb: RgbColor): Float = when {
    rgb.maxChannel >= 220 -> 1.0f
    rgb.maxChannel >= 180 -> 0.81f
    else -> 0.62f
}

internal fun scaleHexByFactor(hex: String, factor: Float): String {
    val rgb = parseHexColor(hex) ?: return hex
    fun ch(v: Int) = (v * factor).toInt().coerceIn(0, 255)
    return "#%02x%02x%02x".format(ch(rgb.r), ch(rgb.g), ch(rgb.b))
}

/**
 * Blue-violet chromatic fills are the Brave accent family (#737ade / #a0a5eb /
 * #434fcf / …). Status coral/orange/red is intentionally excluded.
 */
internal fun isAccentColor(value: String): Boolean {
    val normalized = normalizeOpaqueHex(value) ?: return false
    val rgb = parseHexColor(normalized) ?: return false
    if (rgb.chroma < 50 || rgb.maxChannel < 100) return false
    // Blue-purple: blue dominant over red, green sitting between them.
    return rgb.b >= rgb.g && rgb.g >= rgb.r - 30
}

private fun rewriteHexGroup(
    xml: String,
    targetHex: String,
    matcher: (String) -> Boolean,
    excludeNames: Set<String> = emptySet(),
): Pair<String, Int> {
    val target = normalizeOpaqueHex(targetHex)
        ?: throw PatchException("Color must be opaque hex: $targetHex")
    var replaced = 0
    val out = COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        if (name in excludeNames) return@replace match.value
        if (matcher(raw) && normalizeOpaqueHex(raw) != target) {
            replaced++
            """<color name="$name">$target</color>"""
        } else {
            match.value
        }
    }
    return out to replaced
}

/** How many ids each tier moved. */
internal data class SurfaceTierCounts(val background: Int, val container: Int)

/**
 * Two-tier sweep: window fills take [backgroundHex], raised fills take
 * [containerHex]. [containerNames] carries the ids a Material You role file
 * already declared a container and [baseNames] the ids styles paint the window
 * with — both signals beat the value heuristic.
 *
 * A colour the patch cannot see the use of is judged by its value alone, and
 * values/colors.xml ships light-mode ink (#1c1c1d, #0d0f14, #202124) that
 * satisfies that test. Those ids are black-on-black once flattened, so callers
 * pass the ids known to be text in [excludeNames] and they are left alone.
 *
 * When both hexes match, which is the default, every fill goes to the same
 * target and the container tier is reported as zero, so the output and the log
 * stay identical to the flat AMOLED rewrite.
 */
internal fun rewriteSurfaceTiers(
    xml: String,
    backgroundHex: String,
    containerHex: String,
    containerNames: Set<String> = emptySet(),
    baseNames: Set<String> = emptySet(),
    excludeNames: Set<String> = emptySet(),
): Pair<String, SurfaceTierCounts> {
    val background = normalizeOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val container = normalizeOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")
    val flat = container == background
    var baseCount = 0
    var containerCount = 0
    val out = COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        if (name in excludeNames) return@replace match.value
        if (!isAmoledSurfaceColor(raw)) return@replace match.value
        val raised = name !in baseNames &&
            (name in containerNames || isContainerSurfaceColor(raw))
        val target = if (raised) container else background
        if (normalizeOpaqueHex(raw) == target) return@replace match.value
        if (raised && !flat) containerCount++ else baseCount++
        """<color name="$name">$target</color>"""
    }
    return out to SurfaceTierCounts(background = baseCount, container = containerCount)
}

internal fun rewriteTextColorXml(xml: String, textHex: String): Pair<String, Int> {
    val base = normalizeOpaqueHex(textHex)
        ?: throw PatchException("Text color must be an opaque hex: $textHex")
    var replaced = 0
    val out = COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        val rgb = parseHexColor(normalizeOpaqueHex(raw) ?: "")?.takeIf { isTextColor(raw) }
            ?: return@replace match.value
        val target = scaleHexByFactor(base, textTierFactor(rgb))
        if (normalizeOpaqueHex(raw) == target) {
            match.value
        } else {
            replaced++
            """<color name="$name">$target</color>"""
        }
    }
    return out to replaced
}

internal fun rewriteAccentColorXml(xml: String, accentHex: String): Pair<String, Int> =
    rewriteHexGroup(xml, accentHex, matcher = { isAccentColor(it) })

/**
 * In-place: kill Material You dark roles so they cannot win over night-v31. The
 * base roles take [backgroundHex], the container ladder takes [containerHex] so
 * the second tier survives.
 */
internal fun rewriteMaterialYouDarkNeutrals(
    xml: String,
    backgroundHex: String,
    containerHex: String = backgroundHex,
): Pair<String, Int> {
    val background = normalizeOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val container = normalizeOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")
    var replaced = 0
    val out = COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        val target = when {
            MATERIAL_YOU_CONTAINER_ROLES.matches(raw) -> container
            MATERIAL_YOU_BASE_ROLES.matches(raw) -> background
            else -> return@replace match.value
        }
        replaced++
        """<color name="$name">$target</color>"""
    }
    return out to replaced
}

internal fun collectMaterialYouDarkNeutralNames(xml: String): List<String> =
    COLOR_ELEMENT.findAll(xml)
        .filter { MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches(it.groupValues[2].trim()) }
        .map { it.groupValues[1] }
        .toList()

/**
 * App ids a Material You role file points at a container role, mapped to whether
 * the role is raised. This is the one tier signal that is read instead of
 * guessed: the v31/v34 files name the role outright, and the same id in
 * values-night paints that same surface.
 */
internal fun collectMaterialYouRoleTiers(xml: String): Map<String, Boolean> =
    COLOR_ELEMENT.findAll(xml).mapNotNull { match ->
        val raw = match.groupValues[2].trim()
        when {
            MATERIAL_YOU_CONTAINER_ROLES.matches(raw) -> match.groupValues[1] to true
            MATERIAL_YOU_BASE_ROLES.matches(raw) -> match.groupValues[1] to false
            else -> null
        }
    }.toMap()

/**
 * Ids that values-night-v31 has to re-assert because a Material You role would
 * otherwise win over values-night on API 31+, each mapped to the hex of its own
 * tier so the override file cannot hand a raised id the background colour.
 *
 * Collected from the files as they were *before* the sweep. Reading them back
 * afterwards would count everything the sweep just wrote — including the ids it
 * flattened — and re-assert all of them, which puts the text ids back to black
 * on top of the values-night fix.
 */
internal fun collectOverrideHexes(
    dayXml: String,
    nightXml: String,
    materialYouNames: Collection<String>,
    containerNames: Set<String>,
    baseNames: Set<String>,
    textBearing: Set<String>,
    backgroundHex: String,
    containerHex: String,
): Map<String, String> {
    val background = normalizeOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val container = normalizeOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")

    val names = linkedSetOf<String>()
    val raised = linkedSetOf<String>()
    listOf(dayXml, nightXml).forEach { xml ->
        COLOR_ELEMENT.findAll(xml).forEach { match ->
            val name = match.groupValues[1]
            val raw = match.groupValues[2].trim()
            if (!isAmoledSurfaceColor(raw)) return@forEach
            // Text ink must never be re-asserted as a background, whatever the
            // sweep did.
            if (name in textBearing) return@forEach
            names += name
            if (name !in baseNames && (name in containerNames || isContainerSurfaceColor(raw))) {
                raised += name
            }
        }
    }
    materialYouNames.forEach { name ->
        if (name in textBearing) return@forEach
        names += name
        if (name !in baseNames && name in containerNames) raised += name
    }
    return names.toSortedSet().associateWith { name ->
        if (name in raised) container else background
    }
}

private val WINDOW_BACKGROUND_ITEM = Regex(
    """<item\s+name="(?:android:)?(?:colorBackground|windowBackground)""" +
        """"\s*>\s*@color/([^<]+)\s*<""",
)

/**
 * Ids styles paint the window with. These must never be raised: a window a step
 * brighter than the AMOLED background is exactly the flat-versus-elevated mix-up
 * the surface tier exists to avoid, and the value heuristic cannot tell a
 * #1f1f1f window from a #1f1f1f card.
 */
internal fun collectWindowBackgroundIds(resourceDirectory: File): Set<String> {
    val out = linkedSetOf<String>()
    resourceDirectory.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .flatMap { it.listFiles().orEmpty().toList() }
        .filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
        .forEach { file ->
            WINDOW_BACKGROUND_ITEM.findAll(file.readText()).forEach { out += it.groupValues[1] }
        }
    return out
}

private val TEXT_COLOR_REF = Regex("""textColor[A-Za-z]*">@color/([^<]+)<""")

/**
 * Color names styles mark as text (textColorPrimary / Secondary / Hint / …).
 * Must scan every style qualifier: values-v31/styles.xml re-points the theme at
 * a different id set on Android 12+, and those ids resolve through the
 * res/color selector folders, so reading only values/ misses whatever that
 * qualifier swaps in.
 */
internal fun collectTextColorRefs(stylesXml: String): List<String> =
    TEXT_COLOR_REF.findAll(stylesXml).map { it.groupValues[1] }.distinct().toList()

internal fun collectTextColorRefsFromDir(resourceDirectory: File): List<String> {
    val out = linkedSetOf<String>()
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .flatMap { it.listFiles().orEmpty().toList() }
        .filter { it.isFile && it.name.equals("styles.xml", ignoreCase = true) }
        .forEach { out += collectTextColorRefs(it.readText()) }
    return out.toList()
}

private val STYLE_TEXT_ITEM =
    Regex("""<item\s+name="(?:android:)?text[A-Za-z]*"\s*>\s*@color/([^<]+)\s*<""")
private val LAYOUT_TEXT_ATTR =
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
internal fun collectTextBearingIds(resourceDirectory: File): Set<String> {
    val out = linkedSetOf<String>()
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .flatMap { it.listFiles().orEmpty().toList() }
        .filter { it.isFile && it.name.equals("styles.xml", ignoreCase = true) }
        .forEach { file ->
            STYLE_TEXT_ITEM.findAll(file.readText()).forEach { out += it.groupValues[1] }
        }
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("layout") }
        .forEach { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                .forEach { file ->
                    LAYOUT_TEXT_ATTR.findAll(file.readText())
                        .forEach { out += it.groupValues[1] }
                }
        }
    return out
}

/** Day ink used when an undefined text id must be re-declared for light mode. */
internal const val DAY_INK = "#202124"

/** Colour ids backed by a res/color selector folder rather than a <color> element. */
internal fun selectorColorNames(resourceDirectory: File): Set<String> {
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
internal fun declaredColorNames(resourceDirectory: File): Set<String> {
    val names = linkedSetOf<String>()
    resourceDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .flatMap { it.listFiles().orEmpty().toList() }
        .filter { it.isFile && it.name.equals("colors.xml", ignoreCase = true) }
        .forEach { file ->
            COLOR_ELEMENT.findAll(file.readText()).forEach { names += it.groupValues[1] }
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
internal fun declareMissingTextColors(
    xml: String,
    textNames: Collection<String>,
    declared: Set<String>,
    hex: String,
): Pair<String, Int> {
    val target = normalizeOpaqueHex(hex)
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
internal fun declaredColorNamesIn(file: File): Set<String> {
    if (!file.isFile) return emptySet()
    return COLOR_ELEMENT.findAll(file.readText()).map { it.groupValues[1] }.toSet()
}

/**
 * Text ids whose light-mode value is too dark to read on AMOLED. Only these need
 * a night declaration.
 *
 * Text positions are not always ink: the privacy report paints its stat numbers
 * in the accent palette (#cd4400 orange, #545ff8 blue), and forcing those to the
 * body-ink colour turns a three-colour readout into three identical white
 * numbers. The test is darkness, not "is it ink" — #212529 body ink and #cd4400
 * stat orange are both dark, yet only the first needs rewriting.
 */
internal fun darkTextIdsInDay(xml: String, textIds: Collection<String>): List<String> {
    val dark = COLOR_ELEMENT.findAll(xml)
        .filter { it.groupValues[1] in textIds }
        .filter { isAmoledSurfaceColor(it.groupValues[2].trim()) }
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
internal fun declareNightTextColors(
    xml: String,
    textNames: Collection<String>,
    declaredInNight: Set<String>,
    hex: String,
    resolvedElsewhere: Set<String> = emptySet(),
): Pair<String, Int> {
    val target = normalizeOpaqueHex(hex)
        ?: throw PatchException("Text color must be an opaque hex: $hex")
    // An id already defined in this file, or backed by a selector folder, is not
    // re-declared: a <color> beside a selector file is a duplicate resource.
    val alreadyHere = COLOR_ELEMENT.findAll(xml).map { it.groupValues[1] }.toSet()
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
 * tone has to decide the tier.
 */
internal fun rewriteDarkLStarSelectors(
    xml: String,
    backgroundHex: String,
    containerHex: String = backgroundHex,
): Pair<String, Int> {
    val background = normalizeOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val container = normalizeOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")
    var replaced = 0
    val out = DARK_LSTAR_SELECTOR.replace(xml) { match ->
        val role = match.groupValues[1]
        val tone = match.groupValues[2].toDoubleOrNull() ?: return@replace match.value
        if (tone > DARK_SURFACE_LSTAR_CEILING) return@replace match.value
        // A named role decides the tier outright. A bare palette tone carries no
        // name, so the tone is the only evidence left: tone 6 paints the window,
        // tone 12 and up paint the raised steps.
        val target = when {
            MATERIAL_YOU_CONTAINER_ROLES.matches(role) -> container
            MATERIAL_YOU_BASE_ROLES.matches(role) -> background
            tone >= RAISED_SURFACE_MIN_TONE -> container
            else -> background
        }
        replaced++
        match.value
            .replace(Regex("""n0:color="[^"]*""""), """n0:color="$target"""")
            .replace(Regex("""\s+n0:lStar="[^"]*""""), "")
    }
    return out to replaced
}

private val VECTOR_PATH = Regex("""<path\b""")

private val VECTOR_FILL = Regex("(n0:fillColor|n1:fillColor)=\"([^\"]+)\"")

/**
 * Vector panels (promo cards, sheets) hardcode the dark chrome fill instead of
 * pointing at a color resource. Rewriting them is limited to small path counts
 * so icons — whose fills are ink, not surfaces — are never touched.
 */
internal fun rewriteVectorSurfaceFills(
    xml: String,
    backgroundHex: String,
    containerHex: String = backgroundHex,
): Pair<String, Int> {
    val background = normalizeOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val container = normalizeOpaqueHex(containerHex)
        ?: throw PatchException("Surface color must be an opaque hex: $containerHex")
    val pathCount = VECTOR_PATH.findAll(xml).count()
    if (pathCount == 0 || pathCount > 3) return xml to 0
    var replaced = 0
    val out = VECTOR_FILL.replace(xml) { match ->
        val raw = match.groupValues[2]
        if (!isAmoledSurfaceColor(raw)) return@replace match.value
        val target = if (isContainerSurfaceColor(raw)) container else background
        replaced++
        match.groupValues[1] + "=\"" + target + "\""
    }
    return out to replaced
}

internal fun buildNightV31Overrides(overrides: Map<String, String>): String =
    buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<resources>")
        overrides.toSortedMap().forEach { (name, hex) ->
            val target = normalizeOpaqueHex(hex)
                ?: throw PatchException("Override colour must be an opaque hex: $hex")
            appendLine("""    <color name="$name">$target</color>""")
        }
        appendLine("</resources>")
    }

/** Same colour for every id — the flat AMOLED preset. */
internal fun buildNightV31Overrides(names: Collection<String>, hex: String): String {
    val target = normalizeOpaqueHex(hex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $hex")
    return buildNightV31Overrides(names.toSortedSet().associateWith { target })
}

private val PREF_ATTR_DEFAULT =
    Regex("""([\s](?:[\w.]+:)?defaultValue=")[^"]*(")""")

/**
 * Default the widget OFF only. Never set android:enabled=false — that greys the
 * switch. Backend kill lives in the bytecode getter force.
 */
internal fun rewriteDynamicColorsPreferenceText(text: String): String {
    if (!text.contains(DYNAMIC_COLORS_PREF_KEY)) return text
    return text.replace(
        Regex("""([\s](?:[\w.]+:)?key="$DYNAMIC_COLORS_PREF_KEY"[^>]*?)(/>|>)"""),
    ) { match ->
        var tag = match.groupValues[1]
        tag = if (PREF_ATTR_DEFAULT.containsMatchIn(tag)) {
            PREF_ATTR_DEFAULT.replace(tag, "$1false$2")
        } else {
            tag + """ android:defaultValue="false""""
        }
        tag + match.groupValues[2]
    }
}

internal fun disableDynamicColorsPreference(resourceDirectory: File): Int {
    val xmlDir = resourceDirectory.resolve("xml")
    if (!xmlDir.isDirectory) return 0
    var changed = 0
    xmlDir.listFiles()
        .orEmpty()
        .filter { it.extension.equals("xml", ignoreCase = true) }
        .forEach { file ->
            val text = file.readText()
            val next = rewriteDynamicColorsPreferenceText(text)
            if (next != text) {
                file.writeText(next)
                changed++
            }
        }
    return changed
}

/** Material You role files, oldest first so values-night-v31 wins. */
internal val MATERIAL_YOU_COLORS_PATHS = listOf(
    "values-v31/colors.xml",
    "values-v34/colors.xml",
    "values-v35/colors.xml",
)

/** Selector directories that can pin a surface to a low lStar. */
internal val SELECTOR_DIRS = listOf("color", "color-night", "color-v31")

internal fun applyAmoledResources(
    resourceDirectory: File,
    backgroundHex: String,
    textColorHex: String? = null,
    accentColorHex: String? = null,
    disableDynamicColors: Boolean = true,
    surfaceUsesBackground: Boolean = true,
    surfaceColorHex: String? = null,
): AmoledRewriteResult {
    val background = normalizeOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val containerHex = if (surfaceUsesBackground) {
        background
    } else {
        normalizeOpaqueHex(surfaceColorHex.orEmpty())
            ?: throw PatchException(
                "Surface color must be an opaque hex, got: ${surfaceColorHex ?: "null"}",
            )
    }
    val textRefs = collectTextColorRefsFromDir(resourceDirectory)
    val declared = declaredColorNames(resourceDirectory)
    val textBearing = collectTextBearingIds(resourceDirectory)
    // Read the role files before the sweep rewrites them: an id the app already
    // declares as a container role is the only tier signal that is not a guess.
    val roleTiers = mutableMapOf<String, Boolean>()
    MATERIAL_YOU_COLORS_PATHS.forEach { path ->
        val file = resourceDirectory.resolve(path)
        if (!file.isFile) return@forEach
        roleTiers += collectMaterialYouRoleTiers(file.readText())
    }
    val containerNames = if (surfaceUsesBackground) emptySet() else roleTiers.filterValues { it }.keys
    // Window backgrounds are never raised, whatever their value looks like.
    val baseNames = if (surfaceUsesBackground) {
        emptySet()
    } else {
        collectWindowBackgroundIds(resourceDirectory)
    }
    val dayFile = resourceDirectory.resolve(DAY_COLORS_PATH)
    val nightFile = resourceDirectory.resolve(NIGHT_COLORS_PATH)
    // Snapshot before the sweep: the night-v31 override set is built from what
    // the app shipped, not from what the sweep just wrote.
    val originalDayXml = if (dayFile.isFile) dayFile.readText() else ""
    val originalNightXml = if (nightFile.isFile) nightFile.readText() else ""

    // Chrome windowBackground / colorBackground live as dark hexes in
    // values/colors.xml (#121212 / #ff303030 / …), not only in values-night.
    var dayReplaced = 0
    var dayTextReplaced = 0
    var dayAccentReplaced = 0
    var dayInkInjected = 0
    var containerReplaced = 0
    if (dayFile.isFile) {
        var xml = dayFile.readText()
        val (afterBg, bgTiers) = rewriteSurfaceTiers(
            xml = xml,
            backgroundHex = background,
            containerHex = containerHex,
            containerNames = containerNames,
            baseNames = baseNames,
            excludeNames = textBearing,
        )
        xml = afterBg
        dayReplaced = bgTiers.background
        containerReplaced += bgTiers.container
        if (textColorHex != null) {
            val (afterText, count) = rewriteTextColorXml(xml, textColorHex)
            xml = afterText
            dayTextReplaced = count
            // Light mode keeps dark ink, so only declare what resolves to nothing.
            val (afterInject, injected) = declareMissingTextColors(xml, textRefs, declared, DAY_INK)
            xml = afterInject
            dayInkInjected = injected
        }
        if (accentColorHex != null) {
            val (afterAccent, count) = rewriteAccentColorXml(xml, accentColorHex)
            xml = afterAccent
            dayAccentReplaced = count
        }
        dayFile.writeText(xml)
    }

    if (!nightFile.isFile) {
        throw PatchException("Brave night color resources not found: $NIGHT_COLORS_PATH")
    }
    var nightXml = nightFile.readText()
    val (afterNightBg, nightTiers) = rewriteSurfaceTiers(
        xml = nightXml,
        backgroundHex = background,
        containerHex = containerHex,
        containerNames = containerNames,
        baseNames = baseNames,
        excludeNames = textBearing,
    )
    nightXml = afterNightBg
    val nightReplaced = nightTiers.background
    containerReplaced += nightTiers.container
    var nightTextReplaced = 0
    var nightAccentReplaced = 0
    var nightInkDeclared = 0
    if (textColorHex != null) {
        val (afterText, count) = rewriteTextColorXml(nightXml, textColorHex)
        nightXml = afterText
        nightTextReplaced = count
        // Ids the app renders text with but that only exist in the light config
        // would stay dark-on-dark here, so give night its own readable value.
        // Accent-coloured text (privacy-report stat numbers) is left alone.
        val (afterDeclare, declaredCount) = declareNightTextColors(
            nightXml,
            darkTextIdsInDay(originalDayXml, textBearing),
            declaredColorNamesIn(nightFile),
            textColorHex,
            // An id a selector folder already resolves must not gain a <color>
            // element — that pair is the duplicate that broke text before.
            selectorColorNames(resourceDirectory),
        )
        nightXml = afterDeclare
        nightInkDeclared = declaredCount
    }
    if (accentColorHex != null) {
        val (afterAccent, count) = rewriteAccentColorXml(nightXml, accentColorHex)
        nightXml = afterAccent
        nightAccentReplaced = count
    }
    nightFile.writeText(nightXml)

    // With dynamic colours left on, the Material You role files and the low-lStar
    // selectors are deliberately left alone: pinning them to the AMOLED hex is
    // what repainted the wallpaper palette and made the switch a no-op.
    var materialYouReplaced = 0
    val materialYouNames = mutableListOf<String>()
    if (disableDynamicColors) {
        MATERIAL_YOU_COLORS_PATHS.forEach { path ->
            val file = resourceDirectory.resolve(path)
            if (!file.isFile) return@forEach
            val xml = file.readText()
            materialYouNames += collectMaterialYouDarkNeutralNames(xml)
            val (next, count) = rewriteMaterialYouDarkNeutrals(xml, background, containerHex)
            file.writeText(next)
            materialYouReplaced += count
        }
    }

    // Built from the pre-sweep snapshot: reading the rewritten files back would
    // re-assert every flattened id, including the text ones, as background.
    val overrides = collectOverrideHexes(
        dayXml = originalDayXml,
        nightXml = originalNightXml,
        materialYouNames = materialYouNames,
        containerNames = containerNames,
        baseNames = baseNames,
        textBearing = textBearing,
        backgroundHex = background,
        containerHex = containerHex,
    )

    var lStarReplaced = 0
    if (disableDynamicColors) {
        SELECTOR_DIRS.forEach { dir ->
            val dirFile = resourceDirectory.resolve(dir)
            dirFile.listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                .forEach { file ->
                    val xml = file.readText()
                    val (next, count) = rewriteDarkLStarSelectors(xml, background, containerHex)
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
                    val (next, count) = rewriteVectorSurfaceFills(xml, background, containerHex)
                    if (count > 0) {
                        file.writeText(next)
                        drawableFills += count
                    }
                }
        }

    if (overrides.isNotEmpty()) {
        val nightV31 = resourceDirectory.resolve(NIGHT_V31_COLORS_PATH)
        nightV31.parentFile?.mkdirs()
        nightV31.writeText(buildNightV31Overrides(overrides))
    }

    val dynamicPrefs = if (disableDynamicColors) {
        disableDynamicColorsPreference(resourceDirectory)
    } else {
        0
    }
    return AmoledRewriteResult(
        dayColorsReplaced = dayReplaced,
        nightColorsReplaced = nightReplaced,
        v31MaterialYouReplaced = materialYouReplaced,
        nightV31Overrides = overrides.size,
        containerColorsReplaced = containerReplaced,
        textColorsReplaced = dayTextReplaced + nightTextReplaced,
        accentColorsReplaced = dayAccentReplaced + nightAccentReplaced,
        textIdsInjected = dayInkInjected,
        nightTextDeclared = nightInkDeclared,
        textBearingIds = textBearing.size,
        lStarSelectorsReplaced = lStarReplaced,
        drawableSurfaceFills = drawableFills,
        preferenceFilesChanged = dynamicPrefs,
    )
}

internal data class AmoledRewriteResult(
    val dayColorsReplaced: Int,
    val nightColorsReplaced: Int,
    val v31MaterialYouReplaced: Int,
    val nightV31Overrides: Int,
    val containerColorsReplaced: Int = 0,
    val textColorsReplaced: Int = 0,
    val accentColorsReplaced: Int = 0,
    val textIdsInjected: Int = 0,
    val nightTextDeclared: Int = 0,
    val textBearingIds: Int = 0,
    val lStarSelectorsReplaced: Int = 0,
    val drawableSurfaceFills: Int = 0,
    val preferenceFilesChanged: Int,
)

internal fun forceFalseBooleanPrologueSmali(): String =
    "const/4 v0, 0x0\nreturn v0"

internal fun methodStringConstants(
    method: com.android.tools.smali.dexlib2.iface.Method,
): List<String> {
    val impl = method.implementation ?: return emptyList()
    return impl.instructions.mapNotNull { ins ->
        (ins as? ReferenceInstruction)?.reference as? StringReference
    }.map { it.string }
}

internal fun methodMentionsDynamicColors(method: com.android.tools.smali.dexlib2.iface.Method): Boolean =
    methodStringConstants(method).any {
        it == DYNAMIC_COLORS_PREF_KEY || it == DYNAMIC_COLORS_FEATURE_FLAG
    }

/**
 * AppearancePreferences binds every Appearance switch in one boolean method
 * (J1/k4/…). Force-false on those kills the whole menu. A dedicated
 * dynamic-colors getter only carries the dynamic-colors constant.
 */
internal val APPEARANCE_PREF_KEY_HINTS = listOf(
    "brave_night_mode",
    "brave_bottom_toolbar",
    "brave_disable_sharing",
    "brave_rewards",
    "brave_enable_tab_groups",
    "show_undo_when_tabs",
    "ads_switch",
    "bookmark_bar",
    "address_bar",
    "ui_theme",
    "brave_customize_menu",
    "enable_multi_windows",
    "toolbar_shortcut",
    "navigation_section",
    "general_section",
)

internal fun isDedicatedDynamicColorsGetter(
    method: com.android.tools.smali.dexlib2.iface.Method,
): Boolean {
    if (method.returnType != "Z") return false
    val strings = methodStringConstants(method)
    if (strings.none { it == DYNAMIC_COLORS_PREF_KEY || it == DYNAMIC_COLORS_FEATURE_FLAG }) {
        return false
    }
    return APPEARANCE_PREF_KEY_HINTS.none { hint -> strings.any { it.contains(hint) } }
}

private fun amoledCompatibilities() = listOf(
    Compatibility(
        name = "Brave Browser",
        packageName = "com.brave.browser",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = false)),
    ),
    Compatibility(
        name = "Brave Browser APKM",
        packageName = "com.brave.browser",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = false)),
    ),
    Compatibility(
        name = "Brave Beta",
        packageName = "com.brave.browser_beta",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
    Compatibility(
        name = "Brave Nightly",
        packageName = "com.brave.browser_nightly",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
    Compatibility(
        name = "Brave Nightly APKM",
        packageName = "com.brave.browser_nightly",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
)

// Options live on the public patch; this dependency owns the resource rewrite.
private val braveAmoledResourcePatch: ResourcePatch = resourcePatch(
    name = "Brave AMOLED theme resources",
    description = "Rewrites Brave dark chrome surfaces and Material You dark roles.",
    default = false,
) {
    compatibleWith(*amoledCompatibilities().toTypedArray())
    execute {
        val background = normalizeOpaqueHex(
            braveAmoledThemePatch.options["backgroundColor"].value as? String ?: "#000000",
        ) ?: throw PatchException("Invalid AMOLED background color")
        val text = (braveAmoledThemePatch.options["textColor"].value as? String)
            ?.let { normalizeOpaqueHex(it) }
        val accent = (braveAmoledThemePatch.options["accentColor"].value as? String)
            ?.let { normalizeOpaqueHex(it) }
        val res = get("res")
        if (!res.isDirectory) {
            throw PatchException("Decoded res/ directory not found")
        }
        val disableDynamic =
            (braveAmoledThemePatch.options["disableDynamicColors"].value as? Boolean) ?: true
        val surfaceUsesBackground =
            (braveAmoledThemePatch.options["surfaceUsesBackground"].value as? Boolean) ?: true
        val surface = (braveAmoledThemePatch.options["surfaceColor"].value as? String)
            ?.let { normalizeOpaqueHex(it) }
        val result = applyAmoledResources(
            resourceDirectory = res,
            backgroundHex = background,
            textColorHex = text,
            accentColorHex = accent,
            disableDynamicColors = disableDynamic,
            surfaceUsesBackground = surfaceUsesBackground,
            surfaceColorHex = surface,
        )
        if (result.dayColorsReplaced + result.nightColorsReplaced + result.nightV31Overrides == 0) {
            throw PatchException(
                "No dark surface colors matched; refusing to ship an empty AMOLED rewrite",
            )
        }
        println(
            "[AMOLED] background=$background text=${text ?: "-"} accent=${accent ?: "-"} " +
                "surface=${surface ?: "-"} surfaceTier=${!surfaceUsesBackground} " +
                "dayReplaced=${result.dayColorsReplaced} " +
                "nightReplaced=${result.nightColorsReplaced} " +
                "containerReplaced=${result.containerColorsReplaced} " +
                "materialYouReplaced=${result.v31MaterialYouReplaced} " +
                "nightV31Overrides=${result.nightV31Overrides} " +
                "textReplaced=${result.textColorsReplaced} " +
                "accentReplaced=${result.accentColorsReplaced} " +
                "textIdsInjected=${result.textIdsInjected} " +
                "nightTextDeclared=${result.nightTextDeclared} " +
                "textBearingIds=${result.textBearingIds} " +
                "lStarSelectors=${result.lStarSelectorsReplaced} " +
                "drawableFills=${result.drawableSurfaceFills} " +
                "prefFiles=${result.preferenceFilesChanged}",
        )
    }
}

/**
 * Patch-time AMOLED (issue #21): dark chrome surfaces become pure black (or the
 * chosen opaque hex). Raised surfaces can take a second colour so the elevation
 * ramp survives, and turning the Material You force off now really does leave the
 * wallpaper palette alone. Default off. No runtime theme picker.
 */
@Suppress("unused")
val braveAmoledThemePatch: BytecodePatch = bytecodePatch(
    name = "Brave AMOLED theme",
    description = "Patch-time AMOLED dark theme (issue #21): rewrites Brave dark chrome " +
        "surfaces to pure black (or a custom opaque hex). Raised surfaces — buttons, " +
        "cards, sheets and the Material You container ladder — can take their own " +
        "surface color instead of one flat black, so the UI keeps an elevation step " +
        "like Material 3. Optional text and accent colors (defaults keep Brave's " +
        "#f0f2ff / #737ade). Forces Material You dynamic colors off in bytecode " +
        "(the pref is non-persistent) and overrides system neutral night roles on " +
        "Android 12+; leaving that force off keeps the Material You roles and low-lStar " +
        "selectors untouched. Apply Dark theme in Brave to see it. Does not change web " +
        "content force-dark, NTP theme collections, or add a runtime color picker. " +
        "Default off.",
    default = false,
) {
    compatibleWith(*amoledCompatibilities().toTypedArray())
    dependsOn(braveAmoledResourcePatch)

    val backgroundColor by colorOption(
        key = "backgroundColor",
        default = "#000000",
        title = "AMOLED background",
        description = "Opaque hex for window backgrounds and the rest of dark chrome. " +
            "Use #000000 for pure black OLED. Example elevated near-black: #0a0a0a.",
        required = true,
        validator = { value -> value == null || normalizeOpaqueHex(value) != null },
    )

    val textColor by colorOption(
        key = "textColor",
        default = "#f0f2ff",
        title = "Text color",
        description = "Opaque hex for text and icon ink (primary/secondary/muted). " +
            "Brave dark default: #f0f2ff.",
        required = true,
        validator = { value -> value == null || normalizeOpaqueHex(value) != null },
    )

    val accentColor by colorOption(
        key = "accentColor",
        default = "#737ade",
        title = "Accent color",
        description = "Opaque hex for Brave accent (toggles, links, highlights). " +
            "Default: #737ade. Status coral/orange is left alone.",
        required = true,
        validator = { value -> value == null || normalizeOpaqueHex(value) != null },
    )

    val disableDynamicColors by booleanOption(
        key = "disableDynamicColors",
        default = true,
        title = "Disable Material You dynamic colors",
        description = "Force Brave's wallpaper dynamic colors off so AMOLED surfaces " +
            "are not replaced by system palette roles. The settings switch stays " +
            "clickable but readers always return off. Turning this off also stops " +
            "the Material You role rewrite, so the wallpaper palette survives.",
        required = false,
    )

    val surfaceUsesBackground by booleanOption(
        key = "surfaceUsesBackground",
        default = true,
        title = "Surfaces use the background color",
        description = "ON: buttons, cards and sheets share the AMOLED background — one " +
            "flat black, the original behaviour. OFF: raised surfaces take the " +
            "surface color below instead, keeping the brighter elevation step " +
            "Material 3 uses.",
        required = false,
    )

    val surfaceColor by colorOption(
        key = "surfaceColor",
        default = "#1e2029",
        title = "Surface / button color",
        description = "Opaque hex for raised surfaces — buttons, cards, sheets and the " +
            "Material You container ladder. Only applied when 'Surfaces use the " +
            "background color' is off. #1e2029 is Brave's own dark container tone.",
        required = false,
        validator = { value -> value == null || normalizeOpaqueHex(value) != null },
    )

    execute {
        if (disableDynamicColors == false) {
            println("[AMOLED] Leaving Material You getters untouched (user opt-out)")
            return@execute
        }

        var forced = 0
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                if (!isDedicatedDynamicColorsGetter(method)) return@forEach
                mutableClassDefBy(classDef).methods
                    .first {
                        it.name == method.name && it.parameterTypes == method.parameterTypes
                    }
                    .addInstructions(0, forceFalseBooleanPrologueSmali())
                forced++
            }
        }
        if (forced == 0) {
            throw PatchException(
                "No Material You dynamic-colors boolean readers found; " +
                    "refusing to ship AMOLED that cannot disable Material You",
            )
        }
        println("[AMOLED] Forced $forced dynamic-colors reader(s) to return false")
    }
}
