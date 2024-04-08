package com.mparticle.kits

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle

import com.google.firebase.analytics.FirebaseAnalytics
import com.mparticle.MPEvent
import com.mparticle.MParticle
import com.mparticle.MParticleOptions
import com.mparticle.MParticleOptions.DataplanOptions
import com.mparticle.commerce.CommerceEvent
import com.mparticle.commerce.Product
import com.mparticle.commerce.Promotion
import com.mparticle.commerce.TransactionAttributes
import com.mparticle.consent.ConsentState
import com.mparticle.consent.GDPRConsent
import com.mparticle.identity.IdentityApi
import com.mparticle.identity.MParticleUser
import com.mparticle.internal.CoreCallbacks
import com.mparticle.internal.CoreCallbacks.KitListener
import com.mparticle.testutils.TestingUtils
import junit.framework.TestCase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
class GoogleAnalyticsFirebaseKitTest {
    private lateinit var kitInstance: GoogleAnalyticsFirebaseKit
    private lateinit var firebaseSdk: FirebaseAnalytics
    var random = Random()

    @Mock
    lateinit var user: MParticleUser

    @Mock
    lateinit var filteredMParticleUser: FilteredMParticleUser

    @Before
    @Throws(JSONException::class)
    fun before() {
        FirebaseAnalytics.clearInstance()
        FirebaseAnalytics.setFirebaseId("firebaseId")
        kitInstance = GoogleAnalyticsFirebaseKit()
        MockitoAnnotations.initMocks(this)
        MParticle.setInstance(Mockito.mock(MParticle::class.java))
        Mockito.`when`(MParticle.getInstance()!!.Identity()).thenReturn(
            Mockito.mock(
                IdentityApi::class.java
            )
        )
        val kitManager = KitManagerImpl(
            Mockito.mock(
                Context::class.java
            ), null, emptyCoreCallbacks, mock(MParticleOptions::class.java)
        )
        kitInstance.kitManager = kitManager
        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("id", "-1"))
        kitInstance.onKitCreate(HashMap(), Mockito.mock(Context::class.java))
        firebaseSdk = FirebaseAnalytics.getInstance(null)!!
    }

    /**
     * make sure that all MPEvents are getting translating their getInfo() value to the bundle of the Firebase event.
     * MPEvent.getName() should be the firebase event name in all cases, except when the MPEvent.type is MPEvent.Search
     */
    @Test
    fun testEmptyEvent() {
        kitInstance.logEvent(MPEvent.Builder("eventName", MParticle.EventType.Other).build())
        TestCase.assertEquals(1, firebaseSdk.loggedEvents.size)
        var firebaseEvent = firebaseSdk.loggedEvents[0]
        TestCase.assertEquals("eventName", firebaseEvent.key)
        TestCase.assertEquals(0, firebaseEvent.value.size())

        for (i in 0..9) {
            val event = TestingUtils.getInstance().randomMPEventRich
            firebaseSdk.clearLoggedEvents()
            kitInstance.logEvent(event)
            TestCase.assertEquals(1, firebaseSdk.loggedEvents.size)
            firebaseEvent = firebaseSdk.loggedEvents[0]
            if (event.eventType != MParticle.EventType.Search) {
                TestCase.assertEquals(
                    kitInstance.standardizeName(event.eventName, true),
                    firebaseEvent.key
                )
            } else {
                TestCase.assertEquals("search", firebaseEvent.key)
            }
            event.customAttributes?.let {
                TestCase.assertEquals(it.size, firebaseEvent.value.size())
                for (customAttEvent in it) {
                    val key = kitInstance.standardizeName(customAttEvent.key, true)
                    val value = kitInstance.standardizeValue(customAttEvent.value as String?, true)
                    if (key != null) {
                        TestCase.assertEquals(
                            value, firebaseEvent.value.getString(key)
                        )
                    }
                }
            }
        }
    }

    /**
     * shouldn't do anything, just don't crash
     */
    @Test
    fun testPromotionCommerceEvent() {
        val promotion = Promotion()
        promotion.creative = "asdva"
        promotion.id = "1234"
        promotion.name = "1234asvd"
        promotion.position = "2"
        val event = CommerceEvent.Builder(Promotion.CLICK, promotion).build()
        kitInstance.logEvent(event)
        TestCase.assertEquals(0, firebaseSdk.loggedEvents.size)
    }

    @Test
    @Throws(IllegalAccessException::class)
    fun testCommerceEvent() {
        for (field in Product::class.java.fields) {
            if (Modifier.isPublic(field.modifiers) && Modifier.isStatic(field.modifiers)) {
                firebaseSdk.clearLoggedEvents()
                val eventType = field?.get(null).toString()
                if (eventType != "remove_from_wishlist") {
                    val event = CommerceEvent.Builder(
                        eventType,
                        Product.Builder("asdv", "asdv", 1.3).build()
                    )
                        .transactionAttributes(
                            TransactionAttributes().setId("235").setRevenue(23.3)
                                .setAffiliation("231")
                        )
                        .build()
                    kitInstance.logEvent(event)
                 /*   TestCase.assertEquals(
                        "failed for event type: $eventType",
                        1,
                        firebaseSdk.loggedEvents.size
                    )*/
                }
            }
        }
    }

    @Test
    fun onConsentStateUpdatedTest() {
        val map = HashMap<String, String>()
        map["defaultAdStorageConsentSDK"] = "Granted"
        map["defaultAnalyticsStorageConsentSDK"] = "Granted"
        map["consentMappingSDK"] =
            "[{\\\"jsmap\\\":null,\\\"map\\\":\\\"Performance\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_user_data\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"Marketing\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_personalization\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"testconsent\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_storage\\\"}]"
        map["defaultAdUserDataConsentSDK"] = "Denied"
        map["defaultAdPersonalizationConsentSDK"] = "Denied"

        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("as", map.toMutableMap()))


        val marketingConsent = GDPRConsent.builder(false)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()
        val state = ConsentState.builder()
            .addGDPRConsentState("Marketing", marketingConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)

        val expectedConsentValue =
            firebaseSdk.getConsentState().getKeyByValue("AD_PERSONALIZATION").toString()
        TestCase.assertEquals("DENIED", expectedConsentValue)
        val expectedConsentValue2 =
            firebaseSdk.getConsentState().getKeyByValue("AD_USER_DATA").toString()
        TestCase.assertEquals("DENIED", expectedConsentValue2)
        val expectedConsentValue3 =
            firebaseSdk.getConsentState().getKeyByValue("ANALYTICS_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue3)
        val expectedConsentValue4 =
            firebaseSdk.getConsentState().getKeyByValue("AD_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue4)
    }

    @Test
    fun onConsentStateUpdatedTest_When_Marketing_true() {
        val map = HashMap<String, String>()
        map["defaultAdStorageConsentSDK"] = "Granted"
        map["defaultAnalyticsStorageConsentSDK"] = "Granted"
        map["consentMappingSDK"] =
            "[{\\\"jsmap\\\":null,\\\"map\\\":\\\"Performance\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_user_data\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"Marketing\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_personalization\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"testconsent\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_storage\\\"}]"
        map["defaultAdUserDataConsentSDK"] = "Denied"
        map["defaultAdPersonalizationConsentSDK"] = "Denied"

        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("as", map.toMutableMap()))

        val marketingConsent = GDPRConsent.builder(true)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()
        val state = ConsentState.builder()
            .addGDPRConsentState("Marketing", marketingConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)

        val expectedConsentValue =
            firebaseSdk.getConsentState().getKeyByValue("AD_PERSONALIZATION").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue)
        val expectedConsentValue2 =
            firebaseSdk.getConsentState().getKeyByValue("AD_USER_DATA").toString()
        TestCase.assertEquals("DENIED", expectedConsentValue2)
        val expectedConsentValue3 =
            firebaseSdk.getConsentState().getKeyByValue("ANALYTICS_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue3)
        val expectedConsentValue4 =
            firebaseSdk.getConsentState().getKeyByValue("AD_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue4)
    }

    @Test
    fun onConsentStateUpdatedTest_When_Performance_true() {
        val map = HashMap<String, String>()
        map["defaultAdStorageConsentSDK"] = "Granted"
        map["defaultAnalyticsStorageConsentSDK"] = "Granted"
        map["consentMappingSDK"] =
            "[{\\\"jsmap\\\":null,\\\"map\\\":\\\"Performance\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_user_data\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"Marketing\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_personalization\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"testconsent\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_storage\\\"}]"
        map["defaultAdUserDataConsentSDK"] = "Denied"
        map["defaultAdPersonalizationConsentSDK"] = "Denied"

        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("as", map.toMutableMap()))

        val performanceConsent = GDPRConsent.builder(true)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()
        val state = ConsentState.builder()
            .addGDPRConsentState("Performance", performanceConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)

        val expectedConsentValue =
            firebaseSdk.getConsentState().getKeyByValue("AD_USER_DATA").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue)
        val expectedConsentValue2 =
            firebaseSdk.getConsentState().getKeyByValue("AD_PERSONALIZATION").toString()
        TestCase.assertEquals("DENIED", expectedConsentValue2)
        val expectedConsentValue3 =
            firebaseSdk.getConsentState().getKeyByValue("ANALYTICS_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue3)
        val expectedConsentValue4 =
            firebaseSdk.getConsentState().getKeyByValue("AD_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue4)

    }

    @Test
    fun onConsentStateUpdatedTest_When_Performance_false() {
        val map = HashMap<String, String>()
        map["defaultAdStorageConsentSDK"] = "Granted"
        map["defaultAnalyticsStorageConsentSDK"] = "Granted"
        map["consentMappingSDK"] =
            "[{\\\"jsmap\\\":null,\\\"map\\\":\\\"Performance\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_user_data\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"Marketing\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_personalization\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"testconsent\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_storage\\\"}]"
        map["defaultAdUserDataConsentSDK"] = "Denied"
        map["defaultAdPersonalizationConsentSDK"] = "Denied"

        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("as", map.toMutableMap()))

        val performanceConsent = GDPRConsent.builder(false)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val state = ConsentState.builder()
            .addGDPRConsentState("Performance", performanceConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)

        val expectedConsentValue =
            firebaseSdk.getConsentState().getKeyByValue("AD_USER_DATA").toString()
        TestCase.assertEquals("DENIED", expectedConsentValue)
        val expectedConsentValue2 =
            firebaseSdk.getConsentState().getKeyByValue("AD_PERSONALIZATION").toString()
        TestCase.assertEquals("DENIED", expectedConsentValue2)
        val expectedConsentValue3 =
            firebaseSdk.getConsentState().getKeyByValue("ANALYTICS_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue3)
        val expectedConsentValue4 =
            firebaseSdk.getConsentState().getKeyByValue("AD_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue4)
    }

    @Test
    fun onConsentStateUpdatedTestPerformance_And_Marketing_are_true() {
        val map = HashMap<String, String>()
        map["defaultAdStorageConsentSDK"] = "Granted"
        map["defaultAnalyticsStorageConsentSDK"] = "Granted"
        map["consentMappingSDK"] =
            "[{\\\"jsmap\\\":null,\\\"map\\\":\\\"Performance\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_user_data\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"Marketing\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_personalization\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"testconsent\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_storage\\\"}]"
        map["defaultAdUserDataConsentSDK"] = "Denied"
        map["defaultAdPersonalizationConsentSDK"] = "Denied"

        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("as", map.toMutableMap()))

        val marketingConsent = GDPRConsent.builder(true)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val performanceConsent = GDPRConsent.builder(true)
            .document("parental_consent_agreement_v2")
            .location("17 Cherry Tree Lan 3")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val state = ConsentState.builder()
            .addGDPRConsentState("Marketing", marketingConsent)
            .addGDPRConsentState("Performance", performanceConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)

        val expectedConsentValue =
            firebaseSdk.getConsentState().getKeyByValue("AD_USER_DATA").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue)
        val expectedConsentValue2 =
            firebaseSdk.getConsentState().getKeyByValue("AD_PERSONALIZATION").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue2)
        val expectedConsentValue3 =
            firebaseSdk.getConsentState().getKeyByValue("ANALYTICS_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue3)
        val expectedConsentValue4 =
            firebaseSdk.getConsentState().getKeyByValue("AD_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue4)
    }

    @Test
    fun onConsentStateUpdatedTest_When_No_Defaults_Values() {
        val map = HashMap<String, String>()
        map["consentMappingSDK"] =
            "[{\\\"jsmap\\\":null,\\\"map\\\":\\\"Performance\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_user_data\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"Marketing\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_personalization\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"testconsent\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_storage\\\"}]"


        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("as", map.toMutableMap()))

        val marketingConsent = GDPRConsent.builder(true)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val performanceConsent = GDPRConsent.builder(true)
            .document("parental_consent_agreement_v2")
            .location("17 Cherry Tree Lan 3")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val state = ConsentState.builder()
            .addGDPRConsentState("Marketing", marketingConsent)
            .addGDPRConsentState("Performance", performanceConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)

        val expectedConsentValue =
            firebaseSdk.getConsentState().getKeyByValue("AD_USER_DATA").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue)
        val expectedConsentValue2 =
            firebaseSdk.getConsentState().getKeyByValue("AD_PERSONALIZATION").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue2)
        TestCase.assertEquals(2, firebaseSdk.getConsentState().size)
    }

    @Test
    fun onConsentStateUpdatedTest_When_No_DATA_From_Server() {

        val marketingConsent = GDPRConsent.builder(true)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val performanceConsent = GDPRConsent.builder(true)
            .document("parental_consent_agreement_v2")
            .location("17 Cherry Tree Lan 3")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val state = ConsentState.builder()
            .addGDPRConsentState("Marketing", marketingConsent)
            .addGDPRConsentState("Performance", performanceConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)


        TestCase.assertEquals(0, firebaseSdk.getConsentState().size)
    }

    @Test
    fun onConsentStateUpdatedTest_No_consentMappingSDK() {
        val map = HashMap<String, String>()
        map["defaultAdStorageConsentSDK"] = "Granted"
        map["defaultAnalyticsStorageConsentSDK"] = "Granted"
        map["defaultAdUserDataConsentSDK"] = "Denied"
        map["defaultAdPersonalizationConsentSDK"] = "Denied"

        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("as", map.toMutableMap()))

        val marketingConsent = GDPRConsent.builder(true)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val performanceConsent = GDPRConsent.builder(true)
            .document("parental_consent_agreement_v2")
            .location("17 Cherry Tree Lan 3")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val state = ConsentState.builder()
            .addGDPRConsentState("Marketing", marketingConsent)
            .addGDPRConsentState("Performance", performanceConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)

        val expectedConsentValue =
            firebaseSdk.getConsentState().getKeyByValue("AD_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue)
        val expectedConsentValue2 =
            firebaseSdk.getConsentState().getKeyByValue("ANALYTICS_STORAGE").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue2)
        val expectedConsentValue3 =
            firebaseSdk.getConsentState().getKeyByValue("AD_USER_DATA").toString()
        TestCase.assertEquals("DENIED", expectedConsentValue3)
        val expectedConsentValue4 =
            firebaseSdk.getConsentState().getKeyByValue("AD_PERSONALIZATION").toString()
        TestCase.assertEquals("DENIED", expectedConsentValue4)
        TestCase.assertEquals(4, firebaseSdk.getConsentState().size)

    }

    @Test
    fun onConsentStateUpdatedTest_When_default_is_Unspecified_And_No_consentMappingSDK() {
        val map = HashMap<String, String>()
        map["defaultAdStorageConsentSDK"] = "Unspecified"
        map["defaultAnalyticsStorageConsentSDK"] = "Unspecified"
        map["defaultAdUserDataConsentSDK"] = "Unspecified"
        map["defaultAdPersonalizationConsentSDK"] = "Unspecified"

        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("as", map.toMutableMap()))

        val marketingConsent = GDPRConsent.builder(true)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val performanceConsent = GDPRConsent.builder(true)
            .document("parental_consent_agreement_v2")
            .location("17 Cherry Tree Lan 3")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()
        val state = ConsentState.builder()
            .addGDPRConsentState("Marketing", marketingConsent)
            .addGDPRConsentState("Performance", performanceConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)


        TestCase.assertEquals(0, firebaseSdk.getConsentState().size)
    }

    @Test
    fun onConsentStateUpdatedTest_When_default_is_Unspecified() {
        val map = HashMap<String, String>()
        map["defaultAdStorageConsentSDK"] = "Unspecified"
        map["defaultAnalyticsStorageConsentSDK"] = "Unspecified"
        map["consentMappingSDK"] =
            "[{\\\"jsmap\\\":null,\\\"map\\\":\\\"Performance\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_user_data\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"Marketing\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_personalization\\\"},{\\\"jsmap\\\":null,\\\"map\\\":\\\"testconsent\\\",\\\"maptype\\\":\\\"ConsentPurposes\\\",\\\"value\\\":\\\"ad_storage\\\"}]"
        map["defaultAdUserDataConsentSDK"] = "Unspecified"
        map["defaultAdPersonalizationConsentSDK"] = "Unspecified"

        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("as", map.toMutableMap()))

        val marketingConsent = GDPRConsent.builder(true)
            .document("Test consent")
            .location("17 Cherry Tree Lane")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()

        val performanceConsent = GDPRConsent.builder(true)
            .document("parental_consent_agreement_v2")
            .location("17 Cherry Tree Lan 3")
            .hardwareId("IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702")
            .build()
        val state = ConsentState.builder()
            .addGDPRConsentState("Marketing", marketingConsent)
            .addGDPRConsentState("Performance", performanceConsent)
            .build()
        filteredMParticleUser = FilteredMParticleUser.getInstance(user, kitInstance)

        kitInstance.onConsentStateUpdated(state, state, filteredMParticleUser)

        val expectedConsentValue =
            firebaseSdk.getConsentState().getKeyByValue("AD_USER_DATA").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue)
        val expectedConsentValue2 =
            firebaseSdk.getConsentState().getKeyByValue("AD_PERSONALIZATION").toString()
        TestCase.assertEquals("GRANTED", expectedConsentValue2)
        TestCase.assertEquals(2, firebaseSdk.getConsentState().size)
    }

    fun MutableMap<Any, Any>.getKeyByValue(inputKey: String): Any? {
        for ((key, mapValue) in entries) {
            if (key.toString() == inputKey) {
                return mapValue
            }
        }
        return null
    }

    @Test
    fun testParseToNestedMap_When_JSON_Is_INVALID() {
        var jsonInput =
            "{'GDPR':{'marketing':'{:false,'timestamp':1711038269644:'Test consent','location':'17 Cherry Tree Lane','hardware_id':'IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702'}','performance':'{'consented':true,'timestamp':1711038269644,'document':'parental_consent_agreement_v2','location':'17 Cherry Tree Lan 3','hardware_id':'IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702'}'},'CCPA':'{'consented':true,'timestamp':1711038269644,'document':'ccpa_consent_agreement_v3','location':'17 Cherry Tree Lane','hardware_id':'IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702'}'}"

        val method: Method = GoogleAnalyticsFirebaseKit::class.java.getDeclaredMethod(
            "parseToNestedMap",
            String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(kitInstance, jsonInput)
        Assert.assertEquals(mutableMapOf<String, Any>(), result)
    }

    @Test
    fun testParseToNestedMap_When_JSON_Is_Empty() {
        var jsonInput = ""

        val method: Method = GoogleAnalyticsFirebaseKit::class.java.getDeclaredMethod(
            "parseToNestedMap",
            String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(kitInstance, jsonInput)
        Assert.assertEquals(mutableMapOf<String, Any>(), result)
    }

    @Test
    fun testSearchKeyInNestedMap_When_Input_Key_Is_Empty_String() {
        val map = mapOf(
            "GDPR" to true,
            "marketing" to mapOf(
                "consented" to false,
                "document" to mapOf(
                    "timestamp" to 1711038269644
                )
            )
        )
        val method: Method = GoogleAnalyticsFirebaseKit::class.java.getDeclaredMethod(
            "searchKeyInNestedMap", Map::class.java,
            Any::class.java
        )
        method.isAccessible = true
        val result = method.invoke(kitInstance, map, "")
        Assert.assertEquals(null, result)
    }

    @Test
    fun testSearchKeyInNestedMap_When_Input_Is_Empty_Map() {
        val emptyMap: Map<String, Int> = emptyMap()
        val method: Method = GoogleAnalyticsFirebaseKit::class.java.getDeclaredMethod(
            "searchKeyInNestedMap", Map::class.java,
            Any::class.java
        )
        method.isAccessible = true
        val result = method.invoke(kitInstance, emptyMap, "1")
        Assert.assertEquals(null, result)
    }

    @Test
    fun testParseConsentMapping_When_Input_Is_Empty_Json() {
        val emptyJson = ""
        val method: Method = GoogleAnalyticsFirebaseKit::class.java.getDeclaredMethod(
            "parseConsentMapping",
            String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(kitInstance, emptyJson)
        Assert.assertEquals(emptyMap<String, String>(), result)
    }

    @Test
    fun testParseConsentMapping_When_Input_Is_Invalid_Json() {
        var jsonInput =
            "{'GDPR':{'marketing':'{:false,'timestamp':1711038269644:'Test consent','location':'17 Cherry Tree Lane','hardware_id':'IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702'}','performance':'{'consented':true,'timestamp':1711038269644,'document':'parental_consent_agreement_v2','location':'17 Cherry Tree Lan 3','hardware_id':'IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702'}'},'CCPA':'{'consented':true,'timestamp':1711038269644,'document':'ccpa_consent_agreement_v3','location':'17 Cherry Tree Lane','hardware_id':'IDFA:a5d934n0-232f-4afc-2e9a-3832d95zc702'}'}"
        val method: Method = GoogleAnalyticsFirebaseKit::class.java.getDeclaredMethod(
            "parseConsentMapping",
            String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(kitInstance, jsonInput)
        Assert.assertEquals(emptyMap<String, String>(), result)
    }

    @Test
    fun testParseConsentMapping_When_Input_Is_NULL() {
        val method: Method = GoogleAnalyticsFirebaseKit::class.java.getDeclaredMethod(
            "parseConsentMapping",
            String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(kitInstance, null)
        Assert.assertEquals(emptyMap<String, String>(), result)
    }


    @Test
    fun testNameStandardization() {
        val badPrefixes = arrayOf("firebase_event_name", "google_event_name", "ga_event_name")
        for (badPrefix in badPrefixes) {
            val clean = kitInstance.standardizeName(badPrefix, random.nextBoolean())
            TestCase.assertEquals("event_name", clean)
        }
        val emptySpace1 = "event name"
        val emptySpace2 = "event_name "
        val emptySpace3 = "event  name "
        val emptySpace4 = "event - name "
        TestCase.assertEquals(
            "event_name",
            kitInstance.standardizeName(emptySpace1, random.nextBoolean())
        )
        TestCase.assertEquals(
            "event_name_",
            kitInstance.standardizeName(emptySpace2, random.nextBoolean())
        )
        TestCase.assertEquals(
            "event_name_",
            kitInstance.standardizeName(emptySpace3, random.nextBoolean())
        )
        TestCase.assertEquals(
            "event_name_",
            kitInstance.standardizeName(emptySpace4, random.nextBoolean())
        )
        val badStarts = arrayOf(
            "!@#$%^&*()_+=[]{}|'\"?><:;event_name",
            "_event_name",
            "   event_name",
            "_event_name"
        )
        for (badStart in badStarts) {
            val clean = kitInstance.standardizeName(badStart, random.nextBoolean())
            TestCase.assertEquals("event_name", clean)
        }
        val tooLong =
            "abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890"
        var sanitized: String = kitInstance.standardizeName(tooLong, true).toString()
        TestCase.assertEquals(40, sanitized.length)
        TestCase.assertTrue(tooLong.startsWith(sanitized))
        sanitized = kitInstance.standardizeName(tooLong, false).toString()
        TestCase.assertEquals(24, sanitized.length)
        TestCase.assertTrue(tooLong.startsWith(sanitized))
        sanitized = kitInstance.standardizeValue(tooLong, true)
        TestCase.assertEquals(100, sanitized.length)
        TestCase.assertTrue(tooLong.startsWith(sanitized))
        sanitized = kitInstance.standardizeValue(tooLong, false)
        TestCase.assertEquals(36, sanitized.length)
        TestCase.assertTrue(tooLong.startsWith(sanitized))

    }

    @Test
    fun testScreenNameSanitized() {
        kitInstance.logScreen("Some long Screen name", emptyMap())
        TestCase.assertEquals(
            "Some_long_Screen_name",
            FirebaseAnalytics.getInstance(null)?.currentScreenName
        )
    }

    private var emptyCoreCallbacks: CoreCallbacks = object : CoreCallbacks {
        var activity = Activity()
        override fun isBackgrounded(): Boolean {
            return false
        }

        override fun getUserBucket(): Int {
            return 0
        }

        override fun isEnabled(): Boolean {
            return false
        }

        override fun setIntegrationAttributes(i: Int, map: Map<String, String>) {}
        override fun getIntegrationAttributes(i: Int): Map<String, String> {
            return emptyMap()
        }

        override fun getCurrentActivity(): WeakReference<Activity> {
            return WeakReference(activity)
        }

        override fun getLatestKitConfiguration(): JSONArray? {
            return null
        }

        override fun getDataplanOptions(): DataplanOptions? {
            return null
        }

        override fun isPushEnabled(): Boolean {
            return false
        }

        override fun getPushSenderId(): String? {
            return null
        }

        override fun getPushInstanceId(): String? {
            return null
        }

        override fun getLaunchUri(): Uri? {
            return null
        }

        override fun getLaunchAction(): String? {
            return null
        }

        override fun getKitListener(): KitListener {
            return KitListener.EMPTY
        }
    }
}