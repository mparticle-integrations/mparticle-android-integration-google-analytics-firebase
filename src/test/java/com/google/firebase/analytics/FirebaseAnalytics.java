package com.google.firebase.analytics;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FirebaseAnalytics {

    LinkedList<Map.Entry<String, Bundle>> loggedEvents = new LinkedList<>();
    static String firebaseId;

    static FirebaseAnalytics instance;

    public String getFirebaseInstanceId() {
        return firebaseId;
    }

    public static FirebaseAnalytics getInstance(Context context) {
        if (instance == null) {
            instance = new FirebaseAnalytics();
        }
        return instance;
    }

    public void logEvent(String key, Bundle bundle) {
        loggedEvents.add(new AbstractMap.SimpleEntry<>(key, bundle));
    }

    public void setCurrentScreen(Activity currentActivity, String screenName, String classOverride) {

    }

    public void setUserProperty(String key, String value) {

    }

    /**
     * Access Methods
     */

    public static void clearInstance() {
        instance = null;
    }

    public static void setFirebaseId(String firebaseId) {
        FirebaseAnalytics.firebaseId = firebaseId;
    }

    public List<Map.Entry<String, Bundle>> getLoggedEvents() {
        if (loggedEvents == null) {
            loggedEvents = new LinkedList<>();
        }
        return loggedEvents;
    }

    public void clearLoggedEvents() {
        loggedEvents = new LinkedList<>();
    }
}
