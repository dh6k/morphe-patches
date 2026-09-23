package app.morphe.patches.brave

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BraveStartupPerformancePatchTest {
    @Test
    fun `initialized flag prefers Boolean field`() {
        val fields = sequenceOf(
            "a" to "Lorg/chromium/url/GURL;",
            "b" to "Ljava/lang/Boolean;",
            "d" to "Ljava/util/ArrayList;",
        )
        assertEquals("b", initializedFlagFieldName(fields))
    }

    @Test
    fun `initialized flag falls back when Boolean field renamed`() {
        assertEquals("b", initializedFlagFieldName(sequenceOf("a" to "Lorg/chromium/url/GURL;")))
        assertEquals("b", initializedFlagFieldName(emptySequence()))
    }

    @Test
    fun `init prologue writes Boolean field and returns`() {
        val smali = partnerInitPrologueSmali("b")
        assertTrue(smali.contains("Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;"))
        assertTrue(smali.contains("->b:Ljava/lang/Boolean;"))
        assertTrue(smali.contains("return-void"))
    }

    @Test
    fun `boolean returns are const plus return`() {
        assertEquals("const/4 v0, 0x0\nreturn v0", returnBooleanSmali(false))
        assertEquals("const/4 v0, 0x1\nreturn v0", returnBooleanSmali(true))
    }

    @Test
    fun `callback runner null-checks and returns`() {
        assertTrue(runCallbackImmediatelySmali.contains("if-eqz p1, :done"))
        // Runnable is an interface: invoke-virtual here is IncompatibleClassChangeError.
        assertTrue(runCallbackImmediatelySmali.contains("invoke-interface {p1}, Ljava/lang/Runnable;->run()V"))
        assertFalse(runCallbackImmediatelySmali.contains("invoke-virtual"))
        assertTrue(runCallbackImmediatelySmali.contains("return-void"))
    }

    @Test
    fun `anchors cover init callback and partner gates`() {
        assertEquals(2, partnerInitAnchors.size)
        assertTrue(partnerInitAnchors.all { it.startsWith("Chrome.Homepage.PartnerCustomized") })
        assertEquals(listOf("setOnInitializeAsyncFinished"), partnerCallbackAnchors)
        assertTrue(partnerHomepageRejectAnchors.single().contains("too long"))
        assertTrue(partnerDelegateUrlAnchors.single().contains("delegate URL"))
    }
}
