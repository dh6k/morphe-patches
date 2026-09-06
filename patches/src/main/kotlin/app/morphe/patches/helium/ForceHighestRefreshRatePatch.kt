package app.morphe.patches.helium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val REFRESH_PACKAGE = "io.github.jqssun.helium"

/**
 * Float.MAX_VALUE bits. Any request >= the panel max makes WindowAndroid.l()
 * pick the highest-refresh Display$Mode (nearest to +inf), so this works on
 * 60/90/120/144/165Hz+ panels without knowing the max at patch time.
 */
internal const val MAX_REFRESH_BITS = 0x7f7fFFFF
internal const val WINDOW_ANDROID_CLASS = "Lorg/chromium/ui/base/WindowAndroid;"
internal const val SET_PREFERRED_REFRESH_RATE = "setPreferredRefreshRate"
internal const val GET_SUPPORTED_REFRESH_RATES = "getSupportedRefreshRates"

internal fun maxRefreshConstInstruction(register: String) =
    "const $register, $MAX_REFRESH_BITS"

internal object SetPreferredRefreshRateFingerprint : Fingerprint(
    definingClass = WINDOW_ANDROID_CLASS,
    name = SET_PREFERRED_REFRESH_RATE,
    returnType = "V",
    parameters = listOf("F"),
)

internal object NearestDisplayModeFingerprint : Fingerprint(
    definingClass = WINDOW_ANDROID_CLASS,
    returnType = "V",
    parameters = listOf("F"),
    custom = { method, classDef ->
        classDef.type == WINDOW_ANDROID_CLASS &&
            method.name != SET_PREFERRED_REFRESH_RATE &&
            method.callsTo("getRefreshRate").any { it.definingClass == "Landroid/view/Display\$Mode;" } &&
            method.stringLiterals().contains("Refresh rate not supported : ")
    },
)

internal fun Method.callsTo(name: String): List<MethodReference> {
    val impl = implementation ?: return emptyList()
    return impl.instructions.mapNotNull {
        val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
        if (ref?.name == name) ref else null
    }
}

internal fun Method.stringLiterals(): List<String> {
    val impl = implementation ?: return emptyList()
    return impl.instructions.mapNotNull {
        (it as? ReferenceInstruction)?.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference
    }.map { it.string }
}

internal val forceRefreshRateCompatibility = Compatibility(
    name = "Titanium Browser for Android",
    packageName = REFRESH_PACKAGE,
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = null, isExperimental = true)),
)

@Suppress("unused")
val forceHighestRefreshRatePatch = bytecodePatch(
    name = "Force highest refresh rate",
    description = "Experimental version-unpinned patch: forces Chromium to pick the highest-refresh display mode by requesting Float.MAX_VALUE through WindowAndroid. Works on any panel (60/90/120/144/165Hz+) without knowing the max at patch time. May increase battery usage; ambiguous targets fail closed.",
    default = false,
) {
    compatibleWith(forceRefreshRateCompatibility)

    execute {
        // 1. Any caller-supplied rate becomes MAX, so l() settles on the top mode.
        NearestDisplayModeFingerprint.methodOrNull?.addInstructions(
            0,
            maxRefreshConstInstruction("p1"),
        ) ?: error("nearest-display-mode method not found in WindowAndroid")

        // 2. Same guard on the public setter in case native code calls it directly.
        // Both writes target the float param at method head (no spare register,
        // no clobber of `this`), the pattern validated since v1.5.0.
        SetPreferredRefreshRateFingerprint.methodOrNull?.addInstructions(
            0,
            maxRefreshConstInstruction("p1"),
        ) ?: error("setPreferredRefreshRate(F) not found in WindowAndroid")
    }
}
