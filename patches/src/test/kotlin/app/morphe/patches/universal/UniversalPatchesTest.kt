package app.morphe.patches.universal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniversalPatchesTest {
    @Test fun `analytics metadata uses off polarity`() {
        assertEquals("true", analyticsMetadataOffValue("firebase_analytics_collection_deactivated"))
        assertEquals("false", analyticsMetadataOffValue("firebase_analytics_collection_enabled"))
        assertEquals("false", analyticsMetadataOffValue("firebase_crashlytics_collection_enabled"))
        assertEquals("false", analyticsMetadataOffValue("google_analytics_default_allow_analytics_storage"))
    }

    @Test fun `component classifiers are narrow`() {
        assertTrue(isAnalyticsComponent("com.google.android.gms.analytics.AnalyticsService"))
        assertFalse(isAnalyticsComponent("com.example.analytics.SettingsActivity"))
        assertFalse(isAnalyticsComponent("com.example.ads.SettingsActivity"))
        assertFalse(isAnalyticsComponent("com.google.android.gms.measurement.Settings"))
    }
}
