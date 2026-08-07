package app.morphe.patches.helium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

private const val HELIUM_PACKAGE = "io.github.jqssun.helium"
private const val HELIUM_VERSION = "151.0.7922.71"
internal const val HELIUM_CHILD_PROCESS_CLASS =
    "Lorg/chromium/content/browser/ChildProcessLauncherHelperImpl;"
internal const val HELIUM_SET_PRIORITY_METHOD = "setPriority"
internal val HELIUM_SET_PRIORITY_PARAMETERS = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "Z", "Z", "Z", "I")
internal const val HELIUM_PRIORITY_INSTRUCTION = "const/16 p12, 0x3"

/** Exact APK target for the experimental Helium child-process binding patch. */
internal val heliumChildProcessCompatibility = Compatibility(
    name = "Helium Browser",
    packageName = HELIUM_PACKAGE,
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = HELIUM_VERSION, isExperimental = true)),
)

/**
 * Forces Chromium child processes into strongest binding state. This can reduce LMK kills,
 * but may increase RAM, battery, and process pressure. It does not identify or reload
 * crashed extensions; recovery remains native to Helium/Chromium.
 */
@Suppress("unused")
val keepHeliumChildProcessesAlivePatch = bytecodePatch(
    name = "Keep Helium Child Processes Alive",
    description = "Experimental: applies to all Helium child processes; forces strongest binding. May increase RAM, battery, and process pressure; only reduces LMK probability. Does not detect, reload, or back off crashed extensions.",
    default = false,
) {
    compatibleWith(heliumChildProcessCompatibility)

    execute {
        Fingerprint(
            definingClass = HELIUM_CHILD_PROCESS_CLASS,
            name = HELIUM_SET_PRIORITY_METHOD,
            returnType = "I",
            parameters = HELIUM_SET_PRIORITY_PARAMETERS,
        ).method.addInstructions(0, HELIUM_PRIORITY_INSTRUCTION)
    }
}
