package app.morphe.patches.brave

import java.io.DataOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CustomNtpWallpaperPatchTest {
    @Test
    fun `valid landscape PNG is accepted`() {
        val wallpaper = createPngHeaderFile(width = 1920, height = 1080)

        assertEquals(WallpaperDimensions(1920, 1080), validateWallpaperFile(wallpaper))
    }

    @Test
    fun `undersized PNG is rejected`() {
        val wallpaper = createPngHeaderFile(width = 320, height = 240)

        assertFailsWith<Exception> {
            validateWallpaperFile(wallpaper)
        }
    }

    @Test
    fun `non-PNG is rejected`() {
        val file = kotlin.io.path.createTempFile("wallpaper", ".png").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        assertFailsWith<Exception> {
            validateWallpaperFile(file)
        }
    }

    @Test
    fun `resource install writes nodpi drawable`() {
        val resources = createTempDirectory("wallpaper-resources").toFile()
        val source = createPngHeaderFile(width = 1920, height = 1080)

        installWallpaperResource(resources, source)

        val installed = resources.resolve("drawable-nodpi/$NTP_WALLPAPER_RESOURCE_NAME.png")
        assertTrue(installed.isFile)
        assertEquals(source.readBytes().toList(), installed.readBytes().toList())
    }

    @Test
    fun `factory prologues reference wallpaper drawable and package uri`() {
        val create = forceCreateWallpaperParamsSmali()
        val branded = forceCreateBrandedWallpaperParamsSmali()

        assertTrue("android.resource://" in create)
        assertTrue("/drawable/$NTP_WALLPAPER_RESOURCE_NAME" in create)
        assertTrue("const-string p0, \"$NTP_WALLPAPER_RESOURCE_NAME\"" in create)
        assertTrue("move-object p1, v0" in create)

        assertTrue("android.resource://" in branded)
        assertTrue("/drawable/$NTP_WALLPAPER_RESOURCE_NAME" in branded)
        assertTrue("move-object p3, v0" in branded)
        assertTrue("move-object p6, v0" in branded)
    }

    private fun createPngHeaderFile(width: Int, height: Int): File {
        val file = kotlin.io.path.createTempFile("ntp-wallpaper", ".png").toFile()
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
}
