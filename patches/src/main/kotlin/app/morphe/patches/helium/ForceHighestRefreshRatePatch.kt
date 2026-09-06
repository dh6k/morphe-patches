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

internal object RebuildDisplayModesFingerprint : Fingerprint(
    definingClass = WINDOW_ANDROID_CLASS,
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == WINDOW_ANDROID_CLASS &&
            method.name.length <= 2 &&
            method.parameterTypes.isEmpty() &&
            method.callsTo(GET_SUPPORTED_REFRESH_RATES).isNotEmpty()
    },
)

internal fun Method.callsTo(name: String): List<MethodReference> {
    val impl = implementation ?: return emptyList()
    return impl.instructions.mapNotNull {
        val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
        if (ref?.name == name) ref else null
    }
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
        SetPreferredRefreshRateFingerprint.methodOrNull?.addInstructions(
            0,
            maxRefreshConstInstruction("p1"),
        ) ?: error("setPreferredRefreshRate(F) not found in WindowAndroid")

        // 2. After every display-mode rebuild, actively request MAX so the stored
        // preference F is max even if nobody ever called the setter.
        val rebuild = RebuildDisplayModesFingerprint.methodOrNull
            ?: error("display-mode rebuild method not found in WindowAndroid")
        val impl = rebuild.implementation
            ?: error("display-mode rebuild has no implementation")
        val lastIndex = impl.instructions.count() - 1
        require(lastIndex >= 0) { "display-mode rebuild is empty" }
        // v0 is dead right before a void return; p0 (this) is still live.
        rebuild.addInstructions(
            lastIndex,
            "const v0, $MAX_REFRESH_BITS\n" +
                "invoke-virtual {p0, v0}, $WINDOW_ANDROID_CLASS->$SET_PREFERRED_REFRESH_RATE(F)V",
        )
    }
}
