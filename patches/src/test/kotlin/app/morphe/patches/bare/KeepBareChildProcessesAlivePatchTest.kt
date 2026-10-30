package app.morphe.patches.bare

import app.morphe.patches.helium.resolveLauncherActivityClasses
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeepBareChildProcessesAlivePatchTest {
    @Test
    fun `compatibility selects only the Bare package as an experimental APK target`() {
        assertEquals("org.barebrowser", bareChildProcessCompatibility.packageName)
        assertEquals("Bare Browser", bareChildProcessCompatibility.name)
        assertEquals(null, bareChildProcessCompatibility.targets.single().version)
        assertTrue(bareChildProcessCompatibility.targets.single().isExperimental)
        assertFalse(keepBareChildProcessesAlivePatch.default)
        assertEquals("Keep Bare Extensions Child Processes Alive", keepBareChildProcessesAlivePatch.name)
        // A Titanium-only package must never match this variant.
        assertTrue(bareChildProcessCompatibility.packageName != "io.github.jqssun.helium")
    }

    @Test
    fun `notification strings and service names carry no Titanium branding`() {
        val branded = listOf("titanium", "helium")
        for (value in listOf(
            BARE_KEEP_ALIVE_SERVICE,
            BARE_SPECIAL_USE_SUBTYPE,
            BARE_META_NOTIFICATION_TITLE,
            BARE_META_NOTIFICATION_TEXT,
            BARE_DEFAULT_NOTIFICATION_TITLE,
            BARE_DEFAULT_NOTIFICATION_TEXT,
        )) {
            for (brand in branded) {
                assertFalse(value.lowercase().contains(brand), "branded value: " + value)
            }
        }
        assertEquals("app.morphe.extension.bare.BareProcessKeepAliveService", BARE_KEEP_ALIVE_SERVICE)
        assertEquals("Extension background protection active", BARE_DEFAULT_NOTIFICATION_TITLE)
        assertEquals(BARE_DEFAULT_NOTIFICATION_TITLE, keepBareChildProcessesAlivePatch.options["notificationTitle"].default)
        assertEquals(BARE_DEFAULT_NOTIFICATION_TEXT, keepBareChildProcessesAlivePatch.options["notificationText"].default)
    }

    @Test
    fun `manifest mutation is idempotent and declares the generic service`() {
        val d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application/></manifest>".byteInputStream(),
        )
        mutateBareKeepAliveManifest(d)
        mutateBareKeepAliveManifest(d)
        assertEquals(2, d.getElementsByTagName("uses-permission").length)
        assertEquals(1, d.getElementsByTagName("service").length)
        val service = d.getElementsByTagName("service").item(0) as org.w3c.dom.Element
        assertEquals(BARE_KEEP_ALIVE_SERVICE, service.getAttribute("android:name"))
        assertEquals("false", service.getAttribute("android:exported"))
        assertEquals("specialUse", service.getAttribute("android:foregroundServiceType"))
        assertEquals("", service.getAttribute("android:process"))
        val metas = service.getElementsByTagName("meta-data")
        assertEquals(2, metas.length)
        val values = (0 until metas.length).associate {
            val e = metas.item(it) as org.w3c.dom.Element
            e.getAttribute("android:name") to e.getAttribute("android:value")
        }
        assertEquals(BARE_DEFAULT_NOTIFICATION_TITLE, values[BARE_META_NOTIFICATION_TITLE])
        assertEquals(BARE_DEFAULT_NOTIFICATION_TEXT, values[BARE_META_NOTIFICATION_TEXT])
    }

    @Test
    fun `launcher activity resolution qualifies relative names against the Bare package`() {
        val xml = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"org.barebrowser\">" +
            "<application><activity android:name=\".MainBrowserActivity\">" +
            "<intent-filter><action android:name=\"android.intent.action.MAIN\" />" +
            "<category android:name=\"android.intent.category.LAUNCHER\" /></intent-filter>" +
            "</activity></application></manifest>"
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(xml.byteInputStream())
        assertEquals(
            setOf("Lorg/barebrowser/MainBrowserActivity;"),
            resolveLauncherActivityClasses(document),
        )
    }
}
