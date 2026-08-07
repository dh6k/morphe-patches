package app.morphe.patches.helium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val HELIUM_PACKAGE = "io.github.jqssun.helium"
internal const val HELIUM_CHILD_PROCESS_CLASS =
    "Lorg/chromium/content/browser/ChildProcessLauncherHelperImpl;"
internal const val HELIUM_SET_PRIORITY_METHOD = "setPriority"
internal val HELIUM_SET_PRIORITY_PARAMETERS = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "Z", "Z", "Z", "I")
internal const val HELIUM_PRIORITY_INSTRUCTION = "const/16 p12, 0x3"
internal const val HELIUM_SPAWN_INSTRUCTION = "const/16 v%s, 0x4"
internal const val HELIUM_SPAWN_START_ANCHOR = "ChildProcessLauncher.start"

/** Exact APK target for the experimental Helium child-process binding patch. */
internal val heliumChildProcessCompatibility = Compatibility(
    name = "Helium Browser",
    packageName = HELIUM_PACKAGE,
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = null, isExperimental = true)),
)

/**
 * Forces Chromium child processes into strongest binding state. This can reduce LMK kills,
 * but may increase RAM, battery, and process pressure. It does not identify or reload
 * crashed extensions; recovery remains native to Helium/Chromium.
 */
@Suppress("unused")
val keepHeliumChildProcessesAlivePatch = bytecodePatch(
    name = "Keep Helium Child Processes Alive",
    description = "Experimental: applies to all Helium child processes; forces STRONG binding at launch and IMPORTANT/STRONG on priority updates. May increase RAM, battery, and process pressure; only reduces LMK probability. Does not detect, reload, or back off crashed extensions.",
    default = false,
) {
    compatibleWith(heliumChildProcessCompatibility)

    execute {
        val spawn = Fingerprint(
            definingClass = HELIUM_CHILD_PROCESS_CLASS,
            name = "createAndStart",
            returnType = HELIUM_CHILD_PROCESS_CLASS,
            parameters = listOf("J", "[Ljava/lang/String;", "[Lorg/chromium/base/process_launcher/IFileDescriptorInfo;", "Z", "Z"),
            strings = listOf("ChildProcessLauncher.start", "renderer", "gpu-process"),
        )
        val spawnInstructions = spawn.method.implementation!!.instructions
        val startAnchors = spawnInstructions.withIndex().filter { (_, instruction) ->
            ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == HELIUM_SPAWN_START_ANCHOR
        }.map { it.index }
        require(startAnchors.size == 1) { "Helium createAndStart: expected one start anchor, found ${startAnchors.size}" }
        val startIndex = startAnchors.single()
        val endIndex = spawnInstructions.withIndex().firstOrNull { (index, instruction) ->
            index > startIndex && (instruction as? ReferenceInstruction)?.reference.let { ref ->
                ref is MethodReference && ref.definingClass == "Lorg/chromium/base/TraceEvent;" && ref.returnType == "V" && ref.parameterTypes == listOf("Ljava/lang/String;")
            }
        }?.index ?: error("Helium createAndStart: TraceEvent end anchor not found")
        val candidates = (startIndex + 1 until endIndex).mapNotNull { index ->
            val instruction = spawnInstructions.elementAt(index)
            val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return@mapNotNull null
            if (ref.returnType == "V" || ref.parameterTypes.lastOrNull() != "I") return@mapNotNull null
            val register = when (instruction) {
                is FiveRegisterInstruction -> when (instruction.registerCount) { 1 -> instruction.registerC; 2 -> instruction.registerD; 3 -> instruction.registerE; 4 -> instruction.registerF; 5 -> instruction.registerG; else -> null }
                is RegisterRangeInstruction -> instruction.startRegister + instruction.registerCount - 1
                else -> null
            } ?: return@mapNotNull null
            register to index
        }
        require(candidates.size == 1) { "Helium createAndStart: expected one binding call, found ${candidates.size}" }
        val (bindingRegister, bindingIndex) = candidates.single()
        require(bindingRegister <= 255) { "Helium createAndStart: binding register out of range: v$bindingRegister" }
        spawn.method.addInstructions(bindingIndex, HELIUM_SPAWN_INSTRUCTION.format(bindingRegister))

        Fingerprint(
            definingClass = HELIUM_CHILD_PROCESS_CLASS,
            name = HELIUM_SET_PRIORITY_METHOD,
            returnType = "I",
            parameters = HELIUM_SET_PRIORITY_PARAMETERS,
        ).method.addInstructions(0, HELIUM_PRIORITY_INSTRUCTION)
    }
}
