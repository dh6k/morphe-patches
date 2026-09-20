package app.morphe.patches.quetta

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.helium.callsTo
import app.morphe.patches.helium.maxRefreshConstInstruction
import com.android.tools.smali.dexlib2.iface.Method

/**
 * Quetta-specific Force highest refresh rate.
 *
 * Static anchors from net.quetta.browser 2.0.5 (base.apk inside Quetta_2.0.5 .apks):
 * - WindowAndroid.setPreferredRefreshRate(F)V keeps the public name; forwards to
 *   an obfuscated nearest-mode worker (e.g. u(F)).
 * - That worker picks Display Mode via getRefreshRate + getModeId and applies it
 *   through Window.setAttributes.
 * - Titanium's "Refresh rate not supported : " literal is absent; do not use the
 *   helium fingerprint here.
 */
private const val WINDOW_ANDROID_CLASS = "Lorg/chromium/ui/base/WindowAndroid;"
private const val SET_PREFERRED_REFRESH_RATE = "setPreferredRefreshRate"
private const val DISPLAY_MODE = "Landroid/view/Display\$Mode;"
private const val ANDROID_WINDOW = "Landroid/view/Window;"

private fun Method.callsToType(name: String, definingClass: String): Boolean =
    callsTo(name).any { it.definingClass == definingClass }

internal object QuettaSetPreferredRefreshRateFingerprint : Fingerprint(
    definingClass = WINDOW_ANDROID_CLASS,
    name = SET_PREFERRED_REFRESH_RATE,
    returnType = "V",
    parameters = listOf("F"),
)

internal object QuettaNearestDisplayModeFingerprint : Fingerprint(
    definingClass = WINDOW_ANDROID_CLASS,
    returnType = "V",
    parameters = listOf("F"),
    custom = { method, classDef ->
        classDef.type == WINDOW_ANDROID_CLASS &&
            method.name != SET_PREFERRED_REFRESH_RATE &&
            method.callsToType("getRefreshRate", DISPLAY_MODE) &&
            method.callsToType("getModeId", DISPLAY_MODE) &&
            method.callsToType("setAttributes", ANDROID_WINDOW)
    },
)

@Suppress("unused")
val forceQuettaHighestRefreshRatePatch = bytecodePatch(
    name = "Force highest refresh rate",
    description = "Quetta-adapted experimental version-unpinned patch: forces Chromium WindowAndroid to pick the highest-refresh Display mode by writing Float.MAX_VALUE into setPreferredRefreshRate(F) and the structural nearest-mode worker (getRefreshRate + getModeId + Window.setAttributes). Validated statically on Quetta 2.0.5 base APK; may increase battery usage; ambiguous targets fail closed.",
    default = false,
) {
    compatibleWith(*quettaCompatibilities.toTypedArray())

    execute {
        QuettaNearestDisplayModeFingerprint.methodOrNull?.addInstructions(
            0,
            maxRefreshConstInstruction("p1"),
        ) ?: error("Quetta nearest-display-mode method not found in WindowAndroid")

        QuettaSetPreferredRefreshRateFingerprint.methodOrNull?.addInstructions(
            0,
            maxRefreshConstInstruction("p1"),
        ) ?: error("Quetta setPreferredRefreshRate(F) not found in WindowAndroid")
    }
}
