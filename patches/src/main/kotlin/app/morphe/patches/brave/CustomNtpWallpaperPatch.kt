/*
 * Alpha NTP wallpaper injection for Brave (issue #13).
 *
 * Brave's New tab page settings only expose "Show background images";
 * Chromium/Brave still ship NTPBackgroundImagesBridge wallpaper factories.
 * This patch forces those factories to describe a patch-time PNG so the NTP
 * background becomes the supplied custom image. Ambient wallpaper rendering
 * stays in libchrome.so — if factories are bypassed the patch is a no-op.
 */
package app.morphe.patches.brave

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.ImageSize
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.ResourcePatch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.imageOption
import app.morphe.patcher.patch.resourcePatch
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

internal const val NTP_WALLPAPER_RESOURCE_NAME = "morphe_custom_ntp_wallpaper"
internal const val NTP_BACKGROUND_IMAGES_BRIDGE =
    "Lorg/chromium/chrome/browser/ntp_background_images/NTPBackgroundImagesBridge;"
internal const val BACKGROUND_IMAGE_MODEL =
    "Lorg/chromium/chrome/browser/ntp_background_images/model/BackgroundImage;"
internal const val WALLPAPER_MODEL =
    "Lorg/chromium/chrome/browser/ntp_background_images/model/Wallpaper;"

internal const val MIN_WALLPAPER_DIMENSION = 480
internal const val MAX_WALLPAPER_DIMENSION = 8192
internal const val MAX_WALLPAPER_FILE_SIZE = 8L * 1024L * 1024L

internal data class WallpaperDimensions(
    val width: Int,
    val height: Int,
)

internal object CreateWallpaperFingerprint : Fingerprint(
    definingClass = NTP_BACKGROUND_IMAGES_BRIDGE,
    name = "createWallpaper",
    returnType = BACKGROUND_IMAGE_MODEL,
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;"),
)

internal object CreateBrandedWallpaperFingerprint : Fingerprint(
    definingClass = NTP_BACKGROUND_IMAGES_BRIDGE,
    name = "createBrandedWallpaper",
    returnType = WALLPAPER_MODEL,
    parameters = listOf(
        "Ljava/lang/String;",
        "I",
        "I",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "I",
    ),
)

/**
 * `android.resource://<package>/drawable/morphe_custom_ntp_wallpaper`.
 * Package name is baked at patch time (apps may be renamed, e.g. Origin Nightly).
 */
internal fun ntpWallpaperResourceUri(packageName: String): String =
    "android.resource://$packageName/drawable/$NTP_WALLPAPER_RESOURCE_NAME"

/**
 * Overwrites createWallpaper(String, String, String) params then falls through.
 * Only touches p0-p2 (all String) — never v0/v1, which alias params on tight methods.
 */
internal fun forceCreateWallpaperParamsSmali(packageName: String): String = """
    const-string p0, "$NTP_WALLPAPER_RESOURCE_NAME"
    const-string p1, "${ntpWallpaperResourceUri(packageName)}"
    const-string p2, "Custom"
"""

/**
 * Overwrites createBrandedWallpaper string params (id + URL/credit slots) then
 * falls through. CRITICAL: p1/p2/p5/p8/p9 are int/boolean and on a 10-param
 * static method they alias v1.. — a prior prologue that used v0/v1 caused
 * VerifyError (String in Integer register) and a native SIGTRAP on class load.
 */
internal fun forceCreateBrandedWallpaperParamsSmali(packageName: String): String = """
    const-string p0, "$NTP_WALLPAPER_RESOURCE_NAME"
    const-string p3, "${ntpWallpaperResourceUri(packageName)}"
    move-object p4, p3
    move-object p6, p3
    move-object p7, p3
"""

internal fun validateWallpaperFile(file: File): WallpaperDimensions {
    if (!file.exists()) {
        throw PatchException("Custom NTP wallpaper file does not exist: ${file.absolutePath}")
    }
    if (!file.isFile || !file.canRead()) {
        throw PatchException("Custom NTP wallpaper file is not readable: ${file.absolutePath}")
    }
    if (file.length() > MAX_WALLPAPER_FILE_SIZE) {
        throw PatchException(
            "Custom NTP wallpaper exceeds 8 MiB: ${file.absolutePath}",
        )
    }
    val dimensions = readPngDimensions(file)
        ?: throw PatchException("Custom NTP wallpaper must be a valid PNG file: ${file.absolutePath}")
    if (dimensions.width !in MIN_WALLPAPER_DIMENSION..MAX_WALLPAPER_DIMENSION ||
        dimensions.height !in MIN_WALLPAPER_DIMENSION..MAX_WALLPAPER_DIMENSION
    ) {
        throw PatchException(
            "Custom NTP wallpaper must be between $MIN_WALLPAPER_DIMENSION and " +
                "$MAX_WALLPAPER_DIMENSION px on each side, received " +
                "${dimensions.width}x${dimensions.height}",
        )
    }
    return dimensions
}

