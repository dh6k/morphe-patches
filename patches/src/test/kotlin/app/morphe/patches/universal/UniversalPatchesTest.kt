package app.morphe.patches.universal

import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Element

class UniversalPatchesTest {
    @Test fun `analytics metadata uses off polarity`() {
        assertEquals("true", analyticsMetadataOffValue("firebase_analytics_collection_deactivated"))
        assertEquals("false", analyticsMetadataOffValue("firebase_analytics_collection_enabled"))
        assertEquals("false", analyticsMetadataOffValue("firebase_crashlytics_collection_enabled"))
        assertEquals("false", analyticsMetadataOffValue("google_analytics_default_allow_analytics_storage"))
    }

    @Test fun `component classifiers are narrow`() {
        assertTrue(isAnalyticsComponent("com.google.android.gms.analytics.AnalyticsService"))
        assertTrue(isAnalyticsComponent("com.google.android.gms.measurement.AppMeasurementService"))
        assertTrue(isAnalyticsComponent("com.google.android.gms.measurement.AppMeasurementReceiver"))
        assertFalse(isAnalyticsComponent("com.example.analytics.SettingsActivity"))
        assertFalse(isAnalyticsComponent("com.example.ads.SettingsActivity"))
        assertFalse(isAnalyticsComponent("com.google.android.gms.measurement.Settings"))
    }

    @Test fun `manifest mutation only touches application-level nodes`() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
                <application>
                    <meta-data android:name="firebase_analytics_collection_enabled" android:value="true" />
                    <activity android:name="com.example.MainActivity">
                        <meta-data android:name="firebase_analytics_collection_enabled" android:value="true" />
                    </activity>
                    <service android:name="com.google.android.gms.measurement.AppMeasurementService" />
                    <service android:name="com.example.SyncService" />
                </application>
            </manifest>
            """.trimIndent().byteInputStream(),
        )
        mutateAnalyticsManifest(document)
        val application = document.getElementsByTagName("application").item(0) as Element
        assertEquals(analyticsMetadataOff.size, directChildren(application, "meta-data").size)
        val appMeta = directChildren(application, "meta-data").first {
            manifestAttr(it, "name") == "firebase_analytics_collection_enabled"
        }
        assertEquals("false", appMeta.getAttribute("android:value"))
        val activity = directChildren(application, "activity").single() as Element
        val nestedMeta = (activity.getElementsByTagName("meta-data").item(0) as Element)
        assertEquals("true", nestedMeta.getAttribute("android:value"))
        val services = directChildren(application, "service")
        assertEquals("false", services.first {
            manifestAttr(it, "name") == "com.google.android.gms.measurement.AppMeasurementService"
        }.getAttribute("android:enabled"))
        assertEquals("", services.first {
            manifestAttr(it, "name") == "com.example.SyncService"
        }.getAttribute("android:enabled"))
    }

    @Test fun `firebase analytics setter targets collection switch`() {
        assertEquals(
            "Lcom/google/firebase/analytics/FirebaseAnalytics;",
            FirebaseAnalyticsSetter.definingClass,
        )
        assertEquals("setAnalyticsCollectionEnabled", FirebaseAnalyticsSetter.name)
        assertEquals("V", FirebaseAnalyticsSetter.returnType)
        assertEquals(listOf("Z"), FirebaseAnalyticsSetter.parameters)
    }
}
