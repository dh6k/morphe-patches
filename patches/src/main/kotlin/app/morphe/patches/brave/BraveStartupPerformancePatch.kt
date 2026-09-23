/*
 * Brave Startup Performance Optimization (issue #16).
 *
 * OEM/carrier partner customizations run an async init on the startup path:
 * main-thread SharedPreferences reads, a background ThreadPool, ContentResolver
 * queries, and a 10s timeout that re-runs pending callbacks. On devices with no
 * partner package that is pure CPU/disk waste and can delay first paint.
 *
 * This patch marks PartnerBrowserCustomizations initialized immediately, drains
 * onInitializeAsyncFinished callbacks right away, and forces every partner gate
 * closed so no carrier homepage / incognito lockdown can apply.
 *
 * Target surface confirmed on com.brave.browser_nightly 1.98.21 (classes5.dex):
 *   c(Context)V  <- initializeAsync (obfuscated name, string-anchored)
 *   j(Runnable)V <- setOnInitializeAsyncFinished
 *   isIncognitoDisabled()Z, h(GURL)Z, i(...)Z
 */
package app.morphe.patches.brave

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val PARTNER_CLASS =
    "Lorg/chromium/chrome/browser/partnercustomizations/PartnerBrowserCustomizations;"

/** Preference-key anchors that uniquely identify initializeAsync(Context)V. */
internal val partnerInitAnchors = listOf(
    "Chrome.Homepage.PartnerCustomizedDefaultGurl",
    "Chrome.Homepage.PartnerCustomizedDefaultUri",
)

internal val partnerCallbackAnchors = listOf(
    "setOnInitializeAsyncFinished",
)

internal val partnerHomepageRejectAnchors = listOf(
    "is too long.",
)

internal val partnerDelegateUrlAnchors = listOf(
    "Partner homepage delegate URL read failed : ",
)

internal fun initializedFlagFieldName(fields: Sequence<Pair<String, String>>): String =
    fields.firstOrNull { it.second == "Ljava/lang/Boolean;" }?.first ?: "b"

internal fun partnerInitPrologueSmali(boolField: String): String =
    """
        sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
        iput-object v0, p0, $PARTNER_CLASS->$boolField:Ljava/lang/Boolean;
        return-void
    """.trimIndent()

internal fun returnBooleanSmali(value: Boolean): String =
    if (value) "const/4 v0, 0x1\nreturn v0" else "const/4 v0, 0x0\nreturn v0"

/** Runs a pending onInitializeAsyncFinished Runnable immediately, then returns. */
internal val runCallbackImmediatelySmali =
    """
        if-eqz p1, :done
        invoke-interface {p1}, Ljava/lang/Runnable;->run()V
        :done
        return-void
    """.trimIndent()

internal fun forceExtractNativeLibs(document: org.w3c.dom.Document) {
    val apps = document.getElementsByTagName("application")
    if (apps.length == 0) error("AndroidManifest.xml has no <application>")
    val app = apps.item(0) as Element
    app.setAttribute("android:extractNativeLibs", "true")
}

private fun braveStartupCompatibilities() = listOf(
    Compatibility(
        name = "Brave Browser",
        packageName = "com.brave.browser",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = false)),
    ),
    Compatibility(
        name = "Brave Browser APKM",
        packageName = "com.brave.browser",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = false)),
    ),
    Compatibility(
        name = "Brave Beta",
        packageName = "com.brave.browser_beta",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
    Compatibility(
        name = "Brave Nightly",
        packageName = "com.brave.browser_nightly",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
    Compatibility(
        name = "Brave Nightly APKM",
        packageName = "com.brave.browser_nightly",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
)

// 16 KB page / BTI compatibility: extracted libs load reliably across ARM64 devices.
private val braveNativeExtractionPatch = resourcePatch(
    name = "Brave Native Library Extraction Compatibility",
    description = "Enforces android:extractNativeLibs=true for 16 KB page and BTI compatibility.",
    default = true,
) {
    compatibleWith(*braveStartupCompatibilities().toTypedArray())
    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[Startup Performance] Skipped: AndroidManifest.xml not found.")
            return@execute
        }
        document(manifestFile.absolutePath).use(::forceExtractNativeLibs)
        println("[Startup Performance] Enforced android:extractNativeLibs=true")
    }
}

@Suppress("unused")
val braveStartupPerformanceOptimizationPatch = bytecodePatch(
    name = "Brave Startup Performance Optimization",
    description = "Optimizes startup time and eliminates background CPU/disk overhead by disabling " +
        "unused OEM carrier partner customizations. Marks PartnerBrowserCustomizations initialized " +
        "without SharedPreferences/ContentResolver/ThreadPool/timeout work, drains init callbacks " +
        "immediately, and forces partner homepage and incognito lockdown gates closed.",
    default = true,
) {
    compatibleWith(*braveStartupCompatibilities().toTypedArray())
    dependsOn(braveNativeExtractionPatch)

    execute {
        val hooks = mutableListOf<String>()

        // 1. initializeAsync(Context)V — skip all partner I/O and mark initialized.
        val initFp = Fingerprint(
            definingClass = PARTNER_CLASS,
            returnType = "V",
            parameters = listOf("Landroid/content/Context;"),
            strings = partnerInitAnchors,
        )
        val boolField = initializedFlagFieldName(
            initFp.originalClassDef.fields.asSequence().map { it.name to it.type },
        )
        initFp.method.addInstructions(0, partnerInitPrologueSmali(boolField))
        hooks.add("init(${initFp.method.name})")

        // 2. setOnInitializeAsyncFinished — run pending callbacks now (init is a no-op).
        Fingerprint(
            definingClass = PARTNER_CLASS,
            returnType = "V",
            parameters = listOf("Ljava/lang/Runnable;"),
            strings = partnerCallbackAnchors,
        ).methodOrNull?.let {
            it.addInstructionsWithLabels(0, runCallbackImmediatelySmali)
            hooks.add("callback(${it.name})")
        }

        // 3. Partner can disable incognito via device config — never allow it.
        Fingerprint(
            definingClass = PARTNER_CLASS,
            name = "isIncognitoDisabled",
            returnType = "Z",
            parameters = emptyList(),
        ).methodOrNull?.let {
            it.addInstructions(0, returnBooleanSmali(false))
            hooks.add("incognito")
        }

        // 4. Reject partner homepage URLs (also used as a size/ scheme guard).
        Fingerprint(
            definingClass = PARTNER_CLASS,
            returnType = "Z",
            parameters = listOf("Lorg/chromium/url/GURL;"),
            strings = partnerHomepageRejectAnchors,
        ).methodOrNull?.let {
            it.addInstructions(0, returnBooleanSmali(false))
            hooks.add("homepage")
        }

        // 5. Partner homepage delegate reader — never accept a delegate URL.
        Fingerprint(
            definingClass = PARTNER_CLASS,
            returnType = "Z",
            strings = partnerDelegateUrlAnchors,
        ).methodOrNull?.let {
            it.addInstructions(0, returnBooleanSmali(false))
            hooks.add("delegate")
        }

        if (hooks.isEmpty()) error("no PartnerBrowserCustomizations hooks applied")
        println(
            "[Startup Performance] Neutralized OEM partner customizations via ${hooks.size} hooks " +
                "($boolField; ${hooks.joinToString(", ")})",
        )
    }
}
