/*
 * Brave AMOLED theme (issue #21).
 *
 * Brave dark chrome uses near-black neutrals (#1e2029 / #17171f / …), not pure
 * black. Resource names in release builds are obfuscated (APKTOOL_RENAMED_* /
 * stripped), so surfaces are matched by hex luminance + chroma instead of names.
 *
 * Layers owned here:
 *   values-night/colors.xml      — dark surface hex literals → AMOLED background
 *   values-night-v31/colors.xml  — re-assert those names over Material You system_*
 *   res/xml preference switches  — default Material You dynamic colors off
 *
 * Not owned: web content force-dark (brave_night_mode_enabled_key), NTP theme
 * collections, Chromium ColorProvider / native .pak chrome.
 */
package app.morphe.patches.brave

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.colorOption
import app.morphe.patcher.patch.resourcePatch
import java.io.File

internal const val DYNAMIC_COLORS_PREF_KEY = "brave_android_dynamic_colors_enabled"
internal const val NIGHT_COLORS_PATH = "values-night/colors.xml"
internal const val NIGHT_V31_COLORS_PATH = "values-night-v31/colors.xml"
internal const val V31_COLORS_PATH = "values-v31/colors.xml"

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

/** Material You dark neutral roles that would override values-night on API 31+. */
internal val MATERIAL_YOU_DARK_NEUTRAL_ROLES = Regex(
    pattern = """@android:color/system_neutral[123]_(700|800|900|1000)$""",
    option = RegexOption.IGNORE_CASE,
)

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
 */
internal fun isAmoledSurfaceColor(value: String): Boolean {
    val normalized = normalizeOpaqueHex(value) ?: return false
    val rgb = parseHexColor(normalized) ?: return false
    if (rgb.chroma > 40) return false
    return rgb.maxChannel <= 80 || normalized.lowercase() in KNOWN_DARK_SURFACE_HEXES
}

internal fun rewriteNightColorXml(xml: String, backgroundHex: String): Pair<String, Int> {
    val target = normalizeOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    var replaced = 0
    val out = COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        if (isAmoledSurfaceColor(raw) && normalizeOpaqueHex(raw) != target) {
            replaced++
            """<color name="$name">$target</color>"""
        } else {
            match.value
        }
    }
    return out to replaced
}

internal fun collectSurfaceColorNames(xml: String): List<String> =
    COLOR_ELEMENT.findAll(xml)
        .filter { isAmoledSurfaceColor(it.groupValues[2].trim()) }
        .map { it.groupValues[1] }
        .toList()

internal fun collectMaterialYouDarkNeutralNames(xml: String): List<String> =
    COLOR_ELEMENT.findAll(xml)
        .filter { MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches(it.groupValues[2].trim()) }
        .map { it.groupValues[1] }
        .toList()

internal fun buildNightV31Overrides(names: Collection<String>, backgroundHex: String): String {
    val target = normalizeOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    val unique = names.toSortedSet()
    return buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<resources>")
        unique.forEach { name ->
            appendLine("""    <color name="$name">$target</color>""")
        }
        appendLine("</resources>")
    }
}

/**
 * Preference XML filenames are obfuscated; locate the switch by its stable key
 * and default Material You off so night surfaces stay on our palette.
 */
private val PREF_ATTR_DEFAULT =
    Regex("""([\s](?:[\w.]+:)?defaultValue=")[^"]*(")""")
private val PREF_ATTR_ENABLED =
    Regex("""([\s](?:[\w.]+:)?enabled=")[^"]*(")""")

