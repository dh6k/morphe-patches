package app.morphe.patches.helium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.patch.ResourcePatch
import com.android.tools.smali.dexlib2.iface.ClassDef
import org.w3c.dom.Document
import org.w3c.dom.Element
internal const val HELIUM_KEEP_ALIVE_SERVICE = "app.morphe.extension.helium.HeliumProcessKeepAliveService"
internal const val HELIUM_KEEP_ALIVE_CHANNEL = "helium_extension_runtime"
internal const val HELIUM_KEEP_ALIVE_NOTIFICATION_ID = 0x48454c
internal const val HELIUM_SPECIAL_USE_SUBTYPE = "Maintain browser extension background runtime"
internal const val HELIUM_META_NOTIFICATION_TITLE =
    "app.morphe.extension.helium.NOTIFICATION_TITLE"
internal const val HELIUM_META_NOTIFICATION_TEXT =
    "app.morphe.extension.helium.NOTIFICATION_TEXT"
internal const val HELIUM_DEFAULT_NOTIFICATION_TITLE = "Titanium process protection active"
internal const val HELIUM_DEFAULT_NOTIFICATION_TEXT = "Reduces likelihood of extension runtime reclaim"

internal data class HeliumNotificationConfig(
    val title: String = HELIUM_DEFAULT_NOTIFICATION_TITLE,
    val text: String = HELIUM_DEFAULT_NOTIFICATION_TEXT,
)

internal fun sanitizeHeliumNotificationLine(value: String?, fallback: String): String =
    value?.trim().orEmpty().ifEmpty { fallback }
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
/** Bridges resource and bytecode patch contexts; entries consumed once per package. */
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

internal fun mutateHeliumKeepAliveManifest(
    document: Document,
    config: HeliumNotificationConfig = HeliumNotificationConfig(),
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
    fun removeMetaData(name: String) {
        val nodes = target.getElementsByTagName("meta-data")
        for (i in nodes.length - 1 downTo 0) {
            val node = nodes.item(i) as Element
            if (attrName(node) == name) target.removeChild(node)
        }
    }
    // ponytail: dropped toggle key; remove stale entry from APKs patched with it.
    removeMetaData("app.morphe.extension.helium.NOTIFICATION_ENABLED")
    ensureMetaData(HELIUM_META_NOTIFICATION_TITLE, config.title)
    ensureMetaData(HELIUM_META_NOTIFICATION_TEXT, config.text)
}
private val heliumManifestPatch: ResourcePatch = resourcePatch(
    name = "Titanium keep-alive manifest",
    description = "Declares one safe foreground service.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            LauncherActivityRegistry.put(packageMetadata, resolveLauncherActivityClasses(manifest))
            // Options live on the public patch (manager UI only lists those);
            // values are set before any execute runs, so reading them here is safe.
            val options = keepHeliumChildProcessesAlivePatch.options
            mutateHeliumKeepAliveManifest(
                manifest,
                HeliumNotificationConfig(
                    title = sanitizeHeliumNotificationLine(options["notificationTitle"].value as? String, HELIUM_DEFAULT_NOTIFICATION_TITLE),
                    text = sanitizeHeliumNotificationLine(options["notificationText"].value as? String, HELIUM_DEFAULT_NOTIFICATION_TEXT),
                ),
            )
        }
    }
}
private const val HELIUM_PACKAGE = "io.github.jqssun.helium"
internal const val HELIUM_CHILD_PROCESS_CLASS =
    "Lorg/chromium/content/browser/ChildProcessLauncherHelperImpl;"
internal const val HELIUM_SET_PRIORITY_METHOD: String = HELIUM_PRIORITY_METHOD
internal const val HELIUM_BOOST_CLASS = "Lapp/morphe/extension/helium/HeliumProcessBoost;"
// Chromium ChildBindingState.STRONG / ChildProcessImportance.IMPORTANT from commit ff12f1c.
internal const val HELIUM_STRONG_BINDING_VALUE = 0x4
internal const val HELIUM_IMPORTANT_PRIORITY_VALUE = 0x3
internal const val HELIUM_SPAWN_START_ANCHOR = "ChildProcessLauncher.start"

// High parameter registers (v29/p12 on validated APKs) cannot use 35c invoke;
// every call goes through invoke-static/range (3rc, 16-bit registers).
internal fun heliumSetSpawnCommandLineSmali(commandLineWordOffset: Int): String =
    "invoke-static/range {p$commandLineWordOffset .. p$commandLineWordOffset}, " +
        "$HELIUM_BOOST_CLASS->setSpawnCommandLine([Ljava/lang/String;)V"