internal fun readPngDimensions(file: File): WallpaperDimensions? =
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

            WallpaperDimensions(width, height)
        }
    }.getOrNull()

internal fun installWallpaperResource(resourceDirectory: File, sourceFile: File) {
    val drawableDirectory = resourceDirectory.resolve("drawable-nodpi").apply(File::mkdirs)
    sourceFile.copyTo(
        target = drawableDirectory.resolve("$NTP_WALLPAPER_RESOURCE_NAME.png"),
        overwrite = true,
    )
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

private fun customNtpWallpaperCompatibilities() = listOf(
    Compatibility(
        name = "Brave Browser",
        packageName = "com.brave.browser",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
    Compatibility(
        name = "Brave Beta",
        packageName = "com.brave.browser_beta",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
    Compatibility(
        name = "Brave Nightly",
        packageName = "com.brave.browser_nightly",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
)

// Options live on the public patch; this dependency only materializes the PNG.
private val customNtpWallpaperResourcePatch: ResourcePatch = resourcePatch(
    name = "Custom NTP wallpaper resources",
    description = "Installs the patch-time wallpaper PNG as a Brave drawable.",
    default = false,
) {
    compatibleWith(*customNtpWallpaperCompatibilities().toTypedArray())
    execute {
        val sourcePath = customNtpWallpaperPatch.options["customWallpaper"]?.value as? String
        val trimmed = sourcePath?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            throw PatchException("Custom NTP wallpaper path must not be blank")
        }
        val sourceFile = File(trimmed)
        validateWallpaperFile(sourceFile)
        installWallpaperResource(get("res"), sourceFile)
    }
}

/**
 * Alpha / experimental: forces Brave NTP wallpaper factories to the supplied PNG.
 * Default off. Ambiguous or missing factories fail closed.
 */
@Suppress("unused")
val customNtpWallpaperPatch: BytecodePatch = bytecodePatch(
    name = "Custom NTP wallpaper",
    description = "Alpha experimental version-unpinned patch (issue #13): forces Brave NTP " +
        "background factories (NTPBackgroundImagesBridge.createWallpaper / createBrandedWallpaper) " +
        "to a custom PNG chosen at patch time. Brave's New tab page settings only toggle " +
        "\"Show background images\" — this patch supplies the missing custom wallpaper path. " +
        "Rendering remains in libchrome.so; if a build bypasses these factories the image is " +
        "ignored. Ambiguous targets fail closed. Default off.",
    default = false,
) {
    dependsOn(customNtpWallpaperResourcePatch)
    compatibleWith(*customNtpWallpaperCompatibilities().toTypedArray())

    val customWallpaperPath by imageOption(
        key = "customWallpaper",
        title = "Custom NTP wallpaper",
        description = "PNG wallpaper used as the Brave new-tab background. " +
            "480-8192 px on each side, maximum 8 MiB.",
        required = true,
        allowedExtensions = listOf("png"),
        recommendedSize = ImageSize(1920, 1080),
        validator = { value -> value == null || value.trim().endsWith(".png", ignoreCase = true) },
    )

    execute {
        val sourcePath = customWallpaperPath?.trim().orEmpty()
        if (sourcePath.isEmpty()) {
            throw PatchException("Custom NTP wallpaper path must not be blank")
        }
        validateWallpaperFile(File(sourcePath))

        val packageName = packageMetadata.packageName
        if (packageName.isNullOrBlank()) {
            error("package name unavailable; cannot build wallpaper resource URI")
        }

        CreateWallpaperFingerprint.methodOrNull?.addInstructions(
            0,
            forceCreateWallpaperParamsSmali(packageName),
        ) ?: error("NTPBackgroundImagesBridge.createWallpaper not found")

        CreateBrandedWallpaperFingerprint.methodOrNull?.addInstructions(
            0,
            forceCreateBrandedWallpaperParamsSmali(packageName),
        ) ?: error("NTPBackgroundImagesBridge.createBrandedWallpaper not found")
    }
}
