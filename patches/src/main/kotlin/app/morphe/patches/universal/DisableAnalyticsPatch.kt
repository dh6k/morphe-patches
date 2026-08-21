/*
 * Universal SDK opt-out design informed by adobo, FTL-Patches,
 * android-patches-xtra and kondratjev/morphe-patches. No source is copied.
 */
package app.morphe.patches.universal

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

internal val analyticsMetadataOff = mapOf(
    "firebase_analytics_collection_deactivated" to "true",
    "firebase_analytics_collection_enabled" to "false",
    "firebase_crashlytics_collection_enabled" to "false",
    "firebase_performance_collection_enabled" to "false",
    "firebase_performance_collection_deactivated" to "true",
    "firebase_performance_logcat_enabled" to "false",
    "firebase_data_collection_default_enabled" to "false",
    "google_analytics_adid_collection_enabled" to "false",
    "google_analytics_ssaid_collection_enabled" to "false",
    "google_analytics_default_allow_analytics_storage" to "false",
    "google_analytics_default_allow_ad_storage" to "false",
    "google_analytics_default_allow_ad_user_data" to "false",
    "google_analytics_default_allow_ad_personalization_signals" to "false",
    "io.appmetrica.analytics.auto_tracking_enabled" to "false",
    "io.appmetrica.analytics.location_tracking_enabled" to "false",
    "io.sentry.enabled" to "false",
    "io.sentry.dsn" to "",
)

internal fun analyticsMetadataOffValue(name: String): String? = analyticsMetadataOff[name]

internal fun isAnalyticsComponent(name: String): Boolean =
    name.startsWith("io.appmetrica.analytics.") || name.startsWith("com.yandex.metrica.") ||
        name.startsWith("com.my.tracker.") || name.contains(".mytracker.", true) ||
        name.startsWith("io.sentry.") || name.startsWith("com.adjust.") ||
        name.startsWith("com.appsflyer.") || name.startsWith("com.moengage.") ||
        name.startsWith("com.comscore.") || name.startsWith("com.amplitude.") ||
        name.startsWith("com.mixpanel.") || name.startsWith("com.google.android.gms.analytics.") ||
        name.startsWith("com.google.android.gms.tagmanager.")

private val analyticsManifestPatch = resourcePatch(
    name = "Disable analytics manifest collection",
    description = "Adds opt-out metadata and disables explicit analytics SDK components.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as? Element
                ?: error("AndroidManifest.xml does not contain an <application> element")
            val existing = mutableSetOf<String>()
            val metadata = application.getElementsByTagName("meta-data")
            for (i in 0 until metadata.length) {
                val node = metadata.item(i) as Element
                val name = node.getAttribute("android:name")
                if (name in analyticsMetadataOff) {
                    node.setAttribute("android:value", analyticsMetadataOff.getValue(name))
                    existing += name
                }
            }
            analyticsMetadataOff.forEach { (name, value) ->
                if (name !in existing) {
                    application.appendChild(document.createElement("meta-data").apply {
                        setAttribute("android:name", name)
                        setAttribute("android:value", value)
                    })
                }
            }
            listOf("activity", "provider", "service", "receiver").forEach { tag ->
                val nodes = application.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as Element
                    if (isAnalyticsComponent(node.getAttribute("android:name"))) {
                        node.setAttribute("android:enabled", "false")
                    }
                }
            }
        }
    }
}

internal object FirebaseCrashlyticsSetter : Fingerprint(
    definingClass = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;",
    name = "setCrashlyticsCollectionEnabled", returnType = "V", parameters = listOf("Z"),
)
internal object FirebasePerformanceSetter : Fingerprint(
    definingClass = "Lcom/google/firebase/perf/FirebasePerformance;",
    name = "setPerformanceCollectionEnabled", returnType = "V", parameters = listOf("Z"),
)
internal object MyTrackerInitializer : Fingerprint(
    definingClass = "Lcom/my/tracker/MyTracker;", name = "initTracker", returnType = "V",
    custom = { method, _ -> method.name != "<init>" && method.implementation != null },
)

@Suppress("unused")
val disableAnalyticsPatch = bytecodePatch(
    name = "Disable analytics",
    description = "Disables Firebase Analytics, Crashlytics and Performance through manifest opt-outs and exact runtime setters when present. Other SDK components are disabled only when explicitly declared; custom or server-side telemetry is not covered.",
    default = false,
) {
    dependsOn(analyticsManifestPatch)
    execute {
        FirebaseCrashlyticsSetter.methodOrNull?.addInstructions(0, "const/4 p1, 0x0")
        FirebasePerformanceSetter.methodOrNull?.addInstructions(0, "const/4 p1, 0x0")
        MyTrackerInitializer.methodOrNull?.addInstructions(0, "return-void")
    }
}
