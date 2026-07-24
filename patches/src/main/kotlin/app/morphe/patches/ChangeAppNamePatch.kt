/*
 * Universal adaptation of ChangeAppNamePatch.kt from:
 * https://github.com/durgesh0505/chiggi_morphe_patches
 * Upstream commit: 6b8a9a36cbd36faa4d5b8ce6e811fb428eb365f9
 *
 * Licensed under the GNU General Public License v3.0.
 */

package app.morphe.patches

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

private const val ACTION_MAIN = "android.intent.action.MAIN"
private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
private val LAUNCHER_CATEGORIES = setOf(
    "android.intent.category.LAUNCHER",
    "android.intent.category.LEANBACK_LAUNCHER",
    "android.intent.category.CAR_LAUNCHER",
)

@Suppress("unused")
val changeAppNamePatch = resourcePatch(
    name = "Change app name",
    description = "Changes the app name shown by Android launchers. " +
        "Set the desired name in the patch options.",
    default = false,
) {
    val appName by stringOption(
        key = "appName",
        default = "Patched",
        title = "App name",
        description = "The name shown under the app icon.",
        required = true,
        validator = { value -> !value.isNullOrBlank() },
    )

    execute {
        document("AndroidManifest.xml").use { document ->
            val newName = appName?.trim().orEmpty()
            require(newName.isNotEmpty()) { "App name must not be blank" }

            val application = document.getElementsByTagName("application").item(0) as? Element
                ?: error("AndroidManifest.xml does not contain an <application> element")
            application.setAndroidAttribute("label", newName)

            listOf("activity", "activity-alias").forEach { tagName ->
                val components = document.getElementsByTagName(tagName)
                for (index in 0 until components.length) {
                    val component = components.item(index) as Element
                    if (component.hasLauncherIntentFilter()) {
                        component.setAndroidAttribute("label", newName)
                    }
                }
            }
        }
    }
}

private fun Element.hasLauncherIntentFilter(): Boolean {
    val intentFilters = getElementsByTagName("intent-filter")
    for (filterIndex in 0 until intentFilters.length) {
        val intentFilter = intentFilters.item(filterIndex) as Element
        val hasMainAction = intentFilter
            .getElementsByTagName("action")
            .hasAndroidName(ACTION_MAIN)
        val hasLauncherCategory = intentFilter
            .getElementsByTagName("category")
            .hasAnyAndroidName(LAUNCHER_CATEGORIES)

        if (hasMainAction && hasLauncherCategory) return true
    }

    return false
}

private fun org.w3c.dom.NodeList.hasAndroidName(expectedName: String): Boolean =
    hasAnyAndroidName(setOf(expectedName))

private fun org.w3c.dom.NodeList.hasAnyAndroidName(expectedNames: Set<String>): Boolean {
    for (index in 0 until length) {
        val element = item(index) as Element
        if (element.getAndroidAttribute("name") in expectedNames) return true
    }

    return false
}

private fun Element.getAndroidAttribute(name: String): String =
    getAttributeNS(ANDROID_NAMESPACE, name).ifEmpty {
        getAttribute("android:$name")
    }

private fun Element.setAndroidAttribute(name: String, value: String) {
    val namespacePrefix = lookupPrefix(ANDROID_NAMESPACE)
    val qualifiedName = namespacePrefix
        ?.let { "$it:$name" }
        ?: "android:$name"
    setAttributeNS(ANDROID_NAMESPACE, qualifiedName, value)
}
