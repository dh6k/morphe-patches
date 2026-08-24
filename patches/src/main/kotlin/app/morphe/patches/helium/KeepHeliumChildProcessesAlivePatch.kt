package app.morphe.patches.helium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.ClassDef
import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val HELIUM_KEEP_ALIVE_SERVICE = "app.morphe.extension.helium.HeliumProcessKeepAliveService"
// Keep in sync with HeliumProcessKeepAliveService.CHANNEL_ID / NOTIFICATION_ID (extension module).
internal const val HELIUM_KEEP_ALIVE_CHANNEL = "helium_extension_runtime"
internal const val HELIUM_KEEP_ALIVE_NOTIFICATION_ID = 0x48454c
internal const val HELIUM_SPECIAL_USE_SUBTYPE = "Maintain browser extension background runtime"
internal const val HELIUM_ACTIVITY_CLASS = "Lorg/chromium/chrome/browser/ChromeTabbedActivity;"
internal const val HELIUM_LIFECYCLE_ON_START = "onStart"
internal const val HELIUM_LIFECYCLE_ON_RESUME = "onResume"
internal const val HELIUM_PRIORITY_METHOD = "setPriority"
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

internal fun normalizeManifestClassName(packageName: String, className: String): String {
    val qualified = when {
        className.startsWith(".") -> packageName + className
        '.' in className -> className
        else -> "$packageName.$className"
    }
    return "L${qualified.replace('.', '/')};"
}
internal fun resolveLauncherActivityClasses(document: Document): Set<String> {
    val manifest = document.documentElement
    val packageName = manifest.getAttribute("package")
    val result = linkedSetOf<String>()
    for (tag in listOf("activity", "activity-alias")) {
        val nodes = document.getElementsByTagName(tag)
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val filters = element.getElementsByTagName("intent-filter")
            val launcher = (0 until filters.length).any { filterIndex ->
                val filter = filters.item(filterIndex) as Element
                val actions = filter.getElementsByTagName("action")
                val categories = filter.getElementsByTagName("category")
                val hasMain = (0 until actions.length).any {
                    (actions.item(it) as Element).getAttributeNS(ANDROID_NS, "name") ==
                        "android.intent.action.MAIN"
                }
                val hasLauncher = (0 until categories.length).any {
                    (categories.item(it) as Element).getAttributeNS(ANDROID_NS, "name") ==
                        "android.intent.category.LAUNCHER"
                }
                hasMain && hasLauncher
            }
            if (!launcher) continue
            val attribute = if (tag == "activity-alias") "targetActivity" else "name"
            val className = element.getAttributeNS(ANDROID_NS, attribute)
            if (className.isNotEmpty()) result += normalizeManifestClassName(packageName, className)
        }
    }
    return result
}
/** Bridges resource and bytecode patch contexts via packageName; avoids IdentityHashMap leak. */
private object LauncherActivityRegistry {
    private val entries = mutableMapOf<String, Set<String>>()

    @Synchronized
    fun put(metadata: PackageMetadata, classes: Set<String>) {
        entries[metadata.packageName] = classes
    }

    @Synchronized
    fun take(metadata: PackageMetadata): Set<String> = entries.remove(metadata.packageName).orEmpty()

    @Synchronized
    fun clear(metadata: PackageMetadata) {
        entries.remove(metadata.packageName)
    }
}

internal fun mutateHeliumKeepAliveManifest(document: Document) {
    val manifest = document.documentElement
    val application = document.getElementsByTagName("application").item(0) as? Element
        ?: error("AndroidManifest.xml does not contain an <application> element")

    fun attrName(e: Element): String =
        e.getAttributeNS(ANDROID_NS, "name").ifEmpty { e.getAttribute("android:name") }

    fun ensurePermission(value: String) {
        val nodes = document.getElementsByTagName("uses-permission")
        var first: Element? = null
        val duplicates = mutableListOf<Element>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as Element
            if (attrName(node) == value) {
                if (first == null) first = node else duplicates += node
            }
        }
        duplicates.forEach { manifest.removeChild(it) }
        if (first == null) {
            manifest.appendChild(
                document.createElement("uses-permission").apply { setAttribute("android:name", value) },
            )
        }
    }
    ensurePermission("android.permission.FOREGROUND_SERVICE")
    ensurePermission("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")

    val services = application.getElementsByTagName("service")
    var service: Element? = null
    val serviceDups = mutableListOf<Element>()
    for (i in 0 until services.length) {
        val node = services.item(i) as Element
        if (attrName(node) == HELIUM_KEEP_ALIVE_SERVICE) {
            if (service == null) service = node else serviceDups += node
        }
    }
    serviceDups.forEach { application.removeChild(it) }
    val target = service ?: document.createElement("service").also { application.appendChild(it) }
    target.setAttribute("android:name", HELIUM_KEEP_ALIVE_SERVICE)
    target.setAttribute("android:exported", "false")
    target.removeAttribute("android:process")
    target.setAttribute("android:foregroundServiceType", "specialUse")

    val props = target.getElementsByTagName("property")
    var prop: Element? = null
    val propDups = mutableListOf<Element>()
    for (i in 0 until props.length) {
        val node = props.item(i) as Element
        if (attrName(node) == "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE") {
            if (prop == null) prop = node else propDups += node
        }
    }
    propDups.forEach { target.removeChild(it) }
    val subtype = prop ?: document.createElement("property").also { target.appendChild(it) }
    subtype.setAttribute("android:name", "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE")
    subtype.setAttribute("android:value", HELIUM_SPECIAL_USE_SUBTYPE)
}
private val heliumManifestPatch = resourcePatch(
    name = "Helium keep-alive manifest",
    description = "Declares one safe foreground service.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            LauncherActivityRegistry.put(packageMetadata, resolveLauncherActivityClasses(manifest))
            mutateHeliumKeepAliveManifest(manifest)
        }
    }
}
private const val HELIUM_PACKAGE = "io.github.jqssun.helium"
internal const val HELIUM_CHILD_PROCESS_CLASS =
    "Lorg/chromium/content/browser/ChildProcessLauncherHelperImpl;"
