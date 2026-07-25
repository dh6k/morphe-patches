/*
 * Custom launcher icon patch inspired by Aliucord Manager:
 * https://github.com/Aliucord/Manager/blob/3f800ff37d8ba385b8f779684a5709a3cce31ec8/app/src/main/kotlin/com/aliucord/manager/patcher/steps/patch/PatchIconsStep.kt
 *
 * This implementation targets Morphe's decoded resource model. It does not copy
 * Aliucord's binary resources.arsc or AXML manipulation code.
 */

package app.morphe.patches

import app.morphe.patcher.patch.ImageSize
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.imageOption
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

private const val ACTION_MAIN_ICON = "android.intent.action.MAIN"
private const val ANDROID_NAMESPACE_ICON = "http://schemas.android.com/apk/res/android"
private const val CUSTOM_ICON_RESOURCE_NAME = "morphe_custom_launcher_icon"
private const val CUSTOM_FOREGROUND_RESOURCE_NAME = "morphe_custom_launcher_foreground"
private const val MAX_ICON_FILE_SIZE = 256L * 1024L
private const val MIN_ICON_DIMENSION = 48
private const val MAX_ICON_DIMENSION = 4096
private val LAUNCHER_CATEGORIES_ICON = setOf(
    "android.intent.category.LAUNCHER",
    "android.intent.category.LEANBACK_LAUNCHER",
    "android.intent.category.CAR_LAUNCHER",
)
private val LOCAL_ICON_REFERENCE = Regex(
    pattern = "^@(?:[A-Za-z0-9_.]+:)?(mipmap|drawable)/([A-Za-z0-9_]+)$",
)

@Suppress("unused")
val changeAppIconPatch = resourcePatch(
    name = "Change app icon",
    description = "Changes the Android launcher icon using a custom PNG image. " +
        "Use a square image with transparent adaptive-icon padding.",
    default = false,
) {
    val customIconPath by imageOption(
        key = "customIcon",
        title = "Custom app icon",
        description = "Square PNG, 48-4096 px, maximum 256 KiB. " +
            "Recommended: 432x432 px with transparent padding.",
        required = true,
        allowedExtensions = listOf("png"),
        recommendedSize = ImageSize(432, 432),
        validator = { value ->
            value == null || value.trim().endsWith(".png", ignoreCase = true)
        },
    )

    execute {
        val sourcePath = customIconPath?.trim().orEmpty()
        if (sourcePath.isEmpty()) {
            throw PatchException("Custom app icon path must not be blank")
        }

        val sourceFile = File(sourcePath)
        validateCustomIconFile(sourceFile)

        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: throw PatchException("AndroidManifest.xml does not contain an <application> element")
            val originalIcon = application.getAndroidAttributeIcon("icon")
            val adaptiveLayers = findAdaptiveIconLayers(get("res"), originalIcon)

            installCustomIconResources(
                resourceDirectory = get("res"),
                sourceFile = sourceFile,
                adaptiveLayers = adaptiveLayers,
            )
            applyCustomIconToManifest(manifest)
        }
    }
}

internal data class IconDimensions(
    val width: Int,
    val height: Int,
)

internal data class AdaptiveIconLayers(
    val background: String = "@android:color/transparent",
    val monochrome: String? = null,
)

internal fun validateCustomIconFile(file: File): IconDimensions {
    if (!file.exists()) {
        throw PatchException("Custom app icon file does not exist: ${file.absolutePath}")
    }
    if (!file.isFile || !file.canRead()) {
        throw PatchException("Custom app icon file is not readable: ${file.absolutePath}")
    }
    if (file.length() > MAX_ICON_FILE_SIZE) {
        throw PatchException("Custom app icon exceeds 256 KiB: ${file.absolutePath}")
    }

    val dimensions = readPngDimensions(file)
        ?: throw PatchException("Custom app icon must be a valid PNG file: ${file.absolutePath}")
    if (dimensions.width != dimensions.height) {
        throw PatchException(
            "Custom app icon must be square, received ${dimensions.width}x${dimensions.height}",
        )
    }
    if (dimensions.width !in MIN_ICON_DIMENSION..MAX_ICON_DIMENSION) {
        throw PatchException(
            "Custom app icon dimension must be between $MIN_ICON_DIMENSION and " +
                "$MAX_ICON_DIMENSION px, received ${dimensions.width}px",
        )
    }

    return dimensions
}