internal fun rewriteDynamicColorsPreferenceText(text: String): String {
    if (!text.contains(DYNAMIC_COLORS_PREF_KEY)) return text
    // Require an attribute boundary before key= so longer names like
    // some_other_key="brave_android_..." are not rewritten.
    return text.replace(
        Regex("""([\s](?:[\w.]+:)?key="$DYNAMIC_COLORS_PREF_KEY"[^>]*?)(/>|>)"""),
    ) { match ->
        var tag = match.groupValues[1]
        tag = if (PREF_ATTR_DEFAULT.containsMatchIn(tag)) {
            PREF_ATTR_DEFAULT.replace(tag, "$1false$2")
        } else {
            tag + """ android:defaultValue="false""""
        }
        tag = if (PREF_ATTR_ENABLED.containsMatchIn(tag)) {
            PREF_ATTR_ENABLED.replace(tag, "$1false$2")
        } else {
            tag + """ android:enabled="false""""
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

internal fun applyAmoledResources(
    resourceDirectory: File,
    backgroundHex: String,
    disableDynamicColors: Boolean = true,
): AmoledRewriteResult {
    val nightFile = resourceDirectory.resolve(NIGHT_COLORS_PATH)
    if (!nightFile.isFile) {
        throw PatchException("Brave night color resources not found: $NIGHT_COLORS_PATH")
    }
    val nightXml = nightFile.readText()
    val (rewritten, replaced) = rewriteNightColorXml(nightXml, backgroundHex)
    nightFile.writeText(rewritten)

    val surfaceNames = collectSurfaceColorNames(rewritten)
    val v31File = resourceDirectory.resolve(V31_COLORS_PATH)
    val materialYouNames = if (v31File.isFile) {
        collectMaterialYouDarkNeutralNames(v31File.readText())
    } else {
        emptyList()
    }

    val overrideNames = (surfaceNames + materialYouNames).toSortedSet()
    if (overrideNames.isNotEmpty()) {
        val nightV31 = resourceDirectory.resolve(NIGHT_V31_COLORS_PATH)
        nightV31.parentFile?.mkdirs()
        nightV31.writeText(buildNightV31Overrides(overrideNames, backgroundHex))
    }

    val dynamicPrefs = if (disableDynamicColors) {
        disableDynamicColorsPreference(resourceDirectory)
    } else {
        0
    }
    return AmoledRewriteResult(
        nightColorsReplaced = replaced,
        nightV31Overrides = overrideNames.size,
        preferenceFilesChanged = dynamicPrefs,
    )
}

internal data class AmoledRewriteResult(
    val nightColorsReplaced: Int,
    val nightV31Overrides: Int,
    val preferenceFilesChanged: Int,
)

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

/**
 * Patch-time AMOLED (issue #21 fallback): dark chrome surfaces become pure black
 * (or the chosen opaque hex). Default off. Does not add a runtime theme picker.
 */
@Suppress("unused")
val braveAmoledThemePatch = resourcePatch(
    name = "Brave AMOLED theme",
    description = "Patch-time AMOLED dark theme (issue #21): rewrites Brave dark chrome " +
        "surfaces to pure black (or a custom opaque hex). Overrides Material You night " +
        "colors on Android 12+ and defaults dynamic colors off. Does not change web " +
        "content force-dark, NTP theme collections, or add a runtime color picker. " +
        "Default off.",
    default = false,
) {
    compatibleWith(*amoledCompatibilities().toTypedArray())

    val backgroundColor by colorOption(
        key = "backgroundColor",
        default = "#000000",
        title = "AMOLED background",
        description = "Opaque hex used for dark chrome surfaces. " +
            "Use #000000 for pure black OLED. Example elevated near-black: #0a0a0a.",
        required = true,
        validator = { value -> value == null || normalizeOpaqueHex(value) != null },
    )

    val disableDynamicColors by booleanOption(
        key = "disableDynamicColors",
        default = true,
        title = "Disable Material You dynamic colors",
        description = "Force Brave's wallpaper dynamic-colors switch off (default off) " +
            "so AMOLED surfaces are not replaced by system palette roles.",
        required = false,
    )

    execute {
        val background = normalizeOpaqueHex(backgroundColor ?: "#000000")
            ?: throw PatchException("Invalid AMOLED background color: $backgroundColor")
        val res = get("res")
        if (!res.isDirectory) {
            throw PatchException("Decoded res/ directory not found")
        }

        val result = applyAmoledResources(
            resourceDirectory = res,
            backgroundHex = background,
            disableDynamicColors = disableDynamicColors ?: true,
        )
        if (result.nightColorsReplaced == 0 && result.nightV31Overrides == 0) {
            throw PatchException(
                "No dark surface colors matched; refusing to ship an empty AMOLED rewrite",
            )
        }
        println(
            "[AMOLED] background=$background nightReplaced=${result.nightColorsReplaced} " +
                "nightV31Overrides=${result.nightV31Overrides} " +
                "prefFiles=${result.preferenceFilesChanged}",
        )
    }
}
