package app.morphe.patches.brave

import java.io.DataOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `factory prologues bake package resource uri into string params only`() {
        val pkg = "vip.dh6k.brave.origin.nightly"
        val uri = ntpWallpaperResourceUri(pkg)
        assertEquals(
            "android.resource://vip.dh6k.brave.origin.nightly/drawable/$NTP_WALLPAPER_RESOURCE_NAME",
            uri,
        )

        val create = forceCreateWallpaperParamsSmali(pkg)
        assertTrue("const-string p0, \"$NTP_WALLPAPER_RESOURCE_NAME\"" in create)
        assertTrue("const-string p1, \"$uri\"" in create)
        assertTrue("const-string p2, \"Custom\"" in create)

        val branded = forceCreateBrandedWallpaperParamsSmali(pkg)
        assertTrue("const-string p0, \"$NTP_WALLPAPER_RESOURCE_NAME\"" in branded)
        assertTrue("const-string p3, \"$uri\"" in branded)
        assertTrue("move-object p4, p3" in branded)
        assertTrue("move-object p6, p3" in branded)
        assertTrue("move-object p7, p3" in branded)
    }

    @Test
    fun `branded prologue must not clobber int or boolean params`() {
        val smali = forceCreateBrandedWallpaperParamsSmali("com.brave.browser_nightly")
        // createBrandedWallpaper is static with 10 params; v0/v1 alias p0/p1.
        // Touching v1 put a String into the int slot and failed ART verification.
        assertFalse(Regex("""\bv[0-9]+\b""").containsMatchIn(smali))
        assertFalse(Regex("""\bp[12589]\b""").containsMatchIn(smali))
    }

    @Test
    fun `ambient catalog factory calls helper and only uses v0 v1`() {
        val field =
            "Lorg/chromium/chrome/browser/ntp_background_images/model/BackgroundImage;->a:I"
        val smali = forceAmbientCatalogAccessorSmali(field)

        assertTrue("invoke-static {}, $NTP_WALLPAPER_HELPER->drawableId()I" in smali)
        assertTrue("iput v0, v1, $field" in smali)
        assertTrue("return-object v1" in smali)
        // edi.a() has only 3 registers (v0–v2). v3+ triggers ART VerifyError.
        assertFalse(Regex("""\bv[3-9]\d*\b""").containsMatchIn(smali))
        // R$drawable is stripped / wrong package after rename — must not use it.
        assertFalse("R\$drawable" in smali)
        assertFalse("getIdentifier" in smali)
    }

    @Test
    fun `callback force replaces native wallpaper object with catalog result`() {
        val smali = forceUseAmbientCatalogSmali("Lt9i;", "a")

        assertTrue("invoke-static {}, Lt9i;->a()" in smali)
        assertTrue(BACKGROUND_IMAGE_MODEL in smali)
        assertTrue("move-result-object p1" in smali)
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