internal fun readPngDimensions(file: File): IconDimensions? =
    runCatching {
        DataInputStream(FileInputStream(file).buffered()).use { input ->
            val signature = ByteArray(8)
            input.readFully(signature)
            if (!signature.contentEquals(PNG_SIGNATURE)) return null

            val headerLength = input.readInt()
            val headerType = ByteArray(4)
            input.readFully(headerType)
            if (headerLength != 13 || !headerType.contentEquals(IHDR_CHUNK)) return null

            val width = input.readInt()
            val height = input.readInt()
            if (width <= 0 || height <= 0) return null

            IconDimensions(width, height)
        }
    }.getOrNull()

internal fun findAdaptiveIconLayers(
    resourceDirectory: File,
    iconReference: String?,
): AdaptiveIconLayers {
    val match = iconReference?.let(LOCAL_ICON_REFERENCE::matchEntire)
        ?: return AdaptiveIconLayers()
    val resourceType = match.groupValues[1]
    val resourceName = match.groupValues[2]

    val candidates = resourceDirectory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { directory ->
            directory.isDirectory &&
                directory.name.startsWith("$resourceType-") &&
                directory.name.hasMinimumAndroidVersion(26)
        }
        .map { it.resolve("$resourceName.xml") }
        .filter(File::isFile)
        .sortedWith(
            compareBy<File> { it.parentFile.name.androidVersionQualifier() }
                .thenBy { it.parentFile.name },
        )

    for (candidate in candidates) {
        val document = runCatching {
            secureDocumentBuilderFactory().newDocumentBuilder().parse(candidate)
        }.getOrNull() ?: continue
        val root = document.documentElement
        if (root.tagName != "adaptive-icon") continue

        val background = root.firstLayerDrawable("background")
            ?: "@android:color/transparent"
        val monochrome = root.firstLayerDrawable("monochrome")
        return AdaptiveIconLayers(background, monochrome)
    }

    return AdaptiveIconLayers()
}

internal fun installCustomIconResources(
    resourceDirectory: File,
    sourceFile: File,
    adaptiveLayers: AdaptiveIconLayers,
) {
    val drawableDirectory = resourceDirectory.resolve("drawable-nodpi").apply(File::mkdirs)
    val legacyDirectory = resourceDirectory.resolve("mipmap-nodpi").apply(File::mkdirs)
    val adaptiveDirectory = resourceDirectory.resolve("mipmap-anydpi-v26").apply(File::mkdirs)

    sourceFile.copyTo(
        target = drawableDirectory.resolve("$CUSTOM_FOREGROUND_RESOURCE_NAME.png"),
        overwrite = true,
    )
    sourceFile.copyTo(
        target = legacyDirectory.resolve("$CUSTOM_ICON_RESOURCE_NAME.png"),
        overwrite = true,
    )

    adaptiveDirectory.resolve("$CUSTOM_ICON_RESOURCE_NAME.xml").writeText(
        buildAdaptiveIconXml(adaptiveLayers),
        StandardCharsets.UTF_8,
    )
}

