package app.morphe.patches.helium

import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeliumActivityAndManifestTest {
    // Exact ChromeTabbedActivity
    @Test
    fun `exact ChromeTabbedActivity resolves`() {
        val m = lifecycleMethod("$HELIUM_ACTIVITY_CLASS->onStart()V", "onStart", 5)
        val model = ActivityClassModel(HELIUM_ACTIVITY_CLASS, null, listOf(m))
        assertEquals(m.descriptor, resolveActivityHook(listOf(model)).methodDescriptor)
    }

    // Launcher activity from manifest
    @Test
    fun `launcher activity resolves via manifest fallback`() {
        val m = lifecycleMethod("Lbase;->onStart()V", "onStart", 3)
        val models = listOf(
            ActivityClassModel("Lmy/Launcher;", "Lbase;", emptyList(), isLauncher = true),
            ActivityClassModel("Lbase;", null, listOf(m)),
        )
        val res = resolveActivityHook(models)
        assertEquals("Lbase;->onStart()V", res.methodDescriptor)
        assertEquals(ResolutionStrategy.MANIFEST_FALLBACK, res.strategy)
    }

    // Activity alias handled via manifest class normalization already; here test alias via isLauncher flag
    @Test
    fun `alias launcher resolves`() {
        val m = lifecycleMethod("LaliasTarget;->onStart()V", "onStart", 4)
        val models = listOf(
            ActivityClassModel("Lalias/Alias;", "LaliasTarget;", emptyList(), isLauncher = true),
            ActivityClassModel("LaliasTarget;", null, listOf(m)),
        )
        assertEquals("LaliasTarget;->onStart()V", resolveActivityHook(models).methodDescriptor)
    }

    // Hook inherited from superclass
    @Test
    fun `hook inherited from superclass`() {
        val m = lifecycleMethod("Lsuper;->onStart()V", "onStart", 6)
        val models = listOf(
            ActivityClassModel("Lchild;", "Lsuper;", emptyList()),
            ActivityClassModel("Lsuper;", null, listOf(m)),
        )
        // Need browserEvidence or exact to enter group; make child browserEvidence
        val child = models[0].copy(browserEvidence = true)
        val res = resolveActivityHook(listOf(child, models[1]))
        assertEquals("Lsuper;->onStart()V", res.methodDescriptor)
    }

    // Multiple launcher activities ambiguous
    @Test
    fun `multiple launcher activities ambiguous fails`() {
        val m1 = lifecycleMethod("La;->onStart()V", "onStart", 1)
        val m2 = lifecycleMethod("Lb;->onStart()V", "onStart", 2)
        val models = listOf(
            ActivityClassModel("La;", null, listOf(m1), isLauncher = true),
            ActivityClassModel("Lb;", null, listOf(m2), isLauncher = true),
        )
        assertFailsWith<HeliumResolutionException> { resolveActivityHook(models) }
    }

    // Ambiguous onStart candidates (duplicate scoring)
    @Test
    fun `ambiguous onStart candidates fails`() {
        val m1 = lifecycleMethod("Lx;->onStart()V", "onStart", 1)
        val m2 = lifecycleMethod("Ly;->onStart()V", "onStart", 2)
        val models = listOf(
            ActivityClassModel("Lx;", null, listOf(m1), browserEvidence = true),
            ActivityClassModel("Ly;", null, listOf(m2), browserEvidence = true),
        )
        assertFailsWith<HeliumResolutionException> { resolveActivityHook(models) }
    }

    // onResume fallback
    @Test
    fun `onResume fallback when onStart absent`() {
        val m = lifecycleMethod("Lbase;->onResume()V", "onResume", 3)
        val models = listOf(
            ActivityClassModel("Lmy/App;", "Lbase;", emptyList(), browserEvidence = true),
            ActivityClassModel("Lbase;", null, listOf(m)),
        )
        val res = resolveActivityHook(models)
        assertTrue(res.diagnostics.contains("onResume"))
    }

    // Missing lifecycle super call fails
    @Test
    fun `missing super call fails`() {
        val bad = StructuralMethod("Lbad;->onStart()V", "onStart", "V", emptyList(), 2, false, emptyList())
        val model = ActivityClassModel("Lbad;", null, listOf(bad), browserEvidence = true)
        assertFailsWith<HeliumResolutionException> { resolveActivityHook(listOf(model)) }
    }

    // Manifest mutation idempotency and preservation
    @Test
    fun `manifest mutation idempotent preserves unrelated`() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.github.jqssun.helium">
                <uses-permission android:name="android.permission.INTERNET" />
                <application>
                    <service android:name="android.service.Other" android:exported="true" />
                    <property android:name="other.prop" android:value="keep" />
                </application>
            </manifest>
        """.trimIndent()
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(xml.byteInputStream())
        mutateHeliumKeepAliveManifest(doc)
        mutateHeliumKeepAliveManifest(doc)
        assertEquals(3, doc.getElementsByTagName("uses-permission").length) // INTERNET + 2 helium perms
        assertEquals(2, doc.getElementsByTagName("service").length)
        // Helium service normalized
        val heliumService = (0 until doc.getElementsByTagName("service").length).map { doc.getElementsByTagName("service").item(it) as org.w3c.dom.Element }
            .first { it.getAttribute("android:name") == HELIUM_KEEP_ALIVE_SERVICE }
        assertEquals("false", heliumService.getAttribute("android:exported"))
        assertEquals("", heliumService.getAttribute("android:process"))
        assertEquals("specialUse", heliumService.getAttribute("android:foregroundServiceType"))
        // Unrelated service preserved
        assertTrue((0 until doc.getElementsByTagName("service").length).any { (doc.getElementsByTagName("service").item(it) as org.w3c.dom.Element).getAttribute("android:name") == "android.service.Other" })
        // Duplicate special-use property not duplicated
        assertEquals(1, heliumService.getElementsByTagName("property").length)
    }

    @Test
    fun `manifest with existing android process normalized`() {
        val xml = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="p"><application><service android:name="$HELIUM_KEEP_ALIVE_SERVICE" android:process=":remote" android:exported="true" /></application></manifest>"""
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        mutateHeliumKeepAliveManifest(doc)
        val svc = doc.getElementsByTagName("service").item(0) as org.w3c.dom.Element
        assertEquals("", svc.getAttribute("android:process"))
        assertEquals("false", svc.getAttribute("android:exported"))
    }

    @Test
    fun `duplicate permissions deduplicated`() {
        val xml = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="p"><application/></manifest>"""
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        // Add duplicate perms manually then mutate
        val manifest = doc.documentElement
        repeat(2) {
            manifest.appendChild(doc.createElement("uses-permission").apply { setAttribute("android:name", "android.permission.FOREGROUND_SERVICE") })
        }
        mutateHeliumKeepAliveManifest(doc)
        assertEquals(2, doc.getElementsByTagName("uses-permission").length)
    }

    private fun lifecycleMethod(descriptor: String, name: String, index: Int) = StructuralMethod(
        descriptor, name, "V", emptyList(), 2, false,
        listOf(StructuralInstruction.Invoke(index, "Lsuper;", name, "V", emptyList(), listOf(0), isSuper = true)),
    )
}