@Deprecated("Use HELIUM_PRIORITY_METHOD", ReplaceWith("HELIUM_PRIORITY_METHOD"))
internal const val HELIUM_SET_PRIORITY_METHOD: String = HELIUM_PRIORITY_METHOD
// Chromium ChildBindingState values observed across validated Helium 149-152 APKs.
internal const val HELIUM_STRONG_BINDING_VALUE = 0x4
internal const val HELIUM_IMPORTANT_PRIORITY_VALUE = 0x3
internal const val HELIUM_SPAWN_START_ANCHOR = "ChildProcessLauncher.start"

internal fun heliumStrongBindingInstruction(register: Int) =
    "const/16 v$register, $HELIUM_STRONG_BINDING_VALUE"

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
    description = "Experimental version-unpinned structural/data-flow patch: starts one main-process foreground service with persistent low-priority notification and forces child STRONG binding plus IMPORTANT/STRONG priority updates. Tolerates routine signature, register, and helper-name changes; ambiguous targets fail closed. May increase RAM, battery, and process pressure; mitigates LMK kills only.",
    default = false,
) {
    dependsOn(heliumManifestPatch)
    extendWith("extensions/extension.mpe")
    compatibleWith(heliumChildProcessCompatibility)

    execute {
        val launcherActivities: Set<String>
        try {
            launcherActivities = LauncherActivityRegistry.take(packageMetadata)
            val helperClass = mutableClassDefBy(HELIUM_CHILD_PROCESS_CLASS)
        val createMethods = helperClass.methods.filter { it.implementation != null }
        val resolvedCreate = resolveCreateAndStart(createMethods.map { it.toStructuralMethod() })
            val resolvedBinding = resolveBindingTarget(resolvedCreate)
            validateBindingRegisterSafety(resolvedCreate, resolvedBinding)
            val targetMethod = createMethods.single { it.toStructuralMethod().descriptor == resolvedCreate.descriptor }
        val activityClasses = mutableListOf<ActivityClassModel>()
        val classDefsByType = mutableMapOf<String, ClassDef>()
        classDefForEach { classDef ->
            classDefsByType[classDef.type] = classDef
            val methods = classDef.methods.filter {
                (it.name == HELIUM_LIFECYCLE_ON_START || it.name == HELIUM_LIFECYCLE_ON_RESUME) &&
                    it.returnType == "V" &&
                    it.parameterTypes.isEmpty() &&
                    it.implementation != null
            }
            activityClasses += ActivityClassModel(
                type = classDef.type,
                superclass = classDef.superclass,
                methods = methods.map { it.toStructuralMethod() },
                isLauncher = classDef.type in launcherActivities,
                browserEvidence = classDef.type.contains("Chrome", ignoreCase = true) ||
                    classDef.type.contains("Tabbed", ignoreCase = true) ||
                    classDef.type.contains("Browser", ignoreCase = true),
            )
        }
        val activityModel = resolveActivityHook(activityClasses)
        val activityOwner = activityModel.methodDescriptor.substringBefore("->")
        val activityClass = classDefsByType[activityOwner]
            ?: error("Helium activity lifecycle owner missing: $activityOwner")
        val activity = mutableClassDefBy(activityClass).methods.single {
            it.toStructuralMethod().descriptor == activityModel.methodDescriptor
        }
        val priorities = helperClass.methods.filter { it.implementation != null }
        val priorityModel = resolvePriorityTarget(priorities.map { it.toStructuralMethod() })
        val priorityMethod = priorities.single {
            it.toStructuralMethod().descriptor == priorityModel.methodDescriptor
        }
        activity.addInstructions(
            activityModel.superIndex + 1,
            "invoke-static {p0}, Lapp/morphe/extension/helium/HeliumKeepAliveStarter;->start(Landroid/content/Context;)V",
        )
        targetMethod.addInstructions(
            resolvedBinding.index,
            heliumStrongBindingInstruction(resolvedBinding.register),
        )
            priorityMethod.addInstructions(0, "const/16 p${priorityModel.parameterWordOffset}, ${HELIUM_IMPORTANT_PRIORITY_VALUE}")
        } catch (e: HeliumResolutionException) {
            LauncherActivityRegistry.clear(packageMetadata)
            throw e
        }
    }
}
