package com.mparticle.kits;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.mparticle.commerce.Promotion;
import com.mparticle.commerce.TransactionAttributes;
import com.mparticle.identity.IdentityApi;
import com.mparticle.internal.BackgroundTaskHandler;
import com.mparticle.internal.CoreCallbacks;
import com.mparticle.testutils.TestingUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class GoogleAnalyticsFirebaseKitTest {
    GoogleAnalyticsFirebaseKit kitInstance;
    FirebaseAnalytics firebaseSdk;

    @Before
    public void before() throws JSONException {
        FirebaseAnalytics.clearInstance();
        FirebaseAnalytics.setFirebaseId("firebaseId");
        kitInstance = new GoogleAnalyticsFirebaseKit();
        MParticle.setInstance(Mockito.mock(MParticle.class));
        Mockito.when(MParticle.getInstance().Identity()).thenReturn(Mockito.mock(IdentityApi.class));
        KitManagerImpl kitManager = new KitManagerImpl(Mockito.mock(Context.class), null, emptyCoreCallbacks,  new BackgroundTaskHandler() {
            public void executeNetworkRequest(Runnable runnable) { }
        });
        kitInstance.setKitManager(kitManager);
        kitInstance.setConfiguration(KitConfiguration.createKitConfiguration(new JSONObject().put("id", "-1")));
        kitInstance.onKitCreate(new HashMap<String, String>(), Mockito.mock(Context.class));
        firebaseSdk = FirebaseAnalytics.getInstance(null);
    }

    /**
     * make sure that all MPEvents are getting translating their getInfo() value to the bundle of the Firebase event.
     * MPEvent.getName() should be the firebase event name in all cases, except when the MPEvent.type is MPEvent.Search
     */
    @Test
    public void testEmptyEvent() {
        kitInstance.logEvent(new MPEvent.Builder("eventName", MParticle.EventType.Other).build());
        assertEquals(1, firebaseSdk.getLoggedEvents().size());
        Map.Entry<String, Bundle> firebaseEvent = firebaseSdk.getLoggedEvents().get(0);
        assertEquals("eventName", firebaseEvent.getKey());
        assertEquals(0, firebaseEvent.getValue().size());

        for (int i = 0; i < 10; i++) {
            MPEvent event = TestingUtils.getInstance().getRandomMPEventRich();
            firebaseSdk.clearLoggedEvents();
            kitInstance.logEvent(event);
            assertEquals(1, firebaseSdk.getLoggedEvents().size());
            firebaseEvent = firebaseSdk.getLoggedEvents().get(0);
            if (event.getEventType() != MParticle.EventType.Search) {
                assertEquals(event.getEventName(), firebaseEvent.getKey());
            } else {
                assertEquals("search", firebaseEvent.getKey());
            }
            if (event.getInfo() != null) {
                assertEquals(event.getInfo().size(), firebaseEvent.getValue().size());
                for (Map.Entry<String, String> entry : event.getInfo().entrySet()) {
                    assertEquals(entry.getValue(), firebaseEvent.getValue().getString(entry.getKey()));
                }
            }
        }
    }

    /**
     * shouldn't do anything, just don't crash
     */
    @Test
    public void testPromotionCommerceEvent() {
        Promotion promotion = new Promotion();
        promotion.setCreative("asdva");
        promotion.setId("1234");
        promotion.setName("1234asvd");
        promotion.setPosition("2");
        CommerceEvent event = new CommerceEvent.Builder(Promotion.CLICK, promotion).build();
        kitInstance.logEvent(event);
        assertEquals(0, firebaseSdk.getLoggedEvents().size());
    }

    @Test
    public void testCommerceEvent() throws IllegalAccessException {
        for (Field field: Product.class.getFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                firebaseSdk.clearLoggedEvents();
                String eventType = field.get(null).toString();
                CommerceEvent event = new CommerceEvent.Builder(eventType, new Product.Builder("asdv", "asdv", 1.3).build())
                        .transactionAttributes(new TransactionAttributes().setId("235").setRevenue(23.3).setAffiliation("231"))
                        .build();
                kitInstance.logEvent(event);
                assertEquals("failed for event type: " + eventType, 1, firebaseSdk.getLoggedEvents().size());
            }
        }
    }




    CoreCallbacks emptyCoreCallbacks = new CoreCallbacks() {
        @Override
        public boolean isBackgrounded() {
            return false;
        }

        @Override
        public int getUserBucket() {
            return 0;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void setIntegrationAttributes(int i, Map<String, String> map) {

        }

        @Override
        public Map<String, String> getIntegrationAttributes(int i) {
            return null;
        }

        @Override
        public WeakReference<Activity> getCurrentActivity() {
            return null;
        }

        @Override
        public JSONArray getLatestKitConfiguration() {
            return null;
        }

        @Override
        public boolean isPushEnabled() {
            return false;
        }

        @Override
        public String getPushSenderId() {
            return null;
        }

        @Override
        public String getPushInstanceId() {
            return null;
        }

        @Override
        public Uri getLaunchUri() {
            return null;
        }

        @Override
        public String getLaunchAction() {
            return null;
        }

        @Override
        public void replayAndDisableQueue() {
            
        }
    };

}