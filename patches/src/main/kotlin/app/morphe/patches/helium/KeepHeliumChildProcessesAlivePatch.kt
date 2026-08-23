package app.morphe.patches.helium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val HELIUM_KEEP_ALIVE_SERVICE = "app.morphe.extension.helium.HeliumProcessKeepAliveService"
internal const val HELIUM_KEEP_ALIVE_CHANNEL = "helium_extension_runtime"
internal const val HELIUM_KEEP_ALIVE_NOTIFICATION_ID = 0x48454c
internal const val HELIUM_SPECIAL_USE_SUBTYPE = "Maintain browser extension background runtime"
internal const val HELIUM_ACTIVITY_CLASS = "Lorg/chromium/chrome/browser/ChromeTabbedActivity;"
internal const val HELIUM_ACTIVITY_METHOD = "onStart"

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
internal const val HELIUM_SPAWN_INSTRUCTION = "const/16 v%s, 0x4"
internal const val HELIUM_SPAWN_START_ANCHOR = "ChildProcessLauncher.start"

/** Version-unpinned experimental Helium patch using structural fingerprints; ambiguity fails safely. */
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
    description = "Experimental version-unpinned structural patch: starts one main-process foreground service with persistent low-priority notification and forces child STRONG binding plus IMPORTANT/STRONG priority updates. May increase RAM, battery, and process pressure; mitigates LMK kills only. Ambiguous fingerprints fail safely; no guarantee, force-stop bypass, watchdog, reload, or crash recovery.",
    default = false,
) {
    dependsOn(heliumManifestPatch)
    extendWith("extensions/extension.mpe")
    compatibleWith(heliumChildProcessCompatibility)

    execute {
        val helperClass = mutableClassDefBy(HELIUM_CHILD_PROCESS_CLASS)
        val createMethods = helperClass.methods.filter { it.name == "createAndStart" && it.implementation != null }
        val resolvedCreate = resolveCreateAndStart(createMethods.map { it.toStructuralMethod() })
        val resolvedBinding = resolveBindingTarget(resolvedCreate)
        val targetMethod = createMethods.single { it.toStructuralMethod().descriptor == resolvedCreate.descriptor }
        val activityMethods = mutableListOf<MutableMethod>()
        classDefForEach { classDef ->
            if (classDef.type == HELIUM_ACTIVITY_CLASS || classDef.type.endsWith("/ChromeTabbedActivity;")) {
                activityMethods += mutableClassDefBy(classDef).methods.filter {
                    it.name == "onStart" &&
                        it.returnType == "V" &&
                        it.parameterTypes.isEmpty() &&
                        it.implementation != null
                }
            }
        }
        val activityModel = resolveActivityHook(activityMethods.map { it.toStructuralMethod() })
        val activity = activityMethods.single { it.toStructuralMethod().descriptor == activityModel.methodDescriptor }
        val priorities = helperClass.methods.filter { it.name == HELIUM_SET_PRIORITY_METHOD && it.implementation != null }
        val priorityModel = resolvePriorityTarget(priorities.map { it.toStructuralMethod() })
        val priorityMethod = priorities.single()
        activity.addInstructions(
            activityModel.superIndex + 1,
            "invoke-static {p0}, Lapp/morphe/extension/helium/HeliumKeepAliveStarter;->start(Landroid/content/Context;)V",
        )
        targetMethod.addInstructions(resolvedBinding.index, HELIUM_SPAWN_INSTRUCTION.format(resolvedBinding.register))
        priorityMethod.addInstructions(0, "const/16 p${priorityModel.pRegister}, 0x3")
    }
}