internal fun heliumNoteHelperSmali(instanceRegister: Int): String =
    "invoke-static/range {v$instanceRegister .. v$instanceRegister}, " +
        "$HELIUM_BOOST_CLASS->noteHelper(Ljava/lang/Object;)V"

internal fun heliumScopedBindingSmali(bindingRegister: Int): String =
    """
        invoke-static/range {v$bindingRegister .. v$bindingRegister}, $HELIUM_BOOST_CLASS->scopedBinding(I)I
        move-result v$bindingRegister
    """.trimIndent()

internal fun heliumScopedImportanceSmali(importanceWordOffset: Int, scratchRegister: Int): String =
    """
        invoke-static/range {p0 .. p0}, $HELIUM_BOOST_CLASS->isExtension(Ljava/lang/Object;)Z
        move-result v$scratchRegister
        if-eqz v$scratchRegister, :helium_skip_importance
        const/16 p$importanceWordOffset, $HELIUM_IMPORTANT_PRIORITY_VALUE
        :helium_skip_importance
        nop
    """.trimIndent()

/** Version-unpinned experimental Titanium patch using structural fingerprints; ambiguity fails safely. */
internal val heliumChildProcessCompatibility = Compatibility(
    name = "Titanium Browser for Android",
    packageName = HELIUM_PACKAGE,
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = null, isExperimental = true)),
)

/**
 * Forces Chromium child processes into strongest binding state. This can reduce LMK kills,
 * but may increase RAM, battery, and process pressure. It does not identify or reload
 * crashed extensions; recovery remains native to Titanium/Chromium.
 */
@Suppress("unused")
val keepHeliumChildProcessesAlivePatch: BytecodePatch = bytecodePatch(
    name = "Keep Titanium Extensions Child Processes Alive",
    description = "Experimental version-unpinned structural/data-flow patch: starts one main-process foreground service with persistent low-priority notification and forces STRONG binding plus IMPORTANT priority only for extension child processes (detected via --extension-process, matching upstream Titanium commit ff12f1c). Renderer and GPU children keep stock priority, so RAM and battery pressure stay closer to baseline. Tolerates routine signature, register, and helper-name changes; ambiguous targets fail closed. Mitigates LMK kills only. To hide the notification, use Android Settings > Apps > Titanium > Notifications (the keep-alive service stays active either way).",
    default = false,
) {
    dependsOn(heliumManifestPatch)
    extendWith("extensions/extension.mpe")
    compatibleWith(heliumChildProcessCompatibility)

    // Notification title/text only change what the foreground notification says.
    // To hide it, use Android Settings > Apps > Titanium > Notifications
    // (per-channel toggle) — the keep-alive service stays active either way.
    val notificationTitle by stringOption(
        key = "notificationTitle",
        default = HELIUM_DEFAULT_NOTIFICATION_TITLE,
        title = "Notification title",
        description = "First line of the keep-alive notification. Blank falls back to default.",
        required = false,
        validator = { value -> value == null || value.length <= 200 },
    )
    val notificationText by stringOption(
        key = "notificationText",
        default = HELIUM_DEFAULT_NOTIFICATION_TEXT,
        title = "Notification text",
        description = "Second line of the keep-alive notification. Blank falls back to default.",
        required = false,
        validator = { value -> value == null || value.length <= 200 },
    )

    execute {
        val launcherActivities: Set<String>
        try {
            launcherActivities = LauncherActivityRegistry.take(packageMetadata)
            val helperClass = mutableClassDefBy(HELIUM_CHILD_PROCESS_CLASS)
            val createMethods = helperClass.methods.filter { it.implementation != null }
            val resolvedCreate = resolveCreateAndStart(createMethods.map { it.toStructuralMethod() })
            val resolvedBinding = resolveBindingTarget(resolvedCreate)
            // move-result is 11x (8-bit register); invoke-static/range is 3rc (16-bit).
            if (resolvedBinding.register < 0 || resolvedBinding.register >= 256) {
                throw HeliumResolutionException("binding register v${resolvedBinding.register} not encodable as move-result")
            }
            val targetMethod = createMethods.single { it.toStructuralMethod().descriptor == resolvedCreate.descriptor }
            // Extension-only scoping (upstream commit ff12f1c): boosts apply only to
            // children launched with --extension-process, not renderer/GPU.
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
            ?: error("Titanium activity lifecycle owner missing: $activityOwner")
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
            LauncherActivityRegistry.clear(packageMetadata)
            throw e
        }
    }
}
