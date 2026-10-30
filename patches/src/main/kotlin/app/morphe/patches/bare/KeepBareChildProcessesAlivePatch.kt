package app.morphe.patches.bare

import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.ResourcePatch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.helium.ActivityClassModel
import app.morphe.patches.helium.HELIUM_CHILD_PROCESS_CLASS
import app.morphe.patches.helium.HELIUM_LIFECYCLE_ON_RESUME
import app.morphe.patches.helium.HELIUM_LIFECYCLE_ON_START
import app.morphe.patches.helium.HeliumResolutionException
import app.morphe.patches.helium.heliumNoteHelperSmali
import app.morphe.patches.helium.heliumScopedBindingSmali
import app.morphe.patches.helium.heliumScopedImportanceSmali
import app.morphe.patches.helium.heliumSetSpawnCommandLineSmali
import app.morphe.patches.helium.parameterWordOffset
import app.morphe.patches.helium.resolveActivityHook
import app.morphe.patches.helium.resolveBindingTarget
import app.morphe.patches.helium.resolveCreateAndStart
import app.morphe.patches.helium.resolveExtensionScope
import app.morphe.patches.helium.resolveLauncherActivityClasses
import app.morphe.patches.helium.resolvePriorityTarget
import app.morphe.patches.helium.sanitizeHeliumNotificationLine
import app.morphe.patches.helium.toStructuralMethod
import com.android.tools.smali.dexlib2.iface.ClassDef
import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val BARE_PACKAGE = "org.barebrowser"

// Extension-side identifiers. Nothing here is branded: the service, its notification
// and its manifest keys are namespaced to this app family only by the package name.
internal const val BARE_KEEP_ALIVE_SERVICE = "app.morphe.extension.bare.BareProcessKeepAliveService"
internal const val BARE_SPECIAL_USE_SUBTYPE = "Maintain browser extension background runtime"
internal const val BARE_META_NOTIFICATION_TITLE = "app.morphe.extension.bare.NOTIFICATION_TITLE"
internal const val BARE_META_NOTIFICATION_TEXT = "app.morphe.extension.bare.NOTIFICATION_TEXT"
internal const val BARE_DEFAULT_NOTIFICATION_TITLE = "Extension background protection active"
internal const val BARE_DEFAULT_NOTIFICATION_TEXT = "Reduces likelihood of extension runtime reclaim"

internal data class BareNotificationConfig(
    val title: String = BARE_DEFAULT_NOTIFICATION_TITLE,
    val text: String = BARE_DEFAULT_NOTIFICATION_TEXT,
)

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Declares the keep-alive service. Mirrors the Titanium manifest mutation so both
 * patches behave identically; kept as a clone so the Titanium patch stays untouched.
 * ponytail: duplicated DOM plumbing (<100 lines); unify into a shared helper the next
 * time the Titanium manifest code is touched anyway.
 */
internal fun mutateBareKeepAliveManifest(
    document: Document,
    config: BareNotificationConfig = BareNotificationConfig(),
) {
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
        if (attrName(node) == BARE_KEEP_ALIVE_SERVICE) {
            if (service == null) service = node else serviceDups += node
        }
    }
    serviceDups.forEach { application.removeChild(it) }
    val target = service ?: document.createElement("service").also { application.appendChild(it) }
    target.setAttribute("android:name", BARE_KEEP_ALIVE_SERVICE)
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
    subtype.setAttribute("android:value", BARE_SPECIAL_USE_SUBTYPE)

    fun ensureMetaData(name: String, value: String) {
        val nodes = target.getElementsByTagName("meta-data")
        var first: Element? = null
        val duplicates = mutableListOf<Element>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as Element
            if (attrName(node) == name) {
                if (first == null) first = node else duplicates += node
            }
        }
        duplicates.forEach { target.removeChild(it) }
        val entry = first ?: document.createElement("meta-data").also { target.appendChild(it) }
        entry.setAttribute("android:name", name)
        entry.setAttribute("android:value", value)
    }
    ensureMetaData(BARE_META_NOTIFICATION_TITLE, config.title)
    ensureMetaData(BARE_META_NOTIFICATION_TEXT, config.text)
}

/** Bridges resource and bytecode patch contexts; entries consumed once per package. */
private object BareLauncherActivityRegistry {
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

private val bareManifestPatch: ResourcePatch = resourcePatch(
    name = "Bare keep-alive manifest",
    description = "Declares one safe foreground service.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            BareLauncherActivityRegistry.put(packageMetadata, resolveLauncherActivityClasses(manifest))
            // Options live on the public patch (manager UI only lists those);
            // values are set before any execute runs, so reading them here is safe.
            val options = keepBareChildProcessesAlivePatch.options
            mutateBareKeepAliveManifest(
                manifest,
                BareNotificationConfig(
                    title = sanitizeHeliumNotificationLine(options["notificationTitle"].value as? String, BARE_DEFAULT_NOTIFICATION_TITLE),
                    text = sanitizeHeliumNotificationLine(options["notificationText"].value as? String, BARE_DEFAULT_NOTIFICATION_TEXT),
                ),
            )
        }
    }
}

