package app.morphe.patches

import org.w3c.dom.Document
import java.io.DataOutputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChangeAppIconPatchTest {
    @Test
    fun `valid square PNG header is accepted`() {
        val icon = createPngHeaderFile(width = 432, height = 432)

        assertEquals(IconDimensions(432, 432), validateCustomIconFile(icon))
    }

    @Test
    fun `non-square PNG is rejected`() {
        val icon = createPngHeaderFile(width = 432, height = 216)

        assertFailsWith<Exception> {
            validateCustomIconFile(icon)
        }
    }

    @Test
    fun `adaptive layers come from original icon resource`() {
        val resources = createTempDirectory("icon-resources").toFile()
        val adaptiveDirectory = resources.resolve("mipmap-anydpi-v26").apply(File::mkdirs)
        adaptiveDirectory.resolve("original.xml").writeText(
            """
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@color/original_background" />
                <foreground android:drawable="@drawable/original_foreground" />
                <monochrome android:drawable="@drawable/original_monochrome" />
            </adaptive-icon>
            """.trimIndent(),
        )

        val layers = findAdaptiveIconLayers(resources, "@mipmap/original")

        assertEquals("@color/original_background", layers.background)
        assertEquals("@drawable/original_monochrome", layers.monochrome)
    }

    @Test
    fun `manifest updates application and launcher icon overrides only`() {
        val manifest = parseXml(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:icon="@mipmap/original"
                    android:roundIcon="@mipmap/original_round">
                    <activity android:name=".Launcher" android:icon="@mipmap/activity_icon">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <activity android:name=".Settings" android:icon="@mipmap/settings_icon" />
                </application>
            </manifest>
            """.trimIndent(),
        )

        assertEquals(1, applyCustomIconToManifest(manifest))

        val application = manifest.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        val launcher = manifest.getElementsByTagName("activity").item(0) as org.w3c.dom.Element
        val settings = manifest.getElementsByTagName("activity").item(1) as org.w3c.dom.Element
        assertEquals(
            "@mipmap/morphe_custom_launcher_icon",
            application.getAttribute("android:icon"),
        )
        assertEquals(
            "@mipmap/morphe_custom_launcher_icon",
            application.getAttribute("android:roundIcon"),
        )
        assertEquals(
            "@mipmap/morphe_custom_launcher_icon",
            launcher.getAttribute("android:icon"),
        )
        assertEquals("@mipmap/settings_icon", settings.getAttribute("android:icon"))
    }

    @Test
    fun `resource install creates adaptive and legacy variants`() {
        val resources = createTempDirectory("icon-output").toFile()
        val source = createPngHeaderFile(width = 432, height = 432)

        installCustomIconResources(
            resourceDirectory = resources,
            sourceFile = source,
            adaptiveLayers = AdaptiveIconLayers(
                background = "@color/original_background",
                monochrome = "@drawable/original_monochrome",
            ),
        )

        assertTrue(resources.resolve("drawable-nodpi/morphe_custom_launcher_foreground.png").isFile)
        assertTrue(resources.resolve("mipmap-nodpi/morphe_custom_launcher_icon.png").isFile)
        val adaptiveXml = resources
            .resolve("mipmap-anydpi-v26/morphe_custom_launcher_icon.xml")
            .readText()
        assertTrue("""android:drawable="@color/original_background"""" in adaptiveXml)
        assertTrue("""android:drawable="@drawable/morphe_custom_launcher_foreground"""" in adaptiveXml)
        assertTrue("""android:drawable="@drawable/original_monochrome"""" in adaptiveXml)
    }

    private fun createPngHeaderFile(width: Int, height: Int): File {
        val file = kotlin.io.path.createTempFile("custom-icon", ".png").toFile()
        DataOutputStream(file.outputStream()).use { output ->
            output.write(
                byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                ),
            )
            output.writeInt(13)
            output.write(byteArrayOf(0x49, 0x48, 0x44, 0x52))
            output.writeInt(width)
            output.writeInt(height)
            output.write(ByteArray(5))
        }
        return file
    }

    private fun parseXml(xml: String): Document =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
}
