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
 * Layers owned here:
 *   values-night/colors.xml      — dark surface hex literals → AMOLED background
 *   values-v31/colors.xml        — system_neutral*_(700+) roles → AMOLED background
 *   values-night-v31/colors.xml  — re-assert those names over Material You
 *   res/xml preference switches  — default the dynamic-colors widget to OFF
 *   bytecode getters             — force Material You readers to return false
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
): Pair<String, Int> {
    val target = normalizeOpaqueHex(targetHex)
        ?: throw PatchException("Color must be opaque hex: $targetHex")
    var replaced = 0
    val out = COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        if (matcher(raw) && normalizeOpaqueHex(raw) != target) {
            replaced++
            """<color name="$name">$target</color>"""
        } else {
            match.value
        }
    }
    return out to replaced
}

internal fun rewriteNightColorXml(xml: String, backgroundHex: String): Pair<String, Int> =
    rewriteHexGroup(xml, backgroundHex) { isAmoledSurfaceColor(it) }

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
    rewriteHexGroup(xml, accentHex) { isAccentColor(it) }

/** In-place: kill Material You dark roles so they cannot win over night-v31. */
internal fun rewriteMaterialYouDarkNeutrals(xml: String, backgroundHex: String): Pair<String, Int> {
    val target = normalizeOpaqueHex(backgroundHex)
        ?: throw PatchException("AMOLED background must be an opaque hex color: $backgroundHex")
    var replaced = 0
    val out = COLOR_ELEMENT.replace(xml) { match ->
        val name = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        if (MATERIAL_YOU_DARK_NEUTRAL_ROLES.matches(raw)) {
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

internal fun applyAmoledResources(
    resourceDirectory: File,
    backgroundHex: String,
    textColorHex: String? = null,
    accentColorHex: String? = null,
    disableDynamicColors: Boolean = true,
): AmoledRewriteResult {
    // Chrome windowBackground / colorBackground live as dark hexes in
    // values/colors.xml (#121212 / #ff303030 / …), not only in values-night.
    val dayFile = resourceDirectory.resolve(DAY_COLORS_PATH)
    var dayReplaced = 0
    var dayTextReplaced = 0
    var dayAccentReplaced = 0
    if (dayFile.isFile) {
        var xml = dayFile.readText()
        val (afterBg, bgCount) = rewriteNightColorXml(xml, backgroundHex)
        xml = afterBg
        dayReplaced = bgCount
        if (textColorHex != null) {
            val (afterText, count) = rewriteTextColorXml(xml, textColorHex)
            xml = afterText
            dayTextReplaced = count
        }
        if (accentColorHex != null) {
            val (afterAccent, count) = rewriteAccentColorXml(xml, accentColorHex)
            xml = afterAccent
            dayAccentReplaced = count
        }
        dayFile.writeText(xml)
    }

    val nightFile = resourceDirectory.resolve(NIGHT_COLORS_PATH)
    if (!nightFile.isFile) {
        throw PatchException("Brave night color resources not found: $NIGHT_COLORS_PATH")
    }
    var nightXml = nightFile.readText()
    val (afterNightBg, nightReplaced) = rewriteNightColorXml(nightXml, backgroundHex)
    nightXml = afterNightBg
    var nightTextReplaced = 0
    var nightAccentReplaced = 0
    if (textColorHex != null) {
        val (afterText, count) = rewriteTextColorXml(nightXml, textColorHex)
        nightXml = afterText
        nightTextReplaced = count
    }
    if (accentColorHex != null) {
        val (afterAccent, count) = rewriteAccentColorXml(nightXml, accentColorHex)
        nightXml = afterAccent
        nightAccentReplaced = count
    }
    nightFile.writeText(nightXml)

    val surfaceNames = collectSurfaceColorNames(nightXml) +
        (if (dayFile.isFile) collectSurfaceColorNames(dayFile.readText()) else emptyList())
    val v31File = resourceDirectory.resolve(V31_COLORS_PATH)
    var v31Replaced = 0
    val materialYouNames = if (v31File.isFile) {
        val v31Xml = v31File.readText()
        val names = collectMaterialYouDarkNeutralNames(v31Xml)
        val (next, count) = rewriteMaterialYouDarkNeutrals(v31Xml, backgroundHex)
        v31File.writeText(next)
        v31Replaced = count
        names
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
        dayColorsReplaced = dayReplaced,
        nightColorsReplaced = nightReplaced,
        v31MaterialYouReplaced = v31Replaced,
        nightV31Overrides = overrideNames.size,
        textColorsReplaced = dayTextReplaced + nightTextReplaced,
        accentColorsReplaced = dayAccentReplaced + nightAccentReplaced,
        preferenceFilesChanged = dynamicPrefs,
    )
}

internal data class AmoledRewriteResult(
    val dayColorsReplaced: Int,
    val nightColorsReplaced: Int,
    val v31MaterialYouReplaced: Int,
    val nightV31Overrides: Int,
    val textColorsReplaced: Int = 0,
    val accentColorsReplaced: Int = 0,
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
            braveAmoledThemePatch.options["backgroundColor"]?.value as? String ?: "#000000",
        ) ?: throw PatchException("Invalid AMOLED background color")
        val text = (braveAmoledThemePatch.options["textColor"]?.value as? String)
            ?.let { normalizeOpaqueHex(it) }
        val accent = (braveAmoledThemePatch.options["accentColor"]?.value as? String)
            ?.let { normalizeOpaqueHex(it) }
        val res = get("res")
        if (!res.isDirectory) {
            throw PatchException("Decoded res/ directory not found")
        }
        val disableDynamic =
            (braveAmoledThemePatch.options["disableDynamicColors"]?.value as? Boolean) ?: true
        val result = applyAmoledResources(
            resourceDirectory = res,
            backgroundHex = background,
            textColorHex = text,
            accentColorHex = accent,
            disableDynamicColors = disableDynamic,
        )
        if (result.dayColorsReplaced + result.nightColorsReplaced + result.nightV31Overrides == 0) {
            throw PatchException(
                "No dark surface colors matched; refusing to ship an empty AMOLED rewrite",
            )
        }
        println(
            "[AMOLED] background=$background text=${text ?: "-"} accent=${accent ?: "-"} " +
                "dayReplaced=${result.dayColorsReplaced} " +
                "nightReplaced=${result.nightColorsReplaced} " +
                "v31Replaced=${result.v31MaterialYouReplaced} " +
                "nightV31Overrides=${result.nightV31Overrides} " +
                "textReplaced=${result.textColorsReplaced} " +
                "accentReplaced=${result.accentColorsReplaced} " +
                "prefFiles=${result.preferenceFilesChanged}",
        )
    }
}

/**
 * Patch-time AMOLED (issue #21 fallback): dark chrome surfaces become pure black
 * (or the chosen opaque hex). Also forces Material You readers off so the system
 * palette cannot repaint chrome. Default off. No runtime theme picker.
 */
@Suppress("unused")
val braveAmoledThemePatch: BytecodePatch = bytecodePatch(
    name = "Brave AMOLED theme",
    description = "Patch-time AMOLED dark theme (issue #21): rewrites Brave dark chrome " +
        "surfaces to pure black (or a custom opaque hex). Optional text and accent " +
        "colors (defaults keep Brave's #f0f2ff / #737ade). Forces Material You dynamic " +
        "colors off in bytecode (the pref is non-persistent) and overrides system " +
        "neutral night roles on Android 12+. Apply Dark theme in Brave to see it. " +
        "Does not change web content force-dark, NTP theme collections, or add a " +
        "runtime color picker. Default off.",
    default = false,
) {
    compatibleWith(*amoledCompatibilities().toTypedArray())
    dependsOn(braveAmoledResourcePatch)

    val backgroundColor by colorOption(
        key = "backgroundColor",
        default = "#000000",
        title = "AMOLED background",
        description = "Opaque hex used for dark chrome surfaces. " +
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
            "clickable but readers always return off.",
        required = false,
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
