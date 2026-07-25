package app.morphe.patches

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeAppNamePatchTest {
    @Test
    fun `option key is bundle specific`() {
        assertEquals(setOf("appName"), changeAppNamePatch.options.keys)
    }
}
