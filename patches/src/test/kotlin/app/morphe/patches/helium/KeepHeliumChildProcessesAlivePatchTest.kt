package app.morphe.patches.helium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import javax.xml.parsers.DocumentBuilderFactory

class KeepHeliumChildProcessesAlivePatchTest {
    @Test
    fun `compatibility is version unpinned experimental Titanium APK target`() {
        assertEquals("io.github.jqssun.helium", heliumChildProcessCompatibility.packageName)
        assertEquals(null, heliumChildProcessCompatibility.targets.single().version)
        assertTrue(heliumChildProcessCompatibility.targets.single().isExperimental)
        assertFalse(keepHeliumChildProcessesAlivePatch.default)
        assertEquals("Lorg/chromium/content/browser/ChildProcessLauncherHelperImpl;", HELIUM_CHILD_PROCESS_CLASS)
        assertEquals("setPriority", HELIUM_SET_PRIORITY_METHOD)
        assertEquals("ChildProcessLauncher.start", HELIUM_SPAWN_START_ANCHOR)
    }

    @Test
    fun `boost smali is extension scoped via range invokes`() {
        assertEquals(
            "invoke-static/range {p2 .. p2}, Lapp/morphe/extension/helium/HeliumProcessBoost;->setSpawnCommandLine([Ljava/lang/String;)V",
            heliumSetSpawnCommandLineSmali(2),
        )
        assertEquals(
            "invoke-static/range {v5 .. v5}, Lapp/morphe/extension/helium/HeliumProcessBoost;->noteHelper(Ljava/lang/Object;)V",
            heliumNoteHelperSmali(5),
        )
        val binding = heliumScopedBindingSmali(7)
        assertTrue(binding.contains("scopedBinding(I)I"), binding)
        assertTrue(binding.contains("move-result v7"), binding)
        val importance = heliumScopedImportanceSmali(12, scratchRegister = 3)
        assertTrue(importance.contains("isExtension(Ljava/lang/Object;)Z"), importance)
        assertTrue(importance.contains("move-result v3"), importance)
        assertTrue(importance.contains("const/16 p12, 3"), importance)
        assertTrue(importance.contains(":helium_skip_importance"), importance)
    }
    @Test
    fun `manifest helper is idempotent`() {
        val d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application/></manifest>".byteInputStream()
        )
        mutateHeliumKeepAliveManifest(d); mutateHeliumKeepAliveManifest(d)
        assertEquals(2, d.getElementsByTagName("uses-permission").length)
        assertEquals(1, d.getElementsByTagName("service").length)
        val service = d.getElementsByTagName("service").item(0) as org.w3c.dom.Element
        assertEquals("false", service.getAttribute("android:exported"))
        assertEquals("specialUse", service.getAttribute("android:foregroundServiceType"))
        assertEquals("", service.getAttribute("android:process"))
        assertEquals(1, service.getElementsByTagName("property").length)
    }
    @Test
    fun `notification options default to default lines`() {
        assertEquals(setOf("notificationTitle", "notificationText"), keepHeliumChildProcessesAlivePatch.options.keys)
        assertEquals(HELIUM_DEFAULT_NOTIFICATION_TITLE, keepHeliumChildProcessesAlivePatch.options["notificationTitle"]?.default)
        assertEquals(HELIUM_DEFAULT_NOTIFICATION_TEXT, keepHeliumChildProcessesAlivePatch.options["notificationText"]?.default)
        assertEquals("fallback", sanitizeHeliumNotificationLine("   ", "fallback"))
        assertEquals("fallback", sanitizeHeliumNotificationLine(null, "fallback"))
        assertEquals("Custom", sanitizeHeliumNotificationLine("  Custom ", "fallback"))
    }

    @Test
    fun `manifest helper writes notification meta-data idempotently`() {
        val d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application/></manifest>".byteInputStream()
        )
        val config = HeliumNotificationConfig(title = "T", text = "B")
        mutateHeliumKeepAliveManifest(d, config); mutateHeliumKeepAliveManifest(d, config)
        val service = d.getElementsByTagName("service").item(0) as org.w3c.dom.Element
        val metas = service.getElementsByTagName("meta-data")
        assertEquals(2, metas.length)
        val values = (0 until metas.length).associate {
            val e = metas.item(it) as org.w3c.dom.Element
            e.getAttribute("android:name") to e.getAttribute("android:value")
        }
        assertEquals("T", values[HELIUM_META_NOTIFICATION_TITLE])
        assertEquals("B", values[HELIUM_META_NOTIFICATION_TEXT])
    }

    @Test
    fun `launcher activity resolver handles relative activity and alias names`() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="io.github.jqssun.helium">
                <application>
                    <activity android:name=".MainBrowserActivity">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <activity android:name="io.github.jqssun.helium.RealActivity" />
                    <activity-alias
                        android:name=".Alias"
                        android:targetActivity="io.github.jqssun.helium.RealActivity">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity-alias>
                </application>
            </manifest>
            """.trimIndent().byteInputStream(),
        )
        assertEquals(
            setOf(
                "Lio/github/jqssun/helium/MainBrowserActivity;",
                "Lio/github/jqssun/helium/RealActivity;",
            ),
            resolveLauncherActivityClasses(document),
        )
    }
}