internal fun applyCustomIconToManifest(document: Document): Int {
    val application = document.getElementsByTagName("application").item(0) as? Element
        ?: throw PatchException("AndroidManifest.xml does not contain an <application> element")
    val iconReference = "@mipmap/$CUSTOM_ICON_RESOURCE_NAME"

    application.setAndroidAttributeIcon("icon", iconReference)
    application.setAndroidAttributeIcon("roundIcon", iconReference)

    var overriddenLauncherComponents = 0
    listOf("activity", "activity-alias").forEach { tagName ->
        val components = document.getElementsByTagName(tagName)
        for (index in 0 until components.length) {
            val component = components.item(index) as Element
            if (!component.hasLauncherIntentFilterIcon()) continue

            var changed = false
            if (component.hasAndroidAttributeIcon("icon")) {
                component.setAndroidAttributeIcon("icon", iconReference)
                changed = true
            }
            if (component.hasAndroidAttributeIcon("roundIcon")) {
                component.setAndroidAttributeIcon("roundIcon", iconReference)
                changed = true
            }
            if (changed) overriddenLauncherComponents++
        }
    }

    return overriddenLauncherComponents
}

private fun buildAdaptiveIconXml(layers: AdaptiveIconLayers): String = buildString {
    appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
    appendLine("""<adaptive-icon xmlns:android="$ANDROID_NAMESPACE_ICON">""")
    appendLine("""    <background android:drawable="${layers.background.escapeXmlAttribute()}" />""")
    appendLine("""    <foreground android:drawable="@drawable/$CUSTOM_FOREGROUND_RESOURCE_NAME" />""")
    layers.monochrome?.let {
        appendLine("""    <monochrome android:drawable="${it.escapeXmlAttribute()}" />""")
    }
    appendLine("</adaptive-icon>")
}

private fun Element.firstLayerDrawable(tagName: String): String? {
    val element = getElementsByTagName(tagName).item(0) as? Element ?: return null
    return element.getAndroidAttributeIcon("drawable").takeIf(String::isNotBlank)
}

private fun Element.hasLauncherIntentFilterIcon(): Boolean {
    val intentFilters = getElementsByTagName("intent-filter")
    for (filterIndex in 0 until intentFilters.length) {
        val intentFilter = intentFilters.item(filterIndex) as Element
        val actions = intentFilter.getElementsByTagName("action")
        val categories = intentFilter.getElementsByTagName("category")
        if (
            actions.hasAndroidNameIcon(ACTION_MAIN_ICON) &&
            categories.hasAnyAndroidNameIcon(LAUNCHER_CATEGORIES_ICON)
        ) {
            return true
        }
    }
    return false
}

private fun org.w3c.dom.NodeList.hasAndroidNameIcon(expectedName: String): Boolean =
    hasAnyAndroidNameIcon(setOf(expectedName))

private fun org.w3c.dom.NodeList.hasAnyAndroidNameIcon(expectedNames: Set<String>): Boolean {
    for (index in 0 until length) {
        val element = item(index) as Element
        if (element.getAndroidAttributeIcon("name") in expectedNames) return true
    }
    return false
}

private fun Element.getAndroidAttributeIcon(name: String): String =
    getAttribute("android:$name").ifEmpty {
        getAttributeNS(ANDROID_NAMESPACE_ICON, name)
    }

private fun Element.hasAndroidAttributeIcon(name: String): Boolean =
    hasAttribute("android:$name") || hasAttributeNS(ANDROID_NAMESPACE_ICON, name)

private fun Element.setAndroidAttributeIcon(name: String, value: String) {
    // Morphe's Document parser is not namespace-aware. Updating by qualified
    // name replaces the decoded android:* attribute instead of adding a second
    // namespace-aware attribute that AAPT may ignore.
    setAttribute("android:$name", value)
}

private fun String.hasMinimumAndroidVersion(minimum: Int): Boolean {
    val version = androidVersionQualifier()
    return version != Int.MAX_VALUE && version >= minimum
}

private fun String.androidVersionQualifier(): Int =
    split('-')
        .firstNotNullOfOrNull { qualifier ->
            qualifier.takeIf { it.startsWith('v') }
                ?.drop(1)
                ?.toIntOrNull()
        }
        ?: Int.MAX_VALUE

private fun String.escapeXmlAttribute(): String =
    replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)
private val IHDR_CHUNK = byteArrayOf(0x49, 0x48, 0x44, 0x52)