internal val bareChildProcessCompatibility = Compatibility(
    name = "Bare Browser",
    packageName = BARE_PACKAGE,
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = null, isExperimental = true)),
)

/**
 * Forces Chromium child processes into the strongest binding state, scoped to extensions.
 * This can reduce LMK kills, but may increase RAM, battery, and process pressure. It does
 * not identify or reload crashed extensions; recovery remains native to Chromium.
 */
@Suppress("unused")
val keepBareChildProcessesAlivePatch: BytecodePatch = bytecodePatch(
    name = "Keep Bare Extensions Child Processes Alive",
    description = "Experimental version-unpinned structural/data-flow patch: starts one main-process foreground service with persistent low-priority notification and forces STRONG binding plus IMPORTANT priority only for extension child processes (detected via --extension-process). Renderer and GPU children keep stock priority. Tolerates routine signature, register, and helper-name changes; ambiguous targets fail closed. Mitigates LMK kills only. To hide the notification, use Android Settings > Apps > Bare > Notifications (the keep-alive service stays active either way).",
    default = false,
) {
    dependsOn(bareManifestPatch)
    extendWith("extensions/extension.mpe")
    compatibleWith(bareChildProcessCompatibility)

    // Notification title/text only change what the foreground notification says.
    val notificationTitle by stringOption(
        key = "notificationTitle",
        default = BARE_DEFAULT_NOTIFICATION_TITLE,
        title = "Notification title",
        description = "First line of the keep-alive notification. Blank falls back to default.",
        required = false,
        validator = { value -> value == null || value.length <= 200 },
    )
    val notificationText by stringOption(
        key = "notificationText",
        default = BARE_DEFAULT_NOTIFICATION_TEXT,
        title = "Notification text",
        description = "Second line of the keep-alive notification. Blank falls back to default.",
        required = false,
        validator = { value -> value == null || value.length <= 200 },
    )

    execute {
        val launcherActivities: Set<String>
        try {
            launcherActivities = BareLauncherActivityRegistry.take(packageMetadata)
            val helperClass = mutableClassDefBy(HELIUM_CHILD_PROCESS_CLASS)
            val createMethods = helperClass.methods.filter { it.implementation != null }
            val resolvedCreate = resolveCreateAndStart(createMethods.map { it.toStructuralMethod() })
            val resolvedBinding = resolveBindingTarget(resolvedCreate)
            // move-result is 11x (8-bit register); invoke-static/range is 3rc (16-bit).
            if (resolvedBinding.register < 0 || resolvedBinding.register >= 256) {
                throw HeliumResolutionException("binding register v${resolvedBinding.register} not encodable as move-result")
            }
            val targetMethod = createMethods.single { it.toStructuralMethod().descriptor == resolvedCreate.descriptor }
            // Extension-only scoping: boosts apply only to children launched with
            // --extension-process, not renderer/GPU.
            val scope = resolveExtensionScope(resolvedCreate, HELIUM_CHILD_PROCESS_CLASS, resolvedBinding.index)
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
                ?: error("Bare activity lifecycle owner missing: $activityOwner")
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
                "invoke-static {p0}, Lapp/morphe/extension/bare/BareKeepAliveStarter;->start(Landroid/content/Context;)V",
            )
            // Insert at the resolved binding index first — addInstructions(0, …) would
            // shift every later index and land the boost one instruction too early.
            targetMethod.addInstructions(
                resolvedBinding.index,
                "${heliumNoteHelperSmali(scope.helperInstanceRegister)}\n${heliumScopedBindingSmali(resolvedBinding.register)}",
            )
            targetMethod.addInstructions(0, heliumSetSpawnCommandLineSmali(scope.commandLineParameterWordOffset))
            // Scratch must be a true local. Parameters occupy the last paramWords registers,
            // so v0 aliases p0 (this) exactly when registerCount == paramWords.
            val priorityStructure = priorityMethod.toStructuralMethod()
            val lastParam = priorityStructure.params.lastIndex
            val lastIsWide = priorityStructure.params[lastParam] == "J" || priorityStructure.params[lastParam] == "D"
            val priorityParamWords = priorityStructure.parameterWordOffset(lastParam) + (if (lastIsWide) 2 else 1)
            val priorityRegisterCount = priorityMethod.implementation?.registerCount
                ?: throw HeliumResolutionException("setPriority: missing implementation")
            if (priorityRegisterCount <= priorityParamWords) {
                throw HeliumResolutionException(
                    "setPriority: no spare local register | registerCount=$priorityRegisterCount paramWords=$priorityParamWords",
                )
            }
            priorityMethod.addInstructionsWithLabels(
                0,
                heliumScopedImportanceSmali(priorityModel.parameterWordOffset, scratchRegister = 0),
            )
        } catch (e: HeliumResolutionException) {
            BareLauncherActivityRegistry.clear(packageMetadata)
            throw e
        }
    }
}
