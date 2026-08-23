package app.morphe.patches.helium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val HELIUM_KEEP_ALIVE_SERVICE = "app.morphe.extension.helium.HeliumProcessKeepAliveService"
internal const val HELIUM_KEEP_ALIVE_CHANNEL = "helium_extension_runtime"
internal const val HELIUM_KEEP_ALIVE_NOTIFICATION_ID = 0x48454c
internal const val HELIUM_SPECIAL_USE_SUBTYPE = "Maintain browser extension background runtime"
internal const val HELIUM_ACTIVITY_CLASS = "Lorg/chromium/chrome/browser/ChromeTabbedActivity;"
internal const val HELIUM_ACTIVITY_METHOD = "onStart"
internal const val HELIUM_BINDING_CLASS = "Li92;"
internal const val HELIUM_BINDING_METHOD = "a"
internal const val HELIUM_BINDING_RETURN = "Lx82;"
internal val HELIUM_BINDING_PARAMETERS = listOf("La82;", "Ld92;", "I")

internal fun mutateHeliumKeepAliveManifest(document: Document) {
    val manifest = document.documentElement
    val application = document.getElementsByTagName("application").item(0) as? Element
        ?: error("AndroidManifest.xml does not contain an <application> element")
    fun name(e: Element) = e.getAttributeNS("http://schemas.android.com/apk/res/android", "name").ifEmpty { e.getAttribute("android:name") }
    fun permission(value: String) {
        val nodes = document.getElementsByTagName("uses-permission"); var first: Element? = null
        for (i in 0 until nodes.length) { val n = nodes.item(i) as Element; if (name(n) == value) { if (first == null) first = n else manifest.removeChild(n) } }
        if (first == null) manifest.appendChild(document.createElement("uses-permission").apply { setAttribute("android:name", value) })
    }
    permission("android.permission.FOREGROUND_SERVICE"); permission("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
    val services = application.getElementsByTagName("service"); var service: Element? = null
    for (i in 0 until services.length) { val n = services.item(i) as Element; if (name(n) == HELIUM_KEEP_ALIVE_SERVICE) { if (service == null) service = n else application.removeChild(n) } }
    val target = service ?: document.createElement("service").also { application.appendChild(it) }
    target.setAttribute("android:name", HELIUM_KEEP_ALIVE_SERVICE); target.setAttribute("android:exported", "false"); target.removeAttribute("android:process"); target.setAttribute("android:foregroundServiceType", "specialUse")
    val props = target.getElementsByTagName("property"); var prop: Element? = null
    for (i in 0 until props.length) { val n = props.item(i) as Element; if (name(n) == "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE") { if (prop == null) prop = n else target.removeChild(n) } }
    (prop ?: document.createElement("property").also { target.appendChild(it) }).apply { setAttribute("android:name", "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"); setAttribute("android:value", HELIUM_SPECIAL_USE_SUBTYPE) }
}

private val heliumManifestPatch = resourcePatch(name = "Helium keep-alive manifest", description = "Declares one safe foreground service.", default = false) { execute { document("AndroidManifest.xml").use(::mutateHeliumKeepAliveManifest) } }

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
    targets = listOf(AppTarget(version = "152.0.7977.54", isExperimental = true)),
)

/**
 * Forces Chromium child processes into strongest binding state. This can reduce LMK kills,
 * but may increase RAM, battery, and process pressure. It does not identify or reload
 * crashed extensions; recovery remains native to Helium/Chromium.
 */
@Suppress("unused")
val keepHeliumChildProcessesAlivePatch = bytecodePatch(
    name = "Keep Helium Child Processes Alive",
    description = "Experimental: starts one main-process foreground service with a persistent low-priority notification and forces child STRONG binding plus IMPORTANT/STRONG priority updates. May increase RAM, battery, and process pressure; mitigates LMK kills only. No guarantee, force-stop bypass, watchdog, reload, or crash recovery.",
    default = false,
) {
    dependsOn(heliumManifestPatch)
    extendWith("extensions/extension.mpe")
    compatibleWith(heliumChildProcessCompatibility)

    execute {
        val activity = Fingerprint(definingClass = HELIUM_ACTIVITY_CLASS, name = HELIUM_ACTIVITY_METHOD, returnType = "V", parameters = emptyList()).method
        val activityInstructions = activity.implementation!!.instructions
        val superCalls = activityInstructions.withIndex().filter { (_, ins) ->
            val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
            ref?.name == "onStart" && ref.returnType == "V" && ref.parameterTypes.isEmpty() && ins.opcode == Opcode.INVOKE_SUPER
        }
        require(superCalls.size == 1) { "Helium ChromeTabbedActivity.onStart: expected one Activity.onStart super call, found ${superCalls.size}" }
        activity.addInstructions(superCalls.single().index + 1, "invoke-static {p0}, Lapp/morphe/extension/helium/HeliumKeepAliveStarter;->start(Landroid/content/Context;)V")
        val spawn = Fingerprint(
            definingClass = HELIUM_CHILD_PROCESS_CLASS,
            name = "createAndStart",
            returnType = HELIUM_CHILD_PROCESS_CLASS,
            parameters = listOf("J", "[Ljava/lang/String;", "[Lorg/chromium/base/process_launcher/IFileDescriptorInfo;", "Z", "Z", "Z"),
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
            if (ref.definingClass != HELIUM_BINDING_CLASS || ref.name != HELIUM_BINDING_METHOD ||
                ref.returnType != HELIUM_BINDING_RETURN || ref.parameterTypes != HELIUM_BINDING_PARAMETERS) return@mapNotNull null
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
